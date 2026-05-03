package com.carpool.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Represents a rating given by one user to another after a completed ride.
 * One rating per person per ride — enforced at DB and service level.
 * raterRole distinguishes whether the rater was acting as DRIVER or PASSENGER.
 */
@Entity
@Table(
        name = "ride_ratings",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_rating_ride_rater",
                columnNames = {"ride_id", "rater_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ride_id", nullable = false)
    private Ride ride;

    /**
     * The user who gave this rating.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rater_id", nullable = false)
    private User rater;

    /**
     * The user who received this rating.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ratee_id", nullable = false)
    private User ratee;

    /**
     * Star rating — 1 to 5.
     */
    @Column(nullable = false, columnDefinition = "TINYINT")
    private Integer stars;

    /**
     * Optional comment from the rater.
     */
    @Column(length = 1000)
    private String comment;

    /**
     * Role of the rater at the time of the ride.
     * DRIVER = driver rated the passenger.
     * PASSENGER = passenger rated the driver.
     */
    @Column(name = "rater_role", nullable = false, length = 20)
    private String raterRole;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}