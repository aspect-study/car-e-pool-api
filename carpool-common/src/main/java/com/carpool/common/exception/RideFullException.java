package com.carpool.common.exception;

public class RideFullException extends BadRequestException {
    public RideFullException(Long rideId) {
        super("RIDE_FULL",
                "Sorry, this ride is already fully booked. Please look for another available ride.");
    }
}