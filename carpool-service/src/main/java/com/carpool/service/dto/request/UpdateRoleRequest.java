package com.carpool.service.dto.request;

import com.carpool.domain.enums.UserRole;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(
        @NotNull(message = "role is required")
        UserRole role
) {}
