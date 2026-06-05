package com.carpool.service.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record UpdateDepartureTimeRequest(
        @NotNull(message = "newDepartureTime is required")
        @Future(message = "newDepartureTime must be in the future")
        LocalDateTime newDepartureTime
) {}
