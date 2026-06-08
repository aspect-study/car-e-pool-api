package com.carpool.service.notification;

import com.carpool.common.util.HtmlEscapeUtil;
import com.carpool.domain.entity.Booking;
import com.carpool.domain.entity.Notification;
import com.carpool.domain.entity.Ride;
import com.carpool.domain.entity.User;
import com.carpool.domain.enums.BookingStatus;
import com.carpool.domain.enums.NotificationStatus;
import com.carpool.domain.enums.NotificationTypes;
import com.carpool.domain.enums.RideDirection;
import com.carpool.repository.BookingRepository;
import com.carpool.repository.NotificationRepository;
import com.carpool.repository.RideRepository;
import com.carpool.service.event.RideEvents;
import com.carpool.service.port.TelegramNotificationPort;
import com.carpool.service.profile.ProfileService;
import com.carpool.service.rating.RatingService;
import com.carpool.service.util.ProfileBadgeBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Listens to domain events and dispatches Telegram notifications.
 *
 * Uses HTML parse mode for all messages — safer than Markdown for
 * names and special characters.
 *
 * Flow per notification:
 *   1. INSERT notification record (status=PENDING)
 *   2. Call Telegram sendMessage API
 *   3. UPDATE notification record (status=SENT or FAILED)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final BookingRepository      bookingRepository;
    private final RideRepository         rideRepository;
    private final ProfileService         profileService;
    private final RatingService          ratingService;

    @Autowired @Lazy
    private TelegramNotificationPort telegramPort;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a")
                    .withZone(ZoneId.of("Asia/Manila"));

    // ── Event Listeners ───────────────────────────────────────────────────────

    // ── Event Listeners ───────────────────────────────────────────────────────
    //
    // Threading model for all listeners:
    //   @Async             — runs in a separate thread from the async executor pool,
    //                        decoupled from the caller's thread and transaction.
    //   @TransactionalEventListener(AFTER_COMMIT)
    //                      — only fires after the caller's transaction successfully
    //                        commits. Prevents notifications being sent for rolled-back operations.
    //   @Transactional(REQUIRES_NEW)
    //                      — opens a fresh independent transaction in the async thread
    //                        so notification DB writes (INSERT/UPDATE on notifications table)
    //                        are independent of any outer transaction.
    //
    // Order of Spring AOP proxy application: @Async is processed first (new thread),
    // then @Transactional creates a new transaction in that thread. This is correct
    // and intentional — do not reorder or remove either annotation.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBookingConfirmed(RideEvents.BookingConfirmedEvent event) {
        Booking booking = bookingRepository.findByIdWithDetails(event.booking().getId())
                .orElse(null);
        if (booking == null) {
            log.error("Booking not found for notification: id={}", event.booking().getId());
            return;
        }

        Ride ride = booking.getRide();
        User pax  = booking.getPassenger();

        // Notify passenger only — driver already knows (they just accepted)
        sendAndRecord(pax, NotificationTypes.BOOKING_CONFIRMED,
                buildPassengerConfirmationMessage(booking),
                Map.of("bookingId", booking.getId(),
                        "rideId",    ride.getId(),
                        "contribution", booking.getContributionDue()),
                List.of(List.of(
                        new TelegramNotificationPort.InlineButton(
                                "📋 View My Booking", "VIEW_BOOKING:" + booking.getId())
                )));

        log.info("Booking confirmed notification sent to passenger: bookingId={} passengerId={}",
                booking.getId(), pax.getId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBookingCancelledByPassenger(RideEvents.BookingCancelledByPassengerEvent event) {
        Booking booking = bookingRepository.findByIdWithDetails(event.booking().getId())
                .orElse(null);
        if (booking == null) return;

        User driver = booking.getRide().getDriver();
        User pax    = booking.getPassenger();

        String paxHandle = pax.getTelegramHandle() != null
                ? " (@" + HtmlEscapeUtil.escape(pax.getTelegramHandle()) + ")"
                : "";

        String msg = String.format(
                "🚫 <b>Booking Cancelled</b>\n" +
                        directionLabel(booking.getRide().getDirection()) + "\n\n" +
                        "👤 <b>%s</b>%s\n" +
                        "📍 %s → %s\n" +
                        "🕐 %s\n" +
                        "🪑 %d seat(s) | ⛽ ₱%.2f share\n" +
                        "%s\n\n" +
                        "Seat(s) have been freed up.",
                HtmlEscapeUtil.escape(pax.getFullName()),
                paxHandle,
                HtmlEscapeUtil.escape(booking.getRide().getOriginHub().getName()),
                HtmlEscapeUtil.escape(booking.getRide().getDestinationHub().getName()),
                TIME_FMT.format(booking.getRide().getDepartureTime()
                        .atZone(ZoneId.of("Asia/Manila"))),
                booking.getSeatsReserved(),
                booking.getContributionDue(),
                booking.getCancellationReason() != null
                        ? "📝 Reason: <i>" + HtmlEscapeUtil.escape(booking.getCancellationReason()) + "</i>"
                        : "");

        sendAndRecord(driver, NotificationTypes.BOOKING_CANCELLED_BY_PASSENGER, msg,
                Map.of("bookingId", booking.getId(), "passengerName", pax.getFullName()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRideCancelled(RideEvents.RideCancelledEvent event) {
        // Use only the booking IDs that were active at the moment of cancellation.
        // Re-querying by CANCELLED_BY_DRIVER status would also include passengers
        // previously removed by the driver, who must not receive this notification.
        // findCancelledByDriverBookingsForRide uses JOIN FETCH (no N+1); filter by
        // the captured IDs to exclude passengers removed before this cancellation.
        Set<Long> affectedIds = new HashSet<>(event.affectedBookingIds());
        List<Booking> cancelledBookings = affectedIds.isEmpty()
                ? List.of()
                : bookingRepository.findCancelledByDriverBookingsForRide(event.ride().getId())
                        .stream()
                        .filter(b -> affectedIds.contains(b.getId()))
                        .toList();

        if (cancelledBookings.isEmpty()) {
            log.info("Ride {} cancelled with no affected passengers", event.ride().getId());
            return;
        }

        Ride ride = cancelledBookings.get(0).getRide();
        User driver = ride.getDriver();
        String driverHandle = driver.getTelegramHandle() != null
                ? " (@" + HtmlEscapeUtil.escape(driver.getTelegramHandle()) + ")"
                : "";

        String msg = String.format(
                "⚠️ <b>Ride Cancelled</b>\n" +
                        directionLabel(ride.getDirection()) + "\n\n" +
                        "👤 Driver: <b>%s</b>%s\n" +
                        "📍 %s → %s\n" +
                        "🕐 %s\n" +
                        "%s\n\n" +
                        "Please look for another carpool or arrange alternative transport.",
                HtmlEscapeUtil.escape(driver.getFullName()),
                driverHandle,
                HtmlEscapeUtil.escape(ride.getOriginHub().getName()),
                HtmlEscapeUtil.escape(ride.getDestinationHub().getName()),
                TIME_FMT.format(ride.getDepartureTime().atZone(ZoneId.of("Asia/Manila"))),
                event.reason() != null
                        ? "📝 Reason: <i>" + HtmlEscapeUtil.escape(event.reason()) + "</i>"
                        : "");

        for (Booking booking : cancelledBookings) {
            String pickup = booking.getPickupWaypoint() != null
                    ? booking.getPickupWaypoint().getHub().getName()
                    : ride.getOriginHub().getName();

            String personalMsg = msg + String.format(
                    "\n\n📋 <b>Your Booking</b>\n" +
                            "🚏 Pickup: <b>%s</b>\n" +
                            "🪑 Seats: %d | ⛽ ₱%.2f share",
                    HtmlEscapeUtil.escape(pickup),
                    booking.getSeatsReserved(),
                    booking.getContributionDue());

            sendAndRecord(booking.getPassenger(), NotificationTypes.RIDE_CANCELLED, personalMsg,
                    Map.of("rideId", ride.getId(), "bookingId", booking.getId()));
        }

        log.info("Ride cancellation notifications sent to {} passengers for rideId={}",
                cancelledBookings.size(), ride.getId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRideCompleted(RideEvents.RideCompletedEvent event) {
        List<Booking> activeBookings = bookingRepository
                .findCompletedBookingsForRide(event.ride().getId());

        if (activeBookings.isEmpty()) {
            log.info("Ride {} completed with no bookings to notify", event.ride().getId());
            return;
        }

        Ride ride = activeBookings.get(0).getRide();

        String msg = String.format(
                "✅ <b>Ride Completed!</b>\n" +
                        directionLabel(ride.getDirection()) + "\n\n" +
                        "📍 %s → %s\n" +
                        "🕐 %s\n\n" +
                        "Thank you for carpooling! Please settle your gas share with the driver.",
                HtmlEscapeUtil.escape(ride.getOriginHub().getName()),
                HtmlEscapeUtil.escape(ride.getDestinationHub().getName()),
                TIME_FMT.format(ride.getDepartureTime().atZone(ZoneId.of("Asia/Manila"))));

        for (Booking booking : activeBookings) {
            sendAndRecord(booking.getPassenger(), NotificationTypes.RIDE_COMPLETED, msg,
                    Map.of("rideId", ride.getId(),
                            "contributionDue", booking.getContributionDue()));
        }

        // ── Prompt both driver and passengers to rate each other ──────────────
        for (Booking booking : activeBookings) {
            // Prompt passenger to rate driver
            String passengerRatingMsg = String.format(
                    "⭐ <b>Rate Your Ride!</b>\n" +
                            directionLabel(ride.getDirection()) + "\n\n" +
                            "How was your experience with driver <b>%s</b>?\n\n" +
                            "Tap below to leave a rating:",
                    HtmlEscapeUtil.escape(ride.getDriver().getFullName()));

            try {
                telegramPort.sendMessageWithKeyboard(
                        booking.getPassenger().getTelegramId(),
                        passengerRatingMsg,
                        List.of(List.of(
                                new TelegramNotificationPort.InlineButton("⭐ Rate Now", "RATE_RIDE:" + ride.getId())
                        )));
            } catch (Exception e) {
                log.error("Failed to send rating prompt to passenger: rideId={} passengerId={} error={}",
                        ride.getId(), booking.getPassenger().getId(), e.getMessage());
            }

            // Prompt driver to rate passenger
            String driverRatingMsg = String.format(
                    "⭐ <b>Rate Your Passenger!</b>\n" +
                            directionLabel(ride.getDirection()) + "\n\n" +
                            "How was <b>%s</b> as a passenger?\n\n" +
                            "Tap below to leave a rating:",
                    HtmlEscapeUtil.escape(booking.getPassenger().getFullName()));

            try {
                telegramPort.sendMessageWithKeyboard(
                        ride.getDriver().getTelegramId(),
                        driverRatingMsg,
                        List.of(List.of(
                                new TelegramNotificationPort.InlineButton("⭐ Rate Now", "RATE_RIDE:" + ride.getId())
                        )));
            } catch (Exception e) {
                log.error("Failed to send rating prompt to driver: rideId={} driverId={} error={}",
                        ride.getId(), ride.getDriver().getId(), e.getMessage());
            }
        }
        log.info("Rating prompts sent: rideId={} passengers={}",
                ride.getId(), activeBookings.size());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRideDeparted(RideEvents.RideDepartedEvent event) {
        List<Booking> confirmedBookings = bookingRepository
                .findByRideIdAndStatusIn(
                        event.ride().getId(),
                        List.of(BookingStatus.CONFIRMED));

        if (confirmedBookings.isEmpty()) {
            log.info("Ride {} departed with no confirmed passengers to notify",
                    event.ride().getId());
            return;
        }

        Ride ride = rideRepository.findByIdWithDriverAndWaypoints(event.ride().getId())
                .orElse(null);
        if (ride == null) {
            log.error("Ride not found for notification: id={}", event.ride().getId());
            return;
        }

        String vehicleLine = (ride.getDriver().getCarModel() != null
                && ride.getDriver().getPlateNumber() != null)
                ? String.format("\n🚘 %s%s | 🔢 %s",
                ride.getDriver().getCarColor() != null
                ? HtmlEscapeUtil.escape(ride.getDriver().getCarColor()) + " "
                : "",
                HtmlEscapeUtil.escape(ride.getDriver().getCarModel()),
                HtmlEscapeUtil.escape(ride.getDriver().getPlateNumber()))
                : "";

        String driverHandle = ride.getDriver().getTelegramHandle() != null
                ? " (@" + HtmlEscapeUtil.escape(ride.getDriver().getTelegramHandle()) + ")"
                : "";

        String msg = String.format(
                "🚗 <b>Your driver is on the way!</b>\n" +
                        directionLabel(ride.getDirection()) + "\n\n" +
                        "📍 %s → %s\n" +
                        "🕐 %s\n\n" +
                        "👤 Driver: <b>%s</b>%s%s\n\n" +
                        "📌 <b>Please be at your pickup point.</b>\n" +
                        "<i>Message your driver directly if needed.</i>",
                HtmlEscapeUtil.escape(ride.getOriginHub().getName()),
                HtmlEscapeUtil.escape(ride.getDestinationHub().getName()),
                TIME_FMT.format(ride.getDepartureTime()
                        .atZone(ZoneId.of("Asia/Manila"))),
                HtmlEscapeUtil.escape(ride.getDriver().getFullName()),
                driverHandle,
                vehicleLine);

        for (Booking booking : confirmedBookings) {
            sendAndRecord(booking.getPassenger(),
                    NotificationTypes.RIDE_DEPARTED, msg,
                    Map.of("rideId", ride.getId()));
            log.info("Ride departed notification sent: rideId={} passengerId={}",
                    ride.getId(), booking.getPassenger().getId());
        }

        log.info("Ride departed notifications sent: rideId={} passengers={}",
                ride.getId(), confirmedBookings.size());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBookingRequested(RideEvents.BookingRequestedEvent event) {
        Booking booking = bookingRepository.findByIdWithDetails(event.booking().getId())
                .orElse(null);
        if (booking == null) {
            log.error("Booking not found for notification: id={}", event.booking().getId());
            return;
        }

        Ride ride   = booking.getRide();
        User driver = ride.getDriver();
        User pax    = booking.getPassenger();

        String badge = "";
        try {
            badge = ProfileBadgeBuilder.buildPassengerBadge(
                    profileService.getProfileStats(pax.getId()),
                    ratingService.getPassengerRatingLabel(pax.getId()));
        } catch (Exception e) {
            log.warn("Could not load passenger profile for notification: passengerId={}", pax.getId());
        }

        // Notify driver with Accept/Decline buttons
        try {
            telegramPort.sendMessageWithKeyboard(driver.getTelegramId(),
                    buildDriverBookingRequestMessage(booking, badge),
                    List.of(
                            List.of(
                                    new TelegramNotificationPort.InlineButton("✅ Accept", "ACCEPT_BOOKING:" + booking.getId()),
                                    new TelegramNotificationPort.InlineButton("❌ Decline", "DECLINE_BOOKING:" + booking.getId())
                            ),
                            List.of(
                                    new TelegramNotificationPort.InlineButton("🏠 Go to Menu", "MAIN_MENU")
                            )
                    ));
        } catch (Exception e) {
            log.error("Failed to send booking request to driver: bookingId={} driverId={} error={}",
                    booking.getId(), driver.getId(), e.getMessage());
        }

        // Record notification for driver
        Notification notification = Notification.builder()
                .user(driver)
                .type(NotificationTypes.BOOKING_RECEIVED)
                .payload(new HashMap<>(Map.of(
                        "bookingId", booking.getId(),
                        "rideId", ride.getId(),
                        "passengerName", pax.getFullName())))
                .status(NotificationStatus.SENT)
                .sentAt(Instant.now())
                .build();
        notificationRepository.save(notification);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBookingDeclined(RideEvents.BookingDeclinedEvent event) {
        Booking booking = bookingRepository.findByIdWithDetails(event.booking().getId())
                .orElse(null);
        if (booking == null) return;

        Ride ride = booking.getRide();
        User pax  = booking.getPassenger();

        String driverHandle = ride.getDriver().getTelegramHandle() != null
                ? " (@" + HtmlEscapeUtil.escape(ride.getDriver().getTelegramHandle()) + ")"
                : "";

        String msg = String.format(
                "❌ <b>Booking Request Declined</b>\n" +
                        directionLabel(ride.getDirection()) + "\n\n" +
                        "👤 Driver: <b>%s</b>%s\n" +
                        "📍 %s → %s\n" +
                        "🕐 %s\n" +
                        "🪑 %d seat(s) | ⛽ ₱%.2f share\n" +
                        "%s\n\n" +
                        "Please look for another available ride.",
                HtmlEscapeUtil.escape(ride.getDriver().getFullName()),
                driverHandle,
                HtmlEscapeUtil.escape(ride.getOriginHub().getName()),
                HtmlEscapeUtil.escape(ride.getDestinationHub().getName()),
                TIME_FMT.format(ride.getDepartureTime().atZone(ZoneId.of("Asia/Manila"))),
                booking.getSeatsReserved(),
                booking.getContributionDue(),
                booking.getCancellationReason() != null
                        ? "📝 Reason: <i>" + HtmlEscapeUtil.escape(booking.getCancellationReason()) + "</i>"
                        : "");

        sendAndRecord(pax, NotificationTypes.BOOKING_CANCELLED_BY_DRIVER, msg,
                Map.of("bookingId", booking.getId(), "rideId", ride.getId()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBookingTimedOut(RideEvents.BookingTimedOutEvent event) {
        Booking booking = bookingRepository.findByIdWithDetails(event.booking().getId())
                .orElse(null);
        if (booking == null) return;

        Ride ride   = booking.getRide();
        User pax    = booking.getPassenger();
        User driver = ride.getDriver();

        // Notify passenger — request expired
        String paxMsg = String.format(
                "⏰ <b>Booking Request Expired</b>\n" +
                        directionLabel(ride.getDirection()) + "\n\n" +
                        "Your booking request was not responded to by the driver.\n\n" +
                        "📍 %s → %s\n" +
                        "🕐 %s\n\n" +
                        "Please look for another available ride.",
                HtmlEscapeUtil.escape(ride.getOriginHub().getName()),
                HtmlEscapeUtil.escape(ride.getDestinationHub().getName()),
                TIME_FMT.format(ride.getDepartureTime().atZone(ZoneId.of("Asia/Manila"))));

        sendAndRecord(pax, NotificationTypes.BOOKING_TIMED_OUT, paxMsg,
                Map.of("bookingId", booking.getId(), "rideId", ride.getId()));

        // Notify driver — pending request auto-declined
        String paxHandle = pax.getTelegramHandle() != null
                ? " (@" + HtmlEscapeUtil.escape(pax.getTelegramHandle()) + ")"
                : "";

        String driverMsg = String.format(
                "⏰ <b>Booking Request Expired</b>\n" +
                        directionLabel(ride.getDirection()) + "\n\n" +
                        "The booking request from <b>%s</b>%s has expired " +
                        "because you did not respond in time.\n\n" +
                        "📍 %s → %s\n" +
                        "🕐 %s\n" +
                        "🪑 %d seat(s) | ⛽ ₱%.2f share\n\n" +
                        "The seat(s) have been released back to your ride.",
                HtmlEscapeUtil.escape(pax.getFullName()),
                paxHandle,
                HtmlEscapeUtil.escape(ride.getOriginHub().getName()),
                HtmlEscapeUtil.escape(ride.getDestinationHub().getName()),
                TIME_FMT.format(ride.getDepartureTime().atZone(ZoneId.of("Asia/Manila"))),
                booking.getSeatsReserved(),
                booking.getContributionDue());

        sendAndRecord(driver, NotificationTypes.BOOKING_TIMED_OUT, driverMsg,
                Map.of("bookingId", booking.getId(), "rideId", ride.getId()));

        log.info("Booking timed out notifications sent: bookingId={} passengerId={} driverId={}",
                booking.getId(), pax.getId(), driver.getId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBookingReminder(RideEvents.BookingReminderEvent event) {
        Booking booking = bookingRepository.findByIdWithDetails(event.booking().getId())
                .orElse(null);
        if (booking == null) return;

        Ride ride   = booking.getRide();
        User driver = ride.getDriver();
        User pax    = booking.getPassenger();

        String paxHandle = pax.getTelegramHandle() != null
                ? " (@" + HtmlEscapeUtil.escape(pax.getTelegramHandle()) + ")"
                : "";

        String msg = String.format(
                "⏰ <b>Reminder %d/3 — Pending Booking Request</b>\n" +
                        directionLabel(ride.getDirection()) + "\n\n" +
                        "👤 <b>%s</b>%s is waiting for your response.\n" +
                        "📍 %s → %s\n" +
                        "🕐 %s\n" +
                        "🪑 %d seat(s) | ⛽ ₱%.2f share\n\n" +
                        "Please accept or decline this request.",
                event.reminderNumber(),
                HtmlEscapeUtil.escape(pax.getFullName()),
                paxHandle,
                HtmlEscapeUtil.escape(ride.getOriginHub().getName()),
                HtmlEscapeUtil.escape(ride.getDestinationHub().getName()),
                TIME_FMT.format(ride.getDepartureTime().atZone(ZoneId.of("Asia/Manila"))),
                booking.getSeatsReserved(),
                booking.getContributionDue());

        log.info("Processing booking reminder {}/3: bookingId={} driverId={}",
                event.reminderNumber(), booking.getId(), driver.getId());
        sendAndRecord(driver, NotificationTypes.BOOKING_RECEIVED, msg,
                Map.of("bookingId", booking.getId(),
                       "reminderNumber", event.reminderNumber()),
                List.of(
                        List.of(
                                new TelegramNotificationPort.InlineButton("✅ Accept", "ACCEPT_BOOKING:" + booking.getId()),
                                new TelegramNotificationPort.InlineButton("❌ Decline", "DECLINE_BOOKING:" + booking.getId())
                        ),
                        List.of(
                                new TelegramNotificationPort.InlineButton("🏠 Go to Menu", "MAIN_MENU")
                        )
                ));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBookingCancelledByDriver(RideEvents.BookingCancelledByDriverEvent event) {
        Booking booking = bookingRepository.findByIdWithDetails(event.booking().getId())
                .orElse(null);
        if (booking == null) return;

        Ride ride = booking.getRide();
        User pax  = booking.getPassenger();

        String driverHandle = ride.getDriver().getTelegramHandle() != null
                ? " (@" + HtmlEscapeUtil.escape(ride.getDriver().getTelegramHandle()) + ")"
                : "";

        String msg = String.format(
                "⚠️ <b>Removed from Ride</b>\n" +
                        directionLabel(ride.getDirection()) + "\n\n" +
                        "You have been removed from the following ride:\n\n" +
                        "👤 Driver: <b>%s</b>%s\n" +
                        "📍 %s → %s\n" +
                        "🕐 %s\n" +
                        "🪑 %d seat(s) | ⛽ ₱%.2f share\n\n" +
                        "Please look for another available ride.",
                HtmlEscapeUtil.escape(ride.getDriver().getFullName()),
                driverHandle,
                HtmlEscapeUtil.escape(ride.getOriginHub().getName()),
                HtmlEscapeUtil.escape(ride.getDestinationHub().getName()),
                TIME_FMT.format(ride.getDepartureTime().atZone(ZoneId.of("Asia/Manila"))),
                booking.getSeatsReserved(),
                booking.getContributionDue());

        sendAndRecord(pax, NotificationTypes.BOOKING_CANCELLED_BY_DRIVER, msg,
                Map.of("bookingId", booking.getId(), "rideId", ride.getId()));

        log.info("Driver-removal notification sent to passenger: bookingId={} passengerId={}",
                booking.getId(), pax.getId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBookingAutoSynced(RideEvents.BookingAutoSyncedEvent event) {
        Booking booking = bookingRepository.findByIdWithDetails(event.booking().getId())
                .orElse(null);
        if (booking == null) return;

        User driver = booking.getRide().getDriver();
        User pax    = booking.getPassenger();

        String paxHandle = pax.getTelegramHandle() != null
                ? " (@" + HtmlEscapeUtil.escape(pax.getTelegramHandle()) + ")"
                : "";

        String msg = String.format(
                "ℹ️ <b>Booking Request Withdrawn</b>\n" +
                        directionLabel(booking.getRide().getDirection()) + "\n\n" +
                        "👤 <b>%s</b>%s\n" +
                        "📍 %s → %s\n" +
                        "🕐 %s\n" +
                        "🪑 %d seat(s) | ⛽ ₱%.2f share\n\n" +
                        "This passenger confirmed a seat on another ride. " +
                        "Their request has been automatically withdrawn and the seat(s) freed.",
                HtmlEscapeUtil.escape(pax.getFullName()),
                paxHandle,
                HtmlEscapeUtil.escape(booking.getRide().getOriginHub().getName()),
                HtmlEscapeUtil.escape(booking.getRide().getDestinationHub().getName()),
                TIME_FMT.format(booking.getRide().getDepartureTime().atZone(ZoneId.of("Asia/Manila"))),
                booking.getSeatsReserved(),
                booking.getContributionDue());

        sendAndRecord(driver, NotificationTypes.BOOKING_CANCELLED_BY_PASSENGER, msg,
                Map.of("bookingId", booking.getId(), "passengerName", pax.getFullName()));

        log.info("Auto-sync notification sent to driver: bookingId={} driverId={}",
                booking.getId(), driver.getId());
    }

    // ── Core send + record ────────────────────────────────────────────────────

    private void sendAndRecord(User recipient, String type, String message,
                               Map<String, Object> payloadData) {
        sendAndRecord(recipient, type, message, payloadData, null);
    }

    private void sendAndRecord(User recipient, String type, String message,
                               Map<String, Object> payloadData,
                               List<List<TelegramNotificationPort.InlineButton>> keyboard) {
        Notification notification = Notification.builder()
                .user(recipient)
                .type(type)
                .payload(new HashMap<>(payloadData))
                .status(NotificationStatus.PENDING)
                .build();

        notification = notificationRepository.save(notification);

        try {
            if (keyboard != null && !keyboard.isEmpty()) {
                telegramPort.sendMessageWithKeyboard(recipient.getTelegramId(), message, keyboard);
            } else {
                telegramPort.sendMessage(recipient.getTelegramId(), message);
            }
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(Instant.now());
            log.debug("Notification sent: type={} userId={}", type, recipient.getId());
        } catch (Exception e) {
            notification.setStatus(NotificationStatus.FAILED);
            log.error("Failed to send notification: type={} userId={} error={}",
                    type, recipient.getId(), e.getMessage(), e);
        }

        notificationRepository.save(notification);
    }

    // ── Message builders ──────────────────────────────────────────────────────

    private String buildPassengerConfirmationMessage(Booking booking) {
        Ride ride = booking.getRide();

        String pickupPoint = booking.getPickupWaypoint() != null
                ? booking.getPickupWaypoint().getHub().getName()
                : ride.getOriginHub().getName();

        String dropoffPoint = booking.getDropoffWaypoint() != null
                ? booking.getDropoffWaypoint().getHub().getName()
                : ride.getDestinationHub().getName();

        String driverHandle = ride.getDriver().getTelegramHandle() != null
                ? " (@" + HtmlEscapeUtil.escape(ride.getDriver().getTelegramHandle()) + ")"
                : "";

        String vehicleLine = "";
        if (ride.getVehicle() != null) {
            vehicleLine = String.format("🚘 %s%s | 🔢 %s\n",
                    ride.getVehicle().getColor() != null
                            ? HtmlEscapeUtil.escape(ride.getVehicle().getColor()) + " " : "",
                    HtmlEscapeUtil.escape(ride.getVehicle().getModel()),
                    HtmlEscapeUtil.escape(ride.getVehicle().getPlateNumber()));
        } else if (ride.getDriver().getCarModel() != null
                && ride.getDriver().getPlateNumber() != null) {
            vehicleLine = String.format("🚘 %s%s | 🔢 %s\n",
                    ride.getDriver().getCarColor() != null
                            ? HtmlEscapeUtil.escape(ride.getDriver().getCarColor()) + " " : "",
                    HtmlEscapeUtil.escape(ride.getDriver().getCarModel()),
                    HtmlEscapeUtil.escape(ride.getDriver().getPlateNumber()));
        }

        return String.format(
                "✅ <b>Booking Confirmed!</b>\n" +
                        directionLabel(ride.getDirection()) + "\n\n" +
                        "📍 %s → %s\n" +
                        "🚏 Your pickup: <b>%s</b>\n" +
                        "🏁 Your dropoff: <b>%s</b>\n" +
                        "🕐 %s\n" +
                        "🪑 Seats: %d\n" +
                        "⛽ Suggested share: <b>₱%.2f</b>\n\n" +
                        "👤 Driver: <b>%s</b>%s\n" +
                        "%s" +
                        "%s",
                HtmlEscapeUtil.escape(ride.getOriginHub().getName()),
                HtmlEscapeUtil.escape(ride.getDestinationHub().getName()),
                HtmlEscapeUtil.escape(pickupPoint),
                HtmlEscapeUtil.escape(dropoffPoint),
                TIME_FMT.format(ride.getDepartureTime().atZone(ZoneId.of("Asia/Manila"))),
                booking.getSeatsReserved(),
                booking.getContributionDue(),
                HtmlEscapeUtil.escape(ride.getDriver().getFullName()),
                driverHandle,
                vehicleLine,
                ride.getNotes() != null
                        ? "📝 Note: " + HtmlEscapeUtil.escape(ride.getNotes())
                        : "");
    }

    private String buildDriverBookingRequestMessage(Booking booking, String badge) {
        Ride ride = booking.getRide();
        User pax  = booking.getPassenger();

        String paxHandle = pax.getTelegramHandle() != null
                ? " (@" + HtmlEscapeUtil.escape(pax.getTelegramHandle()) + ")"
                : "";

        String pickupPoint = booking.getPickupWaypoint() != null
                ? booking.getPickupWaypoint().getHub().getName()
                : ride.getOriginHub().getName();

        String badgeLine = badge != null && !badge.isBlank() ? badge + "\n" : "";

        return String.format(
                "🔔 <b>New Booking Request</b>\n" +
                        directionLabel(ride.getDirection()) + "\n\n" +
                        "👤 <b>%s</b>%s\n" +
                        "%s" +
                        "🚏 Pickup at: <b>%s</b>\n" +
                        "🪑 Seats: %d | ⛽ ₱%.2f share\n" +
                        "%s",
                HtmlEscapeUtil.escape(pax.getFullName()),
                paxHandle,
                badgeLine,
                HtmlEscapeUtil.escape(pickupPoint),
                booking.getSeatsReserved(),
                booking.getContributionDue(),
                booking.getPassengerMessage() != null
                        ? "💬 \"" + HtmlEscapeUtil.escape(booking.getPassengerMessage()) + "\"\n"
                        : "");
    }

    // ── HTML escape ───────────────────────────────────────────────────────────

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRideExpired(RideEvents.RideExpiredEvent event) {
        List<Booking> cancelledBookings = bookingRepository
                .findCancelledByDriverBookingsForRide(event.ride().getId());

        if (cancelledBookings.isEmpty()) {
            log.info("Ride {} expired with no affected passengers", event.ride().getId());
            return;
        }

        Ride ride = cancelledBookings.get(0).getRide();

        // If departure was 2+ hours ago — passengers likely already rode
        // No notification needed — would only cause confusion
        long minutesSinceDeparture = java.time.Duration.between(
                ride.getDepartureTime(), java.time.LocalDateTime.now()).toMinutes();

        if (minutesSinceDeparture >= 120) {
            log.info("Ride {} expired but departure was {}min ago — skipping notification",
                    ride.getId(), minutesSinceDeparture);
            return;
        }

        // Departure was recent — passenger was booked but ride never departed
        String msg = String.format(
                "🕐 <b>Ride Did Not Push Through</b>\n" +
                        directionLabel(ride.getDirection()) + "\n\n" +
                        "The ride you booked has expired without departing:\n\n" +
                        "📍 %s → %s\n" +
                        "🕐 Scheduled: %s\n\n" +
                        "Your booking has been automatically cancelled.\n" +
                        "Please look for another available ride.",
                HtmlEscapeUtil.escape(ride.getOriginHub().getName()),
                HtmlEscapeUtil.escape(ride.getDestinationHub().getName()),
                TIME_FMT.format(ride.getDepartureTime().atZone(ZoneId.of("Asia/Manila"))));

        for (Booking booking : cancelledBookings) {
            sendAndRecord(booking.getPassenger(), NotificationTypes.RIDE_CANCELLED, msg,
                    Map.of("rideId", ride.getId()));
        }

        log.info("Ride expiry notifications sent to {} passengers for rideId={}",
                cancelledBookings.size(), ride.getId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRideDepartureReminder(RideEvents.RideDepartureReminderEvent event) {
        Ride ride = rideRepository.findByIdWithDriverAndWaypoints(event.ride().getId())
                .orElse(null);
        if (ride == null) {
            log.error("Ride not found for notification: id={}", event.ride().getId());
            return;
        }

        // Fetch confirmed bookings for this ride
        List<Booking> confirmedBookings = bookingRepository
                .findActiveBookingsForRide(ride.getId())
                .stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                .toList();

        String timeFormatted = ride.getDepartureTime()
                .format(DateTimeFormatter.ofPattern("h:mm a"));

        String route = HtmlEscapeUtil.escape(ride.getOriginHub().getName()) +
                " → " +
                HtmlEscapeUtil.escape(ride.getDestinationHub().getName());

        // Notify driver
        String driverMsg = String.format(
                "🚀 <b>Departure Reminder</b>\n" +
                        directionLabel(ride.getDirection()) + "\n\n" +
                        "Your ride departs in <b>~30 minutes</b>!\n\n" +
                        "📍 %s\n" +
                        "🕐 %s\n" +
                        "👥 %d confirmed passenger%s\n\n" +
                        "Head to your pickup point soon.",
                route,
                timeFormatted,
                confirmedBookings.size(),
                confirmedBookings.size() == 1 ? "" : "s");

        Notification driverNotif = Notification.builder()
                .user(ride.getDriver())
                .type(NotificationTypes.RIDE_DEPARTURE_REMINDER)
                .rideId(ride.getId())
                .payload(new HashMap<>(Map.of("rideId", ride.getId())))
                .status(NotificationStatus.PENDING)
                .build();

        driverNotif = notificationRepository.save(driverNotif);

        try {
            telegramPort.sendMessage(ride.getDriver().getTelegramId(), driverMsg);
            driverNotif.setStatus(NotificationStatus.SENT);
            driverNotif.setSentAt(Instant.now());
        } catch (Exception e) {
            driverNotif.setStatus(NotificationStatus.FAILED);
            log.error("Failed to send departure reminder to driver: rideId={} error={}",
                    ride.getId(), e.getMessage());
        }
        notificationRepository.save(driverNotif);

        // Notify each confirmed passenger
        for (Booking booking : confirmedBookings) {
            String passengerMsg = String.format(
                    "🚀 <b>Departure Reminder</b>\n" +
                            directionLabel(ride.getDirection()) + "\n\n" +
                            "Your ride departs in <b>~30 minutes</b>!\n\n" +
                            "📍 %s\n" +
                            "🕐 %s\n" +
                            "👤 Driver: <b>%s</b>%s\n" +
                            "🚘 %s\n\n" +
                            "Head to your pickup point soon.",
                    route,
                    timeFormatted,
                    HtmlEscapeUtil.escape(ride.getDriver().getFullName()),
                    ride.getDriver().getTelegramHandle() != null
                            ? " (@" + HtmlEscapeUtil.escape(ride.getDriver().getTelegramHandle()) + ")"
                            : "",
                    buildVehicleLine(ride));

            sendAndRecord(booking.getPassenger(),
                    NotificationTypes.RIDE_DEPARTURE_REMINDER,
                    passengerMsg,
                    Map.of("rideId", ride.getId(), "bookingId", booking.getId()));
        }

        log.info("Departure reminders sent for rideId={} — driver + {} passengers",
                ride.getId(), confirmedBookings.size());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRideTimeChanged(RideEvents.RideTimeChangedEvent event) {
        Long rideId = event.ride().getId();
        List<Booking> confirmedBookings = bookingRepository
                .findActiveBookingsForRide(rideId)
                .stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                .toList();

        if (confirmedBookings.isEmpty()) {
            log.info("Ride time changed with no confirmed passengers: rideId={}", rideId);
            return;
        }

        Ride ride = confirmedBookings.get(0).getRide();
        String newTimeFormatted = ride.getDepartureTime()
                .format(DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a"));

        String msg = String.format(
                "⏰ <b>Ride Time Updated</b>\n" +
                        directionLabel(ride.getDirection()) + "\n\n" +
                        "Your driver updated the departure time for your upcoming ride.\n\n" +
                        "📍 %s → %s\n" +
                        "🕐 New time: <b>%s</b>\n\n" +
                        "Does this still work for you?",
                HtmlEscapeUtil.escape(ride.getOriginHub().getName()),
                HtmlEscapeUtil.escape(ride.getDestinationHub().getName()),
                newTimeFormatted);

        for (Booking booking : confirmedBookings) {
            sendAndRecord(booking.getPassenger(), NotificationTypes.RIDE_TIME_CHANGED, msg,
                    Map.of("rideId", rideId, "bookingId", booking.getId()),
                    List.of(
                            List.of(
                                    new TelegramNotificationPort.InlineButton(
                                            "✅ Keep Booking", "KEEP_BOOKING:" + booking.getId()),
                                    new TelegramNotificationPort.InlineButton(
                                            "❌ Cancel Booking", "CANCEL_BOOKING:" + booking.getId())
                            )
                    ));
        }

        log.info("Ride time change notifications sent: rideId={} passengersNotified={}",
                rideId, confirmedBookings.size());
    }

    private static String directionLabel(RideDirection direction) {
        if (direction == null) return "📍 Other";
        return switch (direction) {
            case HOME_TO_WORK -> "🏠 Home → Work";
            case WORK_TO_HOME -> "🏢 Work → Home";
            default           -> "📍 Other";
        };
    }

    private String buildVehicleLine(Ride ride) {
        String model = ride.getDriver().getCarModel();
        String color = ride.getDriver().getCarColor();
        String plate = ride.getDriver().getPlateNumber();

        if (model == null && plate == null) return "<i>No vehicle info</i>";

        StringBuilder sb = new StringBuilder();
        if (color != null) sb.append(HtmlEscapeUtil.escape(color)).append(" ");
        if (model != null) sb.append(HtmlEscapeUtil.escape(model));
        if (plate != null) sb.append(" | 🔢 ").append(HtmlEscapeUtil.escape(plate));
        return sb.toString();
    }
}