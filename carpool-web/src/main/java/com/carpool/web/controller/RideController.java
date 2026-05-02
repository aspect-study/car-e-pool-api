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

import com.carpool.common.response.PagedResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
                
                **Pagination:** `page` (default 0), `size` (default 10, max 50)
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "List of available rides")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", description = "Invalid hub IDs or same origin and destination")
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<RideResponse>>> searchRides(
            @RequestParam Long from,
            @RequestParam Long to,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 50),
                Sort.by("departureTime").ascending());

        return ResponseEntity.ok(ApiResponse.ok(
                rideService.searchRides(from, to, pageable)));
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
                
                **Pagination:** `page` (default 0), `size` (default 10, max 50)
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Filtered and sorted list of rides")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", description = "Invalid direction or time range")
    @GetMapping("/direction")
    public ResponseEntity<ApiResponse<PagedResponse<RideResponse>>> getRidesByDirection(
            @RequestParam RideDirection direction,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) BigDecimal maxShare,
            @RequestParam(required = false) Integer minSeats,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 50));

        return ResponseEntity.ok(ApiResponse.ok(
                rideService.getRidesByDirection(
                        direction,
                        currentUser.getUserId(),
                        from, to,
                        maxShare,
                        minSeats,
                        sortBy,
                        pageable)));
    }

    /**
     * GET /api/v1/rides/mine
     * Driver views all rides they have offered (all statuses).
     */
    @Operation(summary = "Get my offered rides",
            description = """
                Returns all rides offered by the authenticated driver.
                All statuses included, ordered by departure time descending.
                
                **Pagination:** `page` (default 0), `size` (default 10, max 50)
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Driver's ride history")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "Not authenticated")
    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<PagedResponse<RideResponse>>> getMyRides(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 50),
                Sort.by("departureTime").descending());

        return ResponseEntity.ok(ApiResponse.ok(
                rideService.getMyRides(currentUser.getUserId(), pageable)));
    }

    /**
     * GET /api/v1/rides/mine/active
     * Returns the driver's current active ride or null if none exists.
     */
    @Operation(summary = "Get my active ride",
            description = """
                Returns the authenticated driver's current active ride.
                
                Active means status is one of: `ACTIVE`, `FULL`, or `DEPARTED`.
                
                Returns `null` in the data field if no active ride exists.
                Use this as a lightweight check instead of loading the full ride list.
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Active ride returned, or null if none exists")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "Not authenticated")
    @GetMapping("/mine/active")
    public ResponseEntity<ApiResponse<RideResponse>> getMyActiveRide(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        return ResponseEntity.ok(ApiResponse.ok(
                rideService.getActiveRide(currentUser.getUserId())));
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

    /**
     * POST /api/v1/rides/{id}/reannounce
     * Driver re-announces their active ride to the community group.
     * Maximum 3 total announcements per ride (including the original post).
     */
    @Operation(summary = "Re-announce ride to community group",
            description = """
                Driver re-announces their active ride to the community Telegram group.
                
                - Maximum **3 total announcements** per ride (including the original post)
                - Only the ride owner can re-announce
                - Only ACTIVE or FULL rides can be re-announced
                - Reuses the same group announcement format as the original post
                
                Returns the updated ride with the new `announceCount`.
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Ride re-announced to community group")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", description = "Announce limit reached or invalid ride state")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "Not the ride owner")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "Ride not found")
    @PostMapping("/{id}/reannounce")
    public ResponseEntity<ApiResponse<RideResponse>> reannounceRide(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        return ResponseEntity.ok(ApiResponse.ok(
                rideService.reannounceRide(id, currentUser.getUserId())));
    }
}