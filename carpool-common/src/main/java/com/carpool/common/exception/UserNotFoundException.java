package com.carpool.common.exception;

public class UserNotFoundException extends ResourceNotFoundException {
    public UserNotFoundException(Long userId) {
        super("USER_NOT_FOUND",
                "User account not found. Please register first via /start.");
    }
    public UserNotFoundException(String telegramId) {
        super("USER_NOT_FOUND",
                "User account not found. Please register first via /start.");
    }
}