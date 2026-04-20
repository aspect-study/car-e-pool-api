package com.carpool.common.exception;

public class RideNotFoundException extends ResourceNotFoundException {
    public RideNotFoundException(Long rideId) {
        super("RIDE_NOT_FOUND",
                "This ride is no longer available. It may have been cancelled or completed.");
    }
}