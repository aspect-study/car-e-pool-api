package com.carpool.common.exception;

public class SameHubException extends BadRequestException {
    public SameHubException() {
        super("SAME_HUB",
                "Pickup and drop-off locations cannot be the same. Please select different hubs.");
    }
}