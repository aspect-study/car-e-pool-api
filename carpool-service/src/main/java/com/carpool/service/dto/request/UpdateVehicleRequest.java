package com.carpool.service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateVehicleRequest(

        @Size(max = 50, message = "Color must not exceed 50 characters")
        String carColor,           // optional

        @NotBlank(message = "Car model is required")
        @Size(max = 100, message = "Car model must not exceed 100 characters")
        String carModel,

        @NotBlank(message = "Plate number is required")
        @Size(max = 20, message = "Plate number must not exceed 20 characters")
        String plateNumber
) {}