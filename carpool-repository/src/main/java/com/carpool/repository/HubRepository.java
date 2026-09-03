package com.carpool.repository;

import com.carpool.domain.entity.Hub;
import com.carpool.domain.enums.HubStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HubRepository extends JpaRepository<Hub, Long> {

    List<Hub> findByStatusOrderByAreaAscNameAsc(HubStatus status);

    /**
     * Count hubs by status — used for admin stats (pending suggestions count).
     */
    long countByStatus(HubStatus status);

    Optional<Hub> findByCode(String code);

    Optional<Hub> findFirstByNameIgnoreCaseAndArea(String name, String area);

    /**
     * Find all hubs suggested by a specific user that are still pending review.
     */
    List<Hub> findBySuggestedByIdAndStatus(Long userId, HubStatus status);

    /**
     * Admin query: all pending hubs ordered by submission time.
     */
    @Query("SELECT h FROM Hub h WHERE h.status = 'PENDING' ORDER BY h.createdAt ASC")
    List<Hub> findAllPending();

    /**
     * PESSIMISTIC WRITE lock — used exclusively by HubService.bulkApprovePending()
     * so concurrent bulk-approval attempts (e.g. two suggestHub() calls both
     * observing the pending queue at threshold) serialize instead of racing on
     * generateUniqueCode()/the unique hubs.code constraint.
     * Maps to: SELECT * FROM hubs WHERE status = 'PENDING' ORDER BY created_at ASC FOR UPDATE
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM Hub h WHERE h.status = 'PENDING' ORDER BY h.createdAt ASC")
    List<Hub> findAllPendingForUpdate();

    /**
     * Search active hubs by name or area — used for autocomplete in clients.
     */
    @Query("""
        SELECT h FROM Hub h
        WHERE h.status = 'ACTIVE'
          AND (LOWER(h.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(h.area) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY h.area ASC, h.name ASC
        """)
    List<Hub> searchActive(@Param("keyword") String keyword);

    /**
     * Get recently used hubs by user as driver — top 5 distinct hubs.
     */
    @Query(value = """
    SELECT DISTINCT h.* FROM hubs h
    WHERE h.id IN (
        SELECT origin_hub_id FROM rides
        WHERE driver_id = :userId
        AND status NOT IN ('DRAFT', 'CANCELLED')
        UNION
        SELECT destination_hub_id FROM rides
        WHERE driver_id = :userId
        AND status NOT IN ('DRAFT', 'CANCELLED')
    )
    ORDER BY h.name ASC
    LIMIT 5
    """, nativeQuery = true)
    List<Hub> findRecentHubsByDriverId(@Param("userId") Long userId);

    /**
     * Get recently used hubs by user as passenger — top 5 distinct hubs.
     */
    @Query(value = """
    SELECT DISTINCT h.* FROM hubs h
    WHERE h.id IN (
        SELECT r.origin_hub_id FROM bookings b
        JOIN rides r ON b.ride_id = r.id
        WHERE b.passenger_id = :userId
        AND b.status NOT IN ('CANCELLED_BY_PASSENGER', 'CANCELLED_BY_DRIVER')
        UNION
        SELECT r.destination_hub_id FROM bookings b
        JOIN rides r ON b.ride_id = r.id
        WHERE b.passenger_id = :userId
        AND b.status NOT IN ('CANCELLED_BY_PASSENGER', 'CANCELLED_BY_DRIVER')
    )
    ORDER BY h.name ASC
    LIMIT 5
    """, nativeQuery = true)
    List<Hub> findRecentHubsByPassengerId(@Param("userId") Long userId);
}
