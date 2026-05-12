package com.carpool.bot;

import com.carpool.bot.config.BotConfig;
import com.carpool.bot.handler.CallbackHandler;
import com.carpool.bot.handler.MessageHandler;
import com.carpool.bot.ratelimit.BotRateLimiter;
import com.carpool.bot.service.GroupNotificationService;
import com.carpool.bot.util.BotMessageBuilder;
import com.carpool.bot.util.ButtonStyle;
import com.carpool.repository.UserRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Main Telegram bot entry point.
 * Implements SpringLongPollingBot for Spring Boot 3.x/4.x compatibility (telegrambots 8.x).
 * <p>
 * Receives all updates from Telegram and routes them to:
 *   - MessageHandler  — for text messages and commands
 *   - CallbackHandler — for inline button taps
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CarpoolBot implements SpringLongPollingBot, LongPollingUpdateConsumer {

    private final BotConfig         botConfig;
    private final MessageHandler    messageHandler;
    private final CallbackHandler   callbackHandler;
    private final BotRateLimiter    rateLimiter;
    private final UserRepository    userRepository;
    private final TelegramClient    telegramClient;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    // @Lazy breaks the GroupNotificationService ↔ CarpoolBot circular dependency
    @Autowired @Lazy
    private GroupNotificationService groupNotificationService;

    @Override
    public String getBotToken() {
        return botConfig.getBotToken();
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(List<Update> updates) {
        // Multi-threaded processing using Java 25 Virtual Threads
        for (Update update : updates) {
            executor.submit(() -> {
                try {
                    handleParallelUpdate(update);
                } catch (Exception e) {
                    log.error("Critical error in virtual thread: {}", e.getMessage(), e);
                }
            });
        }
    }

    private void handleParallelUpdate(Update update) {
        try {

            if (update.hasMessage()) {
                var msg  = update.getMessage();
                var chat = msg.getChat();
                if (chat.isGroupChat() || chat.isSuperGroupChat()) {
                    if (msg.getNewChatMembers() != null && !msg.getNewChatMembers().isEmpty()) {
                        groupNotificationService.handleNewMembers(msg);
                    } else {
                        log.debug("Ignoring group message from chatId={}", chat.getId());
                    }
                    return;
                }
            }

            // Resolve chatId for rate limiting
            Long chatId = resolveChatId(update);

            if (chatId != null && !rateLimiter.tryConsume(chatId)) {
                // Rate limit exceeded — warn user once per interval, then silent ignore
                if (rateLimiter.shouldWarn(chatId)) {
                    send(BotMessageBuilder.textNoMenu(chatId,
                            "⚠️ Too many requests. Please try again later."));
                }
                return;
            }

            if (update.hasMessage() && update.getMessage().hasText()) {
                // Terms gate — check before routing to MessageHandler
                Long telegramId = update.getMessage().getFrom().getId();
                var userOpt = userRepository.findByTelegramId(telegramId);

                if (userOpt.isPresent() && !userOpt.get().isTermsAccepted()) {
                    // Existing user who hasn't accepted — show reminder
                    // New users are handled inside MessageHandler (auto-register then show welcome)
                    Long gatedChatId = update.getMessage().getChatId();
                    String text = update.getMessage().getText().trim();

                    // Allow /start to pass through so MessageHandler can show welcome screen
                    if (!text.equals("/start")) {
                        send(BotMessageBuilder.textNoMenu(gatedChatId,
                                "⚠️ Please accept our community terms first to use this bot."));
                        send(sendWithInlineInternal(gatedChatId,
                                "Tap below to review and accept:",
                                List.of(List.of(
                                        BotMessageBuilder.button("📄 Review Terms", "TERMS_WELCOME", ButtonStyle.PRIMARY.toString())
                                ))));
                        return;
                    }
                }

                // Handle gate — must have a Telegram @username to use the bot
                if (userOpt.isPresent() && userOpt.get().isTermsAccepted()
                        && userOpt.get().getTelegramHandle() == null) {
                    Long gatedChatId = update.getMessage().getChatId();
                    String text = update.getMessage().getText().trim();
                    if (!text.equals("/start")) {
                        send(BotMessageBuilder.textNoMenu(gatedChatId,
                                """
                                        🚫 <b>Telegram Username Required</b>
                                        
                                        You need a Telegram @username to use this bot.
                                        
                                        📌 <b>Why is this required?</b>
                                        Drivers and passengers need to contact each other \
                                        directly outside the bot to coordinate pickups.
                                        
                                        ⚙️ <b>How to set your username:</b>
                                        1️⃣ Open <b>Telegram Settings</b>
                                        2️⃣ Tap your <b>Profile</b>
                                        3️⃣ Tap <b>Username</b> and set one
                                        
                                        ✅ Once done, send /start and you're good to go!
                                        
                                        <i>Already set it? Just send /start and the bot will detect it automatically.</i>"""));
                        return;
                    }
                }

                // Deleted account gate
                if (userOpt.isPresent() && userOpt.get().isDeleted()) {
                    log.warn("Deleted account attempted bot access telegramId={}",
                            update.getMessage().getFrom().getId());
                    return;
                }

                messageHandler.handle(update.getMessage(), this);

            } else if (update.hasCallbackQuery()) {
                // Terms gate for callbacks
                Long telegramId = update.getCallbackQuery().getFrom().getId();
                String callbackData = update.getCallbackQuery().getData();
                var userOpt = userRepository.findByTelegramId(telegramId);

                // getMessage() is null for channel-post callbacks — nothing to reply to
                var cbMessage = update.getCallbackQuery().getMessage();
                if (cbMessage == null) {
                    answerCallback(update.getCallbackQuery().getId());
                    return;
                }
                Long gatedChatId = cbMessage.getChatId();

                boolean isTermsCallback = callbackData != null && (
                        callbackData.startsWith("TERMS_") );

                if (userOpt.isPresent() && !userOpt.get().isTermsAccepted() && !isTermsCallback) {
                    answerCallback(update.getCallbackQuery().getId());
                    send(sendWithInlineInternal(gatedChatId,
                            "⚠️ Please accept our community terms first.",
                            List.of(List.of(
                                    BotMessageBuilder.button("📄 Review Terms", "TERMS_WELCOME", ButtonStyle.PRIMARY.toString())
                            ))));
                    return;
                }

                // Handle gate for callbacks
                if (userOpt.isPresent() && userOpt.get().isTermsAccepted()
                        && userOpt.get().getTelegramHandle() == null) {
                    answerCallback(update.getCallbackQuery().getId());
                    send(BotMessageBuilder.textNoMenu(gatedChatId,
                            """
                                    🚫 <b>Telegram Username Required</b>
                                    
                                    You need a Telegram @username to use this bot.
                                    
                                    📌 <b>Why is this required?</b>
                                    Drivers and passengers need to contact each other \
                                    directly outside the bot to coordinate pickups.
                                    
                                    ⚙️ <b>How to set your username:</b>
                                    1️⃣ Open <b>Telegram Settings</b>
                                    2️⃣ Tap your <b>Profile</b>
                                    3️⃣ Tap <b>Username</b> and set one
                                    
                                    ✅ Once done, send /start and you're good to go!
                                    
                                    <i>Already set it? Just send /start and the bot will detect it automatically.</i>"""));
                    return;
                }

                // Deleted account gate for callbacks — silent ignore
                if (userOpt.isPresent() && userOpt.get().isDeleted()) {
                    log.warn("Deleted account attempted bot access telegramId={}",
                            update.getCallbackQuery().getFrom().getId());
                    answerCallback(update.getCallbackQuery().getId());
                    return;
                }

                callbackHandler.handle(update.getCallbackQuery(), this);

            } else {
                log.debug("Unhandled update type received");
            }
        } catch (Exception e) {
            log.error("Unhandled exception processing update: {}", e.getMessage(), e);
        }
    }

    // ── Send helpers called by handlers ──────────────────────────────────────

    public void send(SendMessage message) {
        try {
            if (message.getText().isBlank()) {
                log.warn("Attempted to send empty message to chatId={} — skipped",
                        message.getChatId());
                return;
            }

            // Telegram limit is 4096 chars
            if (message.getText().length() > 4096) {
                log.warn("Message too long ({} chars) for chatId={}",
                        message.getText().length(), message.getChatId());
                telegramClient.execute(SendMessage.builder()
                        .chatId(message.getChatId())
                        .text("""
                                ⚠️ Message too long to display.
                                
                                Telegram has a 4,096 character limit per message.""")
                        .parseMode("HTML")
                        .replyMarkup(BotMessageBuilder.inlineButtons(List.of(List.of(
                                BotMessageBuilder.button("🏠 Menu", "MAIN_MENU", ButtonStyle.PRIMARY.toString())
                        ))))
                        .build());
                return;
            }

            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send message to chatId={}: {}",
                    message.getChatId(), e.getMessage());
        }
    }

    public Integer sendReturningId(SendMessage message) {
        try {
            if (message.getText().isBlank()) return null;
            Message sent = telegramClient.execute(message);
            return sent != null ? sent.getMessageId() : null;
        } catch (TelegramApiException e) {
            log.error("Failed to send message to chatId={}: {}", message.getChatId(), e.getMessage());
            return null;
        }
    }

    public void edit(EditMessageText edit) {
        try {
            telegramClient.execute(edit);
        } catch (TelegramApiException e) {
            log.error("Failed to edit message: {}", e.getMessage());
        }
    }

    public void answerCallback(String callbackQueryId) {
        answerCallback(callbackQueryId, null);
    }

    public void answerCallback(String callbackQueryId, String text) {
        try {
            AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQueryId)
                    .text(text)
                    .build();
            telegramClient.execute(answer);
        } catch (TelegramApiException e) {
            log.error("Failed to answer callback: {}", e.getMessage());
        }
    }

    /**
     * Sends a welcome message to the configured welcome topic when new members join the group.
     * Failures are logged but never propagate to the caller.
     */
    public void sendWelcomeToGroup(String text) {
        if (!botConfig.isWelcomeTopicConfigured()) {
            log.debug("Welcome topic not configured — skipping group welcome message");
            return;
        }
        try {
            SendMessage message = SendMessage.builder()
                    .chatId(botConfig.getGroupChatId())
                    .messageThreadId(botConfig.getGroupWelcomeTopicId())
                    .text(text)
                    .parseMode("HTML")
                    .replyMarkup(BotMessageBuilder.inlineButtons(List.of(List.of(
                            InlineKeyboardButton.builder()
                                    .text("🤖 Open " + botConfig.getCommunityName() + " Bot")
                                    .url("https://t.me/" + botConfig.getBotUsername())
                                    .style(ButtonStyle.SUCCESS.toString())
                                    .build()
                    ))))
                    .build();
            telegramClient.execute(message);
            log.info("Group welcome message sent: chatId={} threadId={}",
                    botConfig.getGroupChatId(), botConfig.getGroupWelcomeTopicId());
        } catch (TelegramApiException e) {
            log.error("Failed to send group welcome message: {}", e.getMessage());
        }
    }

    /**
     * Sends a ride announcement to the configured Telegram group topic.
     * Returns the Telegram message ID on success, or null on failure.
     * Failures are logged but never propagate — group posting must not
     * affect the driver's experience.
     */
    public Integer sendToGroup(String text, Long rideId, Long driverId, Integer topicId) {
        try {
            SendMessage message = SendMessage.builder()
                    .chatId(botConfig.getGroupChatId())
                    .messageThreadId(topicId)
                    .text(text)
                    .parseMode("HTML")
                    .replyMarkup(BotMessageBuilder.inlineButtons(List.of(
                            List.of(
                                    InlineKeyboardButton.builder()
                                    .text("\uD83D\uDE98#"+rideId +" ❯❯❯❯ | View | Request a Seat")
                                            .url("https://t.me/" + botConfig.getBotUsername()
                                                    + "?start=RIDE_" + rideId)
                                            .style(ButtonStyle.SUCCESS.toString())
                                            .build()
                            ),
                            List.of(
                                    InlineKeyboardButton.builder()
                                            .text("⭐ Get Ride Alerts")
                                            .url("https://t.me/" + botConfig.getBotUsername()
                                                    + "?start=FOLLOW_RIDE_" + driverId + "_" + rideId)
                                            .style(ButtonStyle.PRIMARY.toString())
                                            .build()
                            )
                    )))
                    .build();
            Message sent = telegramClient.execute(message);
            if (sent == null) {
                log.error("sendToGroup returned null message: rideId={}", rideId);
                return null;
            }
            log.info("Group ride announcement sent: rideId={} chatId={} threadId={} messageId={}",
                    rideId, botConfig.getGroupChatId(), topicId, sent.getMessageId());
            return sent.getMessageId();
        } catch (TelegramApiException e) {
            log.error("Failed to send group announcement: rideId={} error={}",
                    rideId, e.getMessage());
            return null;
        }
    }

    /**
     * Sends a message with a View Ride inline button directly to a user.
     * Used for favorite driver alerts — sends to the follower's chat.
     */
    public void sendToUser(Long telegramId, String text, Long rideId, Long driverId) {
        try {
            SendMessage message = SendMessage.builder()
                    .chatId(telegramId)
                    .text(text)
                    .parseMode("HTML")
                    .replyMarkup(BotMessageBuilder.inlineButtons(List.of(
                            List.of(
                                    InlineKeyboardButton.builder()
                                            .text("👀 View Ride")
                                            .callbackData("VIEW_RIDE:" + rideId)
                                            .style(ButtonStyle.PRIMARY.toString())
                                            .build(),
                                    InlineKeyboardButton.builder()
                                            .text("🎫 Book Ride")
                                            .callbackData("BOOK_RIDE:" + rideId)
                                            .style(ButtonStyle.SUCCESS.toString())
                                            .build()
                            ),
                            List.of(
                                    InlineKeyboardButton.builder()
                                            .text("🔕 Unfollow")
                                            .callbackData("UNFOLLOW_DRIVER:" + driverId)
                                            .style(ButtonStyle.DANGER.toString())
                                            .build()
                            )
                    )))
                    .build();

            telegramClient.execute(message);
            log.info("Direct message sent to telegramId={} rideId={}",
                    telegramId, rideId);
        } catch (TelegramApiException e) {
            log.error("Failed to send direct message to telegramId={} error={}",
                    telegramId, e.getMessage());
        }
    }

    /**
     * Resolves chatId from any update type.
     * Returns null for update types without a chat context.
     */
    private Long resolveChatId(Update update) {
        if (update.hasMessage()) {
            return update.getMessage().getChatId();
        }
        if (update.hasCallbackQuery()) {
            var msg = update.getCallbackQuery().getMessage();
            return msg != null ? msg.getChatId() : null;
        }
        return null;
    }

    /**
     * Internal helper for building inline messages within CarpoolBot.
     * Avoids circular dependency on handler utilities.
     */
    private SendMessage sendWithInlineInternal(Long chatId, String text,
                                               List<List<InlineKeyboardButton>> rows) {
        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .replyMarkup(BotMessageBuilder.inlineButtons(rows))
                .build();
    }

    public boolean deleteMessage(Long chatId, Integer messageId) {
        try {
            telegramClient.execute(DeleteMessage.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .build());
            return true;
        } catch (TelegramApiException e) {
            if (e.getMessage() != null && e.getMessage().contains("message to delete not found")) {
                log.debug("Group message already deleted: chatId={} messageId={}", chatId, messageId);
                return true;
            } else {
                log.warn("Failed to delete message: chatId={} messageId={} error={}",
                        chatId, messageId, e.getMessage());
            }
            return false;
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down Virtual Thread Executor...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                log.warn("Forced shutdown after 5s — some in-flight tasks may have been dropped");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}