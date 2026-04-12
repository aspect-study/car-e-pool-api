package com.carpool.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base exception for all carpool domain exceptions.
 * Carries an HTTP status and error code so the global handler
 * can map them without instanceof chains.
 */
public abstract class CarpoolException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    protected CarpoolException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status    = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus()    { return status; }
    public String getErrorCode()     { return errorCode; }
}
