package com.carpool.common.exception;

public class InsufficientRoleException extends ForbiddenException {
    public InsufficientRoleException(String requiredRole) {
        super("INSUFFICIENT_ROLE",
                "You do not have permission to perform this action. " +
                        "Please contact support if you believe this is a mistake.");
    }
}