package com.carpool.web.controller;

import com.carpool.common.response.ApiResponse;
import com.carpool.service.dto.request.UpdateRoleRequest;
import com.carpool.service.dto.response.UserResponse;
import com.carpool.service.user.UserService;
import com.carpool.service.vehicle.VehicleService;
import com.carpool.web.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User profile and role management")
public class UserController {

    private final UserService userService;
    private final VehicleService vehicleService;

    @Operation(summary = "Get my profile",
            description = "Returns the currently authenticated user's profile. " +
                    "Cached for 10 minutes.",
            security = @SecurityRequirement(name = "bearerAuth"))
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

    @Operation(summary = "Update my role",
            description = """
                    Upgrade or downgrade your role.
                    
                    - `PASSENGER` — can only book rides (default)
                    - `DRIVER` — can only offer rides
                    - `BOTH` — can offer and book rides
                    """,
            security = @SecurityRequirement(name = "bearerAuth"))
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

    /**
     * PATCH /api/v1/users/me/vehicle
     * Save or update driver's vehicle info.
     */
    @Operation(summary = "Update my vehicle info",
            description = """
                Save or update the authenticated driver's vehicle information.
                
                - `carColor` — optional (e.g. Silver, White)
                - `carModel` — required (e.g. Toyota Vios)
                - `plateNumber` — required, unique (e.g. ABC 1234)
                
                Plate number is stored in uppercase.
                Overwrites existing vehicle info — single vehicle per user.
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Vehicle info updated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "Plate number already in use")
    @PatchMapping("/me/vehicle")
    public ResponseEntity<ApiResponse<UserResponse>> updateMyVehicle(
            @Valid @RequestBody com.carpool.service.dto.request.UpdateVehicleRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        return ResponseEntity.ok(ApiResponse.ok(
                vehicleService.updateVehicle(
                        currentUser.getUserId(),
                        request.carColor(),
                        request.carModel(),
                        request.plateNumber())));
    }
}
