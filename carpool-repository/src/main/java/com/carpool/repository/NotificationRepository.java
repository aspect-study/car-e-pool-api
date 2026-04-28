package com.carpool.repository;

import com.carpool.domain.entity.Notification;
import com.carpool.domain.enums.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Fetch failed notifications for retry — can be called by a scheduled job.
     */
    List<Notification> findByStatusOrderByCreatedAtAsc(NotificationStatus status);

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Check if a ride-level notification of a given type already exists.
     * Used by departure reminder scheduler to prevent duplicate sends.
     */
    @Query("SELECT COUNT(n) > 0 FROM Notification n " +
            "WHERE n.rideId = :rideId AND n.type = :type")
    boolean existsByRideIdAndType(
            @Param("rideId") Long rideId,
            @Param("type")   String type);
}
