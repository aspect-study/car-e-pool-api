package com.carpool.bot.state;

/**
 * Represents all possible conversation flows a user can be in.
 * IDLE = no active flow, waiting for command.
 */
public enum BotFlow {

    IDLE,

    // ── Driver: Post a ride ───────────────────────────────────────────────
    POST_RIDE_DIRECTION,
    POST_RIDE_SELECT_DATE,        // calendar shown for post ride flow
    POST_RIDE_TIME_PICK,          // waiting for time slot tap
    POST_RIDE_TIME_NAV,           // navigating earlier/later time window
    POST_RIDE_DEPARTURE_TIME,
    POST_RIDE_ORIGIN,
    POST_RIDE_DESTINATION,
    POST_RIDE_SEATS,
    POST_RIDE_CONTRIBUTION,
    POST_RIDE_NOTES,              // showing saved notes selection
    POST_RIDE_NOTES_WRITE,        // user chose to write custom note — waiting for text input
    BOOKING_MESSAGE,              // passenger typing optional message before booking
    POST_RIDE_VEHICLE_SELECT,     // showing vehicle selection screen
    POST_RIDE_VEHICLE_CONFIRM,    // showing new-vehicle confirmation before saving
    SET_VEHICLE_COLOR,            // waiting for car color input
    SET_VEHICLE_MODEL,            // waiting for car model input
    SET_VEHICLE_PLATE,            // waiting for plate number input
    SET_VEHICLE_CAPACITY,         // waiting for seat capacity input
    POST_RIDE_CONFIRM,

    // ── Repost edit ───────────────────────────────────────────────────────
    REPOST_EDIT_SEATS,
    REPOST_EDIT_CONTRIBUTION,
    REPOST_EDIT_NOTES,

    // ── Re-announce seat edit ─────────────────────────────────────────────
    REANNOUNCE_EDIT_SEATS,

    // ── Driver: Manage active ride ────────────────────────────────────────
    MANAGE_RIDE,
    EDIT_RIDE_TIME_SELECT_DATE,  // calendar shown for edit departure time flow
    EDIT_RIDE_TIME_PICK,         // waiting for time slot tap in edit time flow
    EDIT_RIDE_TIME_CONFIRM,      // confirmation screen before committing the update

    // ── Passenger: Search rides ───────────────────────────────────────────────
    SEARCH_SELECT_DIRECTION,
    SEARCH_SELECT_DATE,
    SEARCH_CALENDAR_NAV,
    SEARCH_SELECT_TIME,
    SEARCH_RESULTS,
    SEARCH_VIEW_RIDE,
    SEARCH_FILTER,              // user is on filter & sort screen

    // ── Passenger: My bookings ────────────────────────────────────────────
    MY_BOOKINGS,

    // ── Rating flow ───────────────────────────────────────────────────────────
    RATING_STARS,        // waiting for passenger/driver to tap a star rating
    RATING_COMMENT,      // waiting for optional comment text after stars selected
    RATING_FAVORITE,     // showing save as favorite prompt after rating saved

    // Help flows
    HELP_MAIN,
    HELP_POST_RIDE,
    HELP_FIND_RIDE,
    HELP_RULES,
    HELP_COMMANDS
}