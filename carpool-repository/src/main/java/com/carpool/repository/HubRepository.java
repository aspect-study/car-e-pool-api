package com.carpool.repository;

import com.carpool.domain.entity.Hub;
import com.carpool.domain.enums.HubStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HubRepository extends JpaRepository<Hub, Long> {

    List<Hub> findByStatusOrderByAreaAscNameAsc(HubStatus status);

    Optional<Hub> findByCode(String code);

    boolean existsByNameIgnoreCaseAndArea(String name, String area);

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
}
