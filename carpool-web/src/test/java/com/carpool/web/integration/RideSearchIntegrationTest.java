package com.carpool.web.integration;

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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the waypoint-aware ride search query against real MySQL.
 *
 * Key scenario: passenger searches for a hub that appears as a WAYPOINT
 * on a ride (not origin/destination) — should still be found.
 * This is the core value of Option B (waypoint model) we designed.
 */
@DisplayName("Ride Search Integration")
@Transactional
class RideSearchIntegrationTest extends BaseIntegrationTest {

    @Autowired private RideService    rideService;
    @Autowired private UserRepository userRepository;
    @Autowired private HubRepository  hubRepository;
    @Autowired private RideRepository rideRepository;

    private User driver;
    private Hub  ayalaMrt;      // origin
    private Hub  bgcHighStreet; // destination
    private Hub  smAura;        // waypoint between origin and destination
    private Hub  filinvest;     // unrelated hub — should NOT appear in results

    @BeforeEach
    void setUp() {
        // Driver
        driver = userRepository.save(User.builder()
                .telegramId(999001L)
                .fullName("Test Driver")
                .role(UserRole.DRIVER)
                .status(UserStatus.ACTIVE)
                .build());

        // Fetch seeded hubs from V2 migration
        ayalaMrt      = hubRepository.findByCode("AYALA_MRT").orElseThrow();
        bgcHighStreet = hubRepository.findByCode("BGC_HIGH_STREET").orElseThrow();
        smAura        = hubRepository.findByCode("SM_AURA").orElseThrow();
        filinvest     = hubRepository.findByCode("FILINVEST_CITY").orElseThrow();
    }

    @Test
    @DisplayName("should find ride when searching by origin and destination")
    void shouldFindRideByOriginAndDestination() {
        // Arrange — create ACTIVE ride: Ayala MRT → BGC High Street
        createActiveRide(ayalaMrt, bgcHighStreet, List.of());

        // Act
        List<RideResponse> results = rideService.searchRides(
                ayalaMrt.getId(), bgcHighStreet.getId());

        // Assert
        assertThat(results).hasSize(1);
        assertThat(results.get(0).originHub().code()).isEqualTo("AYALA_MRT");
        assertThat(results.get(0).destinationHub().code()).isEqualTo("BGC_HIGH_STREET");
    }

    @Test
    @DisplayName("should find ride when passenger searches via waypoint hub")
    void shouldFindRideViaWaypointHub() {
        // Arrange — ride goes Ayala MRT → BGC, passing through SM Aura (waypoint)
        Ride ride = createActiveRide(ayalaMrt, bgcHighStreet, List.of(smAura));

        // Act — passenger searches Ayala MRT → SM Aura
        // SM Aura is a waypoint, not the destination — should still be found
        List<RideResponse> results = rideService.searchRides(
                ayalaMrt.getId(), smAura.getId());

        // Assert — ride is found even though SM Aura is a waypoint, not destination
        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo(ride.getId());
    }

    @Test
    @DisplayName("should NOT find ride when hub is unrelated to route")
    void shouldNotFindRideForUnrelatedHub() {
        // Arrange — ride goes Ayala MRT → BGC only
        createActiveRide(ayalaMrt, bgcHighStreet, List.of());

        // Act — passenger searches for Filinvest which has nothing to do with this ride
        List<RideResponse> results = rideService.searchRides(
                ayalaMrt.getId(), filinvest.getId());

        // Assert
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("should NOT find DRAFT rides in search results")
    void shouldNotFindDraftRides() {
        // Arrange — DRAFT ride (not yet published by driver)
        Ride draftRide = Ride.builder()
                .driver(driver)
                .originHub(ayalaMrt)
                .destinationHub(bgcHighStreet)
                .direction(RideDirection.HOME_TO_WORK)
                .departureTime(LocalDateTime.now().plusHours(2))
                .totalSeats(3).availableSeats(3)
                .contributionAmount(new BigDecimal("150.00"))
                .status(RideStatus.DRAFT) // not published
                .build();
        rideRepository.save(draftRide);

        // Act
        List<RideResponse> results = rideService.searchRides(
                ayalaMrt.getId(), bgcHighStreet.getId());

        // Assert — DRAFT rides are invisible to passengers
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("should NOT find rides with past departure time")
    void shouldNotFindPastRides() {
        // Arrange — ACTIVE ride but departure was yesterday
        Ride pastRide = Ride.builder()
                .driver(driver)
                .originHub(ayalaMrt)
                .destinationHub(bgcHighStreet)
                .direction(RideDirection.HOME_TO_WORK)
                .departureTime(LocalDateTime.now().minusDays(1)) // yesterday
                .totalSeats(3).availableSeats(3)
                .contributionAmount(new BigDecimal("150.00"))
                .status(RideStatus.ACTIVE)
                .build();
        rideRepository.save(pastRide);

        // Act
        List<RideResponse> results = rideService.searchRides(
                ayalaMrt.getId(), bgcHighStreet.getId());

        // Assert — expired rides filtered out
        assertThat(results).isEmpty();
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private Ride createActiveRide(Hub origin, Hub destination, List<Hub> waypointHubs) {
        Ride ride = Ride.builder()
                .driver(driver)
                .originHub(origin)
                .destinationHub(destination)
                .direction(RideDirection.HOME_TO_WORK)
                .departureTime(LocalDateTime.now().plusHours(3))
                .totalSeats(3).availableSeats(3)
                .contributionAmount(new BigDecimal("150.00"))
                .status(RideStatus.ACTIVE)
                .build();

        Ride saved = rideRepository.save(ride);

        // Add waypoints if any
        for (int i = 0; i < waypointHubs.size(); i++) {
            RideWaypoint wp = RideWaypoint.builder()
                    .ride(saved)
                    .hub(waypointHubs.get(i))
                    .sequenceOrder(i + 1)
                    .isPickup(true)
                    .isDropoff(true)
                    .build();
            saved.getWaypoints().add(wp);
        }

        return rideRepository.save(saved);
    }
}