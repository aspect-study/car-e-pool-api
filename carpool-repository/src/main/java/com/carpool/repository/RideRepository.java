package com.carpool.repository;

import com.carpool.domain.entity.Ride;
import com.carpool.domain.enums.RideDirection;
import com.carpool.domain.enums.RideStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
