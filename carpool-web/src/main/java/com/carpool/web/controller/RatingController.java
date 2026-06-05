package com.carpool.web.controller;

import com.carpool.common.response.ApiResponse;
import com.carpool.common.response.PagedResponse;
import com.carpool.service.dto.request.SubmitRatingRequest;
import com.carpool.service.dto.response.RatingEligibilityResponse;
import com.carpool.service.dto.response.RatingResponse;
import com.carpool.service.mapper.EntityMapper;
import com.carpool.service.rating.RatingService;
import com.carpool.web.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Ratings", description = "Driver and passenger ratings")
public class RatingController {

    private final RatingService ratingService;
    private final EntityMapper  mapper;

    @Operation(summary = "Submit a rating",
            description = """
                Submit a star rating (1–5) for a completed ride.

                - Passengers rate the driver (one rating per ride)
                - Drivers rate each confirmed passenger individually
                - Returns **409** if already rated, or if the ride is not COMPLETED
                - Returns **400** if `rideId` or `rateeId` is not found

                Check `/rides/{rideId}/ratings/eligibility` first to get valid `rateeId` values.
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "Rating submitted")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", description = "Ride or user not found")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "Already rated, or ride not COMPLETED")
    @PostMapping("/rides/{rideId}/ratings")
    public ResponseEntity<ApiResponse<RatingResponse>> submitRating(
            @PathVariable Long rideId,
            @Valid @RequestBody SubmitRatingRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        RatingResponse rating = mapper.toRatingResponse(
                ratingService.submitRating(
                        rideId,
                        currentUser.getUserId(),
                        request.rateeId(),
                        request.stars(),
                        request.comment()));

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(rating));
    }

    @Operation(summary = "Get ratings received by a user",
            description = """
                Returns a paginated list of all ratings received by the specified user.
                Ordered newest-first.

                **Pagination:** `page` (default 0), `size` (default 10, max 50)
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Paginated list of ratings")
    @GetMapping("/users/{userId}/ratings")
    public ResponseEntity<ApiResponse<PagedResponse<RatingResponse>>> getRatings(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        Page<RatingResponse> ratings = ratingService
                .getRatingsReceivedPaged(userId, page, Math.min(size, 50))
                .map(mapper::toRatingResponse);

        return ResponseEntity.ok(ApiResponse.ok(PagedResponse.of(ratings)));
    }

    @Operation(summary = "Check rating eligibility for a ride",
            description = """
                Returns whether the authenticated user can rate a specific ride,
                and the list of user IDs they are eligible to rate.

                Clients should call this before showing the rating UI.

                - `canRate: false` — ride not COMPLETED, or user already rated everyone
                - `rateeIds` — for passengers: `[driverId]`; for drivers: list of passenger IDs not yet rated
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Eligibility result")
    @GetMapping("/rides/{rideId}/ratings/eligibility")
    public ResponseEntity<ApiResponse<RatingEligibilityResponse>> checkEligibility(
            @PathVariable Long rideId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        Long raterId = currentUser.getUserId();
        boolean canRate = ratingService.canRate(rideId, raterId);
        java.util.List<Long> rateeIds = canRate
                ? ratingService.getRateeIds(rideId, raterId)
                : java.util.List.of();

        return ResponseEntity.ok(ApiResponse.ok(
                new RatingEligibilityResponse(canRate, rateeIds)));
    }
}
