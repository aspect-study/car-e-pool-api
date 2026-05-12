package com.carpool.bot.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-chatId rate limiter for the Telegram bot.
 * <p>
 * Uses Bucket4j token bucket algorithm — consistent with RateLimitFilter
 * on the REST API layer.
 * <p>
 * Each chatId gets its own bucket. Buckets are created lazily on first request.
 * Warning messages are throttled to once per warn-interval to avoid pile-up.
 * <p>
 * Single-instance assumption: state is in-memory.
 * For multi-instance deployment, replace ConcurrentHashMap with Redis-backed Bucket4j.
 */
@Slf4j
@Component
public class BotRateLimiter {

    private final ConcurrentHashMap<Long, Bucket>  buckets      = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Instant> lastWarned   = new ConcurrentHashMap<>();

    private final int capacity;
    private final int refillTokens;
    private final int refillSeconds;
    private final int warnIntervalSeconds;

    public BotRateLimiter(
            @Value("${carpool.bot.rate-limit.capacity}")             int capacity,
            @Value("${carpool.bot.rate-limit.refill-tokens}")        int refillTokens,
            @Value("${carpool.bot.rate-limit.refill-seconds}")       int refillSeconds,
            @Value("${carpool.bot.rate-limit.warn-interval-seconds}") int warnIntervalSeconds) {
        this.capacity           = capacity;
        this.refillTokens       = refillTokens;
        this.refillSeconds      = refillSeconds;
        this.warnIntervalSeconds = warnIntervalSeconds;
    }

    /**
     * Attempt to consume one token for the given chatId.
     *
     * @return true if request is allowed, false if rate limit exceeded
     */
    public boolean tryConsume(Long chatId) {
        Bucket bucket = buckets.computeIfAbsent(chatId, this::createBucket);
        boolean allowed = bucket.tryConsume(1);

        if (!allowed) {
            log.warn("Bot rate limit exceeded for chatId={}", chatId);
        }

        return allowed;
    }

    /**
     * Check if a warning should be sent to this chatId.
     * Throttled to once per warn-interval to avoid message pile-up.
     *
     * @return true if warning should be sent, false if already warned recently
     */
    public boolean shouldWarn(Long chatId) {
        Instant now = Instant.now();
        boolean[] fired = {false};
        lastWarned.compute(chatId, (id, lastWarnAt) -> {
            if (lastWarnAt == null ||
                    Duration.between(lastWarnAt, now).getSeconds() >= warnIntervalSeconds) {
                fired[0] = true;
                return now;
            }
            return lastWarnAt;
        });
        return fired[0];
    }

    private Bucket createBucket(Long chatId) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(refillTokens, Duration.ofSeconds(refillSeconds))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}