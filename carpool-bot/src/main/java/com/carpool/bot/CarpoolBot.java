package com.carpool.bot;

import com.carpool.bot.config.BotConfig;
import com.carpool.bot.handler.CallbackHandler;
import com.carpool.bot.handler.MessageHandler;
import com.carpool.bot.ratelimit.BotRateLimiter;
import com.carpool.bot.util.BotMessageBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Main Telegram bot entry point.
 * Implements SpringLongPollingBot for Spring Boot 3.x/4.x compatibility (telegrambots 8.x).
 *
 * Receives all updates from Telegram and routes them to:
 *   - MessageHandler  — for text messages and commands
 *   - CallbackHandler — for inline button taps
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CarpoolBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final BotConfig       botConfig;
    private final MessageHandler  messageHandler;
    private final CallbackHandler callbackHandler;
    private final BotRateLimiter rateLimiter;

    // Lazy-initialized to avoid circular dependency
    private TelegramClient telegramClient;

    private TelegramClient getClient() {
        if (telegramClient == null) {
            telegramClient = new OkHttpTelegramClient(getBotToken());
        }
        return telegramClient;
    }

    @Override
    public String getBotToken() {
        return botConfig.getBotToken();
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        try {
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
                messageHandler.handle(update.getMessage(), this);

            } else if (update.hasCallbackQuery()) {
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
            if (message.getText() == null || message.getText().isBlank()) {
                log.warn("Attempted to send empty message to chatId={} — skipped",
                        message.getChatId());
                return;
            }

            // Telegram limit is 4096 chars — truncate if needed
            if (message.getText().length() > 4096) {
                log.warn("Message too long ({} chars) for chatId={} — truncating",
                        message.getText().length(), message.getChatId());
                message.setText(message.getText().substring(0, 4090) + "\n...");
            }

            getClient().execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send message to chatId={}: {}",
                    message.getChatId(), e.getMessage());
        }
    }

    public void edit(EditMessageText edit) {
        try {
            getClient().execute(edit);
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
            getClient().execute(answer);
        } catch (TelegramApiException e) {
            log.error("Failed to answer callback: {}", e.getMessage());
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
            return update.getCallbackQuery().getMessage().getChatId();
        }
        return null;
    }
}