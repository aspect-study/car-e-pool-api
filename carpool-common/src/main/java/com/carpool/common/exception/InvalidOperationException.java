package com.carpool.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidOperationException extends CarpoolException {
    public InvalidOperationException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_OPERATION", message);
    }
}
