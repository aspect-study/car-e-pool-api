package com.carpool.repository;

import com.carpool.domain.entity.User;
import com.carpool.domain.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByTelegramId(Long telegramId);

    boolean existsByTelegramId(Long telegramId);

    /**
     * Used during auth flow — fetch active user only.
     * Suspended/banned users cannot log in.
     */
    Optional<User> findByTelegramIdAndStatus(Long telegramId, UserStatus status);
}
