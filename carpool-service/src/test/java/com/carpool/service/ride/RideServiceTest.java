package com.carpool.service.ride;

import com.carpool.common.exception.*;
import com.carpool.domain.entity.*;
import com.carpool.domain.enums.*;
import com.carpool.repository.BookingRepository;
import com.carpool.repository.HubRepository;
import com.carpool.repository.RideRepository;
import com.carpool.repository.UserRepository;
import com.carpool.repository.VehicleRepository;
import com.carpool.service.dto.request.CreateRideRequest;
import com.carpool.service.dto.request.UpdateRideStatusRequest;
import com.carpool.service.dto.response.RideResponse;
import com.carpool.service.dto.response.UserResponse;
import com.carpool.service.event.RideEvents;
import com.carpool.service.mapper.EntityMapper;
import com.carpool.service.rating.RatingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RideService")
class RideServiceTest {

    @Mock private RideRepository      rideRepository;
    @Mock private HubRepository       hubRepository;
    @Mock private UserRepository      userRepository;
    @Mock private BookingRepository   bookingRepository;
    @Mock private VehicleRepository   vehicleRepository;
    @Mock private EntityMapper        mapper;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private RatingService       ratingService;

    @InjectMocks
    private RideService rideService;

    private User driver;
    private Hub  originHub;
    private Hub  destinationHub;
    private Ride activeRide;

    @BeforeEach
    void setUp() {
        driver = User.builder()
                .id(1L).telegramId(111L).fullName("Driver Juan")
                .role(UserRole.DRIVER).status(UserStatus.ACTIVE)
                .build();

        originHub = Hub.builder()
                .id(10L).code("AYALA_MRT").name("Ayala MRT").area("Makati")
                .status(HubStatus.ACTIVE).build();

        destinationHub = Hub.builder()
                .id(11L).code("BGC_HIGH_STREET").name("BGC High Street").area("Taguig")
                .status(HubStatus.ACTIVE).build();

        activeRide = Ride.builder()
                .id(100L).driver(driver)
                .originHub(originHub).destinationHub(destinationHub)
                .direction(RideDirection.HOME_TO_WORK)
                .departureTime(LocalDateTime.now().plusHours(3))
                .totalSeats(3).availableSeats(3)
                .contributionAmount(new BigDecimal("150.00"))
                .status(RideStatus.ACTIVE)
                .build();
    }

    // ── createRide ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createRide()")
    class CreateRide {

        @Test
        @DisplayName("should create ride as DRAFT with correct seat count")
        void shouldCreateRideAsDraft() {
            var request = new CreateRideRequest(
                    10L, 11L, RideDirection.HOME_TO_WORK,
                    LocalDateTime.now().plusHours(2),
                    3, new BigDecimal("150.00"), "Meet at 7-Eleven", null, null);

            when(userRepository.findById(1L)).thenReturn(Optional.of(driver));
            when(hubRepository.findById(10L)).thenReturn(Optional.of(originHub));
            when(hubRepository.findById(11L)).thenReturn(Optional.of(destinationHub));
            when(rideRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toRideResponse(any())).thenReturn(mock(RideResponse.class));

            rideService.createRide(request, 1L);

            ArgumentCaptor<Ride> captor = ArgumentCaptor.forClass(Ride.class);
            verify(rideRepository).save(captor.capture());

            Ride saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(RideStatus.DRAFT);
            assertThat(saved.getTotalSeats()).isEqualTo(3);
            assertThat(saved.getAvailableSeats()).isEqualTo(3); // availableSeats = totalSeats on create
            assertThat(saved.getNotes()).isEqualTo("Meet at 7-Eleven");
        }

        @Test
        @DisplayName("should throw InsufficientRoleException when PASSENGER tries to create ride")
        void shouldThrowWhenPassengerCreatesRide() {
            User passenger = User.builder()
                    .id(2L).role(UserRole.PASSENGER).build();

            var request = new CreateRideRequest(
                    10L, 11L, RideDirection.HOME_TO_WORK,
                    LocalDateTime.now().plusHours(2),
                    3, new BigDecimal("150.00"), null, null, null);

            when(userRepository.findById(2L)).thenReturn(Optional.of(passenger));

            assertThatThrownBy(() -> rideService.createRide(request, 2L))
                    .isInstanceOf(InsufficientRoleException.class)
                    .hasMessageContaining("permission");
        }

