package com.carpool.service.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record WaypointRequest(

        @NotNull(message = "hubId is required")
        Long hubId,

        @NotNull(message = "sequenceOrder is required")
        @Min(value = 1, message = "sequenceOrder must be >= 1")
        Short sequenceOrder,

        // Defaults to true if not provided — most stops are both pickup and dropoff
        Boolean isPickup,
        Boolean isDropoff
) {}
