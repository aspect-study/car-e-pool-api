package com.carpool.repository;

import com.carpool.domain.entity.Booking;
import com.carpool.domain.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByRideIdAndPassengerId(Long rideId, Long passengerId);

    @Query("""
    SELECT COUNT(b) > 0 FROM Booking b
    WHERE b.ride.id = :rideId
      AND b.passenger.id = :passengerId
      AND b.status IN ('CONFIRMED', 'PENDING')
    """)
    boolean existsActiveByRideIdAndPassengerId(
            @Param("rideId")      Long rideId,
            @Param("passengerId") Long passengerId);

    /**
     * Passenger's booking history.
     */
    List<Booking> findByPassengerIdAndStatusInOrderByCreatedAtDesc(
            Long passengerId, List<BookingStatus> statuses);

    @Query("""
    SELECT b FROM Booking b
    JOIN FETCH b.ride r
    JOIN FETCH r.originHub
    JOIN FETCH r.destinationHub
    JOIN FETCH b.passenger
    WHERE r.id = :rideId
      AND b.status IN ('CONFIRMED', 'PENDING')
    """)
    List<Booking> findActiveBookingsForRide(@Param("rideId") Long rideId);

    /**
     * Count confirmed seats on a ride — used for seat availability checks
     * outside the locked transaction path.
     */
    @Query("""
        SELECT COALESCE(SUM(b.seatsReserved), 0)
        FROM Booking b
        WHERE b.ride.id = :rideId
          AND b.status IN ('PENDING', 'CONFIRMED')
        """)
    Integer sumReservedSeats(@Param("rideId") Long rideId);

    /**
     * Driver's view — all bookings on a specific ride.
     */
    @Query("""
    SELECT b FROM Booking b
    WHERE b.ride.id = :rideId
      AND b.status IN :statuses
    ORDER BY b.createdAt DESC
    """)
    List<Booking> findByRideIdAndStatusIn(
            @Param("rideId")   Long rideId,
            @Param("statuses") List<BookingStatus> statuses);

    /**
     * All active bookings across all rides posted by this driver.
     * JOIN FETCH to avoid LazyInitializationException outside transaction.
     */
    @Query("""
    SELECT b FROM Booking b
    JOIN FETCH b.ride r
    JOIN FETCH r.driver
    JOIN FETCH r.originHub
    JOIN FETCH r.destinationHub
    JOIN FETCH b.passenger
    WHERE r.driver.id = :driverId
      AND b.status IN :statuses
    ORDER BY r.departureTime ASC, b.createdAt DESC
    """)
    List<Booking> findByDriverIdAndStatusIn(
            @Param("driverId") Long driverId,
            @Param("statuses") List<BookingStatus> statuses);

    @Query("""
    SELECT b FROM Booking b
    JOIN FETCH b.ride r
    JOIN FETCH r.driver
    JOIN FETCH r.originHub
    JOIN FETCH r.destinationHub
    JOIN FETCH b.passenger
    WHERE b.id = :id
    """)
    Optional<Booking> findByIdWithDetails(@Param("id") Long id);

    @Query("""
    SELECT b FROM Booking b
    JOIN FETCH b.ride r
    JOIN FETCH r.originHub
    JOIN FETCH r.destinationHub
    JOIN FETCH b.passenger
    WHERE r.id = :rideId
      AND b.status = 'CANCELLED_BY_DRIVER'
    ORDER BY b.createdAt DESC
    """)
    List<Booking> findCancelledByDriverBookingsForRide(@Param("rideId") Long rideId);

    /**
     * Find all PENDING bookings for a specific ride.
     * Used by driver to see pending requests.
     */
    @Query("""
    SELECT b FROM Booking b
    JOIN FETCH b.passenger
    WHERE b.ride.id = :rideId
      AND b.status = 'PENDING'
    ORDER BY b.createdAt ASC
    """)
    List<Booking> findPendingByRideId(@Param("rideId") Long rideId);

    /**
     * Find PENDING bookings that need a reminder — reminder_count < 3
     * and the next reminder interval has passed.
     * Reminder schedule: 5 min, 10 min, 15 min after creation.
     */
    @Query("""
    SELECT b FROM Booking b
    JOIN FETCH b.ride r
    JOIN FETCH r.driver
    JOIN FETCH b.passenger
    WHERE b.status = 'PENDING'
      AND b.reminderCount < 3
      AND b.expiresAt > :now
    """)
    List<Booking> findPendingNeedingReminder(@Param("now") Instant now);

    /**
     * Find PENDING bookings that have exceeded their expiry time
     * and have already received 3 reminders — ready for auto-decline.
     */
    @Query("""
    SELECT b FROM Booking b
    JOIN FETCH b.ride r
    JOIN FETCH r.driver
    JOIN FETCH b.passenger
    WHERE b.status = 'PENDING'
      AND b.expiresAt < :now
    """)
    List<Booking> findExpiredPendingBookings(@Param("now") Instant now);

    /**
     * Count pending requests for a driver's active ride.
     * Used to show badge on driver's main menu.
     */
    @Query("""
    SELECT COUNT(b) FROM Booking b
    WHERE b.ride.driver.id = :driverId
      AND b.status = 'PENDING'
    """)
    long countPendingByDriverId(@Param("driverId") Long driverId);

    /**
     * Count bookings by passenger and status — used for profile stats.
     */
    @Query("""
    SELECT COUNT(b) FROM Booking b
    WHERE b.passenger.id = :passengerId
      AND b.status = :status
    """)
    int countByPassengerIdAndStatus(@Param("passengerId") Long passengerId,
                                    @Param("status") BookingStatus status);

    /**
     * Count total bookings made by passenger — all statuses.
     */
    @Query("SELECT COUNT(b) FROM Booking b WHERE b.passenger.id = :passengerId")
    int countByPassengerId(@Param("passengerId") Long passengerId);

    /**
     * Paginated version — passenger's active bookings.
     * Used by REST API only — bot uses unpaged version.
     */
    Page<Booking> findByPassengerIdAndStatusInOrderByCreatedAtDesc(
            Long passengerId,
            List<BookingStatus> statuses,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Paginated version — bookings for a specific ride.
     * Used by REST API only — bot uses unpaged version.
     */
    @Query("""
    SELECT b FROM Booking b
    WHERE b.ride.id = :rideId
      AND b.status IN :statuses
    ORDER BY b.createdAt DESC
    """)
    Page<Booking> findByRideIdAndStatusIn(
            @Param("rideId")   Long rideId,
            @Param("statuses") List<BookingStatus> statuses,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Last 7 past bookings for passenger booking history.
     * DB-level limit — safe for large datasets.
     */
    @Query("""
    SELECT b FROM Booking b
    WHERE b.passenger.id = :passengerId
      AND b.status IN ('COMPLETED', 'CANCELLED_BY_PASSENGER', 'CANCELLED_BY_DRIVER',
                       'DECLINED', 'TIMED_OUT')
    ORDER BY b.createdAt DESC
    LIMIT 7
    """)
    List<Booking> findTop7PastBookingsByPassengerId(@Param("passengerId") Long passengerId);

    /**
     * Find COMPLETED bookings for a ride — used for post-completion notifications.
     * Called after ride completion when booking status is already COMPLETED.
     */
    @Query("SELECT b FROM Booking b JOIN FETCH b.passenger JOIN FETCH b.ride r " +
            "JOIN FETCH r.originHub JOIN FETCH r.destinationHub JOIN FETCH r.driver " +
            "WHERE r.id = :rideId AND b.status = 'COMPLETED'")
    List<Booking> findCompletedBookingsForRide(@Param("rideId") Long rideId);

    /**
     * Fetch passenger info only for active bookings on a ride.
     * Used for cancel notification summary — avoids full RideResponse load.
     */
    @Query("SELECT b FROM Booking b JOIN FETCH b.passenger " +
            "WHERE b.ride.id = :rideId " +
            "AND b.status IN ('CONFIRMED', 'PENDING')")
    List<Booking> findActivePassengersForRide(@Param("rideId") Long rideId);
}
