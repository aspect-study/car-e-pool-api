package com.carpool.common.exception;

public class NotRideOwnerException extends ForbiddenException {
    public NotRideOwnerException() {
        super("NOT_RIDE_OWNER",
                "You do not have permission to modify this ride.");
    }
}