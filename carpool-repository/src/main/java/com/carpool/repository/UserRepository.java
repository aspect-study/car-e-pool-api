package com.carpool.repository;

import com.carpool.domain.entity.User;
import com.carpool.domain.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByTelegramId(Long telegramId);

    boolean existsByTelegramId(Long telegramId);

    /**
     * Used during auth flow — fetch active user only.
     * Suspended/banned users cannot log in.
     */
    Optional<User> findByTelegramIdAndStatus(Long telegramId, UserStatus status);

    Optional<User> findByPlateNumber(String plateNumber);

    /**
     * Count users registered after a given datetime.
     * Used for admin stats — new users today.
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt > :since")
    long countByCreatedAtAfter(@Param("since") Instant since);

    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt > :since")
    long countUsersCreatedAfter(@Param("since") Instant since);
}