        @Test
        @DisplayName("should throw SameHubException when origin equals destination")
        void shouldThrowWhenSameHub() {
            var request = new CreateRideRequest(
                    10L, 10L, RideDirection.HOME_TO_WORK, // same hub ID
                    LocalDateTime.now().plusHours(2),
                    3, new BigDecimal("150.00"), null, null, null);

            when(userRepository.findById(1L)).thenReturn(Optional.of(driver));
            when(hubRepository.findById(10L)).thenReturn(Optional.of(originHub));

            assertThatThrownBy(() -> rideService.createRide(request, 1L))
                    .isInstanceOf(SameHubException.class);
        }

        @Test
        @DisplayName("should throw DeparturePastException when departure time is in the past")
        void shouldThrowWhenDepartureInPast() {
            var request = new CreateRideRequest(
                    10L, 11L, RideDirection.HOME_TO_WORK,
                    LocalDateTime.now().minusHours(1), // past time
                    3, new BigDecimal("150.00"), null, null, null);

            when(userRepository.findById(1L)).thenReturn(Optional.of(driver));
            when(hubRepository.findById(10L)).thenReturn(Optional.of(originHub));
            when(hubRepository.findById(11L)).thenReturn(Optional.of(destinationHub));

            assertThatThrownBy(() -> rideService.createRide(request, 1L))
                    .isInstanceOf(DeparturePastException.class);
        }
    }

    // ── updateRideStatus (state machine) ──────────────────────────────────────

    @Nested
    @DisplayName("updateRideStatus() - state machine")
    class UpdateRideStatus {

        @Test
        @DisplayName("should transition DRAFT → ACTIVE (publish ride)")
        void shouldPublishDraftRide() {
            activeRide.setStatus(RideStatus.DRAFT);

            when(rideRepository.findById(100L)).thenReturn(Optional.of(activeRide));
            when(rideRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toRideResponse(any())).thenReturn(mock(RideResponse.class));

            rideService.updateRideStatus(100L,
                    new UpdateRideStatusRequest(RideStatus.ACTIVE), 1L);

            assertThat(activeRide.getStatus()).isEqualTo(RideStatus.ACTIVE);
            verify(eventPublisher).publishEvent(any(RideEvents.RidePostedEvent.class));
        }

        @Test
        @DisplayName("should transition ACTIVE → CANCELLED and publish event")
        void shouldCancelActiveRideAndPublishEvent() {
            when(rideRepository.findById(100L)).thenReturn(Optional.of(activeRide));
            when(rideRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toRideResponse(any())).thenReturn(mock(RideResponse.class));

            rideService.updateRideStatus(100L,
                    new UpdateRideStatusRequest(RideStatus.CANCELLED), 1L);

            assertThat(activeRide.getStatus()).isEqualTo(RideStatus.CANCELLED);
            verify(eventPublisher).publishEvent(any(RideEvents.RideCancelledEvent.class));
        }

