package com.carpool.service.profile;

import com.carpool.common.exception.UserNotFoundException;
import com.carpool.domain.entity.User;
import com.carpool.domain.enums.BookingStatus;
import com.carpool.domain.enums.RideStatus;
import com.carpool.repository.BookingRepository;
import com.carpool.repository.RideRepository;
import com.carpool.repository.UserRepository;
import com.carpool.service.dto.response.ProfileStatsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy")
                    .withZone(ZoneId.of("Asia/Manila"));

    /**
     * Build full profile stats for a user — role-aware.
     * Driver stats included if user has posted at least one ride.
     * Passenger stats included if user has made at least one booking.
     */
    @Transactional(readOnly = true)
    public ProfileStatsResponse getProfileStats(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // ── Driver stats ─────────────────────────────────────────────────
        int driverRidesPosted      = rideRepository.countByDriverId(userId);
        int driverCompleted        = rideRepository.countByDriverIdAndStatus(userId, RideStatus.COMPLETED);
        int driverCancelled        = rideRepository.countByDriverIdAndStatus(userId, RideStatus.CANCELLED);
        int driverPassengersServed = rideRepository.sumPassengersServedByDriverId(userId);
        Integer driverCompletionRate = computeRate(driverCompleted, driverRidesPosted);

        // ── Passenger stats ───────────────────────────────────────────────
        int passengerBookingsMade   = bookingRepository.countByPassengerId(userId);
        int passengerCompleted      = bookingRepository.countByPassengerIdAndStatus(
                userId, BookingStatus.COMPLETED);
        int passengerCancelledByMe  = bookingRepository.countByPassengerIdAndStatus(
                userId, BookingStatus.CANCELLED_BY_PASSENGER);
        Integer passengerCompletionRate = computeRate(passengerCompleted, passengerBookingsMade);

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
        boolean isDiver    = ridesPosted   > 0;
        boolean isPassenger = bookingsMade > 0;

        if (isDiver && isPassenger) return "🚗 Driver & 🧳 Passenger";
        if (isDiver)                return "🚗 Driver";
        if (isPassenger)            return "🧳 Passenger";
        return "👋 New Member";
    }
}