package com.carpool.common.exception;

import org.springframework.http.HttpStatus;

public class RatingConflictException extends CarpoolException {
    public RatingConflictException(String message) {
        super(HttpStatus.CONFLICT, "RATING_CONFLICT", message);
    }
}