        @Test
        @DisplayName("should transition DEPARTED → COMPLETED and publish event")
        void shouldCompleteActiveRideAndPublishEvent() {
            activeRide.setStatus(RideStatus.DEPARTED); // must be DEPARTED first

            when(rideRepository.findById(100L)).thenReturn(Optional.of(activeRide));
            when(rideRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(bookingRepository.findActiveBookingsForRide(100L)).thenReturn(List.of());
            when(mapper.toRideResponse(any())).thenReturn(mock(RideResponse.class));

            rideService.updateRideStatus(100L,
                    new UpdateRideStatusRequest(RideStatus.COMPLETED), 1L);

            assertThat(activeRide.getStatus()).isEqualTo(RideStatus.COMPLETED);
            verify(eventPublisher).publishEvent(any(RideEvents.RideCompletedEvent.class));
        }

        @Test
        @DisplayName("should throw InvalidRideStateException for invalid transition DRAFT → COMPLETED")
        void shouldThrowOnInvalidTransition() {
            activeRide.setStatus(RideStatus.DRAFT);
            when(rideRepository.findById(100L)).thenReturn(Optional.of(activeRide));

            assertThatThrownBy(() -> rideService.updateRideStatus(100L,
                    new UpdateRideStatusRequest(RideStatus.COMPLETED), 1L))
                    .isInstanceOf(InvalidRideStateException.class)
                    .hasMessageContaining("publish");
        }

        @Test
        @DisplayName("should throw InvalidRideStateException for invalid transition COMPLETED → ACTIVE")
        void shouldThrowWhenReactivatingCompletedRide() {
            activeRide.setStatus(RideStatus.COMPLETED);
            when(rideRepository.findById(100L)).thenReturn(Optional.of(activeRide));

            assertThatThrownBy(() -> rideService.updateRideStatus(100L,
                    new UpdateRideStatusRequest(RideStatus.ACTIVE), 1L))
                    .isInstanceOf(InvalidRideStateException.class);
        }

        @Test
        @DisplayName("should throw NotRideOwnerException when non-driver updates status")
        void shouldThrowWhenNotOwner() {
            when(rideRepository.findById(100L)).thenReturn(Optional.of(activeRide));

            // User 99 is not the driver (id=1)
            assertThatThrownBy(() -> rideService.updateRideStatus(100L,
                    new UpdateRideStatusRequest(RideStatus.CANCELLED), 99L))
                    .isInstanceOf(NotRideOwnerException.class);
        }

        @Test
        @DisplayName("should throw RideNotFoundException when ride does not exist")
        void shouldThrowWhenRideNotFound() {
            when(rideRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> rideService.updateRideStatus(999L,
                    new UpdateRideStatusRequest(RideStatus.CANCELLED), 1L))
                    .isInstanceOf(RideNotFoundException.class);
        }
    }

    // ── expireStaleRides ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("expireStaleRides()")
    class ExpireStaleRides {

        @Test
        @DisplayName("should auto-depart stale ACTIVE rides past departure + 15 min buffer")
        void shouldAutoDepartStaleRides() {
            Ride staleRide = Ride.builder()
                    .id(200L).driver(driver)
                    .originHub(originHub).destinationHub(destinationHub)
                    .direction(RideDirection.HOME_TO_WORK)
                    .departureTime(LocalDateTime.now().minusMinutes(20)) // past + 15 min buffer
                    .totalSeats(3).availableSeats(3)
                    .status(RideStatus.ACTIVE)
                    .build();

            when(rideRepository.findStaleActiveRides(any())).thenReturn(List.of(staleRide));

            rideService.expireStaleRides();

            assertThat(staleRide.getStatus()).isEqualTo(RideStatus.DEPARTED);
            verify(rideRepository).saveAll(List.of(staleRide));
        }

        @Test
        @DisplayName("should do nothing when no stale rides exist")
        void shouldDoNothingWhenNoStaleRides() {
            when(rideRepository.findStaleActiveRides(any())).thenReturn(List.of());

            rideService.expireStaleRides();

            verify(rideRepository, never()).save(any());
        }

        @Test
        @DisplayName("should auto-depart multiple stale rides")
        void shouldAutoDepartMultipleStaleRides() {
            Ride staleRide1 = Ride.builder()
                    .id(201L).driver(driver)
                    .originHub(originHub).destinationHub(destinationHub)
                    .direction(RideDirection.HOME_TO_WORK)
                    .departureTime(LocalDateTime.now().minusMinutes(30))
                    .totalSeats(3).availableSeats(2)
                    .status(RideStatus.ACTIVE)
                    .build();

            Ride staleRide2 = Ride.builder()
                    .id(202L).driver(driver)
                    .originHub(originHub).destinationHub(destinationHub)
                    .direction(RideDirection.WORK_TO_HOME)
                    .departureTime(LocalDateTime.now().minusMinutes(60))
                    .totalSeats(4).availableSeats(1)
                    .status(RideStatus.FULL)
                    .build();

            when(rideRepository.findStaleActiveRides(any())).thenReturn(List.of(staleRide1, staleRide2));

            rideService.expireStaleRides();

            assertThat(staleRide1.getStatus()).isEqualTo(RideStatus.DEPARTED);
            assertThat(staleRide2.getStatus()).isEqualTo(RideStatus.DEPARTED);
            verify(rideRepository).saveAll(List.of(staleRide1, staleRide2));
        }
    }

// ── completeStaleRides ────────────────────────────────────────────────────

