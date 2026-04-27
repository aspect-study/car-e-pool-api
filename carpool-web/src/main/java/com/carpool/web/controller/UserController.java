package com.carpool.web.controller;

import com.carpool.common.response.ApiResponse;
import com.carpool.service.dto.request.UpdateRoleRequest;
import com.carpool.service.dto.response.UserResponse;
import com.carpool.service.profile.ProfileService;
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
    private final ProfileService profileService;

    @Operation(summary = "Get my profile",
            description = "Returns the currently authenticated user's profile. " +
                    "Cached for 10 minutes.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "User profile returned")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "Not authenticated")
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
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Role updated successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", description = "Invalid role value")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "Not authenticated")
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

    /**
     * GET /api/v1/users/me/stats
     * Returns role-aware profile statistics for the authenticated user.
     */
    @Operation(summary = "Get my profile stats",
            description = """
                Returns role-aware profile statistics for the authenticated user.
                
                **Driver stats** (included if user has posted at least one ride):
                - Rides posted, completed, cancelled
                - Total passengers served
                - Completion rate %
                
                **Passenger stats** (included if user has made at least one booking):
                - Bookings made, completed
                - Cancelled by me (CANCELLED_BY_PASSENGER only)
                - Completion rate %
                
                Stats are computed live — no caching.
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Profile stats returned successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "Not authenticated")
    @GetMapping("/me/stats")
    public ResponseEntity<ApiResponse<com.carpool.service.dto.response.ProfileStatsResponse>> getMyStats(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        return ResponseEntity.ok(ApiResponse.ok(
                profileService.getProfileStats(currentUser.getUserId())));
    }

    /**
     * GET /api/v1/users/me/vehicle
     * Get current vehicle info for authenticated driver.
     */
    @Operation(summary = "Get my vehicle info",
            description = """
                Returns the authenticated driver's current vehicle information.
                
                Returns `null` fields if no vehicle has been set yet.
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Vehicle info returned")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "Not authenticated")
    @GetMapping("/me/vehicle")
    public ResponseEntity<ApiResponse<UserResponse>> getMyVehicle(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        return ResponseEntity.ok(ApiResponse.ok(
                userService.getUserById(currentUser.getUserId())));
    }

    /**
     * DELETE /api/v1/users/me/vehicle
     * Remove vehicle info for authenticated driver.
     */
    @Operation(summary = "Remove my vehicle info",
            description = """
                Removes the authenticated driver's vehicle information.
                
                After removal, `carModel`, `carColor`, and `plateNumber` will be `null`.
                Driver will be prompted to re-enter vehicle info on next Post Ride.
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Vehicle info removed")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "Not authenticated")
    @DeleteMapping("/me/vehicle")
    public ResponseEntity<ApiResponse<UserResponse>> removeMyVehicle(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        vehicleService.clearVehicle(currentUser.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(
                userService.getUserById(currentUser.getUserId())));
    }
}
