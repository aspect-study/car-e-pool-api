package com.carpool.common.exception;

public class UserNotFoundException extends ResourceNotFoundException {
    public UserNotFoundException(Long userId) {
        super("USER_NOT_FOUND", "User not found with id: " + userId);
    }
    public UserNotFoundException(String telegramId) {
        super("USER_NOT_FOUND", "User not found with telegram id: " + telegramId);
    }
}