    @Nested
    @DisplayName("completeStaleRides()")
    class CompleteStaleRides {

        @Test
        @DisplayName("should auto-complete DEPARTED rides older than 2 hours")
        void shouldAutoCompleteStaleRides() {
            Ride departedRide = Ride.builder()
                    .id(300L).driver(driver)
                    .originHub(originHub).destinationHub(destinationHub)
                    .direction(RideDirection.HOME_TO_WORK)
                    .departureTime(LocalDateTime.now().minusHours(3))
                    .totalSeats(3).availableSeats(1)
                    .status(RideStatus.DEPARTED)
                    .build();

            Booking confirmedBooking = Booking.builder()
                    .id(50L).ride(departedRide)
                    .passenger(User.builder().id(2L).build())
                    .seatsReserved(2)
                    .status(BookingStatus.CONFIRMED)
                    .build();

            when(rideRepository.findByStatusAndDepartureTimeBefore(
                    eq(RideStatus.DEPARTED), any())).thenReturn(List.of(departedRide));
            when(bookingRepository.findActiveBookingsForRide(300L))
                    .thenReturn(List.of(confirmedBooking));

            rideService.completeStaleRides();

            assertThat(departedRide.getStatus()).isEqualTo(RideStatus.COMPLETED);
            assertThat(confirmedBooking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
            verify(rideRepository).saveAll(List.of(departedRide));
            verify(bookingRepository).saveAll(List.of(confirmedBooking));
        }

        @Test
        @DisplayName("should do nothing when no stale departed rides")
        void shouldDoNothingWhenNoStaleDepartedRides() {
            when(rideRepository.findByStatusAndDepartureTimeBefore(
                    eq(RideStatus.DEPARTED), any())).thenReturn(List.of());

            rideService.completeStaleRides();

            verify(rideRepository, never()).save(any());
            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("should complete ride even when no bookings exist")
        void shouldCompleteRideWithNoBookings() {
            Ride departedRide = Ride.builder()
                    .id(301L).driver(driver)
                    .originHub(originHub).destinationHub(destinationHub)
                    .direction(RideDirection.HOME_TO_WORK)
                    .departureTime(LocalDateTime.now().minusHours(3))
                    .totalSeats(3).availableSeats(3)
                    .status(RideStatus.DEPARTED)
                    .build();

            when(rideRepository.findByStatusAndDepartureTimeBefore(
                    eq(RideStatus.DEPARTED), any())).thenReturn(List.of(departedRide));
            when(bookingRepository.findActiveBookingsForRide(301L)).thenReturn(List.of());

            rideService.completeStaleRides();

            assertThat(departedRide.getStatus()).isEqualTo(RideStatus.COMPLETED);
            verify(rideRepository).saveAll(List.of(departedRide));
            verify(bookingRepository, never()).save(any());
        }
    }

// ── getRidesByDirection ───────────────────────────────────────────────────

    @Nested
    @DisplayName("getRidesByDirection()")
    class GetRidesByDirection {

        private Ride makeRide(Long id, BigDecimal contribution, int availableSeats,
                              LocalDateTime departureTime) {
            return Ride.builder()
                    .id(id).driver(driver)
                    .originHub(originHub).destinationHub(destinationHub)
                    .direction(RideDirection.HOME_TO_WORK)
                    .departureTime(departureTime)
                    .totalSeats(4).availableSeats(availableSeats)
                    .contributionAmount(contribution)
                    .status(RideStatus.ACTIVE)
                    .build();
        }

        @Test
        @DisplayName("should exclude driver's own rides from results")
        void shouldExcludeDriverOwnRides() {
            Ride ownRide = makeRide(400L, new BigDecimal("50"), 3,
                    LocalDateTime.now().plusHours(1));

            when(rideRepository.findActiveByDirectionAndTimeRange(any(), any(), any(), any()))
                    .thenReturn(List.of(ownRide));

            // driver (id=1) searching — should exclude own ride
            List<RideResponse> result = rideService.getRidesByDirection(
                    RideDirection.HOME_TO_WORK, 1L,
                    LocalDateTime.now(), LocalDateTime.now().plusHours(4),
                    null, null, null);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should filter by max share amount")
        void shouldFilterByMaxPrice() {
            Ride cheapRide     = makeRide(401L, new BigDecimal("50"),  3, LocalDateTime.now().plusHours(1));
            Ride expensiveRide = makeRide(402L, new BigDecimal("200"), 3, LocalDateTime.now().plusHours(2));

            when(rideRepository.findActiveByDirectionAndTimeRange(any(), any(), any(), any()))
                    .thenReturn(List.of(cheapRide, expensiveRide));

            RideResponse cheapResponse = mock(RideResponse.class);
            UserResponse mockDriver1 = mock(UserResponse.class);
            when(mockDriver1.id()).thenReturn(1L);
            when(cheapResponse.driver()).thenReturn(mockDriver1);
            when(mapper.toRideResponse(cheapRide)).thenReturn(cheapResponse);
            when(ratingService.getAverageRatingsByDriverIds(any())).thenReturn(Map.of());

            List<RideResponse> result = rideService.getRidesByDirection(
                    RideDirection.HOME_TO_WORK, 99L,
                    LocalDateTime.now(), LocalDateTime.now().plusHours(4),
                    new BigDecimal("100"), null, null);

            assertThat(result).hasSize(1);
            verify(mapper, never()).toRideResponse(expensiveRide);
        }

        @Test
        @DisplayName("should filter by minimum available seats")
        void shouldFilterByMinSeats() {
            Ride fullRide      = makeRide(403L, new BigDecimal("50"), 1, LocalDateTime.now().plusHours(1));
            Ride availableRide = makeRide(404L, new BigDecimal("50"), 3, LocalDateTime.now().plusHours(2));

            when(rideRepository.findActiveByDirectionAndTimeRange(any(), any(), any(), any()))
                    .thenReturn(List.of(fullRide, availableRide));

            RideResponse availableResponse = mock(RideResponse.class);
            UserResponse mockDriver2 = mock(UserResponse.class);
            when(mockDriver2.id()).thenReturn(1L);
            when(availableResponse.driver()).thenReturn(mockDriver2);
            when(availableResponse.availableSeats()).thenReturn(3);
            when(mapper.toRideResponse(availableRide)).thenReturn(availableResponse);
            when(ratingService.getAverageRatingsByDriverIds(any())).thenReturn(Map.of());

            List<RideResponse> result = rideService.getRidesByDirection(
                    RideDirection.HOME_TO_WORK, 99L,
                    LocalDateTime.now(), LocalDateTime.now().plusHours(4),
                    null, 2, null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).availableSeats()).isEqualTo(3);
            verify(mapper, never()).toRideResponse(fullRide);
        }

        @Test
        @DisplayName("should sort by earliest departure time by default")
        void shouldSortByEarliestByDefault() {
            LocalDateTime early = LocalDateTime.now().plusHours(1);
            LocalDateTime late  = LocalDateTime.now().plusHours(3);

            Ride lateRide  = makeRide(405L, new BigDecimal("50"), 3, late);
            Ride earlyRide = makeRide(406L, new BigDecimal("50"), 3, early);

            when(rideRepository.findActiveByDirectionAndTimeRange(any(), any(), any(), any()))
                    .thenReturn(List.of(lateRide, earlyRide));

            RideResponse lateResponse  = mock(RideResponse.class);
            RideResponse earlyResponse = mock(RideResponse.class);

            UserResponse mockDriver3 = mock(UserResponse.class);
            when(mockDriver3.id()).thenReturn(1L);
            when(lateResponse.driver()).thenReturn(mockDriver3);
            when(earlyResponse.driver()).thenReturn(mockDriver3);

            when(lateResponse.departureTime()).thenReturn(late);
            when(earlyResponse.departureTime()).thenReturn(early);

            when(mapper.toRideResponse(lateRide)).thenReturn(lateResponse);
            when(mapper.toRideResponse(earlyRide)).thenReturn(earlyResponse);
            when(ratingService.getAverageRatingsByDriverIds(any())).thenReturn(Map.of());

            List<RideResponse> result = rideService.getRidesByDirection(
                    RideDirection.HOME_TO_WORK, 99L,
                    LocalDateTime.now(), LocalDateTime.now().plusHours(4),
                    null, null, null); // default sort

            assertThat(result.get(0).departureTime()).isEqualTo(early);
            assertThat(result.get(1).departureTime()).isEqualTo(late);
        }

        @Test
        @DisplayName("should sort by cheapest contribution when CHEAPEST sort specified")
        void shouldSortByCheapest() {
            Ride expRide   = makeRide(407L, new BigDecimal("150"), 3, LocalDateTime.now().plusHours(1));
            Ride cheapRide = makeRide(408L, new BigDecimal("50"),  3, LocalDateTime.now().plusHours(2));

            when(rideRepository.findActiveByDirectionAndTimeRange(any(), any(), any(), any()))
                    .thenReturn(List.of(expRide, cheapRide));

            RideResponse expResponse   = mock(RideResponse.class);
            RideResponse cheapResponse = mock(RideResponse.class);

            UserResponse mockDriver4 = mock(UserResponse.class);
            when(mockDriver4.id()).thenReturn(1L);
            when(expResponse.driver()).thenReturn(mockDriver4);
            when(cheapResponse.driver()).thenReturn(mockDriver4);

            when(expResponse.contributionAmount()).thenReturn(new BigDecimal("150"));
            when(cheapResponse.contributionAmount()).thenReturn(new BigDecimal("50"));

            when(mapper.toRideResponse(expRide)).thenReturn(expResponse);
            when(mapper.toRideResponse(cheapRide)).thenReturn(cheapResponse);
            when(ratingService.getAverageRatingsByDriverIds(any())).thenReturn(Map.of());

            List<RideResponse> result = rideService.getRidesByDirection(
                    RideDirection.HOME_TO_WORK, 99L,
                    LocalDateTime.now(), LocalDateTime.now().plusHours(4),
                    null, null, "CHEAPEST");

            assertThat(result.get(0).contributionAmount())
                    .isEqualByComparingTo(new BigDecimal("50"));
            assertThat(result.get(1).contributionAmount())
                    .isEqualByComparingTo(new BigDecimal("150"));
        }

        @Test
        @DisplayName("should sort by most seats when MOST_SEATS sort specified")
        void shouldSortByMostSeats() {
            Ride fewSeatsRide  = makeRide(409L, new BigDecimal("50"), 1, LocalDateTime.now().plusHours(1));
            Ride manySeatsRide = makeRide(410L, new BigDecimal("50"), 4, LocalDateTime.now().plusHours(2));

            when(rideRepository.findActiveByDirectionAndTimeRange(any(), any(), any(), any()))
                    .thenReturn(List.of(fewSeatsRide, manySeatsRide));

            RideResponse fewResponse  = mock(RideResponse.class);
            RideResponse manyResponse = mock(RideResponse.class);

            UserResponse mockDriver5 = mock(UserResponse.class);
            when(mockDriver5.id()).thenReturn(1L);
            when(fewResponse.driver()).thenReturn(mockDriver5);
            when(manyResponse.driver()).thenReturn(mockDriver5);

            when(fewResponse.availableSeats()).thenReturn(1);
            when(manyResponse.availableSeats()).thenReturn(4);

            when(mapper.toRideResponse(fewSeatsRide)).thenReturn(fewResponse);
            when(mapper.toRideResponse(manySeatsRide)).thenReturn(manyResponse);
            when(ratingService.getAverageRatingsByDriverIds(any())).thenReturn(Map.of());

            List<RideResponse> result = rideService.getRidesByDirection(
                    RideDirection.HOME_TO_WORK, 99L,
                    LocalDateTime.now(), LocalDateTime.now().plusHours(4),
                    null, null, "MOST_SEATS");

            assertThat(result.get(0).availableSeats()).isEqualTo(4);
            assertThat(result.get(1).availableSeats()).isEqualTo(1);
        }
    }
}