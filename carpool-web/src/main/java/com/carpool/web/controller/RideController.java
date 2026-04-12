package com.carpool.web.controller;

import com.carpool.common.response.ApiResponse;
import com.carpool.service.dto.request.CreateRideRequest;
import com.carpool.service.dto.request.UpdateRideStatusRequest;
import com.carpool.service.dto.response.RideResponse;
import com.carpool.service.ride.RideService;
import com.carpool.web.security.AuthenticatedUser;
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
public class RideController {

    private final RideService rideService;

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

    /**
     * GET /api/v1/rides/{id}
     * Ride detail with waypoints eagerly loaded (avoids N+1).
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RideResponse>> getRideById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(rideService.getRideById(id)));
    }

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
