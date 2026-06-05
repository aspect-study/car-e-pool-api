package com.carpool.web.controller;

import com.carpool.common.response.ApiResponse;
import com.carpool.service.dto.request.UpdateRoleRequest;
import com.carpool.service.dto.response.FollowerResponse;
import com.carpool.service.dto.response.UserResponse;
import com.carpool.service.favorite.FavoriteService;
import com.carpool.service.profile.ProfileService;
import com.carpool.service.user.UserService;
import com.carpool.service.vehicle.VehicleService;
import java.util.List;
import com.carpool.web.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final FavoriteService favoriteService;

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
     * GET /api/v1/users/admin/stats
     * Admin only — returns platform-wide statistics.
     */
    @Operation(summary = "[Admin] Get platform statistics",
            description = """
                Returns platform-wide statistics for admin dashboard.
                
                **User stats:**
                - Total registered users
                - New users today
                
                **Ride stats:**
                - Active rides right now
                - Rides posted today
                - Total rides, completed, cancelled
                
                **Booking stats:**
                - Pending requests right now
                - Bookings made today
                - Total bookings, completed
                
                Requires role: `ADMIN`
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Platform statistics returned")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "Not an admin")
    @GetMapping("/admin/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<com.carpool.service.dto.response.AdminStatsResponse>> getAdminStats() {
        return ResponseEntity.ok(ApiResponse.ok(profileService.getAdminStats()));
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

    @Operation(summary = "Delete my account",
            description = """
                Permanently deletes the authenticated user's account.
                
                **What happens:**
                - Active ride is cancelled — all passengers are notified
                - All active bookings (PENDING/CONFIRMED) are cancelled — drivers are notified
                - Personal data is anonymized (name, @handle, Telegram ID)
                - Account is soft-deleted — ride and booking history retained for other users
                
                **This action is irreversible.**
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Account deleted successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "Not authenticated")
    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteMyAccount(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        userService.deleteAccount(currentUser.getUserId());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(summary = "Save a user as favorite",
            description = """
                Save another user as a favorite driver/passenger.
                Idempotent — no error if already saved.
                Returns 400 if trying to save yourself.
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Saved (or already saved)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", description = "Cannot favorite yourself")
    @PostMapping("/{userId}/favorite")
    public ResponseEntity<ApiResponse<Void>> saveFavorite(
            @PathVariable Long userId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        favoriteService.saveFavorite(currentUser.getUserId(), userId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(summary = "Remove a user from favorites",
            description = """
                Remove a saved favorite. Idempotent — no error if not found.
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Removed (or was not saved)")
    @DeleteMapping("/{userId}/favorite")
    public ResponseEntity<ApiResponse<Void>> removeFavorite(
            @PathVariable Long userId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        favoriteService.removeFavorite(currentUser.getUserId(), userId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(summary = "Get my favorites",
            description = """
                Returns all users saved as favorites by the authenticated user.
                Ordered newest-first.
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "List of saved favorites")
    @GetMapping("/me/favorites")
    public ResponseEntity<ApiResponse<List<FollowerResponse>>> getMyFavorites(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        return ResponseEntity.ok(ApiResponse.ok(
                favoriteService.getMyFavoritesAsDtos(currentUser.getUserId())));
    }

    @Operation(summary = "Get my followers",
            description = """
                Returns all users who have saved the authenticated user as a favorite.
                Ordered newest-first.
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "List of followers")
    @GetMapping("/me/followers")
    public ResponseEntity<ApiResponse<List<FollowerResponse>>> getMyFollowers(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        return ResponseEntity.ok(ApiResponse.ok(
                favoriteService.getFollowers(currentUser.getUserId())));
    }
}
