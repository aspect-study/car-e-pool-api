package com.carpool.bot.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Per-chatId rate limiter for the Telegram bot.
 * Uses Bucket4j token bucket algorithm — consistent with RateLimitFilter on the REST API layer.
 * Single-instance assumption: state is in-memory.
 * For multi-instance deployment, replace with Redis-backed Bucket4j.
 */
@Slf4j
@Component
public class BotRateLimiter {

    private final Cache<Long, Bucket>  buckets;
    private final Cache<Long, Instant> lastWarned;
    private final Clock clock;
    private final int   capacity;
    private final int   refillTokens;
    private final int   refillSeconds;
    private final int   warnIntervalSeconds;

    public BotRateLimiter(
            Clock clock,
            Ticker ticker,
            @Value("${carpool.bot.rate-limit.capacity}")              int capacity,
            @Value("${carpool.bot.rate-limit.refill-tokens}")         int refillTokens,
            @Value("${carpool.bot.rate-limit.refill-seconds}")        int refillSeconds,
            @Value("${carpool.bot.rate-limit.warn-interval-seconds}") int warnIntervalSeconds) {
        this.clock               = clock;
        this.capacity            = capacity;
        this.refillTokens        = refillTokens;
        this.refillSeconds       = refillSeconds;
        this.warnIntervalSeconds = warnIntervalSeconds;

        this.buckets = Caffeine.newBuilder()
                .ticker(ticker)
                .expireAfterAccess(1, TimeUnit.HOURS)
                .maximumSize(10_000)
                .build();

        this.lastWarned = Caffeine.newBuilder()
                .ticker(ticker)
                .expireAfterAccess(Duration.ofSeconds(warnIntervalSeconds * 2L))
                .maximumSize(10_000)
                .build();
    }

    public boolean tryConsume(Long chatId) {
        Bucket bucket = buckets.get(chatId, this::createBucket);
        boolean allowed = bucket.tryConsume(1);
        if (!allowed) log.warn("Bot rate limit exceeded for chatId={}", chatId);
        return allowed;
    }

    public boolean shouldWarn(Long chatId) {
        Instant now = clock.instant();
        boolean[] fired = {false};
        lastWarned.asMap().compute(chatId, (id, lastWarnAt) -> {
            if (lastWarnAt == null || Duration.between(lastWarnAt, now).getSeconds() >= warnIntervalSeconds) {
                fired[0] = true;
                return now;
            }
            return lastWarnAt;
        });
        return fired[0];
    }

    private Bucket createBucket(Long ignored) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(refillTokens, Duration.ofSeconds(refillSeconds))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}