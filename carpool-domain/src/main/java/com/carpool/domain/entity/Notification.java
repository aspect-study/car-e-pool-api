package com.carpool.domain.entity;

import com.carpool.domain.enums.NotificationChannel;
import com.carpool.domain.enums.NotificationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Audit log of every outbound notification sent by the system.
 *
 * Records are written BEFORE the send attempt (status=PENDING),
 * then updated to SENT or FAILED after the Telegram API call completes.
 * This gives us a retry-able queue and a full audit trail.
 *
 * 'payload' stores a JSON snapshot of what was sent — useful for
 * debugging and replaying failed notifications without re-querying.
 */
@Entity
@Table(
    name = "notifications",
    indexes = @Index(name = "idx_notif_user", columnList = "user_id, status")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Notification type identifier, e.g. "BOOKING_CONFIRMED", "RIDE_CANCELLED".
     * Use constants from NotificationTypes to avoid magic strings.
     */
    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    @Builder.Default
    private NotificationChannel channel = NotificationChannel.TELEGRAM;

    /**
     * JSON snapshot of the notification content at time of send.
     * Stored as JSON column — no need to re-query ride/booking data on retry.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "json")
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Optional ride reference — set for ride-level notifications
     * (e.g. RIDE_DEPARTURE_REMINDER) to allow efficient duplicate checks.
     * Null for booking-level notifications.
     */
    @Column(name = "ride_id")
    private Long rideId;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
