package com.carpool.bot.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * Bot configuration — values injected from application properties.
 * Same token property as NotificationService to avoid duplication.
 */
@Getter
@Configuration
public class BotConfig {

    /**
     * Terms version accepted by the user.
     * NULL = not yet accepted. '1.0' = current version accepted.
     */
    public static final String CURRENT_TERMS_VERSION = "1.0";

    @Value("${carpool.telegram.bot-token}")
    private String botToken;

    @Value("${carpool.telegram.bot-username}")
    private String botUsername;

    @Value("${carpool.community.name:SouthPool}")
    private String communityName;

    @Value("${carpool.community.corridor:South MM to BGC/Makati}")
    private String corridor;

    @Value("${carpool.community.group-invite-link:https://t.me/southispoolofcare}")
    private String groupInviteLink;

    @Value("${carpool.admin.telegram-ids:}")
    private String adminTelegramIds;

    @Value("${carpool.telegram.group-chat-id}")
    private Long groupChatId;

    @Value("${carpool.telegram.group-home-to-work-topic-id}")
    private Integer groupHomeToWorkTopicId;

    @Value("${carpool.telegram.group-work-to-home-topic-id}")
    private Integer groupWorkToHomeTopicId;

    /**
     * Returns true if the given Telegram ID belongs to an admin.
     */
    public boolean isAdmin(Long telegramId) {
        if (adminTelegramIds == null || adminTelegramIds.isBlank()) return false;
        return Arrays.stream(adminTelegramIds.split(","))
                .map(String::trim)
                .anyMatch(id -> id.equals(String.valueOf(telegramId)));
    }
}