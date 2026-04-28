package com.carpool.service.notification;

import com.carpool.domain.entity.Booking;
import com.carpool.domain.entity.Notification;
import com.carpool.domain.entity.Ride;
import com.carpool.domain.entity.User;
import com.carpool.domain.enums.NotificationStatus;
import com.carpool.domain.enums.NotificationTypes;
import com.carpool.repository.BookingRepository;
import com.carpool.repository.NotificationRepository;
import com.carpool.service.event.RideEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private final RestClient restClient = RestClient.create();

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a")
                    .withZone(ZoneId.of("Asia/Manila"));

    @Value("${carpool.telegram.bot-token}")
    private String botToken;

    // ── Event Listeners ───────────────────────────────────────────────────────

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
                        "contribution", booking.getContributionDue()));

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
                ? " (@" + escape(pax.getTelegramHandle()) + ")"
                : "";

        String msg = String.format(
                "🚫 <b>Booking Cancelled</b>\n\n" +
                        "👤 <b>%s</b>%s\n" +
                        "📍 %s → %s\n" +
                        "🕐 %s\n" +
                        "🪑 %d seat(s) | ⛽ ₱%.2f share\n" +
                        "%s\n\n" +
                        "Seat(s) have been freed up.",
                escape(pax.getFullName()),
                paxHandle,
                escape(booking.getRide().getOriginHub().getName()),
                escape(booking.getRide().getDestinationHub().getName()),
                TIME_FMT.format(booking.getRide().getDepartureTime()
                        .atZone(ZoneId.of("Asia/Manila"))),
                booking.getSeatsReserved(),
                booking.getContributionDue(),
                booking.getCancellationReason() != null
                        ? "📝 Reason: <i>" + escape(booking.getCancellationReason()) + "</i>"
                        : "");

        sendAndRecord(driver, NotificationTypes.BOOKING_CANCELLED_BY_PASSENGER, msg,
                Map.of("bookingId", booking.getId(), "passengerName", pax.getFullName()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRideCancelled(RideEvents.RideCancelledEvent event) {
        // Fetch CANCELLED_BY_DRIVER bookings — just cancelled by driver
        List<Booking> cancelledBookings = bookingRepository
                .findCancelledByDriverBookingsForRide(event.ride().getId());

        if (cancelledBookings.isEmpty()) {
            log.info("Ride {} cancelled with no affected passengers", event.ride().getId());
            return;
        }

        Ride ride = cancelledBookings.get(0).getRide();
        User driver = ride.getDriver();
        String driverHandle = driver.getTelegramHandle() != null
                ? " (@" + escape(driver.getTelegramHandle()) + ")"
                : "";

        String msg = String.format(
                "⚠️ <b>Ride Cancelled</b>\n\n" +
                        "👤 Driver: <b>%s</b>%s\n" +
                        "📍 %s → %s\n" +
                        "🕐 %s\n" +
                        "%s\n\n" +
                        "Please look for another carpool or arrange alternative transport.",
                escape(driver.getFullName()),
                driverHandle,
                escape(ride.getOriginHub().getName()),
                escape(ride.getDestinationHub().getName()),
                TIME_FMT.format(ride.getDepartureTime().atZone(ZoneId.of("Asia/Manila"))),
                event.reason() != null
                        ? "📝 Reason: <i>" + escape(event.reason()) + "</i>"
                        : "");

        for (Booking booking : cancelledBookings) {
            String pickup = booking.getPickupWaypoint() != null
                    ? booking.getPickupWaypoint().getHub().getName()
                    : ride.getOriginHub().getName();

            String personalMsg = msg + String.format(
                    "\n\n📋 <b>Your Booking</b>\n" +
                            "🚏 Pickup: <b>%s</b>\n" +
                            "🪑 Seats: %d | ⛽ ₱%.2f share",
                    escape(pickup),
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
        // Bookings are already COMPLETED at this point — query by COMPLETED status
        List<Booking> activeBookings = bookingRepository
                .findCompletedBookingsForRide(event.ride().getId());

        if (activeBookings.isEmpty()) {
            log.info("Ride {} completed with no bookings to notify", event.ride().getId());
            return;
        }

        Ride ride = activeBookings.get(0).getRide();

        String msg = String.format(
                "✅ <b>Ride Completed!</b>\n\n" +
                        "📍 %s → %s\n" +
                        "🕐 %s\n\n" +
                        "Thank you for carpooling! Please settle your gas share with the driver.",
                escape(ride.getOriginHub().getName()),
                escape(ride.getDestinationHub().getName()),
                TIME_FMT.format(ride.getDepartureTime().atZone(ZoneId.of("Asia/Manila"))));

        for (Booking booking : activeBookings) {
            sendAndRecord(booking.getPassenger(), NotificationTypes.RIDE_COMPLETED, msg,
                    Map.of("rideId", ride.getId(),
                            "contributionDue", booking.getContributionDue()));
        }
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

        // Notify driver with Accept/Decline buttons
        sendTelegramMessageWithButtons(driver.getTelegramId(),
                buildDriverBookingRequestMessage(booking),
                List.of(
                        List.of(
                                Map.of("text", "✅ Accept",
                                        "callback_data", "ACCEPT_BOOKING:" + booking.getId()),
                                Map.of("text", "❌ Decline",
                                        "callback_data", "DECLINE_BOOKING:" + booking.getId())
                        ),
                        List.of(
                                Map.of("text", "🏠 Go to Menu",
                                        "callback_data", "MAIN_MENU")
                        )
                ));

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
                ? " (@" + escape(ride.getDriver().getTelegramHandle()) + ")"
                : "";

        String msg = String.format(
                "❌ <b>Booking Request Declined</b>\n\n" +
                        "👤 Driver: <b>%s</b>%s\n" +
                        "📍 %s → %s\n" +
                        "🕐 %s\n" +
                        "🪑 %d seat(s) | ⛽ ₱%.2f share\n" +
                        "%s\n\n" +
                        "Please look for another available ride.",
                escape(ride.getDriver().getFullName()),
                driverHandle,
                escape(ride.getOriginHub().getName()),
                escape(ride.getDestinationHub().getName()),
                TIME_FMT.format(ride.getDepartureTime().atZone(ZoneId.of("Asia/Manila"))),
                booking.getSeatsReserved(),
                booking.getContributionDue(),
                booking.getCancellationReason() != null
                        ? "📝 Reason: <i>" + escape(booking.getCancellationReason()) + "</i>"
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
                "⏰ <b>Booking Request Expired</b>\n\n" +
                        "Your booking request was not responded to by the driver.\n\n" +
                        "📍 %s → %s\n" +
                        "🕐 %s\n\n" +
                        "Please look for another available ride.",
                escape(ride.getOriginHub().getName()),
                escape(ride.getDestinationHub().getName()),
                TIME_FMT.format(ride.getDepartureTime().atZone(ZoneId.of("Asia/Manila"))));

        sendAndRecord(pax, NotificationTypes.BOOKING_TIMED_OUT, paxMsg,
                Map.of("bookingId", booking.getId(), "rideId", ride.getId()));

        // Notify driver — pending request auto-declined
        String paxHandle = pax.getTelegramHandle() != null
                ? " (@" + escape(pax.getTelegramHandle()) + ")"
                : "";

        String driverMsg = String.format(
                "⏰ <b>Booking Request Expired</b>\n\n" +
                        "The booking request from <b>%s</b>%s has expired " +
                        "because you did not respond in time.\n\n" +
                        "📍 %s → %s\n" +
                        "🕐 %s\n" +
                        "🪑 %d seat(s) | ⛽ ₱%.2f share\n\n" +
                        "The seat(s) have been released back to your ride.",
                escape(pax.getFullName()),
                paxHandle,
                escape(ride.getOriginHub().getName()),
                escape(ride.getDestinationHub().getName()),
                TIME_FMT.format(ride.getDepartureTime().atZone(ZoneId.of("Asia/Manila"))),
                booking.getSeatsReserved(),
                booking.getContributionDue());

        sendAndRecord(driver, NotificationTypes.BOOKING_RECEIVED, driverMsg,
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
                ? " (@" + escape(pax.getTelegramHandle()) + ")"
                : "";

        // Compute remaining minutes
        long remainingMinutes = java.time.Duration.between(
                Instant.now(), booking.getExpiresAt()).toMinutes();

        String msg = String.format(
                "⏰ <b>Reminder %d/3 — Pending Booking Request</b>\n\n" +
                        "👤 <b>%s</b>%s is waiting for your response.\n" +
                        "📍 %s → %s\n" +
                        "🕐 %s\n" +
                        "🪑 %d seat(s) | ⛽ ₱%.2f share\n\n" +
                        "⚠️ Auto-declines in ~%d minutes if no response.",
                event.reminderNumber(),
                escape(pax.getFullName()),
                paxHandle,
                escape(ride.getOriginHub().getName()),
                escape(ride.getDestinationHub().getName()),
                TIME_FMT.format(ride.getDepartureTime().atZone(ZoneId.of("Asia/Manila"))),
                booking.getSeatsReserved(),
                booking.getContributionDue(),
                Math.max(0, remainingMinutes));

        sendTelegramMessageWithButtons(driver.getTelegramId(), msg,
                List.of(
                        List.of(
                                Map.of("text", "✅ Accept",
                                        "callback_data", "ACCEPT_BOOKING:" + booking.getId()),
                                Map.of("text", "❌ Decline",
                                        "callback_data", "DECLINE_BOOKING:" + booking.getId())
                        ),
                        List.of(
                                Map.of("text", "🏠 Go to Menu",
                                        "callback_data", "MAIN_MENU")
                        )
                ));

        // Record reminder notification
        Notification notification = Notification.builder()
                .user(driver)
                .type(NotificationTypes.BOOKING_RECEIVED)
                .payload(new HashMap<>(Map.of(
                        "bookingId", booking.getId(),
                        "reminderNumber", event.reminderNumber())))
                .status(NotificationStatus.SENT)
                .sentAt(Instant.now())
                .build();
        notificationRepository.save(notification);
    }

    // ── Core send + record ────────────────────────────────────────────────────

    private void sendAndRecord(User recipient, String type, String message,
                               Map<String, Object> payloadData) {
        Notification notification = Notification.builder()
                .user(recipient)
                .type(type)
                .payload(new HashMap<>(payloadData))
                .status(NotificationStatus.PENDING)
                .build();

        notification = notificationRepository.save(notification);

        try {
            sendTelegramMessage(recipient.getTelegramId(), message);
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(Instant.now());
            log.debug("Notification sent: type={} userId={}", type, recipient.getId());
        } catch (Exception e) {
            notification.setStatus(NotificationStatus.FAILED);
            log.error("Failed to send notification: type={} userId={} error={}",
                    type, recipient.getId(), e.getMessage());
        }

        notificationRepository.save(notification);
    }

    private void sendTelegramMessage(Long chatId, String text) {
        sendTelegramMessage(chatId, text, true);
    }

    private void sendTelegramMessage(Long chatId, String text, boolean withMenuButton) {
        if (botToken == null || botToken.isBlank()) {
            log.warn("Telegram bot token not configured — skipping message to chatId={}", chatId);
            return;
        }

        log.info("Sending Telegram notification to chatId={}", chatId);

        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id",    chatId);
        body.put("text",       text);
        body.put("parse_mode", "HTML");

        if (withMenuButton) {
            body.put("reply_markup", Map.of(
                    "inline_keyboard", List.of(
                            List.of(Map.of(
                                    "text",          "🏠 Go to Menu",
                                    "callback_data", "MAIN_MENU"
                            ))
                    )
            ));
        }

        restClient.post()
                .uri(url)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    // ── Message builders ──────────────────────────────────────────────────────

    private String buildDriverBookingMessage(Booking booking) {
        Ride ride = booking.getRide();
        User pax  = booking.getPassenger();

        String pickupPoint = booking.getPickupWaypoint() != null
                ? booking.getPickupWaypoint().getHub().getName()
                : ride.getOriginHub().getName();

        String paxHandle = pax.getTelegramHandle() != null
                ? " (@" + escape(pax.getTelegramHandle()) + ")"
                : "";

        return String.format(
                "🎉 <b>New Booking!</b>\n\n" +
                        "👤 Passenger: <b>%s</b>%s\n" +
                        "🪑 Seats: %d\n" +
                        "📍 Route: %s → %s\n" +
                        "🚏 Pickup at: <b>%s</b>\n" +
                        "🕐 %s\n" +
                        "⛽ Suggested share: ₱%.2f\n\n" +
                        "Contact the passenger directly to coordinate.",
                escape(pax.getFullName()),
                paxHandle,
                booking.getSeatsReserved(),
                escape(ride.getOriginHub().getName()),
                escape(ride.getDestinationHub().getName()),
                escape(pickupPoint),
                TIME_FMT.format(ride.getDepartureTime().atZone(ZoneId.of("Asia/Manila"))),
                booking.getContributionDue());
    }

    private String buildPassengerConfirmationMessage(Booking booking) {
        Ride ride = booking.getRide();

        String pickupPoint = booking.getPickupWaypoint() != null
                ? booking.getPickupWaypoint().getHub().getName()
                : ride.getOriginHub().getName();

        String dropoffPoint = booking.getDropoffWaypoint() != null
                ? booking.getDropoffWaypoint().getHub().getName()
                : ride.getDestinationHub().getName();

        String driverHandle = ride.getDriver().getTelegramHandle() != null
                ? " (@" + escape(ride.getDriver().getTelegramHandle()) + ")"
                : "";

        return String.format(
                "✅ <b>Booking Confirmed!</b>\n\n" +
                        "📍 %s → %s\n" +
                        "🚏 Your pickup: <b>%s</b>\n" +
                        "🏁 Your dropoff: <b>%s</b>\n" +
                        "🕐 %s\n" +
                        "🪑 Seats: %d\n" +
                        "⛽ Suggested share: <b>₱%.2f</b>\n\n" +
                        "👤 Driver: <b>%s</b>%s\n" +
                        "%s",
                escape(ride.getOriginHub().getName()),
                escape(ride.getDestinationHub().getName()),
                escape(pickupPoint),
                escape(dropoffPoint),
                TIME_FMT.format(ride.getDepartureTime().atZone(ZoneId.of("Asia/Manila"))),
                booking.getSeatsReserved(),
                booking.getContributionDue(),
                escape(ride.getDriver().getFullName()),
                driverHandle,
                ride.getNotes() != null
                        ? "📝 Note: " + escape(ride.getNotes())
                        : "");
    }

    private void sendTelegramMessageWithButtons(Long chatId, String text,
                                                List<List<Map<String, String>>> keyboard) {
        if (botToken == null || botToken.isBlank()) {
            log.warn("Telegram bot token not configured — skipping message to chatId={}", chatId);
            return;
        }

        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id",    chatId);
        body.put("text",       text);
        body.put("parse_mode", "HTML");
        body.put("reply_markup", Map.of("inline_keyboard", keyboard));

        try {
            restClient.post()
                    .uri(url)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Failed to send Telegram message with buttons to chatId={}: {}",
                    chatId, e.getMessage());
        }
    }

    private String buildDriverBookingRequestMessage(Booking booking) {
        Ride ride = booking.getRide();
        User pax  = booking.getPassenger();

        String paxHandle = pax.getTelegramHandle() != null
                ? " (@" + escape(pax.getTelegramHandle()) + ")"
                : "";

        String pickupPoint = booking.getPickupWaypoint() != null
                ? booking.getPickupWaypoint().getHub().getName()
                : ride.getOriginHub().getName();

        long expiresInMinutes = java.time.Duration.between(
                Instant.now(), booking.getExpiresAt()).toMinutes();

        return String.format(
                "🔔 <b>New Booking Request</b>\n\n" +
                        "👤 <b>%s</b>%s\n" +
                        "🚏 Pickup at: <b>%s</b>\n" +
                        "🪑 Seats: %d | ⛽ ₱%.2f share\n" +
                        "%s\n" +
                        "⏰ Expires in %d minutes",
                escape(pax.getFullName()),
                paxHandle,
                escape(pickupPoint),
                booking.getSeatsReserved(),
                booking.getContributionDue(),
                booking.getPassengerMessage() != null
                        ? "💬 \"" + escape(booking.getPassengerMessage()) + "\"\n"
                        : "",
                Math.max(0, expiresInMinutes));
    }

    private String buildPassengerRequestSentMessage(Booking booking) {
        Ride ride   = booking.getRide();
        User driver = ride.getDriver();

        String driverHandle = driver.getTelegramHandle() != null
                ? " (@" + escape(driver.getTelegramHandle()) + ")"
                : "";

        long expiresInMinutes = java.time.Duration.between(
                Instant.now(), booking.getExpiresAt()).toMinutes();

        return String.format(
                "⏳ <b>Booking Request Sent!</b>\n\n" +
                        "📍 %s → %s\n" +
                        "🕐 %s\n" +
                        "🪑 Seats: %d | ⛽ ₱%.2f share\n" +
                        "👤 Driver: <b>%s</b>%s\n\n" +
                        "Waiting for driver approval.\n" +
                        "⏰ Auto-declines in %d minutes if no response.",
                escape(ride.getOriginHub().getName()),
                escape(ride.getDestinationHub().getName()),
                TIME_FMT.format(ride.getDepartureTime().atZone(ZoneId.of("Asia/Manila"))),
                booking.getSeatsReserved(),
                booking.getContributionDue(),
                escape(driver.getFullName()),
                driverHandle,
                Math.max(0, expiresInMinutes));
    }

    // ── HTML escape ───────────────────────────────────────────────────────────

    private String escape(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

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
                "🕐 <b>Ride Did Not Push Through</b>\n\n" +
                        "The ride you booked has expired without departing:\n\n" +
                        "📍 %s → %s\n" +
                        "🕐 Scheduled: %s\n\n" +
                        "Your booking has been automatically cancelled.\n" +
                        "Please look for another available ride.",
                escape(ride.getOriginHub().getName()),
                escape(ride.getDestinationHub().getName()),
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
        Ride ride = event.ride();

        // Fetch confirmed bookings for this ride
        List<Booking> confirmedBookings = bookingRepository
                .findActiveBookingsForRide(ride.getId())
                .stream()
                .filter(b -> b.getStatus().name().equals("CONFIRMED"))
                .toList();

        String timeFormatted = ride.getDepartureTime()
                .format(DateTimeFormatter.ofPattern("h:mm a"));

        String route = escape(ride.getOriginHub().getName()) +
                " → " +
                escape(ride.getDestinationHub().getName());

        // Notify driver
        String driverMsg = String.format(
                "🚀 <b>Departure Reminder</b>\n\n" +
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
            sendTelegramMessage(ride.getDriver().getTelegramId(), driverMsg);
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
                    "🚀 <b>Departure Reminder</b>\n\n" +
                            "Your ride departs in <b>~30 minutes</b>!\n\n" +
                            "📍 %s\n" +
                            "🕐 %s\n" +
                            "👤 Driver: <b>%s</b>%s\n" +
                            "🚘 %s\n\n" +
                            "Head to your pickup point soon.",
                    route,
                    timeFormatted,
                    escape(ride.getDriver().getFullName()),
                    ride.getDriver().getTelegramHandle() != null
                            ? " (@" + escape(ride.getDriver().getTelegramHandle()) + ")"
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

    private String buildVehicleLine(Ride ride) {
        String model = ride.getDriver().getCarModel();
        String color = ride.getDriver().getCarColor();
        String plate = ride.getDriver().getPlateNumber();

        if (model == null && plate == null) return "<i>No vehicle info</i>";

        StringBuilder sb = new StringBuilder();
        if (color != null) sb.append(escape(color)).append(" ");
        if (model != null) sb.append(escape(model));
        if (plate != null) sb.append(" | 🔢 ").append(escape(plate));
        return sb.toString();
    }
}