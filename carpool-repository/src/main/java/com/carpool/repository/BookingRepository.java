package com.carpool.repository;

import com.carpool.domain.entity.Booking;
import com.carpool.domain.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByRideIdAndPassengerId(Long rideId, Long passengerId);

    boolean existsByRideIdAndPassengerId(Long rideId, Long passengerId);

    /**
     * Passenger's booking history.
     */
    List<Booking> findByPassengerIdAndStatusInOrderByCreatedAtDesc(
            Long passengerId, List<BookingStatus> statuses);

    /**
     * All active bookings on a ride — used when driver cancels ride
     * to notify all affected passengers.
     */
    @Query("""
        SELECT b FROM Booking b
        JOIN FETCH b.passenger
        WHERE b.ride.id = :rideId
          AND b.status IN ('PENDING', 'CONFIRMED')
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
     */
    @Query("""
    SELECT b FROM Booking b
    WHERE b.ride.driver.id = :driverId
      AND b.status IN :statuses
    ORDER BY b.ride.departureTime ASC, b.createdAt DESC
    """)
    List<Booking> findByDriverIdAndStatusIn(
            @Param("driverId") Long driverId,
            @Param("statuses") List<BookingStatus> statuses);
}
