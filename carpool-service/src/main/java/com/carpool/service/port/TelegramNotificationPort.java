package com.carpool.service.port;

import java.util.List;

public interface TelegramNotificationPort {

    record InlineButton(String text, String callbackData) {}

    void sendMessage(long chatId, String html);
    void sendMessage(long chatId, String html, boolean appendMenuButton);
    void sendMessageWithKeyboard(long chatId, String html, List<List<InlineButton>> keyboard);
}