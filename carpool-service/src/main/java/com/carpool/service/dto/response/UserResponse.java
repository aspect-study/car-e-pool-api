package com.carpool.service.dto.response;

import com.carpool.domain.enums.UserRole;
import com.carpool.domain.enums.UserStatus;

import java.time.Instant;

public record UserResponse(
        Long id,
        Long telegramId,
        String telegramHandle,
        String fullName,
        String photoUrl,
        UserRole role,
        UserStatus status,
        Instant createdAt
) {}
