package com.carpool.bot.adapter;

import com.carpool.bot.util.BotMessageBuilder;
import com.carpool.bot.util.ButtonStyle;
import com.carpool.service.port.TelegramNotificationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TelegramNotificationAdapter implements TelegramNotificationPort {

    private final TelegramClient telegramClient;

    @Override
    public void sendMessage(long chatId, String html) {
        sendMessage(chatId, html, true);
    }

    @Override
    public void sendMessage(long chatId, String html, boolean appendMenuButton) {
        var msg = SendMessage.builder().chatId(chatId).text(html).parseMode("HTML");
        if (appendMenuButton) {
            msg.replyMarkup(BotMessageBuilder.inlineButtons(
                    List.of(List.of(BotMessageBuilder.button(
                            "🏠 Go to Menu", "MAIN_MENU", ButtonStyle.PRIMARY.toString())))));
        }
        execute(msg.build());
    }

    @Override
    public void sendMessageWithKeyboard(long chatId, String html,
                                        List<List<TelegramNotificationPort.InlineButton>> keyboard) {
        var rows = keyboard.stream()
                .map(row -> new InlineKeyboardRow(row.stream()
                        .map(b -> InlineKeyboardButton.builder()
                                .text(b.text()).callbackData(b.callbackData()).build())
                        .toList()))
                .toList();
        execute(SendMessage.builder()
                .chatId(chatId).text(html).parseMode("HTML")
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build());
    }

    // Propagates as RuntimeException so NotificationService's try/catch can mark FAILED.
    private void execute(SendMessage msg) {
        try {
            telegramClient.execute(msg);
        } catch (Exception e) {
            throw new RuntimeException("Telegram send failed for chatId=" + msg.getChatId(), e);
        }
    }
}