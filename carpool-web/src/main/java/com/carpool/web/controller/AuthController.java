package com.carpool.web.controller;

import com.carpool.common.response.ApiResponse;
import com.carpool.service.auth.TelegramAuthService;
import com.carpool.service.dto.request.TelegramAuthRequest;
import com.carpool.service.dto.response.AuthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Telegram Login Widget authentication")
public class AuthController {

    private final TelegramAuthService telegramAuthService;
    @Operation(
            summary = "Login via Telegram",
            description = """
                    Exchange Telegram Login Widget auth data for a JWT token.
                    
                    The hash field is verified server-side using HMAC-SHA256
                    against the bot token. Auth data older than 24 hours is rejected.
                    """
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Login successful — returns JWT token")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "Invalid or expired Telegram auth data")
    /**
     * Exchange Telegram Login Widget auth data for a JWT.
     * Public endpoint — no authentication required.
     *
     * POST /api/v1/auth/telegram
     */
    @PostMapping("/telegram")
    public ResponseEntity<ApiResponse<AuthResponse>> telegramLogin(
            @Valid @RequestBody TelegramAuthRequest request) {

        AuthResponse auth = telegramAuthService.authenticate(request);
        return ResponseEntity.ok(ApiResponse.ok(auth));
    }

}
