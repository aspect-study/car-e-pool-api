package com.carpool.repository;

import com.carpool.domain.entity.RideRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface RideRatingRepository extends JpaRepository<RideRating, Long> {

    /**
     * Check if a user has already rated a specific ride.
     * Used to prevent duplicate ratings.
     */
    boolean existsByRideIdAndRaterId(Long rideId, Long raterId);

    /**
     * Find a specific rating by ride and rater.
     */
    Optional<RideRating> findByRideIdAndRaterId(Long rideId, Long raterId);

    /**
     * Get all ratings received by a user (as ratee).
     * Used to calculate average rating and display review history.
     */
    List<RideRating> findByRateeIdOrderByCreatedAtDesc(Long rateeId);

    Page<RideRating> findByRateeIdOrderByCreatedAtDesc(Long rateeId, Pageable pageable);

    @Query("SELECT r FROM RideRating r " +
           "JOIN FETCH r.rater " +
           "JOIN FETCH r.ratee " +
           "JOIN FETCH r.ride " +
           "WHERE r.ratee.id = :rateeId " +
           "ORDER BY r.createdAt DESC")
    Page<RideRating> findByRateeIdWithAssociations(
            @Param("rateeId") Long rateeId, Pageable pageable);

    /**
     * Calculate average star rating for a user.
     * Returns null if no ratings exist yet.
     */
    @Query("SELECT AVG(r.stars) FROM RideRating r WHERE r.ratee.id = :rateeId")
    Double findAverageRatingByRateeId(@Param("rateeId") Long rateeId);

    /**
     * Count total ratings received by a user.
     */
    long countByRateeId(Long rateeId);

    /**
     * Calculate platform-wide average star rating across all ratings.
     * Returns null if no ratings exist yet. Used for admin stats.
     */
    @Query("SELECT AVG(r.stars) FROM RideRating r")
    Double findGlobalAverageRating();

    /**
     * Calculate average rating as driver only.
     */
    @Query("SELECT AVG(r.stars) FROM RideRating r " +
            "WHERE r.ratee.id = :rateeId AND r.raterRole = 'PASSENGER'")
    Double findAverageDriverRatingByRateeId(@Param("rateeId") Long rateeId);

    /**
     * Calculate average rating as passenger only.
     */
    @Query("SELECT AVG(r.stars) FROM RideRating r " +
            "WHERE r.ratee.id = :rateeId AND r.raterRole = 'DRIVER'")
    Double findAveragePassengerRatingByRateeId(@Param("rateeId") Long rateeId);

    /**
     * Count driver ratings received.
     */
    @Query("SELECT COUNT(r) FROM RideRating r " +
            "WHERE r.ratee.id = :rateeId AND r.raterRole = 'PASSENGER'")
    long countDriverRatingsByRateeId(@Param("rateeId") Long rateeId);

    /**
     * Count passenger ratings received.
     */
    @Query("SELECT COUNT(r) FROM RideRating r " +
            "WHERE r.ratee.id = :rateeId AND r.raterRole = 'DRIVER'")
    long countPassengerRatingsByRateeId(@Param("rateeId") Long rateeId);

    /**
     * Get all ratings given by a user (as rater).
     */
    List<RideRating> findByRaterIdOrderByCreatedAtDesc(Long raterId);

    /**
     * Checks if rater has already rated a specific ratee on a specific ride.
     * Used for multi-passenger rides — driver can rate each passenger independently.
     */
    boolean existsByRideIdAndRaterIdAndRateeId(Long rideId, Long raterId, Long rateeId);

    /**
     * Batch fetch all ratee IDs already rated by a given rater on a ride.
     * Used to avoid N+1 in the passenger selection screen.
     */
    @Query("SELECT r.ratee.id FROM RideRating r WHERE r.ride.id = :rideId AND r.rater.id = :raterId")
    Set<Long> findRateeIdsByRideIdAndRaterId(@Param("rideId") Long rideId, @Param("raterId") Long raterId);

    /**
     * Batch fetch average driver ratings for a list of driver IDs.
     * Used by RideService to enrich ride search results in a single query.
     */
    @Query("SELECT r.ratee.id, AVG(r.stars) FROM RideRating r " +
            "WHERE r.ratee.id IN :driverIds AND r.raterRole = 'PASSENGER' " +
            "GROUP BY r.ratee.id")
    List<Object[]> findAverageRatingsByDriverIds(@Param("driverIds") List<Long> driverIds);
}