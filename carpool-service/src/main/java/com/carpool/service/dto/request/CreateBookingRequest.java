package com.carpool.service.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateBookingRequest(

        @NotNull(message = "seatsReserved is required")
        @Min(value = 1, message = "seatsReserved must be at least 1")
        @Max(value = 4, message = "Cannot reserve more than 4 seats at once")
        Integer seatsReserved,

        // NULL = board at ride's origin hub
        Long pickupWaypointId,

        // NULL = alight at ride's destination hub
        Long dropoffWaypointId
) {}
