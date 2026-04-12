package com.carpool.repository;

import com.carpool.domain.entity.Notification;
import com.carpool.domain.enums.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Fetch failed notifications for retry — can be called by a scheduled job.
     */
    List<Notification> findByStatusOrderByCreatedAtAsc(NotificationStatus status);

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);
}
