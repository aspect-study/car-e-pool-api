package com.carpool.service.dto.request;

import com.carpool.domain.enums.RideStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateRideStatusRequest(
        @NotNull(message = "status is required")
        RideStatus status
) {}
