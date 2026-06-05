package com.carpool.web.controller;

import com.carpool.common.response.ApiResponse;
import com.carpool.domain.enums.BookingStatus;
import com.carpool.service.booking.BookingService;
import com.carpool.service.dto.request.CancelBookingRequest;
import com.carpool.service.dto.request.CreateBookingRequest;
import com.carpool.service.dto.request.DeclineBookingRequest;
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
import com.carpool.common.response.PagedResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Seat reservation and payment tracking")
public class BookingController {

    private final BookingService bookingService;

    /**
     * POST /api/v1/rides/{rideId}/bookings
     * Passenger books a seat. Runs under pessimistic lock — race-condition safe.
     */
    @Operation(summary = "Book a seat on a ride",
            description = """
                    Passenger reserves a seat. Runs under **pessimistic lock** —
                    race-condition safe even if two passengers book simultaneously.
                    
                    Booking starts as **PENDING** — driver must Accept or Decline.
                    Auto-declines after 60 minutes if driver does not respond.
                    
                    - `pickupWaypointId` — null means board at ride's origin hub
                    - `dropoffWaypointId` — null means alight at ride's destination hub
                    - `passengerMessage` — optional note to driver (max 500 chars)
                    """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "Booking request sent — awaiting driver approval")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "Ride is full or passenger already has active booking")
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
     * GET /api/v1/rides/{rideId}/bookings
     * Driver views all bookings on their ride.
     */
    @Operation(summary = "Get bookings for a ride",
            description = """
            Driver views all bookings on their ride.
            
            **Optional filter:** `status` — filter by booking status.
            If omitted, returns CONFIRMED and PENDING bookings only.
            Pass `status=COMPLETED` to review passengers on a past ride.
            
            Ordered by creation date ascending.
            
            **Pagination:** `page` (default 0), `size` (default 10, max 50)
            """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "List of bookings for the ride")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "Not the ride owner")
    @GetMapping("/rides/{rideId}/bookings")
    public ResponseEntity<ApiResponse<PagedResponse<BookingResponse>>> getRideBookings(
            @PathVariable Long rideId,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 50),
                Sort.by("createdAt").ascending());

        return ResponseEntity.ok(ApiResponse.ok(
                bookingService.getBookingsByRideId(rideId, status, pageable)));
    }

    /**
     * GET /api/v1/bookings/mine
     * Passenger views all their bookings (all statuses).
     */
    @Operation(summary = "Get my bookings",
            description = """
                Returns all bookings made by the authenticated passenger.
                Includes all statuses (PENDING, CONFIRMED, COMPLETED, CANCELLED, etc.)
                ordered by creation date descending.
                
                **Pagination:** `page` (default 0), `size` (default 10, max 50)
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Passenger's booking history")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "Not authenticated")
    @GetMapping("/bookings/mine")
    public ResponseEntity<ApiResponse<PagedResponse<BookingResponse>>> getMyBookings(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 50),
                Sort.by("createdAt").descending());

        return ResponseEntity.ok(ApiResponse.ok(
                bookingService.getMyBookings(currentUser.getUserId(), pageable)));
    }

    /**
     * POST /api/v1/bookings/{id}/accept
     * Driver accepts a pending booking request.
     */
    @Operation(summary = "Accept a booking request",
            description = """
                    Driver accepts a PENDING booking request.
                    
                    - Booking transitions: PENDING → CONFIRMED
                    - Passenger is notified via Telegram
                    - If ride was FULL, no change to ride status
                    """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Booking accepted — passenger notified")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "Not the ride owner")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "Booking not found")
    @PostMapping("/bookings/{id}/accept")
    public ResponseEntity<ApiResponse<BookingResponse>> acceptBooking(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        BookingResponse booking = bookingService.acceptBooking(id, currentUser.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(booking));
    }

    /**
     * POST /api/v1/bookings/{id}/decline
     * Driver declines a pending booking request with reason.
     */
    @Operation(summary = "Decline a booking request",
            description = """
                Driver declines a PENDING booking request.
                
                - Booking transitions: PENDING → DECLINED
                - Seat is restored to the ride
                - If ride was FULL, transitions back to ACTIVE
                - Passenger is notified with the decline reason
                
                Decline reasons: Already fully booked, Route change, Vehicle issue, Other
                
                **Request body is optional** — omit entirely or send `{}` if no reason provided.
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Booking declined — passenger notified")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "Not the ride owner")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "Booking not found")
    @PostMapping("/bookings/{id}/decline")
    public ResponseEntity<ApiResponse<BookingResponse>> declineBooking(
            @PathVariable Long id,
            @RequestBody(required = false) DeclineBookingRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        String reason = request != null ? request.reason() : null;
        BookingResponse booking = bookingService.declineBooking(
                id, currentUser.getUserId(), reason);
        return ResponseEntity.ok(ApiResponse.ok(booking));
    }

    /**
     * DELETE /api/v1/bookings/{id}
     * Passenger cancels their booking with optional reason.
     */
    @Operation(summary = "Cancel my booking",
            description = """
                Passenger cancels their own PENDING or CONFIRMED booking.
                
                - Booking transitions: PENDING/CONFIRMED → CANCELLED_BY_PASSENGER
                - Seat is restored to the ride
                - If ride was FULL, transitions back to ACTIVE
                - Driver is notified with the cancellation reason
                
                Cancel reasons: Found another ride, Change of plans, Running late, Other
                
                **Request body is optional** — omit entirely or send `{}` if no reason provided.
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Booking cancelled — driver notified")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "Not the booking owner")
    @DeleteMapping("/bookings/{id}")
    public ResponseEntity<ApiResponse<BookingResponse>> cancelBooking(
            @PathVariable Long id,
            @RequestBody(required = false) CancelBookingRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        String reason = request != null ? request.reason() : null;
        BookingResponse booking = bookingService.cancelBooking(
                id, currentUser.getUserId(), reason);
        return ResponseEntity.ok(ApiResponse.ok(booking));
    }

    /**
     * PATCH /api/v1/bookings/{id}/payment
     * Record cash contribution payment on a booking.
     */
    @Operation(summary = "Record gas share payment",
            description = """
                    Record a cash gas share payment on a booking.
                    
                    Payment status is automatically recalculated:
                    - Partial payment → `PARTIALLY_PAID`
                    - Full payment → `PAID`
                    
                    Multiple calls accumulate (e.g. ₱100 + ₱50 = ₱150 total paid).
                    """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Payment recorded")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "Not the ride owner")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "Booking not found")
    @PatchMapping("/bookings/{id}/payment")
    public ResponseEntity<ApiResponse<BookingResponse>> updatePayment(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePaymentRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        BookingResponse booking = bookingService.updatePayment(
                id, request, currentUser.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(booking));
    }

    /**
     * GET /api/v1/bookings/{id}
     * Get single booking detail — accessible by booking owner or ride driver.
     */
    @Operation(summary = "Get booking detail",
            description = """
                Returns full detail of a single booking.
                
                Accessible by:
                - The passenger who made the booking
                - The driver of the ride
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Booking detail")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "Not the booking owner or ride driver")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "Booking not found")
    @GetMapping("/bookings/{id}")
    public ResponseEntity<ApiResponse<BookingResponse>> getBookingById(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        return ResponseEntity.ok(ApiResponse.ok(
                bookingService.getBookingById(id, currentUser.getUserId())));
    }
}