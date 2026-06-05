package com.carpool.service.dto.request;

import jakarta.validation.constraints.*;

public record SubmitRatingRequest(
        @NotNull(message = "rateeId is required")
        Long rateeId,

        @NotNull(message = "stars is required")
        @Min(value = 1, message = "stars must be at least 1")
        @Max(value = 5, message = "stars cannot exceed 5")
        Integer stars,

        @Size(max = 1000, message = "comment cannot exceed 1000 characters")
        String comment
) {}
