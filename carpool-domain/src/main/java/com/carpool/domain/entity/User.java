package com.carpool.domain.entity;

import com.carpool.domain.enums.UserRole;
import com.carpool.domain.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;

/**
 * Platform user. Identity is anchored to Telegram — no passwords stored.
 * telegramId is the immutable unique identifier from Telegram OAuth.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_id", nullable = false, unique = true)
    private Long telegramId;

    @Column(name = "telegram_handle", length = 100)
    private String telegramHandle;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @Builder.Default
    private UserRole role = UserRole.PASSENGER;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "car_model", length = 100)
    private String carModel;

    @Column(name = "car_color", length = 50)
    private String carColor;

    @Column(name = "plate_number", length = 20, unique = true)
    private String plateNumber;

    // Convenience: can this user offer rides?
    public boolean canDrive() {
        return role == UserRole.DRIVER || role == UserRole.BOTH;
    }

    // Convenience: does this driver have vehicle info saved?
    public boolean hasVehicleInfo() {
        return carModel != null && !carModel.isBlank()
                && plateNumber != null && !plateNumber.isBlank();
    }
}
