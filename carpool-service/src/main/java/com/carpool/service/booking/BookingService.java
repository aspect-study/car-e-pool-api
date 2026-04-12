package com.carpool.service.booking;

import com.carpool.common.exception.*;
import com.carpool.domain.entity.Booking;
import com.carpool.domain.entity.Ride;
import com.carpool.domain.entity.RideWaypoint;
import com.carpool.domain.entity.User;
import com.carpool.domain.enums.BookingStatus;
import com.carpool.domain.enums.PaymentStatus;
import com.carpool.domain.enums.RideStatus;
import com.carpool.repository.BookingRepository;
import com.carpool.repository.RideRepository;
import com.carpool.repository.UserRepository;
import com.carpool.service.dto.request.CreateBookingRequest;
import com.carpool.service.dto.request.UpdatePaymentRequest;
import com.carpool.service.dto.response.BookingResponse;
import com.carpool.service.event.RideEvents;
import com.carpool.service.mapper.EntityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository    bookingRepository;
    private final RideRepository       rideRepository;
    private final UserRepository       userRepository;
    private final EntityMapper         mapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * ══════════════════════════════════════════════════════════════════════
     * CRITICAL SECTION — Seat reservation with pessimistic locking
     * ══════════════════════════════════════════════════════════════════════
     *
     * Race condition scenario:
     *   Passenger A and Passenger B both click "Book" for the last seat.
     *   Without locking: both read availableSeats=1, both pass the check,
     *   both insert a booking → availableSeats goes to -1.
     *
     * With SELECT FOR UPDATE:
     *   Passenger A gets the lock, books, commits, releases.
     *   Passenger B then gets the lock, reads availableSeats=0, throws RideFullException.
     *
     * The @Transactional here ensures:
     *   - Lock is held for the entire duration of the method
     *   - Lock is released on commit or rollback
     */
    @Transactional
    public BookingResponse createBooking(Long rideId, CreateBookingRequest request,
                                         Long passengerUserId) {

        // ── 1. Acquire pessimistic write lock on the ride row ────────────
        Ride ride = rideRepository.findByIdWithLock(rideId)
                .orElseThrow(() -> new RideNotFoundException(rideId));

        // ── 2. Validate ride state ───────────────────────────────────────
        if (ride.getStatus() != RideStatus.ACTIVE) {
            throw new InvalidRideStateException(
                    "Ride " + rideId + " is not accepting bookings. Status: " + ride.getStatus());
        }

        // ── 3. Check available seats ─────────────────────────────────────
        if (ride.getAvailableSeats() < request.seatsReserved()) {
            throw new RideFullException(rideId);
        }

        // ── 4. Prevent duplicate bookings ────────────────────────────────
        if (bookingRepository.existsByRideIdAndPassengerId(rideId, passengerUserId)) {
            throw new DuplicateBookingException(rideId);
        }

        // ── 5. Prevent passenger booking their own ride ──────────────────
        if (ride.getDriver().getId().equals(passengerUserId)) {
            throw new InvalidRideStateException("You cannot book your own ride.");
        }

        User passenger = userRepository.findById(passengerUserId)
                .orElseThrow(() -> new UserNotFoundException(passengerUserId));

        // ── 6. Resolve optional pickup/dropoff waypoints ─────────────────
        RideWaypoint pickupWaypoint  = resolveWaypoint(request.pickupWaypointId(),  ride, "pickup");
        RideWaypoint dropoffWaypoint = resolveWaypoint(request.dropoffWaypointId(), ride, "dropoff");

        // ── 7. Calculate contribution due ────────────────────────────────
        BigDecimal contributionDue = ride.getContributionAmount()
                .multiply(BigDecimal.valueOf(request.seatsReserved()));

        // ── 8. Insert booking ────────────────────────────────────────────
        Booking booking = Booking.builder()
                .ride(ride)
                .passenger(passenger)
                .seatsReserved(request.seatsReserved())
                .pickupWaypoint(pickupWaypoint)
                .dropoffWaypoint(dropoffWaypoint)
                .status(BookingStatus.CONFIRMED)
                .contributionDue(contributionDue)
                .build();

        // ── 9. Decrement available seats ─────────────────────────────────
        short updatedSeats = (short) (ride.getAvailableSeats() - request.seatsReserved());
        ride.setAvailableSeats(updatedSeats);

        // ── 10. Transition ride to FULL if no seats remain ───────────────
        if (updatedSeats == 0) {
            ride.setStatus(RideStatus.FULL);
            log.info("Ride {} is now FULL", rideId);
        }

        rideRepository.save(ride);
        Booking saved = bookingRepository.save(booking);

        log.info("Booking confirmed: id={} rideId={} passengerId={} seats={} remainingSeats={}",
                saved.getId(), rideId, passengerUserId,
                request.seatsReserved(), updatedSeats);

        // ── 11. Async: notify driver and passenger ───────────────────────
        // Fired after transaction commits to avoid notifying on rollback
        eventPublisher.publishEvent(new RideEvents.BookingConfirmedEvent(saved));

        return mapper.toBookingResponse(saved);
    }

    /**
     * Passenger cancels their own booking.
     * Restores seats to the ride — transitions FULL → ACTIVE if seats freed.
     */
    @Transactional
    public BookingResponse cancelBooking(Long bookingId, Long passengerUserId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (!booking.getPassenger().getId().equals(passengerUserId)) {
            throw new NotBookingOwnerException();
        }

        if (booking.getStatus() != BookingStatus.CONFIRMED
                && booking.getStatus() != BookingStatus.PENDING) {
            throw new InvalidRideStateException(
                    "Cannot cancel booking with status: " + booking.getStatus());
        }

        booking.setStatus(BookingStatus.CANCELLED_BY_PASSENGER);

        // Restore seats using pessimistic lock on the ride
        Ride ride = rideRepository.findByIdWithLock(booking.getRide().getId())
                .orElseThrow(() -> new RideNotFoundException(booking.getRide().getId()));

        short restoredSeats = (short) (ride.getAvailableSeats() + booking.getSeatsReserved());
        ride.setAvailableSeats(restoredSeats);

        // Reopen ride if it was FULL and seats are now available
        if (ride.getStatus() == RideStatus.FULL) {
            ride.setStatus(RideStatus.ACTIVE);
            log.info("Ride {} re-opened to ACTIVE after passenger cancellation", ride.getId());
        }

        rideRepository.save(ride);
        Booking saved = bookingRepository.save(booking);

        log.info("Booking cancelled by passenger: bookingId={} rideId={} passengerId={}",
                bookingId, ride.getId(), passengerUserId);

        eventPublisher.publishEvent(new RideEvents.BookingCancelledByPassengerEvent(saved));

        return mapper.toBookingResponse(saved);
    }

    /**
     * Record cash payment for a booking.
     * Updates contributionPaid and recalculates paymentStatus.
     */
    @Transactional
    public BookingResponse updatePayment(Long bookingId, UpdatePaymentRequest request,
                                          Long passengerUserId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (!booking.getPassenger().getId().equals(passengerUserId)) {
            throw new NotBookingOwnerException();
        }

        BigDecimal totalPaid = booking.getContributionPaid().add(request.amountPaid());
        booking.setContributionPaid(totalPaid);
        booking.setPaymentMethod(request.paymentMethod());

        // Recalculate payment status
        int comparison = totalPaid.compareTo(booking.getContributionDue());
        if (comparison >= 0) {
            booking.setPaymentStatus(PaymentStatus.PAID);
        } else if (totalPaid.compareTo(BigDecimal.ZERO) > 0) {
            booking.setPaymentStatus(PaymentStatus.PARTIALLY_PAID);
        }

        log.info("Payment updated: bookingId={} paid={} due={} status={}",
                bookingId, totalPaid, booking.getContributionDue(), booking.getPaymentStatus());

        return mapper.toBookingResponse(bookingRepository.save(booking));
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getMyBookings(Long passengerUserId) {
        return bookingRepository.findByPassengerIdAndStatusInOrderByCreatedAtDesc(
                        passengerUserId,
                        List.of(BookingStatus.CONFIRMED, BookingStatus.PENDING,
                                BookingStatus.COMPLETED, BookingStatus.CANCELLED_BY_PASSENGER,
                                BookingStatus.CANCELLED_BY_DRIVER))
                .stream()
                .map(mapper::toBookingResponse)
                .toList();
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Resolves an optional waypoint ID to a RideWaypoint entity,
     * validating that the waypoint belongs to the ride.
     */
    private RideWaypoint resolveWaypoint(Long waypointId, Ride ride, String label) {
        if (waypointId == null) return null;

        return ride.getWaypoints().stream()
                .filter(wp -> wp.getId().equals(waypointId))
                .findFirst()
                .orElseThrow(() -> new InvalidRideStateException(
                        "Waypoint " + waypointId + " does not belong to ride " + ride.getId()
                        + " (" + label + ")"));
    }
}
