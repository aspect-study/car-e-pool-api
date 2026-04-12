package com.carpool.common.exception;

import org.springframework.http.HttpStatus;

public class BadRequestException extends CarpoolException {
    public BadRequestException(String errorCode, String message) {
        super(HttpStatus.BAD_REQUEST, errorCode, message);
    }
}
