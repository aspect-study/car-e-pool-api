package com.carpool.web.integration;

import com.carpool.common.exception.DuplicateBookingException;
import com.carpool.common.exception.RideFullException;
import com.carpool.domain.entity.*;
import com.carpool.domain.enums.*;
import com.carpool.repository.*;
import com.carpool.service.booking.BookingService;
import com.carpool.service.dto.request.CreateBookingRequest;
import com.carpool.service.dto.response.BookingResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for BookingService against real MySQL.
 *
 * Most critical test: concurrent booking simulation.
 * Unit tests with mocks cannot test actual DB-level pessimistic locking.
 * This is the test that proves the lock actually works.
 */
@DisplayName("Booking Integration")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BookingIntegrationTest extends BaseIntegrationTest {

    @Autowired private BookingService    bookingService;
    @Autowired private UserRepository    userRepository;
    @Autowired private HubRepository     hubRepository;
    @Autowired private RideRepository    rideRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private JdbcTemplate      jdbcTemplate;

    private User   driver;
    private User   passenger1;
    private User   passenger2;
    private User   passenger3;
    private Ride   ride;

    @BeforeEach
    void setUp() {
        // Create test users with unique telegram IDs per test run
        long seed = System.currentTimeMillis();

        driver = userRepository.save(User.builder()
                .telegramId(seed + 1).fullName("Driver")
                .role(UserRole.DRIVER).status(UserStatus.ACTIVE).build());

        passenger1 = userRepository.save(User.builder()
                .telegramId(seed + 2).fullName("Passenger 1")
                .role(UserRole.PASSENGER).status(UserStatus.ACTIVE).build());

        passenger2 = userRepository.save(User.builder()
                .telegramId(seed + 3).fullName("Passenger 2")
                .role(UserRole.PASSENGER).status(UserStatus.ACTIVE).build());

        passenger3 = userRepository.save(User.builder()
                .telegramId(seed + 4).fullName("Passenger 3")
                .role(UserRole.PASSENGER).status(UserStatus.ACTIVE).build());

        Hub origin      = hubRepository.findByCode("AYALA_MRT").orElseThrow();
        Hub destination = hubRepository.findByCode("BGC_HIGH_STREET").orElseThrow();

        // Ride with only 1 seat — critical for concurrency test
        ride = rideRepository.save(Ride.builder()
                .driver(driver)
                .originHub(origin)
                .destinationHub(destination)
                .direction(RideDirection.HOME_TO_WORK)
                .departureTime(LocalDateTime.now().plusHours(3))
                .totalSeats(1)
                .availableSeats(1)
                .contributionAmount(new BigDecimal("150.00"))
                .status(RideStatus.ACTIVE)
                .build());
    }

    @AfterEach
    void tearDown() {
        if (driver == null) return;
        // Delete in FK order: bookings → rides → users
        jdbcTemplate.update(
                "DELETE b FROM bookings b JOIN rides r ON b.ride_id = r.id WHERE r.driver_id = ?",
                driver.getId());
        jdbcTemplate.update("DELETE FROM rides WHERE driver_id = ?", driver.getId());
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?, ?, ?)",
                driver.getId(), passenger1.getId(), passenger2.getId(), passenger3.getId());
    }

    @Test
    @DisplayName("should book seat and persist to real DB")
    void shouldBookAndPersistToDb() {
        // Act
        BookingResponse response = bookingService.createBooking(
                ride.getId(),
                new CreateBookingRequest(1, null, null, null),
                passenger1.getId());

        // Assert — booking persisted
        assertThat(response.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(response.seatsReserved()).isEqualTo(1);
        assertThat(response.contributionDue()).isEqualByComparingTo("150.00");

        // Verify ride seats decremented in DB
        Ride updated = rideRepository.findById(ride.getId()).orElseThrow();
        assertThat(updated.getAvailableSeats()).isEqualTo(0);
        assertThat(updated.getStatus()).isEqualTo(RideStatus.FULL);
    }

    @Test
    @DisplayName("should throw DuplicateBookingException when same passenger books twice")
    void shouldThrowOnDuplicateBooking() {
        // Need a ride with 2+ seats so it stays ACTIVE after first booking
        Hub origin      = hubRepository.findByCode("AYALA_MRT").orElseThrow();
        Hub destination = hubRepository.findByCode("BGC_HIGH_STREET").orElseThrow();

        Ride multiSeatRide = rideRepository.save(Ride.builder()
                .driver(driver)
                .originHub(origin)
                .destinationHub(destination)
                .direction(RideDirection.HOME_TO_WORK)
                .departureTime(LocalDateTime.now().plusHours(3))
                .totalSeats(3)        // 3 seats — stays ACTIVE after first booking
                .availableSeats(3)
                .contributionAmount(new BigDecimal("150.00"))
                .status(RideStatus.ACTIVE)
                .build());

        // First booking — succeeds, ride stays ACTIVE (2 seats remain)
        bookingService.createBooking(
                multiSeatRide.getId(),
                new CreateBookingRequest(1, null, null, null),
                passenger1.getId());

        // Second booking by same passenger — must throw DuplicateBookingException
        assertThatThrownBy(() -> bookingService.createBooking(
                multiSeatRide.getId(),
                new CreateBookingRequest(1, null, null, null),
                passenger1.getId()))
                .isInstanceOf(DuplicateBookingException.class);
    }

    /**
     * ══════════════════════════════════════════════════════════════════════
     * CONCURRENCY TEST — The most important integration test
     * ══════════════════════════════════════════════════════════════════════
     *
     * Simulates 3 passengers clicking "Book" simultaneously for 1 remaining seat.
     *
     * Expected result:
     *   - Exactly 1 booking succeeds (the one that got the DB lock first)
     *   - Exactly 2 bookings fail with RideFullException
     *   - available_seats in DB = 0 (never goes negative)
     *
     * This test CANNOT be replicated with unit tests + mocks.
     * Only real DB pessimistic locking can guarantee this behavior.
     */
    @Test
    @DisplayName("should allow only 1 booking when 3 passengers book simultaneously (1 seat)")
    void shouldHandleConcurrentBookingWithPessimisticLock() throws InterruptedException {
        int threadCount = 3;
        List<Long> passengerIds = List.of(
                passenger1.getId(), passenger2.getId(), passenger3.getId());

        CountDownLatch startLatch  = new CountDownLatch(1);  // all threads start together
        CountDownLatch doneLatch   = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<String>  errors       = new CopyOnWriteArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final Long passengerId = passengerIds.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await(); // wait for all threads to be ready
                    bookingService.createBooking(
                            ride.getId(),
                            new CreateBookingRequest(1, null, null, null),
                            passengerId);
                    successCount.incrementAndGet();
                } catch (RideFullException | com.carpool.common.exception.InvalidRideStateException e) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    errors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // release all threads simultaneously
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert — no unexpected errors
        assertThat(errors)
                .as("Unexpected errors during concurrent booking: " + errors)
                .isEmpty();

        // Assert — exactly 1 succeeded
        assertThat(successCount.get())
                .as("Expected exactly 1 successful booking")
                .isEqualTo(1);

        // Assert — exactly 2 failed
        assertThat(failureCount.get())
                .as("Expected exactly 2 failed bookings")
                .isEqualTo(2);

        // Assert — DB state is correct (never went negative)
        Ride finalRide = rideRepository.findById(ride.getId()).orElseThrow();
        assertThat(finalRide.getAvailableSeats())
                .as("Available seats should be 0, never negative")
                .isEqualTo(0);
        assertThat(finalRide.getStatus())
                .as("Ride should be FULL after last seat taken")
                .isEqualTo(RideStatus.FULL);

        // Assert — only 1 booking record in DB
        List<com.carpool.domain.entity.Booking> bookings =
                bookingRepository.findActiveBookingsForRide(ride.getId());
        assertThat(bookings)
                .as("Only 1 booking should exist in DB")
                .hasSize(1);
    }
}