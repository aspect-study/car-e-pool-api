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
    POST_RIDE_NOTES,              // showing saved notes selection
    POST_RIDE_NOTES_WRITE,        // user chose to write custom note — waiting for text input
    BOOKING_MESSAGE,              // passenger typing optional message before booking
    POST_RIDE_VEHICLE_CONFIRM,    // showing vehicle confirmation screen
    SET_VEHICLE_COLOR,            // waiting for car color input
    SET_VEHICLE_MODEL,            // waiting for car model input
    SET_VEHICLE_PLATE,            // waiting for plate number input
    POST_RIDE_CONFIRM,

    // ── Driver: Manage active ride ────────────────────────────────────────
    MANAGE_RIDE,

    // ── Passenger: Search rides ───────────────────────────────────────────────
    SEARCH_SELECT_DIRECTION,
    SEARCH_SELECT_TIME,
    SEARCH_RESULTS,
    SEARCH_VIEW_RIDE,
    SEARCH_FILTER,              // user is on filter & sort screen

    // ── Passenger: My bookings ────────────────────────────────────────────
    MY_BOOKINGS,

    // Help flows
    HELP_MAIN,
    HELP_POST_RIDE,
    HELP_FIND_RIDE,
    HELP_RULES,
    HELP_COMMANDS
}