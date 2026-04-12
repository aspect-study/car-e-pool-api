package com.carpool.common.exception;

public class NotRideOwnerException extends ForbiddenException {
    public NotRideOwnerException() {
        super("NOT_RIDE_OWNER", "You are not the owner of this ride.");
    }
}
