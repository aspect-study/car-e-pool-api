package com.carpool.bot.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Bot configuration — values injected from application properties.
 * Same token property as NotificationService to avoid duplication.
 */
@Getter
@Configuration
public class BotConfig {

    public static final String CURRENT_TERMS_VERSION = "1.0";

    @Value("${carpool.telegram.bot-token}")
    private String botToken;

    @Value("${carpool.telegram.bot-username}")
    private String botUsername;

    @Value("${carpool.community.name:SouthPool}")
    private String communityName;

    @Value("${carpool.community.corridor:South MM to BGC/Makati}")
    private String corridor;
}