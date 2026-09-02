package com.carpool.service.dto.request;

import com.carpool.domain.enums.RideDirection;
import com.carpool.service.ride.RideSeatLimits;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CreateRideRequest(

        @NotNull(message = "originHubId is required")
        Long originHubId,

        @NotNull(message = "destinationHubId is required")
        Long destinationHubId,

        @NotNull(message = "direction is required")
        RideDirection direction,

        @NotNull(message = "departureTime is required")
        @Future(message = "departureTime must be in the future")
        LocalDateTime departureTime,

        @NotNull(message = "totalSeats is required")
        @Min(value = RideSeatLimits.MIN_TOTAL_SEATS, message = "totalSeats must be at least 1")
        @Max(value = RideSeatLimits.MAX_TOTAL_SEATS, message = "totalSeats cannot exceed 8")
        Integer totalSeats,

        @NotNull(message = "contributionAmount is required")
        @DecimalMin(value = "0.00", message = "contributionAmount cannot be negative")
        BigDecimal contributionAmount,

        @Size(max = 1000, message = "notes cannot exceed 1000 characters")
        String notes,

        // Optional ordered waypoints between origin and destination
        @Valid
        List<WaypointRequest> waypoints,

        // Optional — vehicle selected by driver at post time
        Long vehicleId
) {}
