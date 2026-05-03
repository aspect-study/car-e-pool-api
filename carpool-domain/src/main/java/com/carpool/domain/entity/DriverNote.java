package com.carpool.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "driver_notes",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_note",
                columnNames = {"user_id", "content_hash"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Owner of this note — always a driver or BOTH role user.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Original note content — displayed to user as-is.
     */
    @Column(nullable = false, length = 1000)
    private String content;

    /**
     * SHA-256 of normalized content (trimmed + lowercased + collapsed whitespace).
     * Used for deduplication — faster than string comparison.
     */
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    /**
     * How many times this note has been reused.
     * Higher = more preferred by driver.
     */
    @Column(name = "used_count", nullable = false)
    private int usedCount;

    /**
     * Last time this note was selected — used for LRU replacement.
     */
    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (lastUsedAt == null) {
            lastUsedAt = createdAt;
        }
    }
}