package com.carpool.common.exception;

public class NotBookingOwnerException extends ForbiddenException {
    public NotBookingOwnerException() {
        super("NOT_BOOKING_OWNER",
                "You do not have permission to modify this booking.");
    }
}