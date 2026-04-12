package com.carpool.service.dto.response;

import com.carpool.domain.enums.BookingStatus;
import com.carpool.domain.enums.PaymentMethod;
import com.carpool.domain.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record BookingResponse(
        Long id,
        Long rideId,
        UserResponse passenger,
        Integer seatsReserved,
        WaypointResponse pickupWaypoint,    // null = ride's origin
        WaypointResponse dropoffWaypoint,   // null = ride's destination
        BookingStatus status,
        BigDecimal contributionDue,
        BigDecimal contributionPaid,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        Instant createdAt
) {}
