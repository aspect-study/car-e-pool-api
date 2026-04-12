package com.carpool.common.exception;

public class RideFullException extends BadRequestException {
    public RideFullException(Long rideId) {
        super("RIDE_FULL", "Ride " + rideId + " has no available seats.");
    }
}
