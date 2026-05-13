package com.carpool.bot.config;

import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.Clock;

/**
 * Produces the TelegramClient bean independently of CarpoolBot.
 * Breaks the circular dependency — CarpoolBot receives TelegramClient
 * via constructor injection like any other Spring bean.
 */
@Configuration
public class TelegramClientConfig {

    @Bean
    public TelegramClient telegramClient(BotConfig botConfig) {
        return new OkHttpTelegramClient(botConfig.getBotToken());
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public Ticker ticker() {
        return Ticker.systemTicker();
    }
}