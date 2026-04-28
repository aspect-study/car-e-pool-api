package com.carpool.repository;

import com.carpool.domain.entity.Ride;
import com.carpool.domain.enums.RideDirection;
import com.carpool.domain.enums.RideStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RideRepository extends JpaRepository<Ride, Long> {

    /**
     * PESSIMISTIC WRITE lock — used exclusively in BookingService
     * during seat reservation to prevent race conditions.
     * Maps to: SELECT * FROM rides WHERE id = ? FOR UPDATE
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Ride r WHERE r.id = :id")
    Optional<Ride> findByIdWithLock(@Param("id") Long id);

    /**
     * Core ride search query.
     *
     * Matches rides where the requested hub appears as:
     *   - the ride's origin hub, OR
     *   - a waypoint hub (allows "passing through" search)
     * ...AND destination matches similarly.
     *
     * Only returns ACTIVE rides with future departure times.
     */
    @Query("""
        SELECT DISTINCT r FROM Ride r
        LEFT JOIN r.waypoints wp
        WHERE r.status = 'ACTIVE'
          AND r.departureTime > :now
          AND (r.originHub.id = :fromHubId OR wp.hub.id = :fromHubId)
          AND (r.destinationHub.id = :toHubId
            OR EXISTS (
                SELECT w FROM RideWaypoint w
                WHERE w.ride = r AND w.hub.id = :toHubId
            ))
        ORDER BY r.departureTime ASC
        """)
    List<Ride> searchAvailable(
            @Param("fromHubId") Long fromHubId,
            @Param("toHubId")   Long toHubId,
            @Param("now")       LocalDateTime now
    );

    /**
     * Directional search — HOME_TO_WORK or WORK_TO_HOME filter.
     * Used when client wants to filter by commute direction.
     */
    @Query("""
        SELECT DISTINCT r FROM Ride r
        LEFT JOIN r.waypoints wp
        WHERE r.status = 'ACTIVE'
          AND r.direction = :direction
          AND r.departureTime > :now
          AND (r.originHub.id = :fromHubId OR wp.hub.id = :fromHubId)
          AND (r.destinationHub.id = :toHubId
            OR EXISTS (
                SELECT w FROM RideWaypoint w
                WHERE w.ride = r AND w.hub.id = :toHubId
            ))
        ORDER BY r.departureTime ASC
        """)
    List<Ride> searchAvailableByDirection(
            @Param("fromHubId")  Long fromHubId,
            @Param("toHubId")    Long toHubId,
            @Param("direction")  RideDirection direction,
            @Param("now")        LocalDateTime now
    );

    /**
     * Driver's own rides — for "My Offered Rides" view.
     */
    List<Ride> findByDriverIdAndStatusInOrderByDepartureTimeDesc(
            Long driverId, List<RideStatus> statuses);

    /**
     * Fetch ride with waypoints eagerly — avoids N+1 in ride detail endpoint.
     */
    @Query("""
        SELECT r FROM Ride r
        LEFT JOIN FETCH r.waypoints wp
        LEFT JOIN FETCH wp.hub
        WHERE r.id = :id
        """)
    Optional<Ride> findByIdWithWaypoints(@Param("id") Long id);

    @Query("""
    SELECT r FROM Ride r
    WHERE r.direction = :direction
      AND r.status IN :statuses
      AND r.departureTime > :now
    ORDER BY r.departureTime ASC
    """)
    List<Ride> findActiveByDirection(
            @Param("direction") RideDirection direction,
            @Param("statuses")  List<RideStatus> statuses,
            @Param("now")       LocalDateTime now);

    @Query("""
    SELECT r FROM Ride r
    WHERE r.direction = :direction
      AND r.status IN :statuses
      AND r.departureTime >= :from
      AND r.departureTime <= :to
    ORDER BY r.departureTime ASC
    """)
    List<Ride> findActiveByDirectionAndTimeRange(
            @Param("direction") RideDirection direction,
            @Param("statuses")  List<RideStatus> statuses,
            @Param("from")      LocalDateTime from,
            @Param("to")        LocalDateTime to);

    /**
     * Finds rides that are still ACTIVE or FULL but departure time has already passed.
     * Used by scheduler to auto-expire stale rides.
     */
    @Query("""
    SELECT r FROM Ride r
    WHERE r.status IN ('ACTIVE', 'FULL')
      AND r.departureTime < :cutoff
    """)
    List<Ride> findStaleActiveRides(@Param("cutoff") LocalDateTime cutoff);

    List<Ride> findByStatusAndDepartureTimeBefore(RideStatus status, LocalDateTime cutoff);

    /**
     * Count rides by driver and status — used for profile stats.
     */
    @Query("SELECT COUNT(r) FROM Ride r WHERE r.driver.id = :driverId AND r.status = :status")
    int countByDriverIdAndStatus(@Param("driverId") Long driverId,
                                 @Param("status") RideStatus status);

    /**
     * Count total rides posted by driver — all statuses.
     */
    @Query("SELECT COUNT(r) FROM Ride r WHERE r.driver.id = :driverId")
    int countByDriverId(@Param("driverId") Long driverId);

    /**
     * Sum of seats reserved on completed bookings for driver's rides.
     * Represents total passengers served.
     */
    @Query("""
    SELECT COALESCE(SUM(b.seatsReserved), 0)
    FROM Booking b
    JOIN b.ride r
    WHERE r.driver.id = :driverId
      AND b.status = 'COMPLETED'
    """)
    int sumPassengersServedByDriverId(@Param("driverId") Long driverId);

    /**
     * Paginated version of searchAvailable — hub-to-hub search.
     */
    @Query("""
    SELECT DISTINCT r FROM Ride r
    LEFT JOIN r.waypoints wp
    WHERE r.status = 'ACTIVE'
      AND r.departureTime > :now
      AND (r.originHub.id = :fromHubId OR wp.hub.id = :fromHubId)
      AND (r.destinationHub.id = :toHubId
        OR EXISTS (
            SELECT w FROM RideWaypoint w
            WHERE w.ride = r AND w.hub.id = :toHubId
        ))
    ORDER BY r.departureTime ASC
    """)
    Page<Ride> searchAvailablePaged(
            @Param("fromHubId") Long fromHubId,
            @Param("toHubId")   Long toHubId,
            @Param("now")       LocalDateTime now,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Paginated version of driver's own rides.
     */
    Page<Ride> findByDriverIdAndStatusInOrderByDepartureTimeDesc(
            Long driverId,
            List<RideStatus> statuses,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Last 3 completed or cancelled rides for repost feature.
     * DB-level LIMIT — no in-memory filtering needed.
     */
    @Query("""
    SELECT r FROM Ride r
    WHERE r.driver.id = :driverId
      AND r.status IN ('COMPLETED', 'CANCELLED')
    ORDER BY r.departureTime DESC
    LIMIT 3
    """)
    List<Ride> findTop3CompletedOrCancelledByDriverId(@Param("driverId") Long driverId);

    /**
     * Count rides by status — used for admin stats.
     */
    @Query("SELECT COUNT(r) FROM Ride r WHERE r.status = :status")
    long countByRideStatus(@Param("status") RideStatus status);

    /**
     * Count rides with status in list — used for active rides count.
     */
    @Query("SELECT COUNT(r) FROM Ride r WHERE r.status IN :statuses")
    long countByStatusIn(@Param("statuses") List<RideStatus> statuses);

    /**
     * Count rides created after a given datetime — used for today's activity.
     */
    @Query("SELECT COUNT(r) FROM Ride r WHERE r.createdAt > :since")
    long countRidesCreatedAfter(@Param("since") Instant since);

    /**
     * Find ACTIVE or FULL rides departing within a time window.
     * Used by departure reminder scheduler.
     */
    @Query("""
    SELECT r FROM Ride r
    WHERE r.status IN :statuses
      AND r.departureTime >= :from
      AND r.departureTime <= :to
    """)
    List<Ride> findRidesDepartingBetween(
            @Param("from")     LocalDateTime from,
            @Param("to")       LocalDateTime to,
            @Param("statuses") List<RideStatus> statuses);
}
