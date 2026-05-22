package com.carpool.domain.enums;

/**
 * All notification type identifiers used in the system.
 * Reference these constants instead of hardcoding strings.
 */
public final class NotificationTypes {

    private NotificationTypes() {}

    // Sent to DRIVER
    public static final String BOOKING_RECEIVED       = "BOOKING_RECEIVED";
    public static final String BOOKING_CANCELLED_BY_PASSENGER = "BOOKING_CANCELLED_BY_PASSENGER";

    // Sent to PASSENGER
    public static final String BOOKING_CONFIRMED            = "BOOKING_CONFIRMED";
    public static final String BOOKING_DECLINED             = "BOOKING_DECLINED";
    public static final String BOOKING_TIMED_OUT            = "BOOKING_TIMED_OUT";
    public static final String BOOKING_CANCELLED_BY_DRIVER  = "BOOKING_CANCELLED_BY_DRIVER";
    public static final String RIDE_CANCELLED               = "RIDE_CANCELLED";
    public static final String RIDE_COMPLETED               = "RIDE_COMPLETED";
    public static final String RIDE_DEPARTED                = "RIDE_DEPARTED";
    public static final String RIDE_TIME_CHANGED            = "RIDE_TIME_CHANGED";

    // Sent to BOTH (or per-role as needed)
    public static final String PAYMENT_REMINDER       = "PAYMENT_REMINDER";
    public static final String RIDE_DEPARTURE_REMINDER =  "RIDE_DEPARTURE_REMINDER";

    // ── Rating ────────────────────────────────────────────────────────────────
    // Sent to both driver and passenger after ride completion — prompts rating.
    public static final String RATING_PROMPT = "RATING_PROMPT";

    // ── Favorites ─────────────────────────────────────────────────────────────
    // Sent to followers when a favorite driver posts a new ride.
    public static final String FAVORITE_DRIVER_POSTED = "FAVORITE_DRIVER_POSTED";
}
