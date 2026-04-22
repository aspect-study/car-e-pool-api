package com.carpool.web.controller;

import com.carpool.common.response.ApiResponse;
import com.carpool.domain.enums.RideDirection;
import com.carpool.service.dto.request.CreateRideRequest;
import com.carpool.service.dto.request.UpdateRideStatusRequest;
import com.carpool.service.dto.response.RideResponse;
import com.carpool.service.ride.RideService;
import com.carpool.web.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/rides")
@RequiredArgsConstructor
@Tag(name = "Rides", description = "Ride offering and search")
public class RideController {

    private final RideService rideService;

    /**
     * POST /api/v1/rides
     * Driver creates a new ride (starts as DRAFT).
     */
    @Operation(summary = "Create a new ride",
            description = """
                    Driver creates a ride. Starts as **DRAFT** — not visible to passengers yet.
                    
                    Call `PATCH /api/v1/rides/{id}/status` with `ACTIVE` to publish.
                    
                    Requires role: `DRIVER` or `BOTH`.
                    """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "Ride created as DRAFT")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "User does not have DRIVER role")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "Driver already has an active ride")
    @PostMapping
    public ResponseEntity<ApiResponse<RideResponse>> createRide(
            @Valid @RequestBody CreateRideRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        RideResponse ride = rideService.createRide(request, currentUser.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(ride));
    }

    /**
     * GET /api/v1/rides?from=1&to=5
     * Search available rides by origin and destination hub IDs.
     */
    @Operation(summary = "Search available rides by hub",
            description = """
                    Search ACTIVE rides by origin and destination hub ID.
                    Results include rides where the hub appears as a waypoint.
                    Ordered by departure time ascending.
                    """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "List of available rides")
    @GetMapping
    public ResponseEntity<ApiResponse<List<RideResponse>>> searchRides(
            @RequestParam Long from,
            @RequestParam Long to) {

        return ResponseEntity.ok(ApiResponse.ok(rideService.searchRides(from, to)));
    }

    /**
     * GET /api/v1/rides/direction
     * Search rides by direction with time window, filters, and sort.
     * Used by the bot's Find a Ride flow — exposed here for REST clients.
     */
    @Operation(summary = "Search rides by direction",
            description = """
                    Search ACTIVE rides by direction and time window.
                    Supports filtering and sorting — mirrors the bot's Find a Ride flow.
                    
                    **Direction values:** `HOME_TO_WORK`, `WORK_TO_HOME`
                    
                    **Sort values:**
                    - `EARLIEST` — by departure time ascending (default)
                    - `CHEAPEST` — by gas share ascending
                    - `MOST_SEATS` — by available seats descending
                    
                    Driver's own rides are excluded from results.
                    """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Filtered and sorted list of rides")
    @GetMapping("/direction")
    public ResponseEntity<ApiResponse<List<RideResponse>>> getRidesByDirection(
            @RequestParam RideDirection direction,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) BigDecimal maxShare,
            @RequestParam(required = false) Integer minSeats,
            @RequestParam(required = false) String sortBy,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        return ResponseEntity.ok(ApiResponse.ok(
                rideService.getRidesByDirection(
                        direction,
                        currentUser.getUserId(),
                        from, to,
                        maxShare,
                        minSeats,
                        sortBy)));
    }

    /**
     * GET /api/v1/rides/mine
     * Driver views all rides they have offered (all statuses).
     */
    @Operation(summary = "Get my offered rides",
            description = """
                    Returns all rides offered by the authenticated driver.
                    All statuses included, ordered by departure time descending.
                    """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<List<RideResponse>>> getMyRides(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        return ResponseEntity.ok(
                ApiResponse.ok(rideService.getMyRides(currentUser.getUserId())));
    }

    /**
     * GET /api/v1/rides/{id}
     * Ride detail with waypoints eagerly loaded (avoids N+1).
     */
    @Operation(summary = "Get ride details",
            description = """
                    Returns full ride details including all waypoints.
                    Uses eager fetch to avoid N+1.
                    """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Ride details")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "Ride not found")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RideResponse>> getRideById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(rideService.getRideById(id)));
    }

    /**
     * PATCH /api/v1/rides/{id}/status
     * Driver transitions ride status.
     */
    @Operation(summary = "Update ride status",
            description = """
                    Driver transitions ride status. Allowed transitions:
                    
                    - `DRAFT` → `ACTIVE` — publish ride (visible to passengers)
                    - `ACTIVE` → `DEPARTED` — start ride (passengers locked in)
                    - `FULL` → `DEPARTED` — start full ride
                    - `ACTIVE` → `CANCELLED` — cancel ride (notifies all passengers)
                    - `FULL` → `CANCELLED` — cancel full ride (notifies all passengers)
                    - `DEPARTED` → `COMPLETED` — mark ride done (notifies passengers to settle share)
                    
                    Note: COMPLETED can only be reached from DEPARTED, not directly from ACTIVE/FULL.
                    """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Ride status updated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", description = "Invalid status transition")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "Not the ride owner")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "Ride not found")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<RideResponse>> updateRideStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRideStatusRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        RideResponse ride = rideService.updateRideStatus(
                id, request, currentUser.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(ride));
    }
}