package com.carpool.bot.handler;

import com.carpool.bot.CarpoolBot;
import com.carpool.bot.state.UserState;

/**
 * Immutable value object passed to every BotCommand execution.
 * Eliminates the repetitive (chatId, carpoolUserId, telegramId, state, parts, bot)
 * parameter list that existed across all handler methods.
 */
public record BotContext(
        Long chatId,
        Long carpoolUserId,
        Long telegramId,
        UserState state,
        String payload,       // parts[1] if present, null otherwise
        String[] parts,       // full split of callback data on ":"
        CarpoolBot bot,
        Integer messageId
) {

    /**
     * Convenience — returns payload parsed as Long, or null if absent/unparseable.
     * Used by handlers that need an entity ID from the callback payload.
     */
    public Long entityId() {
        if (payload == null) return null;
        try {
            return Long.parseLong(payload);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}