package com.carpool.web.controller;

import com.carpool.common.response.ApiResponse;
import com.carpool.service.dto.request.UpdateRoleRequest;
import com.carpool.service.dto.response.UserResponse;
import com.carpool.service.user.UserService;
import com.carpool.web.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * GET /api/v1/users/me
     * Returns the current authenticated user's profile.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        return ResponseEntity.ok(
                ApiResponse.ok(userService.getUserById(currentUser.getUserId())));
    }

    /**
     * PATCH /api/v1/users/me/role
     * Allows a user to upgrade their role (e.g. PASSENGER → DRIVER).
     * Downgrade is also allowed — a DRIVER can revert to PASSENGER.
     */
    @PatchMapping("/me/role")
    public ResponseEntity<ApiResponse<UserResponse>> updateMyRole(
            @Valid @RequestBody UpdateRoleRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        return ResponseEntity.ok(
                ApiResponse.ok(userService.updateRole(currentUser.getUserId(), request)));
    }
}
