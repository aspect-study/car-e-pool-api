package com.carpool.service.ride;

import com.carpool.common.exception.*;
import com.carpool.domain.entity.*;
import com.carpool.domain.enums.*;
import com.carpool.repository.BookingRepository;
import com.carpool.repository.HubRepository;
import com.carpool.repository.RideRepository;
import com.carpool.repository.UserRepository;
import com.carpool.service.dto.request.CreateRideRequest;
import com.carpool.service.dto.request.UpdateRideStatusRequest;
import com.carpool.service.dto.response.RideResponse;
import com.carpool.service.event.RideEvents;
import com.carpool.service.mapper.EntityMapper;
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
    @Mock private EntityMapper        mapper;
    @Mock private ApplicationEventPublisher eventPublisher;

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
                    3, new BigDecimal("150.00"), "Meet at 7-Eleven", null);

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
                    3, new BigDecimal("150.00"), null, null);

            when(userRepository.findById(2L)).thenReturn(Optional.of(passenger));

            assertThatThrownBy(() -> rideService.createRide(request, 2L))
                    .isInstanceOf(InsufficientRoleException.class)
                    .hasMessageContaining("DRIVER");
        }

        @Test
        @DisplayName("should throw SameHubException when origin equals destination")
        void shouldThrowWhenSameHub() {
            var request = new CreateRideRequest(
                    10L, 10L, RideDirection.HOME_TO_WORK, // same hub ID
                    LocalDateTime.now().plusHours(2),
                    3, new BigDecimal("150.00"), null, null);

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
                    3, new BigDecimal("150.00"), null, null);

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
            // No event published for ACTIVE transition
            verifyNoInteractions(eventPublisher);
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
        @DisplayName("should transition ACTIVE → COMPLETED and publish event")
        void shouldCompleteActiveRideAndPublishEvent() {
            when(rideRepository.findById(100L)).thenReturn(Optional.of(activeRide));
            when(rideRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
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
                    .hasMessageContaining("DRAFT")
                    .hasMessageContaining("COMPLETED");
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
}