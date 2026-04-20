package com.carpool.common.exception;

public class DuplicateBookingException extends BadRequestException {
    public DuplicateBookingException(Long rideId) {
        super("DUPLICATE_BOOKING",
                "You already have an active booking request on this ride. " +
                        "Please wait for the driver to respond or cancel your existing request.");
    }
}