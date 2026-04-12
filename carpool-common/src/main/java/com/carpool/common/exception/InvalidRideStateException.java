package com.carpool.common.exception;

public class InvalidRideStateException extends BadRequestException {
    public InvalidRideStateException(String message) {
        super("INVALID_RIDE_STATE", message);
    }
}
