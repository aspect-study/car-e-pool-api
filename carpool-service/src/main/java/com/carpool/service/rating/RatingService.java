package com.carpool.service.rating;

import com.carpool.domain.entity.Ride;
import com.carpool.domain.entity.RideRating;
import com.carpool.domain.entity.User;
import com.carpool.domain.enums.BookingStatus;
import com.carpool.domain.enums.RideStatus;
import com.carpool.repository.BookingRepository;
import com.carpool.repository.RideRatingRepository;
import com.carpool.repository.RideRepository;
import com.carpool.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles all rating operations.
 * Ratings are mutual — both driver and passenger rate each other
 * after a completed ride. One rating per person per ride.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RatingService {

    private final RideRatingRepository ratingRepository;
    private final RideRepository       rideRepository;
    private final UserRepository       userRepository;
    private final BookingRepository    bookingRepository;

    // ── Submit rating ─────────────────────────────────────────────────────

    /**
     * Submits a rating from one user to another for a completed ride.
     * Validates: ride is COMPLETED, rater was part of the ride,
     * rater has not already rated this ride.
     */
    @Transactional
    public RideRating submitRating(Long rideId, Long raterId,
                                   Long rateeId, int stars,
                                   String comment) {
        // Validate inputs first — before any DB duplicate checks
        if (stars < 1 || stars > 5) {
            throw new IllegalArgumentException(
                    "Stars must be between 1 and 5.");
        }

        if (comment != null && comment.trim().length() > 1000) {
            throw new IllegalArgumentException(
                    "Comment is too long. Maximum 1000 characters allowed.");
        }

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Ride not found: " + rideId));

        if (ride.getStatus() != RideStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Ratings can only be submitted for completed rides.");
        }

        // Determine rater role — single declaration, uses already-loaded ride
        boolean raterIsDriver = ride.getDriver().getId().equals(raterId);

        // Duplicate check — per-ratee for drivers, per-ride for passengers
        if (raterIsDriver) {
            if (ratingRepository.existsByRideIdAndRaterIdAndRateeId(
                    rideId, raterId, rateeId)) {
                throw new IllegalStateException(
                        "You have already rated this passenger.");
            }
        } else {
            if (ratingRepository.existsByRideIdAndRaterId(rideId, raterId)) {
                throw new IllegalStateException(
                        "You have already rated this ride.");
            }
        }

        User rater = userRepository.findById(raterId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Rater not found: " + raterId));

        User ratee = userRepository.findById(rateeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Ratee not found: " + rateeId));

        String raterRole = raterIsDriver ? "DRIVER" : "PASSENGER";

        RideRating rating = RideRating.builder()
                .ride(ride)
                .rater(rater)
                .ratee(ratee)
                .stars(stars)
                .comment(comment != null && !comment.isBlank()
                        ? comment.trim() : null)
                .raterRole(raterRole)
                .build();

        RideRating saved = ratingRepository.save(rating);
        log.info("Rating saved: rideId={} raterId={} rateeId={} stars={} role={}",
                rideId, raterId, rateeId, stars, raterRole);
        return saved;
    }

    // ── Check eligibility ─────────────────────────────────────────────────

    /**
     * Checks if a user is eligible to rate a specific ride.
     * Must be part of the ride (driver or confirmed passenger)
     * and must not have already rated.
     */
    public boolean canRate(Long rideId, Long raterId) {
        Ride ride = rideRepository.findById(rideId).orElse(null);
        if (ride == null || ride.getStatus() != RideStatus.COMPLETED) return false;

        // Driver — can rate if at least one passenger hasn't been rated yet
        if (ride.getDriver().getId().equals(raterId)) {
            List<Long> passengerIds = bookingRepository
                    .findByRideIdAndStatusIn(
                            rideId, List.of(BookingStatus.CONFIRMED,
                                    BookingStatus.COMPLETED))
                    .stream()
                    .map(b -> b.getPassenger().getId())
                    .toList();

            return passengerIds.stream()
                    .anyMatch(passengerId -> !ratingRepository
                            .existsByRideIdAndRaterIdAndRateeId(
                                    rideId, raterId, passengerId));
        }

        // Passenger — can only rate once (one driver per ride)
        if (ratingRepository.existsByRideIdAndRaterId(rideId, raterId)) return false;

        // Check if rater was a confirmed passenger
        return bookingRepository.findByRideIdAndStatusIn(
                        rideId, List.of(BookingStatus.CONFIRMED,
                                BookingStatus.COMPLETED))
                .stream()
                .anyMatch(b -> b.getPassenger().getId().equals(raterId));
    }

    /**
     * Returns all ratee IDs for a given ride and rater.
     * Passenger rater → returns only the driver ID (single ratee).
     * Driver rater → returns ALL confirmed passenger IDs (multiple ratees).
     */
    public List<Long> getRateeIds(Long rideId, Long raterId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Ride not found: " + rideId));

        // If rater is passenger, ratee is the driver — single
        if (!ride.getDriver().getId().equals(raterId)) {
            return List.of(ride.getDriver().getId());
        }

        // If rater is driver, ratees are ALL confirmed passengers
        List<Long> passengerIds = bookingRepository
                .findByRideIdAndStatusIn(
                        rideId, List.of(BookingStatus.CONFIRMED,
                                BookingStatus.COMPLETED))
                .stream()
                .map(b -> b.getPassenger().getId())
                .toList();

        if (passengerIds.isEmpty()) {
            throw new IllegalStateException(
                    "No confirmed passengers found for ride: " + rideId);
        }
        return passengerIds;
    }

    /**
     * Kept for backward compatibility — single ratee use case (passenger rates driver).
     * @deprecated Use getRateeIds() instead.
     */
    @Deprecated
    public Long getRateeId(Long rideId, Long raterId) {
        return getRateeIds(rideId, raterId).get(0);
    }

    // ── Rating stats ──────────────────────────────────────────────────────

    /**
     * Returns formatted average driver rating string.
     * Example: "⭐ 4.8 (12 ratings)" or null if no ratings yet.
     */
    public String getDriverRatingLabel(Long userId) {
        Double avg   = ratingRepository.findAverageDriverRatingByRateeId(userId);
        long   count = ratingRepository.countDriverRatingsByRateeId(userId);
        if (avg == null || count == 0) return null;
        return String.format("⭐ %.1f (%d %s)",
                avg, count, count == 1 ? "rating" : "ratings");
    }

    /**
     * Returns formatted average passenger rating string.
     * Example: "⭐ 4.9 (8 ratings)" or null if no ratings yet.
     */
    public String getPassengerRatingLabel(Long userId) {
        Double avg   = ratingRepository.findAveragePassengerRatingByRateeId(userId);
        long   count = ratingRepository.countPassengerRatingsByRateeId(userId);
        if (avg == null || count == 0) return null;
        return String.format("⭐ %.1f (%d %s)",
                avg, count, count == 1 ? "rating" : "ratings");
    }

    /**
     * Returns a short rating label for ride cards.
     * Uses overall average (driver ratings only — shown on ride card).
     * Example: "⭐ 4.8" or empty string if no ratings yet.
     */
    public String getRideCardRatingLabel(Long driverUserId) {
        Double avg   = ratingRepository.findAverageDriverRatingByRateeId(driverUserId);
        long   count = ratingRepository.countDriverRatingsByRateeId(driverUserId);
        if (avg == null || count == 0) return "";
        return String.format(" ⭐ %.1f", avg);
    }

    /**
     * Checks if a user has already rated a specific ride.
     */
    public boolean hasRated(Long rideId, Long raterId) {
        return ratingRepository.existsByRideIdAndRaterId(rideId, raterId);
    }

    /**
     * Checks if rater has already rated a specific ratee on a specific ride.
     * Used for multi-passenger rides — prevents blocking driver from rating
     * remaining passengers after rating the first one.
     */
    public boolean hasRatedPassenger(Long rideId, Long raterId, Long rateeId) {
        return ratingRepository.existsByRideIdAndRaterIdAndRateeId(
                rideId, raterId, rateeId);
    }

    public Set<Long> getRatedPassengerIds(Long rideId, Long raterId) {
        return ratingRepository.findRateeIdsByRideIdAndRaterId(rideId, raterId);
    }

    /**
     * Returns all ratings received by a user — for profile display.
     */
    public List<RideRating> getRatingsReceived(Long userId) {
        return ratingRepository.findByRateeIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Batch fetch average driver ratings for a list of driver IDs.
     * Returns a map of driverId → avgRating.
     * Used by RideService to enrich search results in one query instead of N.
     */
    public Map<Long, Double> getAverageRatingsByDriverIds(List<Long> driverIds) {
        if (driverIds == null || driverIds.isEmpty()) return Map.of();
        try {
            return ratingRepository.findAverageRatingsByDriverIds(driverIds)
                    .stream()
                    .filter(row -> row[0] != null && row[1] != null)
                    .collect(Collectors.toMap(
                            row -> ((Number) row[0]).longValue(),
                            row -> ((Number) row[1]).doubleValue()));
        } catch (Exception e) {
            log.warn("Failed to batch fetch driver ratings for driverIds={}: {}",
                    driverIds, e.getMessage());
            return Map.of();
        }
    }
}