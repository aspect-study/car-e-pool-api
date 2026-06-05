package com.carpool.service.dto.request;

import jakarta.validation.constraints.*;

public record AddVehicleRequest(
        @NotBlank(message = "model is required")
        String model,

        String color,

        @NotBlank(message = "plateNumber is required")
        String plateNumber,

        @Min(value = 1, message = "seatCapacity must be at least 1")
        @Max(value = 8, message = "seatCapacity cannot exceed 8")
        Integer seatCapacity
) {}
