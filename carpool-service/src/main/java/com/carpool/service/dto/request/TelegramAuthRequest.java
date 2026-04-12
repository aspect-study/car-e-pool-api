package com.carpool.service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Payload sent by the client after user completes Telegram Login Widget flow.
 * All fields come directly from Telegram's auth callback — no client manipulation.
 *
 * Telegram Login Widget docs:
 * https://core.telegram.org/widgets/login#receiving-authorization-data
 */
public record TelegramAuthRequest(

        @NotNull(message = "id is required")
        Long id,

        @NotBlank(message = "first_name is required")
        String firstName,

        String lastName,       // optional — not all Telegram users have last name

        String username,       // optional — Telegram handle (@username)

        String photoUrl,       // optional — profile photo URL from Telegram CDN

        @NotNull(message = "auth_date is required")
        Long authDate,         // Unix timestamp of when user authorized

        @NotBlank(message = "hash is required")
        String hash            // HMAC-SHA256 signature from Telegram — we verify this
) {}
