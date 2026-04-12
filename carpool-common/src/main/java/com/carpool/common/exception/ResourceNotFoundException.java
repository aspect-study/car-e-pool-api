package com.carpool.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends CarpoolException {
    public ResourceNotFoundException(String errorCode, String message) {
        super(HttpStatus.NOT_FOUND, errorCode, message);
    }
}
