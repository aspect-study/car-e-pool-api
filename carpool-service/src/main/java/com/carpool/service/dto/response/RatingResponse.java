package com.carpool.service.dto.response;

import java.time.LocalDateTime;

public record RatingResponse(
        Long                id,
        Long                rideId,
        UserSummaryResponse rater,
        UserSummaryResponse ratee,
        int                 stars,
        String              comment,
        String              raterRole,
        LocalDateTime       createdAt
) {}
