package com.carpool.service.dto.response;

public record UserSummaryResponse(
        Long   id,
        String fullName,
        String telegramHandle,
        Double avgRating
) {}
