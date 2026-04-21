package com.carpool.service.dto.response;

/**
 * Profile statistics for a user — role-aware.
 * Driver stats are null if user has never posted a ride.
 * Passenger stats are null if user has never booked a ride.
 */
public record ProfileStatsResponse(

        // ── Basic info ────────────────────────────────────────────────────
        Long   userId,
        String fullName,
        String telegramHandle,
        String roleLabel,          // "Driver", "Passenger", "Driver & Passenger"
        String memberSince,        // formatted: "Apr 1, 2026"

        // ── Driver stats (null if never drove) ────────────────────────────
        Integer driverRidesPosted,
        Integer driverCompleted,
        Integer driverCancelled,
        Integer driverPassengersServed,
        Integer driverCompletionRate,  // percentage 0-100, null if no rides

        // ── Passenger stats (null if never booked) ────────────────────────
        Integer passengerBookingsMade,
        Integer passengerCompleted,
        Integer passengerCancelledByMe,
        Integer passengerCompletionRate  // percentage 0-100, null if no bookings
) {}