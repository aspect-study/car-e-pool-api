package com.carpool.common.exception;

public class InsufficientRoleException extends ForbiddenException {
    public InsufficientRoleException(String requiredRole) {
        super("INSUFFICIENT_ROLE", "This action requires role: " + requiredRole);
    }
}
