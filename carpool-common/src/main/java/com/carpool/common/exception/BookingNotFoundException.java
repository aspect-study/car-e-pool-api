package com.carpool.common.exception;

public class BookingNotFoundException extends ResourceNotFoundException {
    public BookingNotFoundException(Long bookingId) {
        super("BOOKING_NOT_FOUND",
                "This booking no longer exists. It may have already been cancelled or expired.");
    }
}