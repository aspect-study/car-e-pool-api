package com.carpool.common.exception;

public class DeparturePastException extends BadRequestException {
    public DeparturePastException() {
        super("DEPARTURE_IN_PAST",
                "Departure time must be in the future. Please enter a valid departure time.");
    }
}