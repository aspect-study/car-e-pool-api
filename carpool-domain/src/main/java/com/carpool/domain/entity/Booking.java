package com.carpool.domain.entity;

import com.carpool.domain.enums.BookingStatus;
import com.carpool.domain.enums.PaymentMethod;
import com.carpool.domain.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * A passenger's reservation on a ride.
 *
 * pickup/dropoff waypoints are optional:
 *   - NULL pickupWaypoint  = passenger boards at ride's origin hub
 *   - NULL dropoffWaypoint = passenger alights at ride's destination hub
 *
 * Seat reservation and availableSeats decrement happen atomically
 * in BookingService under pessimistic lock (SELECT FOR UPDATE on Ride).
 */
@Entity
@Table(
    name = "bookings",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_booking",
        columnNames = {"ride_id", "passenger_id"}
    ),
    indexes = @Index(name = "idx_booking_passenger",
                     columnList = "passenger_id, status")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ride_id", nullable = false)
    private Ride ride;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_id", nullable = false)
    private User passenger;

    @Column(name = "seats_reserved", nullable = false)
    @Builder.Default
    private Integer seatsReserved = 1;

    /**
     * NULL = board at ride's origin hub.
     * Set when passenger wants to be picked up at a waypoint instead.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pickup_waypoint_id")
    private RideWaypoint pickupWaypoint;

    /**
     * NULL = alight at ride's destination hub.
     * Set when passenger wants to be dropped off at a waypoint instead.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dropoff_waypoint_id")
    private RideWaypoint dropoffWaypoint;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private BookingStatus status = BookingStatus.PENDING;

    // ── Payment tracking ─────────────────────────────────────────────────────

    @Column(name = "contribution_due", nullable = false, precision = 8, scale = 2)
    @Builder.Default
    private BigDecimal contributionDue = BigDecimal.ZERO;

    @Column(name = "contribution_paid", nullable = false, precision = 8, scale = 2)
    @Builder.Default
    private BigDecimal contributionPaid = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    @Builder.Default
    private PaymentMethod paymentMethod = PaymentMethod.CASH;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    /**
     * Reserved for future PayMongo transaction reference.
     */
    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    // ── Pending approval ─────────────────────────────────────────────────────

    /**
     * Optional message from passenger to driver — shown during approval.
     * NULL = no message.
     */
    @Column(name = "passenger_message", length = 800)
    private String passengerMessage;

    /**
     * Optional reason from driver when declining.
     * NULL = no reason given (MVP: button-only decline).
     */
    @Column(name = "decline_reason", length = 255)
    private String declineReason;

    /**
     * Number of reminders sent to driver (0-3).
     * When reaches 3 and expires_at has passed → auto TIMED_OUT.
     */
    @Column(name = "reminder_count", nullable = false)
    @Builder.Default
    private int reminderCount = 0;

    /**
     * Auto-decline deadline — set at booking creation time.
     * Scheduler checks this to auto-decline unresponded bookings.
     */
    @Column(name = "expires_at")
    private java.time.Instant expiresAt;
}
