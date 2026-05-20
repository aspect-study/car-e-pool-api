package com.carpool.domain.entity;

import com.carpool.domain.enums.RideDirection;
import com.carpool.domain.enums.RideStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A carpool ride offered by a driver.
 *
 * Seat management rules:
 *  - availableSeats is always decremented/incremented within a transaction
 *    using pessimistic locking (SELECT FOR UPDATE) in BookingService.
 *  - availableSeats == 0 triggers status transition to FULL automatically.
 */
@Entity
@Table(
    name = "rides",
    indexes = {
        @Index(name = "idx_ride_search",
               columnList = "origin_hub_id, destination_hub_id, departure_time, status"),
        @Index(name = "idx_ride_driver",
               columnList = "driver_id, status"),
        @Index(name = "idx_ride_direction",
               columnList = "direction, departure_time, status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ride extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private User driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_hub_id", nullable = false)
    private Hub originHub;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_hub_id", nullable = false)
    private Hub destinationHub;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 20)
    @Builder.Default
    private RideDirection direction = RideDirection.OTHER;

    @Column(name = "departure_time", nullable = false)
    private LocalDateTime departureTime;

    @Column(name = "total_seats", nullable = false)
    private Integer totalSeats;

    @Column(name = "available_seats", nullable = false)
    private Integer availableSeats;

    @Column(name = "contribution_amount", nullable = false, precision = 8, scale = 2)
    @Builder.Default
    private BigDecimal contributionAmount = BigDecimal.ZERO;

    @Column(name = "notes", length = 300)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private RideStatus status = RideStatus.DRAFT;

    @Column(name = "announce_count", nullable = false)
    @Builder.Default
    private Integer announceCount = 1;

    @Column(name = "group_message_id")
    private Integer groupMessageId;

    @Column(name = "group_message_posted_at")
    private Instant groupMessagePostedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    /**
     * Ordered waypoints between origin and destination.
     * CascadeType.ALL: waypoints are lifecycle-managed with the ride.
     * orphanRemoval: if a waypoint is removed from the list, it's deleted from DB.
     */
    @OneToMany(mappedBy = "ride", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sequenceOrder ASC")
    @Builder.Default
    private List<RideWaypoint> waypoints = new ArrayList<>();
}
