package com.carpool.service.admin;

import com.carpool.domain.enums.BookingStatus;
import com.carpool.domain.enums.HubStatus;
import com.carpool.domain.enums.RideStatus;
import com.carpool.repository.BookingRepository;
import com.carpool.repository.HubRepository;
import com.carpool.repository.RideRatingRepository;
import com.carpool.repository.RideRepository;
import com.carpool.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * Provides admin statistics for monitoring community health.
 * All queries are read-only — no side effects.
 */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final UserRepository       userRepository;
    private final RideRepository       rideRepository;
    private final BookingRepository    bookingRepository;
    private final HubRepository        hubRepository;
    private final RideRatingRepository rideRatingRepository;

    @Transactional(readOnly = true)
    @Cacheable(value = "adminStats", key = "'global'")
    public AdminStats getStats() {
        ZoneId manila = ZoneId.of("Asia/Manila");
        Instant startOfDay = LocalDate.now(manila).atStartOfDay(manila).toInstant();
        Instant startOfWeek = LocalDate.now(manila)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(manila)
                .toInstant();

        return new AdminStats(
                // Users
                userRepository.count(),
                userRepository.countByCreatedAtAfter(startOfDay),
                userRepository.countByCreatedAtAfter(startOfWeek),

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
                bookingRepository.countBookingsCreatedAfter(startOfDay),
                bookingRepository.countByBookingStatus(BookingStatus.DECLINED),
                bookingRepository.countByBookingStatus(BookingStatus.CANCELLED_BY_DRIVER),
                bookingRepository.countByBookingStatus(BookingStatus.CANCELLED_BY_PASSENGER),
                bookingRepository.countByBookingStatus(BookingStatus.TIMED_OUT),

                // Community health
                hubRepository.countByStatus(HubStatus.PENDING),
                rideRatingRepository.findGlobalAverageRating(),
                rideRatingRepository.count()
        );
    }

    public record AdminStats(
            long totalUsers,
            long newUsersToday,
            long newUsersThisWeek,

            long activeRidesNow,
            long totalRides,
            long completedRides,
            long cancelledRides,
            long ridesPostedToday,

            long pendingBookingsNow,
            long totalBookings,
            long completedBookings,
            long bookingsMadeToday,
            long declinedBookings,
            long cancelledByDriverBookings,
            long cancelledByPassengerBookings,
            long timedOutBookings,

            long pendingHubSuggestions,
            Double avgPlatformRating,
            long totalRatings
    ) {
        public double cancellationRate() {
            return totalRides == 0 ? 0.0 : (cancelledRides * 100.0 / totalRides);
        }

        public double bookingCompletionRate() {
            return totalBookings == 0 ? 0.0 : (completedBookings * 100.0 / totalBookings);
        }
    }
}
