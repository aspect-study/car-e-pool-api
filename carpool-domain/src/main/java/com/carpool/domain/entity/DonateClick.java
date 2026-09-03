package com.carpool.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Records a tap of a donate button (e.g. "GCash") — curiosity/intent signal only.
 * The actual transfer happens manually outside the app and is never tracked here.
 */
@Entity
@Table(name = "donate_clicks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DonateClick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
