package com.carpool.service.dto.response;

import java.util.List;

public record RatingEligibilityResponse(
        boolean    canRate,
        List<Long> rateeIds
) {}
