package com.carpool.domain.entity;

import com.carpool.domain.enums.HubStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a pickup/dropoff landmark.
 * ACTIVE hubs are admin-approved and visible to all users.
 * PENDING hubs are user-suggested, usable on the suggesting ride,
 * but not visible in the general hub list until approved.
 */
@Entity
@Table(name = "hubs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Hub extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Short machine-readable code, e.g. "BGC_HIGH_STREET".
     * NULL for PENDING hubs that haven't been approved yet.
     */
    @Column(name = "code", unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "area", nullable = false, length = 100)
    private String area;

    /**
     * The user who suggested this hub. NULL for admin-seeded hubs.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suggested_by")
    private User suggestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private HubStatus status = HubStatus.ACTIVE;

    @OneToMany(mappedBy = "hub", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HubAlias> aliases = new ArrayList<>();
}
