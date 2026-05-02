package com.carpool.service.dto.response;

public record AdminStatsResponse(
        // User stats
        long totalUsers,
        long newUsersToday,
        // Ride stats
        long activeRidesNow,
        long ridesPostedToday,
        long totalRides,
        long completedRides,
        long cancelledRides,
        // Booking stats
        long pendingRequestsNow,
        long bookingsMadeToday,
        long totalBookings,
        long completedBookings
) {}