package com.carpool.common.exception;

public class DuplicateBookingException extends BadRequestException {
    public DuplicateBookingException(Long rideId) {
        super("DUPLICATE_BOOKING", "You already have a booking on ride " + rideId + ".");
    }
}
