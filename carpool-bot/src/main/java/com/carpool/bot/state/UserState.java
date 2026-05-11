package com.carpool.bot.state;

import com.carpool.domain.enums.RideDirection;
import lombok.Builder;
import lombok.Getter;
import lombok.With;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

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
    private final Integer timeWindowStart; // start hour of visible time picker window (0-23)
    private final Integer seats;
    private final BigDecimal contribution;
    private final String notes;

    // ── Repost edit state ─────────────────────────────────────────────────
    @Builder.Default
    private final boolean repostEditMode = false;
    private final Integer repostEditMsgId;

    // ── Navigation state ──────────────────────────────────────────────────
    // Used for back navigation and confirmation screens
    private final Long selectedRideId;
    private final Long selectedBookingId;

    // ── Search time range ─────────────────────────────────────────────
    // User's preferred departure time window for ride search
    private final LocalDateTime searchFrom;
    private final LocalDateTime searchTo;

    // ── Repost state ──────────────────────────────────────────────────
    // Stores last posted ride ID for quick repost feature
    private final Long lastPostedRideId;

    // ── Driver notes state ────────────────────────────────────────────
    // Stores selected note ID during notes preview step
    private final Long selectedNoteId;

    // ── Vehicle state ─────────────────────────────────────────────────
    // Temporary storage during vehicle input flow
    // Cleared after save or cancel — never persisted in state long-term
    private final String pendingCarColor;
    private final String pendingCarModel;
    private final String pendingPlateNumber;

    // ── Rating temporary state ────────────────────────────────────────────────
    // Cleared after rating is submitted or skipped.
    private final Long    pendingRatingRideId;  // ride being rated
    private final Long    pendingRateeId;       // user being rated
    private final Integer pendingStars;         // stars selected, held during comment step

    // ── Search calendar state ─────────────────────────────────────────────
    // Tracks selected date and current calendar month during date picker flow
    private final LocalDate searchDay;      // date selected by user
    private final YearMonth calendarMonth;  // current month shown on calendar

    public static UserState initial(Long carpoolUserId) {
        return UserState.builder()
                .flow(BotFlow.IDLE)
                .carpoolUserId(carpoolUserId)
                .build();
    }

    // ── Search filters ────────────────────────────────────────────────
    // Applied when user taps "Filter & Sort" on search results
    private final BigDecimal filterMaxPrice;   // null = any price
    private final Integer    filterMinSeats;   // null = any, 1/2/3
    private final String     filterSortBy;     // EARLIEST, CHEAPEST, MOST_SEATS — default EARLIEST

    // ── Pagination state ──────────────────────────────────────────────
    // Current page in search results (0-indexed)
    @Builder.Default
    private final int searchPage = 0;
}