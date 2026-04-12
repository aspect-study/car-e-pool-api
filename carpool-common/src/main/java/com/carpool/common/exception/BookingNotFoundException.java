package com.carpool.common.exception;

public class BookingNotFoundException extends ResourceNotFoundException {
    public BookingNotFoundException(Long bookingId) {
        super("BOOKING_NOT_FOUND", "Booking not found with id: " + bookingId);
    }
}
