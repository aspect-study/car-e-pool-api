package com.carpool.common.exception;

public class SameHubException extends BadRequestException {
    public SameHubException() {
        super("SAME_HUB", "Origin and destination hubs must be different.");
    }
}
