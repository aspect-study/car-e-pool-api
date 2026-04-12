package com.carpool.service.dto.response;

import com.carpool.domain.enums.RideDirection;
import com.carpool.domain.enums.RideStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public record RideResponse(
        Long id,
        UserResponse driver,
        HubResponse originHub,
        HubResponse destinationHub,
        RideDirection direction,
        LocalDateTime departureTime,
        Integer totalSeats,
        Integer availableSeats,
        BigDecimal contributionAmount,
        String notes,
        RideStatus status,
        List<WaypointResponse> waypoints,
        Instant createdAt
) {}
