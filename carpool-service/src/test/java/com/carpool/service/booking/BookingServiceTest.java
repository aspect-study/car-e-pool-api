package com.carpool.service.booking;

import com.carpool.common.exception.*;
import com.carpool.domain.entity.*;
import com.carpool.domain.enums.*;
import com.carpool.repository.BookingRepository;
import com.carpool.repository.RideRepository;
import com.carpool.repository.UserRepository;
import com.carpool.service.dto.request.CreateBookingRequest;
import com.carpool.service.dto.request.UpdatePaymentRequest;
import com.carpool.service.dto.response.BookingResponse;
import com.carpool.service.event.RideEvents;
import com.carpool.service.mapper.EntityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
@DisplayName("BookingService")
class BookingServiceTest {

    @Mock private BookingRepository         bookingRepository;
    @Mock private RideRepository            rideRepository;
    @Mock private UserRepository            userRepository;
    @Mock private EntityMapper              mapper;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private BookingService bookingService;

    // ── Shared fixtures ───────────────────────────────────────────────────────

    private User driver;
    private User passenger;
    private Hub  originHub;
    private Hub  destinationHub;
    private Ride ride;

    @BeforeEach
    void setUp() {
        driver = User.builder()
                .id(1L).telegramId(111L).fullName("Driver Juan")
                .role(UserRole.DRIVER).status(UserStatus.ACTIVE)
                .build();

        passenger = User.builder()
                .id(2L).telegramId(222L).fullName("Passenger Maria")
                .role(UserRole.PASSENGER).status(UserStatus.ACTIVE)
                .build();

        originHub = Hub.builder()
                .id(10L).code("AYALA_MRT").name("Ayala MRT").area("Makati")
                .status(HubStatus.ACTIVE).build();

        destinationHub = Hub.builder()
                .id(11L).code("BGC_HIGH_STREET").name("BGC High Street").area("Taguig")
                .status(HubStatus.ACTIVE).build();

        ride = Ride.builder()
                .id(100L)
                .driver(driver)
                .originHub(originHub)
                .destinationHub(destinationHub)
                .departureTime(LocalDateTime.now().plusHours(2))
                .totalSeats(3)
                .availableSeats(3)
                .contributionAmount(new BigDecimal("150.00"))
                .status(RideStatus.ACTIVE)
                .build();
    }

    // ── createBooking ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createBooking()")
    class CreateBooking {

        @Test
        @DisplayName("should confirm booking and decrement available seats")
        void shouldConfirmBookingAndDecrementSeats() {
            var request = new CreateBookingRequest(1, null, null, null);

            when(rideRepository.findByIdWithLock(100L)).thenReturn(Optional.of(ride));
            when(bookingRepository.existsActiveByRideIdAndPassengerId(100L, 2L)).thenReturn(false);
            when(userRepository.findById(2L)).thenReturn(Optional.of(passenger));
            when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toBookingResponse(any())).thenReturn(mock(BookingResponse.class));

            bookingService.createBooking(100L, request, 2L);

            assertThat(ride.getAvailableSeats()).isEqualTo(2);
            assertThat(ride.getStatus()).isEqualTo(RideStatus.ACTIVE);

            ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);
            verify(bookingRepository).save(bookingCaptor.capture());

            Booking saved = bookingCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(saved.getSeatsReserved()).isEqualTo(1);
            assertThat(saved.getContributionDue()).isEqualByComparingTo("150.00");

