package com.carpool.web.controller;

import com.carpool.common.response.ApiResponse;
import com.carpool.service.booking.BookingService;
import com.carpool.service.dto.request.CreateBookingRequest;
import com.carpool.service.dto.request.UpdatePaymentRequest;
import com.carpool.service.dto.response.BookingResponse;
import com.carpool.web.security.AuthenticatedUser;
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
public class BookingController {

    private final BookingService bookingService;

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

    /**
     * DELETE /api/v1/bookings/{id}
     * Passenger cancels their booking. Restores seat to ride.
     */
    @DeleteMapping("/bookings/{id}")
    public ResponseEntity<ApiResponse<BookingResponse>> cancelBooking(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        BookingResponse booking = bookingService.cancelBooking(id, currentUser.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(booking));
    }

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
