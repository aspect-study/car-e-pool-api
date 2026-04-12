package com.carpool.common.exception;

public class HubNotFoundException extends ResourceNotFoundException {
    public HubNotFoundException(Long hubId) {
        super("HUB_NOT_FOUND", "Hub not found with id: " + hubId);
    }
}
