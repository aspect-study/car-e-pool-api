package com.carpool.domain.enums;

public enum RideStatus {
    DRAFT,      // Just created — not yet visible to passengers
    ACTIVE,     // Published — accepting bookings
    FULL,       // All seats taken — still accepting if cancellation occurs
    DEPARTED,   // Driver has started the ride — no more bookings
    COMPLETED,  // Ride finished — driver marked as done
    CANCELLED   // Cancelled by driver or system
}