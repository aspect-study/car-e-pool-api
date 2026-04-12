package com.carpool.service.dto.response;

/**
 * Returned after successful Telegram auth.
 * Client stores the token and sends it as: Authorization: Bearer <token>
 */
public record AuthResponse(
        String accessToken,
        String tokenType,       // always "Bearer"
        long expiresInMs,
        UserResponse user
) {}
