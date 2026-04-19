package com.carpool.domain.enums;

public enum BookingStatus {
    PENDING,                // Awaiting driver approval
    CONFIRMED,              // Driver accepted
    CANCELLED_BY_PASSENGER, // Passenger cancelled
    CANCELLED_BY_DRIVER,    // Driver cancelled
    COMPLETED,              // Ride completed
    DECLINED,               // Driver explicitly declined
    TIMED_OUT               // Auto-declined after 3 reminders with no response
}