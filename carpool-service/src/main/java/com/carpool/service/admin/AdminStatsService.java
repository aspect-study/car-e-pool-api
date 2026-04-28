package com.carpool.service.admin;

import com.carpool.domain.enums.BookingStatus;
import com.carpool.domain.enums.RideStatus;
import com.carpool.repository.BookingRepository;
import com.carpool.repository.RideRepository;
import com.carpool.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Provides admin statistics for monitoring community health.
 * All queries are read-only — no side effects.
 */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final UserRepository    userRepository;
    private final RideRepository    rideRepository;
    private final BookingRepository bookingRepository;

    @Transactional(readOnly = true)
    public AdminStats getStats() {
        Instant startOfDay = LocalDate.now(ZoneId.of("Asia/Manila"))
                .atStartOfDay(ZoneId.of("Asia/Manila"))
                .toInstant();

        return new AdminStats(
                // Users
                userRepository.count(),
                userRepository.countByCreatedAtAfter(startOfDay),

                // Rides
                rideRepository.countByStatusIn(
                        List.of(RideStatus.ACTIVE, RideStatus.FULL)),
                rideRepository.count(),
                rideRepository.countByRideStatus(RideStatus.COMPLETED),
                rideRepository.countByRideStatus(RideStatus.CANCELLED),
                rideRepository.countRidesCreatedAfter(startOfDay),

                // Bookings
                bookingRepository.countByBookingStatus(BookingStatus.PENDING),
                bookingRepository.count(),
                bookingRepository.countByBookingStatus(BookingStatus.COMPLETED),
                bookingRepository.countBookingsCreatedAfter(startOfDay)
        );
    }

    public record AdminStats(
            long totalUsers,
            long newUsersToday,
            long activeRidesNow,
            long totalRides,
            long completedRides,
            long cancelledRides,
            long ridesPostedToday,
            long pendingBookingsNow,
            long totalBookings,
            long completedBookings,
            long bookingsMadeToday
    ) {}
}