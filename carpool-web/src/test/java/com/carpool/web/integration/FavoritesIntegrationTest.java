package com.carpool.web.integration;

import com.carpool.common.exception.InvalidOperationException;
import com.carpool.domain.entity.User;
import com.carpool.domain.enums.UserRole;
import com.carpool.domain.enums.UserStatus;
import com.carpool.repository.UserRepository;
import com.carpool.service.dto.response.FollowerResponse;
import com.carpool.service.favorite.FavoriteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Favorites Integration")
class FavoritesIntegrationTest extends BaseIntegrationTest {

    @Autowired private FavoriteService favoriteService;
    @Autowired private UserRepository  userRepository;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        long seed = System.currentTimeMillis();

        userA = userRepository.save(User.builder()
                .telegramId(seed + 30).fullName("User A")
                .role(UserRole.PASSENGER).status(UserStatus.ACTIVE).build());

        userB = userRepository.save(User.builder()
                .telegramId(seed + 31).fullName("User B")
                .role(UserRole.DRIVER).status(UserStatus.ACTIVE).build());
    }

    @Test
    @DisplayName("saveFavorite — happy path")
    void saveFavorite_happyPath() {
        assertThatCode(() -> favoriteService.saveFavorite(userA.getId(), userB.getId()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("saveFavorite — idempotent (second call does not throw)")
    void saveFavorite_idempotent() {
        favoriteService.saveFavorite(userA.getId(), userB.getId());

        assertThatCode(() -> favoriteService.saveFavorite(userA.getId(), userB.getId()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("saveFavorite — self-favorite throws 400 InvalidOperationException")
    void saveFavorite_selfFavorite_throws400() {
        assertThatThrownBy(() -> favoriteService.saveFavorite(userA.getId(), userA.getId()))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    @DisplayName("removeFavorite — idempotent (works even when not saved)")
    void removeFavorite_idempotent() {
        assertThatCode(() -> favoriteService.removeFavorite(userA.getId(), userB.getId()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("getMyFavoritesAsDtos — returns saved favorites as FollowerResponse")
    void getMyFavoritesAsDtos_returnsList() {
        favoriteService.saveFavorite(userA.getId(), userB.getId());

        List<FollowerResponse> favorites = favoriteService.getMyFavoritesAsDtos(userA.getId());

        assertThat(favorites).hasSize(1);
        assertThat(favorites.get(0).userId()).isEqualTo(userB.getId());
        assertThat(favorites.get(0).fullName()).isEqualTo("User B");
    }

    @Test
    @DisplayName("getFollowers — returns followers of userB as FollowerResponse")
    void getFollowers_returnsList() {
        favoriteService.saveFavorite(userA.getId(), userB.getId());

        List<FollowerResponse> followers = favoriteService.getFollowers(userB.getId());

        assertThat(followers).hasSize(1);
        assertThat(followers.get(0).userId()).isEqualTo(userA.getId());
    }
}
