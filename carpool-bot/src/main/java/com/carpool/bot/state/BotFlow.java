package com.carpool.bot.state;

/**
 * Represents all possible conversation flows a user can be in.
 * IDLE = no active flow, waiting for command.
 */
public enum BotFlow {

    IDLE,

    // ── Driver: Post a ride ───────────────────────────────────────────────
    POST_RIDE_DIRECTION,
    POST_RIDE_DEPARTURE_TIME,
    POST_RIDE_ORIGIN,
    POST_RIDE_DESTINATION,
    POST_RIDE_SEATS,
    POST_RIDE_CONTRIBUTION,
    POST_RIDE_NOTES,
    POST_RIDE_CONFIRM,

    // ── Driver: Manage active ride ────────────────────────────────────────
    MANAGE_RIDE,

    // ── Passenger: Search rides ───────────────────────────────────────────
    SEARCH_SELECT_DIRECTION,
    SEARCH_SELECT_TIME,
    SEARCH_RESULTS,
    SEARCH_VIEW_RIDE,

    // ── Passenger: My bookings ────────────────────────────────────────────
    MY_BOOKINGS
}