            verify(eventPublisher).publishEvent(any(RideEvents.BookingConfirmedEvent.class));
        }

        @Test
        @DisplayName("should transition ride to FULL when last seat is booked")
        void shouldTransitionRideToFullWhenLastSeatBooked() {
            ride.setAvailableSeats(1);
            var request = new CreateBookingRequest(1, null, null, null);

            when(rideRepository.findByIdWithLock(100L)).thenReturn(Optional.of(ride));
            when(bookingRepository.existsActiveByRideIdAndPassengerId(100L, 2L)).thenReturn(false);
            when(userRepository.findById(2L)).thenReturn(Optional.of(passenger));
            when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toBookingResponse(any())).thenReturn(mock(BookingResponse.class));

            bookingService.createBooking(100L, request, 2L);

            assertThat(ride.getAvailableSeats()).isEqualTo(0);
            assertThat(ride.getStatus()).isEqualTo(RideStatus.FULL);
        }

        @Test
        @DisplayName("should calculate contribution correctly for multiple seats")
        void shouldCalculateContributionForMultipleSeats() {
            var request = new CreateBookingRequest(2, null, null, null);

            when(rideRepository.findByIdWithLock(100L)).thenReturn(Optional.of(ride));
            when(bookingRepository.existsActiveByRideIdAndPassengerId(100L, 2L)).thenReturn(false);
            when(userRepository.findById(2L)).thenReturn(Optional.of(passenger));
            when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toBookingResponse(any())).thenReturn(mock(BookingResponse.class));

            bookingService.createBooking(100L, request, 2L);

            ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
            verify(bookingRepository).save(captor.capture());
            assertThat(captor.getValue().getContributionDue())
                    .isEqualByComparingTo("300.00");
        }

        @Test
        @DisplayName("should throw RideNotFoundException when ride does not exist")
        void shouldThrowWhenRideNotFound() {
            when(rideRepository.findByIdWithLock(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    bookingService.createBooking(999L,
                            new CreateBookingRequest(1, null, null, null), 2L))
                    .isInstanceOf(RideNotFoundException.class);
        }

        @Test
        @DisplayName("should throw InvalidRideStateException when ride is not ACTIVE")
        void shouldThrowWhenRideNotActive() {
            ride.setStatus(RideStatus.FULL);
            when(rideRepository.findByIdWithLock(100L)).thenReturn(Optional.of(ride));

            assertThatThrownBy(() ->
                    bookingService.createBooking(100L,
                            new CreateBookingRequest(1, null, null, null), 2L))
                    .isInstanceOf(InvalidRideStateException.class);
        }

        @Test
        @DisplayName("should throw RideFullException when requested seats exceed available")
        void shouldThrowWhenInsufficientSeats() {
            ride.setAvailableSeats(1);
            when(rideRepository.findByIdWithLock(100L)).thenReturn(Optional.of(ride));

            assertThatThrownBy(() ->
                    bookingService.createBooking(100L,
                            new CreateBookingRequest(2, null, null, null), 2L))
                    .isInstanceOf(RideFullException.class)
                    .hasMessageContaining("100");
        }

        @Test
        @DisplayName("should throw DuplicateBookingException when passenger already booked")
        void shouldThrowOnDuplicateBooking() {
            when(rideRepository.findByIdWithLock(100L)).thenReturn(Optional.of(ride));
            when(bookingRepository.existsActiveByRideIdAndPassengerId(100L, 2L)).thenReturn(true);

            assertThatThrownBy(() ->
                    bookingService.createBooking(100L,
                            new CreateBookingRequest(1, null, null, null), 2L))
                    .isInstanceOf(DuplicateBookingException.class);
        }

        @Test
        @DisplayName("should throw InvalidRideStateException when driver tries to book own ride")
        void shouldThrowWhenDriverBooksOwnRide() {
            when(rideRepository.findByIdWithLock(100L)).thenReturn(Optional.of(ride));
            when(bookingRepository.existsActiveByRideIdAndPassengerId(100L, 1L)).thenReturn(false);

            assertThatThrownBy(() ->
                    bookingService.createBooking(100L,
                            new CreateBookingRequest(1, null, null, null), 1L))
                    .isInstanceOf(InvalidRideStateException.class)
                    .hasMessageContaining("own ride");
        }
    }

    // ── cancelBooking ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cancelBooking()")
    class CancelBooking {

        private Booking confirmedBooking;

        @BeforeEach
        void setUpBooking() {
            confirmedBooking = Booking.builder()
                    .id(500L)
                    .ride(ride)
                    .passenger(passenger)
                    .seatsReserved(1)
                    .status(BookingStatus.CONFIRMED)
                    .contributionDue(new BigDecimal("150.00"))
                    .build();
        }

        @Test
        @DisplayName("should cancel booking and restore seats to ride")
        void shouldCancelAndRestoreSeats() {
            ride.setAvailableSeats(2);

            when(bookingRepository.findById(500L)).thenReturn(Optional.of(confirmedBooking));
            when(rideRepository.findByIdWithLock(100L)).thenReturn(Optional.of(ride));
            when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toBookingResponse(any())).thenReturn(mock(BookingResponse.class));

            bookingService.cancelBooking(500L, 2L, null);

            assertThat(ride.getAvailableSeats()).isEqualTo(3);
            assertThat(confirmedBooking.getStatus())
                    .isEqualTo(BookingStatus.CANCELLED_BY_PASSENGER);
        }

        @Test
        @DisplayName("should reopen FULL ride to ACTIVE when passenger cancels")
        void shouldReopenFullRideOnCancel() {
            ride.setStatus(RideStatus.FULL);
            ride.setAvailableSeats(0);

            when(bookingRepository.findById(500L)).thenReturn(Optional.of(confirmedBooking));
            when(rideRepository.findByIdWithLock(100L)).thenReturn(Optional.of(ride));
            when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toBookingResponse(any())).thenReturn(mock(BookingResponse.class));

            bookingService.cancelBooking(500L, 2L, null);

            assertThat(ride.getStatus()).isEqualTo(RideStatus.ACTIVE);
            assertThat(ride.getAvailableSeats()).isEqualTo(1);
        }

        @Test
        @DisplayName("should throw NotBookingOwnerException when wrong user cancels")
        void shouldThrowWhenNotOwner() {
            when(bookingRepository.findById(500L)).thenReturn(Optional.of(confirmedBooking));

            assertThatThrownBy(() -> bookingService.cancelBooking(500L, 99L, null))
                    .isInstanceOf(NotBookingOwnerException.class);
        }

        @Test
        @DisplayName("should throw InvalidRideStateException when booking already cancelled")
        void shouldThrowWhenAlreadyCancelled() {
            confirmedBooking.setStatus(BookingStatus.CANCELLED_BY_PASSENGER);
            when(bookingRepository.findById(500L)).thenReturn(Optional.of(confirmedBooking));

            assertThatThrownBy(() -> bookingService.cancelBooking(500L, 2L, null))
                    .isInstanceOf(InvalidRideStateException.class);
        }

        @Test
        @DisplayName("should throw InvalidRideStateException when ride has already departed")
        void shouldThrowWhenRideDeparted() {
            ride.setStatus(RideStatus.DEPARTED);
            when(bookingRepository.findById(500L)).thenReturn(Optional.of(confirmedBooking));

            assertThatThrownBy(() -> bookingService.cancelBooking(500L, 2L, null))
                    .isInstanceOf(InvalidRideStateException.class)
                    .hasMessageContaining("already started");
        }
    }

    // ── updatePayment ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updatePayment()")
    class UpdatePayment {

        private Booking booking;

        @BeforeEach
        void setUpBooking() {
            booking = Booking.builder()
                    .id(500L)
                    .ride(ride)
                    .passenger(passenger)
                    .seatsReserved(1)
                    .status(BookingStatus.CONFIRMED)
                    .contributionDue(new BigDecimal("150.00"))
                    .contributionPaid(BigDecimal.ZERO)
                    .paymentStatus(PaymentStatus.UNPAID)
                    .build();
        }

        @Test
        @DisplayName("should mark PAID when full amount is settled")
        void shouldMarkPaidOnFullPayment() {
            when(bookingRepository.findById(500L)).thenReturn(Optional.of(booking));
            when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toBookingResponse(any())).thenReturn(mock(BookingResponse.class));

            bookingService.updatePayment(500L,
                    new UpdatePaymentRequest(new BigDecimal("150.00"), PaymentMethod.CASH), 2L);

            assertThat(booking.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(booking.getContributionPaid()).isEqualByComparingTo("150.00");
        }

        @Test
        @DisplayName("should mark PARTIALLY_PAID on partial payment")
        void shouldMarkPartiallyPaid() {
            when(bookingRepository.findById(500L)).thenReturn(Optional.of(booking));
            when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toBookingResponse(any())).thenReturn(mock(BookingResponse.class));

            bookingService.updatePayment(500L,
                    new UpdatePaymentRequest(new BigDecimal("100.00"), PaymentMethod.CASH), 2L);

            assertThat(booking.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
            assertThat(booking.getContributionPaid()).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("should accumulate payments across multiple calls")
        void shouldAccumulatePayments() {
            booking.setContributionPaid(new BigDecimal("50.00"));
            booking.setPaymentStatus(PaymentStatus.PARTIALLY_PAID);

            when(bookingRepository.findById(500L)).thenReturn(Optional.of(booking));
            when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toBookingResponse(any())).thenReturn(mock(BookingResponse.class));

            bookingService.updatePayment(500L,
                    new UpdatePaymentRequest(new BigDecimal("100.00"), PaymentMethod.GCASH), 2L);

            assertThat(booking.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(booking.getContributionPaid()).isEqualByComparingTo("150.00");
        }
    }
}