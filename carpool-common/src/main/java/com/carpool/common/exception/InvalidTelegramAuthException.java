package com.carpool.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidTelegramAuthException extends CarpoolException {
    public InvalidTelegramAuthException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_TELEGRAM_AUTH",
              "Telegram authentication data is invalid or expired.");
    }
}
