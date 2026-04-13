package com.carpool.web.controller;

import com.carpool.common.response.ApiResponse;
import com.carpool.service.auth.TelegramAuthService;
import com.carpool.service.dto.request.TelegramAuthRequest;
import com.carpool.service.dto.response.AuthResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final TelegramAuthService telegramAuthService;

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
