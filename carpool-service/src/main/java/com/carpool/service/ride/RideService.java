package com.carpool.service.ride;

import com.carpool.common.exception.*;
import com.carpool.domain.entity.*;
import com.carpool.domain.enums.BookingStatus;
import com.carpool.domain.enums.RideDirection;
import com.carpool.domain.enums.RideStatus;
import com.carpool.repository.HubRepository;
import com.carpool.repository.BookingRepository;
import com.carpool.repository.RideRepository;
import com.carpool.repository.UserRepository;
import com.carpool.service.dto.request.CreateRideRequest;
import com.carpool.service.dto.request.UpdateRideStatusRequest;
import com.carpool.service.dto.request.WaypointRequest;
import com.carpool.service.dto.response.HubResponse;
import com.carpool.service.dto.response.RideResponse;
import com.carpool.service.event.RideEvents;
import com.carpool.service.mapper.EntityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RideService {

    private final RideRepository      rideRepository;
    private final HubRepository       hubRepository;
    private final UserRepository      userRepository;
    private final BookingRepository   bookingRepository;
    private final EntityMapper        mapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public RideResponse createRide(CreateRideRequest request, Long driverUserId) {
        User driver = userRepository.findById(driverUserId)
                .orElseThrow(() -> new UserNotFoundException(driverUserId));

        if (!driver.canDrive()) {
            throw new InsufficientRoleException("DRIVER");
        }

        // Validate: driver can only have one active ride at a time
        boolean hasActiveRide = !rideRepository
                .findByDriverIdAndStatusInOrderByDepartureTimeDesc(
                        driverUserId,
                        List.of(RideStatus.ACTIVE, RideStatus.FULL))
                .isEmpty();

        if (hasActiveRide) {
            throw new InvalidRideStateException(
                    "You already have an active ride. Cancel or complete it first before posting a new one.");
        }

        // Prevent posting a ride if user has an active booking as passenger
        boolean hasActiveBooking = !bookingRepository
                .findByPassengerIdAndStatusInOrderByCreatedAtDesc(
                        driverUserId,
                        List.of(BookingStatus.CONFIRMED, BookingStatus.PENDING))
                .isEmpty();

        if (hasActiveBooking) {
            throw new InvalidRideStateException(
                    "You have an active booking as a passenger. Cancel it first before posting a ride.");
        }

        Hub origin = hubRepository.findById(request.originHubId())
                .orElseThrow(() -> new HubNotFoundException(request.originHubId()));

        Hub destination = hubRepository.findById(request.destinationHubId())
                .orElseThrow(() -> new HubNotFoundException(request.destinationHubId()));

        if (origin.getId().equals(destination.getId())) {
            throw new SameHubException();
        }

        if (request.departureTime().isBefore(LocalDateTime.now())) {
            throw new DeparturePastException();
        }

        Ride ride = Ride.builder()
                .driver(driver)
                .originHub(origin)
                .destinationHub(destination)
                .direction(request.direction())
                .departureTime(request.departureTime())
                .totalSeats(request.totalSeats())
                .availableSeats(request.totalSeats())
                .contributionAmount(request.contributionAmount())
                .notes(request.notes())
                .status(RideStatus.DRAFT)
                .build();

        // Build waypoints if provided
        if (request.waypoints() != null && !request.waypoints().isEmpty()) {
            List<RideWaypoint> waypoints = buildWaypoints(request.waypoints(), ride);
            ride.setWaypoints(waypoints);
        }

        Ride saved = rideRepository.save(ride);
        log.info("Ride created: id={} driver={} {}→{} seats={} status=DRAFT",
                saved.getId(), driverUserId,
                origin.getCode(), destination.getCode(), saved.getTotalSeats());

        return mapper.toRideResponse(saved);
    }

    /**
     * Driver publishes, cancels, or completes a ride.
     * Allowed transitions:
     *   DRAFT      → ACTIVE    (publish)
     *   ACTIVE     → CANCELLED (driver cancels — notifies all passengers)
     *   FULL       → CANCELLED (driver cancels full ride)
     *   ACTIVE     → COMPLETED (driver marks done — notifies passengers)
     *   FULL       → COMPLETED
     */
    @Transactional
    public RideResponse updateRideStatus(Long rideId, UpdateRideStatusRequest request,
                                         Long requestingUserId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException(rideId));

        if (!ride.getDriver().getId().equals(requestingUserId)) {
            throw new NotRideOwnerException();
        }

        validateStatusTransition(ride.getStatus(), request.status());

        RideStatus previous = ride.getStatus();
        ride.setStatus(request.status());
        Ride saved = rideRepository.save(ride);

        // Publish events for state transitions that affect passengers
        if (request.status() == RideStatus.CANCELLED) {
            // Cancel all active bookings on this ride
            List<Booking> activeBookings = bookingRepository.findActiveBookingsForRide(rideId);
            activeBookings.forEach(b -> {
                b.setStatus(BookingStatus.CANCELLED_BY_DRIVER);
                bookingRepository.save(b);
            });
            log.info("Cancelled {} bookings for rideId={}", activeBookings.size(), rideId);
            eventPublisher.publishEvent(new RideEvents.RideCancelledEvent(saved));
        } else if (request.status() == RideStatus.COMPLETED) {
            log.info("Ride completed: id={} by driverId={}", rideId, requestingUserId);

            // Update all confirmed bookings to COMPLETED
            List<Booking> activeBookings = bookingRepository.findActiveBookingsForRide(rideId);
            activeBookings.forEach(b -> {
                b.setStatus(BookingStatus.COMPLETED);
                bookingRepository.save(b);
            });
            log.info("Completed {} bookings for rideId={}", activeBookings.size(), rideId);

            eventPublisher.publishEvent(new RideEvents.RideCompletedEvent(saved));
        }

        log.info("Ride status updated: id={} {} → {}", rideId, previous, request.status());
        return mapper.toRideResponse(saved);
    }

    @Transactional(readOnly = true)
    public RideResponse getRideById(Long rideId) {
        // Use eager waypoint fetch to avoid N+1 on detail endpoint
        Ride ride = rideRepository.findByIdWithWaypoints(rideId)
                .orElseThrow(() -> new RideNotFoundException(rideId));
        return mapper.toRideResponse(ride);
    }

    @Transactional(readOnly = true)
    public List<RideResponse> searchRides(Long fromHubId, Long toHubId) {
        // Validate hubs exist before querying — better error message
        if (!hubRepository.existsById(fromHubId)) throw new HubNotFoundException(fromHubId);
        if (!hubRepository.existsById(toHubId))   throw new HubNotFoundException(toHubId);
        if (fromHubId.equals(toHubId))             throw new SameHubException();

        return rideRepository.searchAvailable(fromHubId, toHubId, LocalDateTime.now())
                .stream()
                .map(mapper::toRideResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RideResponse> getMyRides(Long driverUserId) {
        return rideRepository.findByDriverIdAndStatusInOrderByDepartureTimeDesc(
                        driverUserId,
                        List.of(RideStatus.DRAFT, RideStatus.ACTIVE,
                                RideStatus.FULL, RideStatus.COMPLETED, RideStatus.CANCELLED))
                .stream()
                .map(mapper::toRideResponse)
                .toList();
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private List<RideWaypoint> buildWaypoints(List<WaypointRequest> requests, Ride ride) {
        List<RideWaypoint> waypoints = new ArrayList<>();
        for (WaypointRequest wp : requests) {
            Hub hub = hubRepository.findById(wp.hubId())
                    .orElseThrow(() -> new HubNotFoundException(wp.hubId()));

            waypoints.add(RideWaypoint.builder()
                    .ride(ride)
                    .hub(hub)
                    .sequenceOrder(wp.sequenceOrder())
                    .isPickup(wp.isPickup()  != null ? wp.isPickup()  : true)
                    .isDropoff(wp.isDropoff() != null ? wp.isDropoff() : true)
                    .build());
        }
        return waypoints;
    }

    private void validateStatusTransition(RideStatus current, RideStatus requested) {
        boolean valid = switch (requested) {
            case ACTIVE    -> current == RideStatus.DRAFT;
            case DEPARTED  -> current == RideStatus.ACTIVE || current == RideStatus.FULL;
            case CANCELLED -> current == RideStatus.ACTIVE || current == RideStatus.FULL
                    || current == RideStatus.DRAFT;
            case COMPLETED -> current == RideStatus.DEPARTED;
            default        -> false;
        };

        if (!valid) {
            throw new InvalidRideStateException(
                    "Cannot transition ride from " + current + " to " + requested);
        }
    }

    @Transactional(readOnly = true)
    public List<RideResponse> getRidesByDirection(RideDirection direction, Long excludeUserId,
                                                  LocalDateTime from, LocalDateTime to) {
        return rideRepository.findActiveByDirectionAndTimeRange(
                        direction,
                        List.of(RideStatus.ACTIVE),
                        from,
                        to)
                .stream()
                .filter(r -> !r.getDriver().getId().equals(excludeUserId))
                .map(mapper::toRideResponse)
                .toList();
    }

    /**
     * Expires rides whose departure time has passed but are still ACTIVE or FULL.
     * Called by scheduler every 30 minutes.
     */
    @Transactional
    public void expireStaleRides() {
        // 15-minute buffer — gives driver time to tap Start Ride
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(15);
        List<Ride> staleRides = rideRepository.findStaleActiveRides(cutoff);

        if (staleRides.isEmpty()) {
            log.debug("No stale rides to auto-depart");
            return;
        }

        for (Ride ride : staleRides) {
            ride.setStatus(RideStatus.DEPARTED);
            rideRepository.save(ride);
            log.info("Auto-departed stale ride: id={} departureTime={}",
                    ride.getId(), ride.getDepartureTime());
        }

        log.info("Auto-departed {} stale rides", staleRides.size());
    }

    /**
     * Auto-completes rides that have been DEPARTED for 2+ hours.
     * Transitions DEPARTED → COMPLETED and marks all bookings as COMPLETED.
     */
    @Transactional
    public void completeStaleRides() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(2);
        List<Ride> departedRides = rideRepository
                .findByStatusAndDepartureTimeBefore(RideStatus.DEPARTED, cutoff);

        if (departedRides.isEmpty()) {
            log.debug("No stale departed rides to auto-complete");
            return;
        }

        for (Ride ride : departedRides) {
            ride.setStatus(RideStatus.COMPLETED);
            rideRepository.save(ride);

            List<Booking> activeBookings = bookingRepository.findActiveBookingsForRide(ride.getId());
            activeBookings.forEach(b -> {
                b.setStatus(BookingStatus.COMPLETED);
                bookingRepository.save(b);
            });

            log.info("Auto-completed stale ride: id={} departureTime={}",
                    ride.getId(), ride.getDepartureTime());
        }

        log.info("Auto-completed {} stale departed rides", departedRides.size());
    }

    @Transactional(readOnly = true)
    public HubResponse getHubById(Long hubId) {
        return hubRepository.findById(hubId)
                .map(mapper::toHubResponse)
                .orElseThrow(() -> new HubNotFoundException(hubId));
    }
}
