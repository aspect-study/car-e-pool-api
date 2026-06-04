package com.carpool.service.rating;

import com.carpool.domain.entity.RideRating;
import com.carpool.domain.entity.User;
import com.carpool.repository.BookingRepository;
import com.carpool.repository.RideRatingRepository;
import com.carpool.repository.RideRepository;
import com.carpool.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("RatingService")
class RatingServiceTest {

    @Mock private RideRatingRepository ratingRepository;
    @Mock private RideRepository       rideRepository;
    @Mock private UserRepository       userRepository;
    @Mock private BookingRepository    bookingRepository;

    @InjectMocks private RatingService ratingService;

    @Nested
    @DisplayName("getRatingsReceivedPaged")
    class GetRatingsReceivedPaged {

        @Test
        @DisplayName("returns page 0 results from repository")
        void returnsPageZero() {
            User mockUser = mock(User.class);
            when(mockUser.getFullName()).thenReturn("Test User");
            RideRating rating = mock(RideRating.class);
            when(rating.getRatee()).thenReturn(mockUser);
            Page<RideRating> expected = new PageImpl<>(List.of(rating),
                    PageRequest.of(0, 5), 1);
            when(ratingRepository.findByRateeIdOrderByCreatedAtDesc(
                    eq(42L), eq(PageRequest.of(0, 5))))
                    .thenReturn(expected);

            Page<RideRating> result = ratingService.getRatingsReceivedPaged(42L, 0, 5);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("returns empty page when user has no ratings")
        void returnsEmptyPage() {
            Page<RideRating> empty = new PageImpl<>(
                    Collections.emptyList(), PageRequest.of(0, 5), 0);
            when(ratingRepository.findByRateeIdOrderByCreatedAtDesc(
                    eq(99L), eq(PageRequest.of(0, 5))))
                    .thenReturn(empty);

            Page<RideRating> result = ratingService.getRatingsReceivedPaged(99L, 0, 5);

            assertThat(result.isEmpty()).isTrue();
            assertThat(result.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("delegates correct page number and size to repository")
        void delegatesPageAndSize() {
            Page<RideRating> page = new PageImpl<>(Collections.emptyList(),
                    PageRequest.of(2, 5), 0);
            when(ratingRepository.findByRateeIdOrderByCreatedAtDesc(
                    eq(7L), eq(PageRequest.of(2, 5))))
                    .thenReturn(page);

            ratingService.getRatingsReceivedPaged(7L, 2, 5);

            verify(ratingRepository).findByRateeIdOrderByCreatedAtDesc(
                    eq(7L), eq(PageRequest.of(2, 5)));
        }

        @Test
        @DisplayName("last page has correct hasNext false")
        void lastPageHasNoNext() {
            User mockUser = mock(User.class);
            when(mockUser.getFullName()).thenReturn("Test User");
            RideRating r1 = mock(RideRating.class);
            RideRating r2 = mock(RideRating.class);
            when(r1.getRatee()).thenReturn(mockUser);
            when(r2.getRatee()).thenReturn(mockUser);
            // 2 total items, page size 5 → single page, no next
            Page<RideRating> lastPage = new PageImpl<>(List.of(r1, r2),
                    PageRequest.of(0, 5), 2);
            when(ratingRepository.findByRateeIdOrderByCreatedAtDesc(
                    eq(1L), eq(PageRequest.of(0, 5))))
                    .thenReturn(lastPage);

            Page<RideRating> result = ratingService.getRatingsReceivedPaged(1L, 0, 5);

            assertThat(result.hasNext()).isFalse();
            assertThat(result.getTotalPages()).isEqualTo(1);
        }
    }
}
