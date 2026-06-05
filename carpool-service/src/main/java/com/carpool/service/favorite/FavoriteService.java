package com.carpool.service.favorite;

import com.carpool.common.exception.InvalidOperationException;
import com.carpool.domain.entity.User;
import com.carpool.domain.entity.UserFavorite;
import com.carpool.repository.UserFavoriteRepository;
import com.carpool.repository.UserRepository;
import com.carpool.service.dto.response.FollowerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Handles all favorite operations.
 * Users can save other users as favorites after rating them.
 * Followers are alerted when a favorite driver posts a new ride.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final UserFavoriteRepository favoriteRepository;
    private final UserRepository         userRepository;

    // ── Save favorite ─────────────────────────────────────────────────────

    /**
     * Saves a user as a favorite.
     * Silently ignored if already saved — no error thrown.
     */
    @Transactional
    public void saveFavorite(Long followerId, Long favoriteId) {
        if (followerId.equals(favoriteId)) {
            throw new InvalidOperationException(
                    "You cannot save yourself as a favorite.");
        }

        if (favoriteRepository.existsByFollowerIdAndFavoriteId(
                followerId, favoriteId)) {
            log.info("Favorite already exists: followerId={} favoriteId={}",
                    followerId, favoriteId);
            return;
        }

        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new InvalidOperationException(
                        "Follower not found: " + followerId));

        User favorite = userRepository.findById(favoriteId)
                .orElseThrow(() -> new InvalidOperationException(
                        "Favorite user not found: " + favoriteId));

        favoriteRepository.save(UserFavorite.builder()
                .follower(follower)
                .favorite(favorite)
                .build());

        log.info("Favorite saved: followerId={} favoriteId={}",
                followerId, favoriteId);
    }

    // ── Remove favorite ───────────────────────────────────────────────────

    /**
     * Removes a saved favorite.
     * Idempotent — silently ignored if not found or already removed (e.g. duplicate
     * Telegram callback re-delivery after a restart).
     */
    @Transactional
    public void removeFavorite(Long followerId, Long favoriteId) {
        int deleted = favoriteRepository.deleteByFollowerIdAndFavoriteId(followerId, favoriteId);
        if (deleted > 0) {
            log.info("Favorite removed: followerId={} favoriteId={}", followerId, favoriteId);
        } else {
            log.info("Favorite already removed (idempotent): followerId={} favoriteId={}", followerId, favoriteId);
        }
    }

    // ── Check favorite ────────────────────────────────────────────────────

    /**
     * Checks if a user has saved another user as a favorite.
     */
    public boolean isFavorite(Long followerId, Long favoriteId) {
        return favoriteRepository.existsByFollowerIdAndFavoriteId(
                followerId, favoriteId);
    }

    // ── Get favorites ─────────────────────────────────────────────────────

    /**
     * Returns all users saved as favorites by a follower.
     */
    public List<UserFavorite> getMyFavorites(Long followerId) {
        return favoriteRepository.findByFollowerIdOrderByCreatedAtDesc(followerId);
    }

    @Transactional(readOnly = true)
    public List<FollowerResponse> getMyFavoritesAsDtos(Long followerId) {
        return favoriteRepository.findByFollowerIdOrderByCreatedAtDesc(followerId)
                .stream()
                .map(uf -> new FollowerResponse(
                        uf.getFavorite().getId(),
                        uf.getFavorite().getFullName(),
                        uf.getFavorite().getTelegramHandle(),
                        uf.getCreatedAt()))
                .toList();
    }

    // ── Get followers ─────────────────────────────────────────────────────

    /**
     * Returns all follower IDs for a given user.
     * Used by NotificationService to alert followers when a ride is posted.
     * Returns IDs only — avoids loading full User entities.
     */
    public List<Long> getFollowerIds(Long favoriteId) {
        return favoriteRepository.findFollowerIdsByFavoriteId(favoriteId);
    }

    /**
     * Returns follower count for a given user.
     * Used for profile display.
     */
    public long getFollowerCount(Long favoriteId) {
        return favoriteRepository.countByFavoriteId(favoriteId);
    }

    /**
     * Returns all followers of a driver as DTOs, newest first.
     * Used for the "My Followers" bot screen.
     */
    @Transactional(readOnly = true)
    public List<FollowerResponse> getFollowers(Long driverId) {
        return favoriteRepository.findByFavoriteIdWithFollowerOrderByCreatedAtDesc(driverId)
                .stream()
                .map(uf -> new FollowerResponse(
                        uf.getFollower().getId(),
                        uf.getFollower().getFullName(),
                        uf.getFollower().getTelegramHandle(),
                        uf.getCreatedAt()))
                .toList();
    }
}