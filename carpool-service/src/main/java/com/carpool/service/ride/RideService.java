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
import com.carpool.service.rating.RatingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import com.carpool.common.response.PagedResponse;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.math.BigDecimal;
import java.util.Map;

import static java.util.stream.Collectors.toList;

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
    private final RatingService       ratingService;

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

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
        boolean hasActiveBooking = bookingRepository
                .countByPassengerIdAndStatusIn(
                        driverUserId,
                        List.of(BookingStatus.CONFIRMED, BookingStatus.PENDING)) > 0;

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

        if (request.departureTime().isBefore(LocalDateTime.now(MANILA))) {
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
     * Driver publishes, departs, cancels, or completes a ride.
     * Allowed transitions:
     *   DRAFT      → ACTIVE    (publish — visible to passengers)
     *   ACTIVE     → DEPARTED  (start ride)
     *   FULL       → DEPARTED  (start full ride)
     *   ACTIVE     → CANCELLED (driver cancels — notifies all passengers)
     *   FULL       → CANCELLED (driver cancels full ride)
     *   DEPARTED   → COMPLETED (mark ride done — notifies passengers)
     */
    @Transactional
    public RideResponse updateRideStatus(Long rideId, UpdateRideStatusRequest request,
                                         Long requestingUserId) {
        return updateRideStatus(rideId, request, requestingUserId, null);
    }

    @Transactional
    public RideResponse updateRideStatus(Long rideId, UpdateRideStatusRequest request,
                                         Long requestingUserId, String cancellationReason) {
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
            eventPublisher.publishEvent(new RideEvents.RideCancelledEvent(saved, cancellationReason));
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
        } else if (request.status() == RideStatus.DEPARTED) {
            log.info("Ride departed: id={} by driverId={}", rideId, requestingUserId);
            eventPublisher.publishEvent(new RideEvents.RideDepartedEvent(saved));
        } else if (request.status() == RideStatus.ACTIVE
                && previous == RideStatus.DRAFT) {
            // Ride just published — notify group
            eventPublisher.publishEvent(new RideEvents.RidePostedEvent(saved));
            log.info("Ride posted event published: rideId={}", saved.getId());
        }

        log.info("Ride status updated: id={} {} → {}", rideId, previous, request.status());
        return mapper.toRideResponse(saved);
    }

    @Transactional(readOnly = true)
    public RideResponse getRideById(Long rideId) {
        // Use eager waypoint fetch to avoid N+1 on detail endpoint
        Ride ride = rideRepository.findByIdWithWaypoints(rideId)
                .orElseThrow(() -> new RideNotFoundException(rideId));
        return withRating(mapper.toRideResponse(ride),
                ratingService.getAverageRatingsByDriverIds(List.of(ride.getDriver().getId()))
                        .get(ride.getDriver().getId()));
    }

    @Transactional(readOnly = true)
    public PagedResponse<RideResponse> searchRides(Long fromHubId, Long toHubId,
                                                   Pageable pageable) {
        if (!hubRepository.existsById(fromHubId)) throw new HubNotFoundException(fromHubId);
        if (!hubRepository.existsById(toHubId))   throw new HubNotFoundException(toHubId);
        if (fromHubId.equals(toHubId))             throw new SameHubException();

        Page<RideResponse> page = rideRepository
                .searchAvailablePaged(fromHubId, toHubId, LocalDateTime.now(MANILA), pageable)
                .map(mapper::toRideResponse);

        return PagedResponse.of(page);
    }

    @Transactional(readOnly = true)
    public PagedResponse<RideResponse> getMyRides(Long driverUserId, Pageable pageable) {
        Page<RideResponse> page = rideRepository
                .findByDriverIdAndStatusInOrderByDepartureTimeDesc(
                        driverUserId,
                        List.of(RideStatus.DRAFT, RideStatus.ACTIVE,
                                RideStatus.FULL, RideStatus.COMPLETED, RideStatus.CANCELLED),
                        pageable)
                .map(mapper::toRideResponse);

        return PagedResponse.of(page);
    }

    /**
     * Unpaged version — used internally by bot and schedulers.
     * Not exposed via REST.
     */
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
            String message = switch (current) {
                case DEPARTED  -> "This ride has already started and cannot be changed.";
                case COMPLETED -> "This ride has already been completed.";
                case CANCELLED -> "This ride has already been cancelled.";
                case DRAFT     -> "Please publish your ride first before changing its status.";
                default        -> "This status change is not allowed at this stage.";
            };
            throw new InvalidRideStateException(message);
        }
    }

    @Transactional(readOnly = true)
    public PagedResponse<RideResponse> getRidesByDirection(RideDirection direction,
                                                           Long excludeUserId,
                                                           LocalDateTime from,
                                                           LocalDateTime to,
                                                           BigDecimal maxPrice,
                                                           Integer minSeats,
                                                           String sortBy,
                                                           Pageable pageable) {
        // Filter in-memory — sort/filter logic too complex for DB query
        // Acceptable at current scale (20-50 active rides max at any time)
        List<RideResponse> filtered = rideRepository
                .findActiveByDirectionAndTimeRange(
                        direction, List.of(RideStatus.ACTIVE), from, to)
                .stream()
                .filter(r -> !r.getDriver().getId().equals(excludeUserId))
                .filter(r -> maxPrice == null
                        || r.getContributionAmount().compareTo(maxPrice) <= 0)
                .filter(r -> minSeats == null
                        || r.getAvailableSeats() >= minSeats)
                .map(mapper::toRideResponse)
                .sorted(getComparator(sortBy))
                .toList();

        List<RideResponse> enriched = withRatings(filtered);

        // Manual slice for pagination
        int pageNum  = pageable.getPageNumber();
        int pageSize = pageable.getPageSize();
        int fromIdx    = Math.min(pageNum * pageSize, enriched.size());
        int toIdx      = Math.min(fromIdx + pageSize, enriched.size());
        List<RideResponse> content = enriched.subList(fromIdx, toIdx);
        boolean isLast     = toIdx >= enriched.size();
        int totalPages = (int) Math.ceil((double) enriched.size() / pageSize);

        return new PagedResponse<>(content, pageNum, pageSize,
                enriched.size(), totalPages, isLast);
    }

    /**
     * Unpaged version — used internally by bot.
     * Not exposed via REST.
     */
    @Transactional(readOnly = true)
    public List<RideResponse> getRidesByDirection(RideDirection direction, Long excludeUserId,
                                                  LocalDateTime from, LocalDateTime to,
                                                  BigDecimal maxPrice, Integer minSeats,
                                                  String sortBy) {
        return withRatings(
                rideRepository.findActiveByDirectionAndTimeRange(
                                direction, List.of(RideStatus.ACTIVE), from, to)
                        .stream()
                        .filter(r -> !r.getDriver().getId().equals(excludeUserId))
                        .filter(r -> maxPrice == null
                                || r.getContributionAmount().compareTo(maxPrice) <= 0)
                        .filter(r -> minSeats == null
                                || r.getAvailableSeats() >= minSeats)
                        .map(mapper::toRideResponse)
                        .sorted(getComparator(sortBy))
                        .toList());
    }

    private Comparator<RideResponse> getComparator(String sortBy) {
        if (sortBy == null) return Comparator.comparing(RideResponse::departureTime);
        return switch (sortBy) {
            case "CHEAPEST"    -> Comparator.comparing(RideResponse::contributionAmount);
            case "MOST_SEATS"  -> Comparator.comparing(RideResponse::availableSeats).reversed();
            default            -> Comparator.comparing(RideResponse::departureTime);
        };
    }

    /**
     * Expires rides whose departure time has passed but are still ACTIVE or FULL.
     * Called by scheduler every 30 minutes.
     */
    @Transactional
    public void expireStaleRides() {
        // 15-minute buffer — gives driver time to tap Start Ride
        LocalDateTime cutoff = LocalDateTime.now(MANILA).minusMinutes(15);
        List<Ride> staleRides = rideRepository.findStaleActiveRides(cutoff);

        if (staleRides.isEmpty()) {
            log.debug("No stale rides to auto-depart");
            return;
        }

        staleRides.forEach(ride -> {
            ride.setStatus(RideStatus.DEPARTED);
            log.info("Auto-departed stale ride: id={} departureTime={}",
                    ride.getId(), ride.getDepartureTime());
        });

        rideRepository.saveAll(staleRides);
        // Notify passengers — same behavior as manual Start Ride
        staleRides.forEach(ride ->
                eventPublisher.publishEvent(new RideEvents.RideDepartedEvent(ride)));
        log.info("Auto-departed {} stale rides", staleRides.size());
    }

    /**
     * Auto-completes rides that have been DEPARTED for 2+ hours.
     * Transitions DEPARTED → COMPLETED and marks all bookings as COMPLETED.
     */
    @Transactional
    public void completeStaleRides() {
        LocalDateTime cutoff = LocalDateTime.now(MANILA).minusHours(2);
        List<Ride> departedRides = rideRepository
                .findByStatusAndDepartureTimeBefore(RideStatus.DEPARTED, cutoff);

        if (departedRides.isEmpty()) {
            log.debug("No stale departed rides to auto-complete");
            return;
        }

        List<Booking> allBookings = new ArrayList<>();

        for (Ride ride : departedRides) {
            ride.setStatus(RideStatus.COMPLETED);

            List<Booking> activeBookings = bookingRepository.findActiveBookingsForRide(ride.getId());
            activeBookings.forEach(b -> b.setStatus(BookingStatus.COMPLETED));
            allBookings.addAll(activeBookings);

            log.info("Auto-completed stale ride: id={} departureTime={}",
                    ride.getId(), ride.getDepartureTime());
        }

        rideRepository.saveAll(departedRides);
        bookingRepository.saveAll(allBookings);
        // Notify passengers + trigger rating prompts — same as manual Complete Ride
        departedRides.forEach(ride ->
                eventPublisher.publishEvent(new RideEvents.RideCompletedEvent(ride)));
        log.info("Auto-completed {} stale departed rides", departedRides.size());
    }

    @Transactional(readOnly = true)
    public HubResponse getHubById(Long hubId) {
        return hubRepository.findById(hubId)
                .map(mapper::toHubResponse)
                .orElseThrow(() -> new HubNotFoundException(hubId));
    }

    /**
     * Returns last 3 completed or cancelled rides for repost feature.
     * DB-level limit — safe for large datasets.
     */
    @Transactional(readOnly = true)
    public List<RideResponse> getRecentRidesForRepost(Long driverUserId) {
        return rideRepository.findTop3CompletedOrCancelledByDriverId(driverUserId)
                .stream()
                .map(mapper::toRideResponse)
                .toList();
    }

    @Transactional
    public RideResponse reannounceRide(Long rideId, Long requestingUserId) {
        Ride ride = rideRepository.findByIdWithLock(rideId)
                .orElseThrow(() -> new RideNotFoundException(rideId));

        if (!ride.getDriver().getId().equals(requestingUserId)) {
            throw new NotRideOwnerException();
        }

        if (ride.getStatus() != RideStatus.ACTIVE && ride.getStatus() != RideStatus.FULL) {
            throw new InvalidRideStateException(
                    "Only ACTIVE or FULL rides can be re-announced.");
        }

        if (ride.getAnnounceCount() == null || ride.getAnnounceCount() >= 3) {
            throw new InvalidRideStateException(
                    "This ride has already been announced 3 times. Maximum reached.");
        }

        ride.setAnnounceCount(ride.getAnnounceCount() + 1);
        Ride saved = rideRepository.save(ride);

        // Reuse the same RidePostedEvent — GroupNotificationService handles the message
        eventPublisher.publishEvent(new RideEvents.RidePostedEvent(saved));

        log.info("Ride re-announced: rideId={} announceCount={} driverId={}",
                saved.getId(), saved.getAnnounceCount(), requestingUserId);

        return mapper.toRideResponse(saved);
    }

    /**
     * Returns the driver's current active ride (ACTIVE, FULL, or DEPARTED).
     * Returns null if no active ride exists — does not throw.
     * Used by REST clients as a lightweight check before loading full ride list.
     */
    @Transactional(readOnly = true)
    public RideResponse getActiveRide(Long driverUserId) {
        return rideRepository.findActiveRideByDriverId(driverUserId)
                .map(mapper::toRideResponse)
                .orElse(null);
    }

    /**
     * Enriches a list of RideResponses with driver avg ratings in a single batch query.
     * Only called on search methods — not on create/update/status flows.
     */
    private List<RideResponse> withRatings(List<RideResponse> rides) {
        if (rides.isEmpty()) return rides;
        List<Long> driverIds = rides.stream()
                .map(r -> r.driver().id())
                .distinct()
                .toList();
        Map<Long, Double> avgMap = ratingService.getAverageRatingsByDriverIds(driverIds);
        return rides.stream()
                .map(r -> withRating(r, avgMap.get(r.driver().id())))
                .toList();
    }

    private RideResponse withRating(RideResponse r, Double avg) {
        return new RideResponse(
                r.id(), r.driver(), r.originHub(), r.destinationHub(),
                r.direction(), r.departureTime(), r.totalSeats(),
                r.availableSeats(), r.contributionAmount(), r.notes(),
                r.status(), r.waypoints(), r.createdAt(),
                r.announceCount(), avg);
    }

}
