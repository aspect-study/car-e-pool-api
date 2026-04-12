package com.carpool.common.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends CarpoolException {
    public ForbiddenException(String errorCode, String message) {
        super(HttpStatus.FORBIDDEN, errorCode, message);
    }
}
