package com.carpool.web.controller;

import com.carpool.common.response.ApiResponse;
import com.carpool.service.booking.BookingService;
import com.carpool.service.dto.request.CreateBookingRequest;
import com.carpool.service.dto.request.UpdatePaymentRequest;
import com.carpool.service.dto.response.BookingResponse;
import com.carpool.web.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Seat reservation and payment tracking")
public class BookingController {

    private final BookingService bookingService;

    @Operation(summary = "Book a seat on a ride",
            description = """
                    Passenger reserves a seat. Runs under **pessimistic lock** —
                    race-condition safe even if two passengers book simultaneously.
                    
                    - `pickupWaypointId` — null means board at ride's origin hub
                    - `dropoffWaypointId` — null means alight at ride's destination hub
                    """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "Booking confirmed")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "Ride is full or already booked")
    /**
     * POST /api/v1/rides/{rideId}/bookings
     * Passenger books a seat. Runs under pessimistic lock — race-condition safe.
     */
    @PostMapping("/rides/{rideId}/bookings")
    public ResponseEntity<ApiResponse<BookingResponse>> createBooking(
            @PathVariable Long rideId,
            @Valid @RequestBody CreateBookingRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        BookingResponse booking = bookingService.createBooking(
                rideId, request, currentUser.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(booking));
    }

    @Operation(summary = "Get my bookings",
            description = "Returns all bookings made by the authenticated passenger, " +
                    "all statuses, ordered by creation date descending.",
            security = @SecurityRequirement(name = "bearerAuth"))
    /**
     * GET /api/v1/bookings/mine
     * Passenger views all their bookings (all statuses).
     */
    @GetMapping("/bookings/mine")
    public ResponseEntity<ApiResponse<List<BookingResponse>>> getMyBookings(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        return ResponseEntity.ok(
                ApiResponse.ok(bookingService.getMyBookings(currentUser.getUserId())));
    }

    @Operation(summary = "Cancel my booking",
            description = "Passenger cancels their booking. " +
                    "Automatically restores seat to the ride. " +
                    "If ride was FULL, transitions back to ACTIVE.",
            security = @SecurityRequirement(name = "bearerAuth"))
    /**
     * DELETE /api/v1/bookings/{id}
     * Passenger cancels their booking. Restores seat to ride.
     */
    @DeleteMapping("/bookings/{id}")
    public ResponseEntity<ApiResponse<BookingResponse>> cancelBooking(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        BookingResponse booking = bookingService.cancelBooking(id, currentUser.getUserId(), null);
        return ResponseEntity.ok(ApiResponse.ok(booking));
    }

    @Operation(summary = "Record cash payment",
            description = """
                    Record a cash contribution payment on a booking.
                    
                    Payment status is automatically recalculated:
                    - Partial payment → `PARTIALLY_PAID`
                    - Full payment → `PAID`
                    
                    Multiple calls accumulate (e.g. ₱100 + ₱50 = ₱150 total paid).
                    """,
            security = @SecurityRequirement(name = "bearerAuth"))
    /**
     * PATCH /api/v1/bookings/{id}/payment
     * Record cash contribution payment on a booking.
     */
    @PatchMapping("/bookings/{id}/payment")
    public ResponseEntity<ApiResponse<BookingResponse>> updatePayment(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePaymentRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        BookingResponse booking = bookingService.updatePayment(
                id, request, currentUser.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(booking));
    }
}
