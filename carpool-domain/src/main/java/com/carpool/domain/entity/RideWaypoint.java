package com.carpool.domain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * An intermediate stop on a ride route.
 * sequenceOrder defines the order of stops between origin and destination.
 * A hub can be pickup-only, dropoff-only, or both.
 */
@Entity
@Table(
    name = "ride_waypoints",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_waypoint",
        columnNames = {"ride_id", "hub_id"}
    ),
    indexes = @Index(name = "idx_wp_hub", columnList = "hub_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideWaypoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ride_id", nullable = false)
    private Ride ride;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hub_id", nullable = false)
    private Hub hub;

    @Column(name = "sequence_order", nullable = false)
    private Integer sequenceOrder;

    @Column(name = "is_pickup", nullable = false)
    @Builder.Default
    private Boolean isPickup = true;

    @Column(name = "is_dropoff", nullable = false)
    @Builder.Default
    private Boolean isDropoff = true;
}
