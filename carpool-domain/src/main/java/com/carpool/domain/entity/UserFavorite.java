package com.carpool.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Represents a user saving another user as a favorite.
 * Used to alert followers when a favorite driver posts a new ride.
 * No duplicate favorites — enforced at DB and service level.
 */
@Entity
@Table(
        name = "user_favorites",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_favorite_follower_favorite",
                columnNames = {"follower_id", "favorite_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The user who saved the favorite.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_id", nullable = false)
    private User follower;

    /**
     * The user who was saved as a favorite.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "favorite_id", nullable = false)
    private User favorite;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}