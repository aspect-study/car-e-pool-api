package com.carpool.service.ride;

/**
 * Shared seat-count bounds for a ride's total capacity — used by both ride creation
 * validation (CreateRideRequest) and RideService.updateTotalSeats() so the two limits
 * can't drift out of sync.
 */
public final class RideSeatLimits {

    public static final int MIN_TOTAL_SEATS = 1;
    public static final int MAX_TOTAL_SEATS = 8;

    private RideSeatLimits() {}
}
