package com.carpool.bot.state;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Manages per-user conversation state using an in-memory Caffeine cache.
 * <p>
 * Key   = Telegram chat ID (Long)
 * Value = UserState (immutable, replaced on each transition)
 * <p>
 * TTL: 30 minutes of inactivity resets the user to IDLE.
 * This handles the case where a user abandons a flow mid-way.
 */
@Slf4j
@Component
public class StateManager {

    private final Cache<Long, UserState> cache = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    /**
     * Get current state for a chat ID.
     * Returns IDLE state with null carpoolUserId if no state exists yet.
     */
    public UserState get(Long chatId) {
        return cache.getIfPresent(chatId);
    }

    /**
     * Save updated state for a chat ID.
     * Always replaces the entire state object (immutable @With pattern).
     */
    public void save(Long chatId, UserState state) {
        cache.put(chatId, state);
        log.debug("State saved: chatId={} flow={}", chatId, state.getFlow());
    }

    /**
     * Reset user to IDLE — called after flow completion or on /cancel.
     * Preserves carpoolUserId so the user doesn't need to re-authenticate.
     */
    public void reset(Long chatId) {
        UserState current = get(chatId);
        if (current != null) {
            save(chatId, UserState.initial(current.getCarpoolUserId()));
        } else {
            cache.invalidate(chatId);
        }
        log.debug("State reset: chatId={}", chatId);
    }

}