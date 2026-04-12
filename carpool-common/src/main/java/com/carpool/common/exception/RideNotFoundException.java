package com.carpool.common.exception;

public class RideNotFoundException extends ResourceNotFoundException {
    public RideNotFoundException(Long rideId) {
        super("RIDE_NOT_FOUND", "Ride not found with id: " + rideId);
    }
}
