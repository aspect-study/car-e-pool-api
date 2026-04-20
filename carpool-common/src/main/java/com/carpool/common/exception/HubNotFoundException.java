package com.carpool.common.exception;

public class HubNotFoundException extends ResourceNotFoundException {
    public HubNotFoundException(Long hubId) {
        super("HUB_NOT_FOUND",
                "The selected location could not be found. Please try a different hub or landmark.");
    }
}