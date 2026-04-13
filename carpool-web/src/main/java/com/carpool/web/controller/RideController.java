package com.carpool.web.controller;

import com.carpool.common.response.ApiResponse;
import com.carpool.service.dto.request.CreateRideRequest;
import com.carpool.service.dto.request.UpdateRideStatusRequest;
import com.carpool.service.dto.response.RideResponse;
import com.carpool.service.ride.RideService;
import com.carpool.web.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rides")
@RequiredArgsConstructor
@Tag(name = "Rides", description = "Ride offering and search")
public class RideController {

    private final RideService rideService;

    @Operation(summary = "Create a new ride",
            description = """
                    Driver creates a ride. Starts as **DRAFT** — not visible to passengers yet.
                    
                    Call `PATCH /api/v1/rides/{id}/status` with `ACTIVE` to publish.
                    
                    Requires role: `DRIVER` or `BOTH`.
                    """,
            security = @SecurityRequirement(name = "bearerAuth"))
    /**
     * POST /api/v1/rides
     * Driver creates a new ride (starts as DRAFT).
     */
    @PostMapping
    public ResponseEntity<ApiResponse<RideResponse>> createRide(
            @Valid @RequestBody CreateRideRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        RideResponse ride = rideService.createRide(request, currentUser.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(ride));
    }

    @Operation(summary = "Search available rides",
            description = "Search ACTIVE rides by origin and destination hub ID. " +
                    "Results include rides where the hub appears as a waypoint " +
                    "(not just origin/destination). Ordered by departure time ascending.",
            security = @SecurityRequirement(name = "bearerAuth"))
    /**
     * GET /api/v1/rides?from=1&to=5
     * Search available rides by origin and destination hub IDs.
     * Optional: &direction=HOME_TO_WORK
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<RideResponse>>> searchRides(
            @RequestParam Long from,
            @RequestParam Long to) {

        return ResponseEntity.ok(ApiResponse.ok(rideService.searchRides(from, to)));
    }

    @Operation(summary = "Get my offered rides",
            description = "Returns all rides offered by the authenticated driver, " +
                    "all statuses, ordered by departure time descending.",
            security = @SecurityRequirement(name = "bearerAuth"))
    /**
     * GET /api/v1/rides/mine
     * Driver views all rides they have offered (all statuses).
     */
    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<List<RideResponse>>> getMyRides(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        return ResponseEntity.ok(
                ApiResponse.ok(rideService.getMyRides(currentUser.getUserId())));
    }

    @Operation(summary = "Get ride details",
            description = "Returns full ride details including all waypoints. " +
                    "Uses eager fetch to avoid N+1.",
            security = @SecurityRequirement(name = "bearerAuth"))
    /**
     * GET /api/v1/rides/{id}
     * Ride detail with waypoints eagerly loaded (avoids N+1).
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RideResponse>> getRideById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(rideService.getRideById(id)));
    }

    @Operation(summary = "Update ride status",
            description = """
                    Driver transitions ride status. Allowed transitions:
                    
                    - `DRAFT` → `ACTIVE` — publish ride (visible to passengers)
                    - `ACTIVE` → `CANCELLED` — cancel ride (notifies all passengers)
                    - `FULL` → `CANCELLED` — cancel full ride (notifies all passengers)
                    - `ACTIVE` → `COMPLETED` — mark ride done (notifies passengers to pay)
                    - `FULL` → `COMPLETED` — mark full ride done
                    """,
            security = @SecurityRequirement(name = "bearerAuth"))
    /**
     * PATCH /api/v1/rides/{id}/status
     * Driver transitions ride status:
     *   DRAFT → ACTIVE (publish)
     *   ACTIVE/FULL → CANCELLED
     *   ACTIVE/FULL → COMPLETED
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<RideResponse>> updateRideStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRideStatusRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        RideResponse ride = rideService.updateRideStatus(id, request, currentUser.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(ride));
    }
}
