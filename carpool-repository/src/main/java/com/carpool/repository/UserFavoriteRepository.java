package com.carpool.repository;

import com.carpool.domain.entity.UserFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserFavoriteRepository extends JpaRepository<UserFavorite, Long> {

    /**
     * Check if a user has already saved another user as favorite.
     * Used to prevent duplicates.
     */
    boolean existsByFollowerIdAndFavoriteId(Long followerId, Long favoriteId);

    /**
     * Get all favorites saved by a user.
     * Used to display the user's favorite list.
     */
    List<UserFavorite> findByFollowerIdOrderByCreatedAtDesc(Long followerId);

    /**
     * Get all followers of a specific user.
     * Used to alert followers when a favorite driver posts a ride.
     */
    List<UserFavorite> findByFavoriteId(Long favoriteId);

    /**
     * Get all followers of a specific user, newest first, with follower User loaded eagerly.
     * JOIN FETCH avoids N+1 queries when accessing follower fields.
     * Used for the "My Followers" screen in the driver profile.
     */
    @Query("SELECT uf FROM UserFavorite uf JOIN FETCH uf.follower " +
           "WHERE uf.favorite.id = :favoriteId ORDER BY uf.createdAt DESC")
    List<UserFavorite> findByFavoriteIdWithFollowerOrderByCreatedAtDesc(
            @Param("favoriteId") Long favoriteId);

    /**
     * Get follower IDs of a specific user — lightweight version.
     * Avoids loading full User entities when only IDs are needed.
     */
    @Query("SELECT uf.follower.id FROM UserFavorite uf " +
            "WHERE uf.favorite.id = :favoriteId")
    List<Long> findFollowerIdsByFavoriteId(@Param("favoriteId") Long favoriteId);

    /**
     * Remove a specific favorite.
     */
    void deleteByFollowerIdAndFavoriteId(Long followerId, Long favoriteId);

    /**
     * Count how many users have saved a specific user as favorite.
     */
    long countByFavoriteId(Long favoriteId);

    /**
     * Get follower Telegram IDs of a specific user — eliminates N+1 in notification loop.
     * Returns telegramId directly, no secondary userRepository lookup needed.
     */
    @Query("SELECT uf.follower.telegramId FROM UserFavorite uf " +
            "WHERE uf.favorite.id = :favoriteId")
    List<Long> findFollowerTelegramIdsByFavoriteId(@Param("favoriteId") Long favoriteId);
}