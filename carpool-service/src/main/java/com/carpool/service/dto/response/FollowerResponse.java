package com.carpool.service.dto.response;

import java.time.LocalDateTime;

public record FollowerResponse(
        Long   userId,
        String fullName,
        String telegramHandle,
        LocalDateTime followedAt
) {}