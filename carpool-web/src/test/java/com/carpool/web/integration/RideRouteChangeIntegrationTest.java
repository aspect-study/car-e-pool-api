package com.carpool.web.integration;

import com.carpool.common.exception.InvalidRideStateException;
import com.carpool.common.exception.NotRideOwnerException;
import com.carpool.common.exception.SameHubException;
import com.carpool.domain.entity.*;
import com.carpool.domain.enums.*;
import com.carpool.repository.*;
import com.carpool.service.dto.response.RideResponse;
import com.carpool.service.ride.RideService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies RideService.updateRoute against real MySQL — ownership, state,
 * and route-validity guards, plus that origin/destination are actually persisted.
 */
@DisplayName("Ride Route Change Integration")
@Transactional
class RideRouteChangeIntegrationTest extends BaseIntegrationTest {

    @Autowired private RideService    rideService;
    @Autowired private UserRepository userRepository;
    @Autowired private HubRepository  hubRepository;
    @Autowired private RideRepository rideRepository;

    private User driver;
    private User otherUser;
    private Hub  ayalaMrt;      // origin
    private Hub  bgcHighStreet; // destination
    private Hub  smAura;        // new destination for the happy path

    @BeforeEach
    void setUp() {
        driver = userRepository.save(User.builder()
                .telegramId(999101L).fullName("Route Test Driver")
                .role(UserRole.DRIVER).status(UserStatus.ACTIVE)
                .build());

        otherUser = userRepository.save(User.builder()
                .telegramId(999102L).fullName("Other User")
                .role(UserRole.DRIVER).status(UserStatus.ACTIVE)
                .build());

        // Fetch seeded hubs from V2 migration
        ayalaMrt      = hubRepository.findByCode("AYALA_MRT").orElseThrow();
        bgcHighStreet = hubRepository.findByCode("BGC_HIGH_STREET").orElseThrow();
        smAura        = hubRepository.findByCode("SM_AURA").orElseThrow();
    }

    private Ride createActiveRide() {
        return rideRepository.save(Ride.builder()
                .driver(driver)
                .originHub(ayalaMrt)
                .destinationHub(bgcHighStreet)
                .direction(RideDirection.HOME_TO_WORK)
                .departureTime(LocalDateTime.now().plusHours(3))
                .totalSeats(3)
                .availableSeats(3)
                .contributionAmount(new BigDecimal("150.00"))
                .status(RideStatus.ACTIVE)
                .build());
    }

    @Test
    @DisplayName("owner can change the destination and it persists to real DB")
    void ownerChangesDestination() {
        Ride ride = createActiveRide();

        RideResponse result = rideService.updateRoute(
                ride.getId(), null, smAura.getId(), driver.getId());

        assertThat(result.destinationHub().id()).isEqualTo(smAura.getId());
        assertThat(result.originHub().id()).isEqualTo(ayalaMrt.getId());

        Ride updated = rideRepository.findById(ride.getId()).orElseThrow();
        assertThat(updated.getDestinationHub().getId()).isEqualTo(smAura.getId());
        assertThat(updated.getOriginHub().getId()).isEqualTo(ayalaMrt.getId());
    }

    @Test
    @DisplayName("non-owner cannot change the route")
    void nonOwnerForbidden() {
        Ride ride = createActiveRide();

        assertThatThrownBy(() -> rideService.updateRoute(
                ride.getId(), null, smAura.getId(), otherUser.getId()))
                .isInstanceOf(NotRideOwnerException.class);
    }

    @Test
    @DisplayName("changing destination to the current origin throws SameHubException")
    void sameHubRejected() {
        Ride ride = createActiveRide();

        assertThatThrownBy(() -> rideService.updateRoute(
                ride.getId(), null, ayalaMrt.getId(), driver.getId()))
                .isInstanceOf(SameHubException.class);
    }

    @Test
    @DisplayName("submitting the unchanged route throws InvalidRideStateException")
    void noOpChangeRejected() {
        Ride ride = createActiveRide();

        assertThatThrownBy(() -> rideService.updateRoute(
                ride.getId(), ayalaMrt.getId(), bgcHighStreet.getId(), driver.getId()))
                .isInstanceOf(InvalidRideStateException.class);
    }

    @Test
    @DisplayName("providing no fields throws InvalidRideStateException")
    void emptyRequestRejected() {
        Ride ride = createActiveRide();

        assertThatThrownBy(() -> rideService.updateRoute(
                ride.getId(), null, null, driver.getId()))
                .isInstanceOf(InvalidRideStateException.class);
    }
}
