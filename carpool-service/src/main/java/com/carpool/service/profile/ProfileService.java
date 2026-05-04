package com.carpool.service.profile;

import com.carpool.common.exception.UserNotFoundException;
import com.carpool.domain.entity.User;
import com.carpool.domain.enums.BookingStatus;
import com.carpool.domain.enums.RideStatus;
import com.carpool.repository.BookingRepository;
import com.carpool.repository.RideRepository;
import com.carpool.repository.UserRepository;
import com.carpool.service.admin.AdminStatsService;
import com.carpool.service.dto.response.AdminStatsResponse;
import com.carpool.service.dto.response.ProfileStatsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository    userRepository;
    private final RideRepository    rideRepository;
    private final BookingRepository bookingRepository;
    private final AdminStatsService adminStatsService;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy")
                    .withZone(ZoneId.of("Asia/Manila"));

    /**
     * Build full profile stats for a user — role-aware.
     * Driver stats included if user has posted at least one ride.
     * Passenger stats included if user has made at least one booking.
     */
    @Cacheable(value = "profileStats", key = "#userId")
    @Transactional(readOnly = true)
    public ProfileStatsResponse getProfileStats(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // ── Driver stats ─────────────────────────────────────────────────
        int driverRidesPosted      = rideRepository.countByDriverId(userId);
        int driverCompleted        = rideRepository.countByDriverIdAndStatus(userId, RideStatus.COMPLETED);
        int driverCancelled        = rideRepository.countByDriverIdAndStatus(userId, RideStatus.CANCELLED);
        int driverPassengersServed = rideRepository.sumPassengersServedByDriverId(userId);
        // Completion rate uses only terminal rides (completed + cancelled)
        // Excludes active/departed rides still in progress from the denominator
        Integer driverCompletionRate = computeRate(driverCompleted, driverCompleted + driverCancelled);

        // ── Passenger stats ───────────────────────────────────────────────
        int passengerBookingsMade   = bookingRepository.countByPassengerId(userId);
        int passengerCompleted      = bookingRepository.countByPassengerIdAndStatus(
                userId, BookingStatus.COMPLETED);
        int passengerCancelledByMe  = bookingRepository.countByPassengerIdAndStatus(
                userId, BookingStatus.CANCELLED_BY_PASSENGER);
        // Same logic — only terminal bookings in denominator
        Integer passengerCompletionRate = computeRate(passengerCompleted,
                passengerCompleted + passengerCancelledByMe);

        // ── Role label ────────────────────────────────────────────────────
        String roleLabel = buildRoleLabel(driverRidesPosted, passengerBookingsMade);

        // ── Member since ──────────────────────────────────────────────────
        String memberSince = DATE_FMT.format(user.getCreatedAt());

        return new ProfileStatsResponse(
                user.getId(),
                user.getFullName(),
                user.getTelegramHandle(),
                roleLabel,
                memberSince,
                // Vehicle info — null if not set
                user.getCarModel(),
                user.getCarColor(),
                user.getPlateNumber(),
                driverRidesPosted   > 0 ? driverRidesPosted      : null,
                driverRidesPosted   > 0 ? driverCompleted         : null,
                driverRidesPosted   > 0 ? driverCancelled         : null,
                driverRidesPosted   > 0 ? driverPassengersServed  : null,
                driverRidesPosted   > 0 ? driverCompletionRate    : null,
                passengerBookingsMade > 0 ? passengerBookingsMade   : null,
                passengerBookingsMade > 0 ? passengerCompleted       : null,
                passengerBookingsMade > 0 ? passengerCancelledByMe   : null,
                passengerBookingsMade > 0 ? passengerCompletionRate  : null
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Compute completion rate as percentage.
     * Returns null if total is 0 to avoid division by zero.
     */
    private Integer computeRate(int completed, int total) {
        if (total == 0) return null;
        return (int) Math.round((completed * 100.0) / total);
    }

    /**
     * Build role label based on activity — not DB role column.
     * More accurate than stored role since it reflects actual usage.
     */
    private String buildRoleLabel(int ridesPosted, int bookingsMade) {
        boolean isDriver    = ridesPosted   > 0;
        boolean isPassenger = bookingsMade  > 0;

        if (isDriver && isPassenger) return "🚗 Driver & 🧳 Passenger";
        if (isDriver)                return "🚗 Driver";
        if (isPassenger)             return "🧳 Passenger";
        return "👋 New Member";
    }

    /**
     * Platform-wide statistics for admin dashboard.
     */
    @Transactional(readOnly = true)
    public AdminStatsResponse getAdminStats() {
        AdminStatsService.AdminStats s = adminStatsService.getStats();
        return new AdminStatsResponse(
                s.totalUsers(),
                s.newUsersToday(),
                s.activeRidesNow(),
                s.ridesPostedToday(),
                s.totalRides(),
                s.completedRides(),
                s.cancelledRides(),
                s.pendingBookingsNow(),
                s.bookingsMadeToday(),
                s.totalBookings(),
                s.completedBookings()
        );
    }
}