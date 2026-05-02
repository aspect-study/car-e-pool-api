package com.carpool.service.user;

import com.carpool.common.exception.UserNotFoundException;
import com.carpool.domain.entity.Booking;
import com.carpool.domain.entity.User;
import com.carpool.domain.enums.BookingStatus;
import com.carpool.domain.enums.RideStatus;
import com.carpool.domain.enums.UserStatus;
import com.carpool.repository.BookingRepository;
import com.carpool.repository.RideRepository;
import com.carpool.repository.UserRepository;
import com.carpool.service.dto.request.UpdateRoleRequest;
import com.carpool.service.dto.response.UserResponse;
import com.carpool.service.event.RideEvents;
import com.carpool.service.mapper.EntityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final EntityMapper   mapper;
    private final RideRepository rideRepository;
    private final BookingRepository bookingRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Cacheable(value = "users", key = "#userId")
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId) {
        return userRepository.findById(userId)
                .map(mapper::toUserResponse)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    /**
     * Driver role upgrade — evicts user cache so the new role
     * is reflected on the next request without stale data.
     */
    @CacheEvict(value = "users", key = "#userId")
    @Transactional
    public UserResponse updateRole(Long userId, UpdateRoleRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // NOTE: Role change takes effect on next login.
        // Existing JWTs carry the old role until they expire.
        // User must re-authenticate to get a token with the updated role.
        log.info("User role updated: userId={} {} → {}", userId, user.getRole(), request.role());
        user.setRole(request.role());
        return mapper.toUserResponse(userRepository.save(user));
    }

    /**
     * Admin: suspend or ban a user.
     */
    @CacheEvict(value = "users", key = "#userId")
    @Transactional
    public UserResponse updateStatus(Long userId, UserStatus status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        log.warn("User status changed: userId={} {} → {}", userId, user.getStatus(), status);
        user.setStatus(status);
        return mapper.toUserResponse(userRepository.save(user));
    }

    /**
     * Soft-deletes the user account.
     * - Anonymizes personal data (name, handle, telegramId)
     * - Cancels all active bookings and notifies affected drivers
     * - Cancels active ride and notifies all passengers
     * - Marks account as deleted with a timestamp
     */
    @CacheEvict(value = "users", key = "#userId")
    @Transactional
    public void deleteAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // Cancel active ride if exists
        rideRepository.findActiveRideByDriverId(userId).ifPresent(ride -> {
            ride.setStatus(RideStatus.CANCELLED);
            rideRepository.save(ride);
            eventPublisher.publishEvent(new RideEvents.RideCancelledEvent(ride, "Account deleted"));
            log.info("Cancelled active ride rideId={} due to account deletion", ride.getId());
        });

        // Cancel all active bookings
        List<Booking> activeBookings = bookingRepository.findByPassengerIdAndStatusInOrderByCreatedAtDesc(
                userId,
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED));

        for (Booking booking : activeBookings) {
            booking.setStatus(BookingStatus.CANCELLED_BY_PASSENGER);
            booking.setCancellationReason("Passenger account deleted");
            bookingRepository.save(booking);
            eventPublisher.publishEvent(
                    new RideEvents.BookingCancelledByPassengerEvent(booking));
            log.info("Cancelled bookingId={} due to account deletion", booking.getId());
        }

        // Anonymize personal data — hard delete is avoided to preserve
        // ride/booking history integrity for other users
        user.setFullName("Deleted User");
        user.setTelegramHandle(null);
        user.setTelegramId(0L);
        user.setDeleted(true);
        user.setDeletedAt(Instant.now());
        userRepository.save(user);

        log.info("Account deleted and anonymized for userId={}", userId);
    }
}
