package com.carpool.bot.state;

import com.carpool.domain.enums.RideDirection;
import lombok.Builder;
import lombok.Getter;
import lombok.With;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable per-user conversation state stored in Caffeine cache.
 *
 * @With (Lombok) generates withXxx() methods that return a new instance
 * with the specified field changed — keeps state transitions explicit and safe.
 *
 * Stored in StateManager keyed by Telegram chat ID (Long).
 */
@Getter
@Builder
@With
public class UserState {

    // Current position in the conversation flow
    @Builder.Default
    private final BotFlow flow = BotFlow.IDLE;

    // The carpool user ID (from our DB) — set after first /start
    private final Long carpoolUserId;

    // ── Ride posting state ────────────────────────────────────────────────
    private final RideDirection direction;
    private final Long originHubId;
    private final String originHubName;
    private final Long destinationHubId;
    private final String destinationHubName;
    private final LocalDateTime departureTime;
    private final Integer seats;
    private final BigDecimal contribution;
    private final String notes;

    // ── Navigation state ──────────────────────────────────────────────────
    // Used for back navigation and confirmation screens
    private final Long selectedRideId;
    private final Long selectedBookingId;

    // ── Repost state ──────────────────────────────────────────────────────
    // Stores last posted ride ID for quick repost feature
    private final Long lastPostedRideId;

    // ── Search time range ─────────────────────────────────────────────
    // User's preferred departure time window for ride search
    private final LocalDateTime searchFrom;
    private final LocalDateTime searchTo;

    public static UserState initial(Long carpoolUserId) {
        return UserState.builder()
                .flow(BotFlow.IDLE)
                .carpoolUserId(carpoolUserId)
                .build();
    }
}