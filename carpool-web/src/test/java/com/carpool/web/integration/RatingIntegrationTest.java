package com.carpool.web.integration;

import com.carpool.common.exception.RatingConflictException;
import com.carpool.domain.entity.*;
import com.carpool.domain.enums.*;
import com.carpool.repository.*;
import com.carpool.service.mapper.EntityMapper;
import com.carpool.service.rating.RatingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Rating Integration")
class RatingIntegrationTest extends BaseIntegrationTest {

    @Autowired private RatingService     ratingService;
    @Autowired private EntityMapper      mapper;
    @Autowired private UserRepository    userRepository;
    @Autowired private HubRepository     hubRepository;
    @Autowired private RideRepository    rideRepository;
    @Autowired private BookingRepository bookingRepository;

    private User driver;
    private User passenger;
    private Ride completedRide;
    private Booking confirmedBooking;

    @BeforeEach
    void setUp() {
        long seed = System.currentTimeMillis();

        driver = userRepository.save(User.builder()
                .telegramId(seed + 20).fullName("Rating Driver")
                .role(UserRole.DRIVER).status(UserStatus.ACTIVE).build());

        passenger = userRepository.save(User.builder()
                .telegramId(seed + 21).fullName("Rating Passenger")
                .role(UserRole.PASSENGER).status(UserStatus.ACTIVE).build());

        Hub origin = hubRepository.findByCode("AYALA_MRT").orElseThrow();
        Hub destination = hubRepository.findByCode("BGC_HIGH_STREET").orElseThrow();

        completedRide = rideRepository.save(Ride.builder()
                .driver(driver)
                .originHub(origin)
                .destinationHub(destination)
                .direction(RideDirection.HOME_TO_WORK)
                .departureTime(LocalDateTime.now().minusHours(2))
                .totalSeats(4)
                .availableSeats(3)
                .status(RideStatus.COMPLETED)
                .contributionAmount(BigDecimal.valueOf(100))
                .build());

        confirmedBooking = bookingRepository.save(Booking.builder()
                .ride(completedRide)
                .passenger(passenger)
                .status(BookingStatus.COMPLETED)
                .build());
    }

    @Test
    @DisplayName("submitRating — passenger rates driver, returns saved RideRating")
    void submitRating_passengerRatesDriver_succeeds() {
        var rating = ratingService.submitRating(
                completedRide.getId(), passenger.getId(), driver.getId(), 5, "Great driver!");

        assertThat(rating.getId()).isNotNull();
        assertThat(rating.getStars()).isEqualTo(5);
        assertThat(rating.getRaterRole()).isEqualTo("PASSENGER");
    }

    @Test
    @DisplayName("submitRating — duplicate rating throws 409 RatingConflictException")
    void submitRating_duplicate_throws409() {
        ratingService.submitRating(
                completedRide.getId(), passenger.getId(), driver.getId(), 4, null);

        assertThatThrownBy(() ->
                ratingService.submitRating(
                        completedRide.getId(), passenger.getId(), driver.getId(), 3, null))
                .isInstanceOf(RatingConflictException.class);
    }

    @Test
    @DisplayName("submitRating — non-completed ride throws 409 RatingConflictException")
    void submitRating_nonCompletedRide_throws409() {
        Hub origin = hubRepository.findByCode("AYALA_MRT").orElseThrow();
        Hub destination = hubRepository.findByCode("BGC_HIGH_STREET").orElseThrow();

        Ride activeRide = rideRepository.save(Ride.builder()
                .driver(driver)
                .originHub(origin)
                .destinationHub(destination)
                .direction(RideDirection.HOME_TO_WORK)
                .departureTime(LocalDateTime.now().plusHours(2))
                .totalSeats(4)
                .availableSeats(4)
                .status(RideStatus.ACTIVE)
                .contributionAmount(BigDecimal.valueOf(100))
                .build());

        assertThatThrownBy(() ->
                ratingService.submitRating(
                        activeRide.getId(), passenger.getId(), driver.getId(), 5, null))
                .isInstanceOf(RatingConflictException.class);
    }

    @Test
    @DisplayName("getRatingsReceivedPaged — no LazyInitializationException (regression test)")
    void getRatingsReceivedPaged_noLazyInitException() {
        ratingService.submitRating(
                completedRide.getId(), passenger.getId(), driver.getId(), 4, "Good");

        assertThatCode(() -> {
            org.springframework.data.domain.Page<com.carpool.domain.entity.RideRating> page =
                    ratingService.getRatingsReceivedPaged(driver.getId(), 0, 10);
            page.getContent().forEach(r -> mapper.toRatingResponse(r));
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("getRatingsReceivedPaged — pagination returns correct page size")
    void getRatingsReceivedPaged_pagination() {
        ratingService.submitRating(
                completedRide.getId(), passenger.getId(), driver.getId(), 5, null);

        var page = ratingService.getRatingsReceivedPaged(driver.getId(), 0, 10);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("canRate — passenger eligible before rating, ineligible after")
    void canRate_eligibilityToggle() {
        assertThat(ratingService.canRate(completedRide.getId(), passenger.getId())).isTrue();

        ratingService.submitRating(
                completedRide.getId(), passenger.getId(), driver.getId(), 5, null);

        assertThat(ratingService.canRate(completedRide.getId(), passenger.getId())).isFalse();
    }

    @Test
    @DisplayName("getRateeIds — passenger gets driver ID as single ratee")
    void getRateeIds_passengerGetsDriverId() {
        var rateeIds = ratingService.getRateeIds(completedRide.getId(), passenger.getId());

        assertThat(rateeIds).containsExactly(driver.getId());
    }
}
