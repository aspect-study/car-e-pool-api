package com.carpool.service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SuggestHubRequest(
        @NotBlank(message = "name is required")
        @Size(max = 150, message = "name cannot exceed 150 characters")
        String name,

        @NotBlank(message = "area is required")
        @Size(max = 100, message = "area cannot exceed 100 characters")
        String area
) {}
