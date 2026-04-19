package com.carpool.service.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateBookingRequest(

        @NotNull(message = "seatsReserved is required")
        @Min(value = 1, message = "seatsReserved must be at least 1")
        @Max(value = 8, message = "Cannot reserve more than 8 seats at once")
        Integer seatsReserved,

        // NULL = board at ride's origin hub
        Long pickupWaypointId,

        // NULL = alight at ride's destination hub
        Long dropoffWaypointId,

        // Optional message from passenger to driver — shown during approval
        @Size(max = 800, message = "Message cannot exceed 800 characters")
        String passengerMessage
) {}