package com.carpool.service.dto.response;

import com.carpool.domain.enums.HubStatus;

public record HubResponse(
        Long id,
        String code,
        String name,
        String area,
        HubStatus status
) {}
