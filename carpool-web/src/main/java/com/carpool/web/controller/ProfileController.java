package com.carpool.web.controller;

import com.carpool.common.response.ApiResponse;
import com.carpool.service.dto.response.ProfileStatsResponse;
import com.carpool.service.profile.ProfileService;
import com.carpool.web.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
@Tag(name = "Profile", description = "User profile statistics")
public class ProfileController {

    private final ProfileService profileService;

    /**
     * GET /api/v1/users/me/stats
     * Returns role-aware profile statistics for the authenticated user.
     */
    @Operation(summary = "Get my profile stats",
            description = """
                    Returns role-aware profile statistics for the authenticated user.
                    
                    **Driver stats** (included if user has posted at least one ride):
                    - Rides posted, completed, cancelled
                    - Total passengers served (sum of seats on completed bookings)
                    - Completion rate %
                    
                    **Passenger stats** (included if user has made at least one booking):
                    - Bookings made, completed
                    - Cancelled by me (CANCELLED_BY_PASSENGER only —
                      DECLINED/TIMED_OUT/CANCELLED_BY_DRIVER not counted against passenger)
                    - Completion rate %
                    
                    Stats are computed live from rides and bookings tables.
                    No caching — always reflects current data.
                    """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Profile stats returned successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "Not authenticated")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<ProfileStatsResponse>> getMyStats(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        ProfileStatsResponse stats = profileService.getProfileStats(currentUser.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(stats));
    }
}