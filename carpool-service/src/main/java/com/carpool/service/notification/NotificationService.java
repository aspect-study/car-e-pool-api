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
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
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
 * @Async on each listener method means they run on a virtual thread,
 * completely decoupled from the transaction that published the event.
 *
 * Flow per notification:
 *   1. INSERT notification record (status=PENDING) — audit trail first
 *   2. Call Telegram sendMessage API
 *   3. UPDATE notification record (status=SENT or FAILED)
 *
 * If Telegram API is down, the FAILED records can be retried by a
 * scheduled job querying notifications WHERE status = 'FAILED'.
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
    @EventListener
    public void onBookingConfirmed(RideEvents.BookingConfirmedEvent event) {
        Booking booking = event.booking();
        Ride    ride    = booking.getRide();
        User    driver  = ride.getDriver();
        User    pax     = booking.getPassenger();

        // Notify driver
        String driverMsg = buildDriverBookingMessage(booking);
        sendAndRecord(driver, NotificationTypes.BOOKING_RECEIVED, driverMsg,
                Map.of("bookingId", booking.getId(), "rideId", ride.getId(),
                       "passengerName", pax.getFullName(),
                       "seats", booking.getSeatsReserved()));

        // Notify passenger
        String paxMsg = buildPassengerConfirmationMessage(booking);
        sendAndRecord(pax, NotificationTypes.BOOKING_CONFIRMED, paxMsg,
                Map.of("bookingId", booking.getId(), "rideId", ride.getId(),
                       "contribution", booking.getContributionDue()));
    }

    @Async
    @EventListener
    public void onBookingCancelledByPassenger(RideEvents.BookingCancelledByPassengerEvent event) {
        Booking booking = event.booking();
        User    driver  = booking.getRide().getDriver();
        User    pax     = booking.getPassenger();

        String msg = String.format(
                "🚫 *Booking Cancelled*\n\n" +
                "%s has cancelled their booking on your ride.\n" +
                "📍 %s → %s\n" +
                "🕐 %s\n\n" +
                "Seat(s) have been freed up.",
                pax.getFullName(),
                booking.getRide().getOriginHub().getName(),
                booking.getRide().getDestinationHub().getName(),
                TIME_FMT.format(booking.getRide().getDepartureTime()
                        .atZone(ZoneId.of("Asia/Manila"))));

        sendAndRecord(driver, NotificationTypes.BOOKING_CANCELLED_BY_PASSENGER, msg,
                Map.of("bookingId", booking.getId(), "passengerName", pax.getFullName()));
    }

    @Async
    @EventListener
    public void onRideCancelled(RideEvents.RideCancelledEvent event) {
        Ride ride = event.ride();

        // Fetch all affected passengers in one query
        List<Booking> activeBookings = bookingRepository.findActiveBookingsForRide(ride.getId());

        if (activeBookings.isEmpty()) {
            log.info("Ride {} cancelled with no active bookings — no notifications needed", ride.getId());
            return;
        }

        String msg = String.format(
                "⚠️ *Ride Cancelled*\n\n" +
                "Unfortunately, the driver has cancelled the following ride:\n\n" +
                "📍 %s → %s\n" +
                "🕐 %s\n\n" +
                "Please look for another carpool or arrange alternative transport.",
                ride.getOriginHub().getName(),
                ride.getDestinationHub().getName(),
                TIME_FMT.format(ride.getDepartureTime()
                        .atZone(ZoneId.of("Asia/Manila"))));

        for (Booking booking : activeBookings) {
            sendAndRecord(booking.getPassenger(), NotificationTypes.RIDE_CANCELLED, msg,
                    Map.of("rideId", ride.getId()));
        }

        log.info("Ride cancellation notifications sent to {} passengers for rideId={}",
                activeBookings.size(), ride.getId());
    }

    @Async
    @EventListener
    public void onRideCompleted(RideEvents.RideCompletedEvent event) {
        Ride ride = event.ride();
        List<Booking> activeBookings = bookingRepository.findActiveBookingsForRide(ride.getId());

        String msg = String.format(
                "✅ *Ride Completed!*\n\n" +
                "📍 %s → %s\n" +
                "🕐 %s\n\n" +
                "Thank you for carpooling! Please settle your contribution with the driver.",
                ride.getOriginHub().getName(),
                ride.getDestinationHub().getName(),
                TIME_FMT.format(ride.getDepartureTime()
                        .atZone(ZoneId.of("Asia/Manila"))));

        for (Booking booking : activeBookings) {
            sendAndRecord(booking.getPassenger(), NotificationTypes.RIDE_COMPLETED, msg,
                    Map.of("rideId", ride.getId(),
                           "contributionDue", booking.getContributionDue()));
        }
    }

    // ── Core send + record logic ──────────────────────────────────────────────

    /**
     * Insert PENDING record → send → update to SENT or FAILED.
     * Notification record is always written regardless of send outcome.
     */
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

    /**
     * Calls Telegram Bot API sendMessage endpoint.
     * Uses RestClient (Java 21 replacement for RestTemplate in new code).
     * parse_mode=Markdown enables bold/italic formatting in messages.
     */
    private void sendTelegramMessage(Long chatId, String text) {
        if (botToken == null || botToken.isBlank()) {
            log.warn("Telegram bot token not configured — skipping message to chatId={}", chatId);
            return;
        }

        log.info("Sending Telegram notification to chatId={}", chatId);

        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";

        Map<String, Object> body = Map.of(
                "chat_id",    chatId,
                "text",       text,
                "parse_mode", "Markdown"
        );

        restClient.post()
                .uri(url)
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

        return String.format(
                "🎉 *New Booking!*\n\n" +
                "👤 Passenger: %s\n" +
                "🪑 Seats: %d\n" +
                "📍 Route: %s → %s\n" +
                "🚏 Pickup at: %s\n" +
                "🕐 %s\n" +
                "💵 Contribution: ₱%.2f\n\n" +
                "Reply to this message or PM the passenger for coordination.",
                pax.getFullName(),
                booking.getSeatsReserved(),
                ride.getOriginHub().getName(),
                ride.getDestinationHub().getName(),
                pickupPoint,
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

        return String.format(
                "✅ *Booking Confirmed!*\n\n" +
                "📍 %s → %s\n" +
                "🚏 Your pickup: *%s*\n" +
                "🏁 Your dropoff: *%s*\n" +
                "🕐 %s\n" +
                "🪑 Seats: %d\n" +
                "💵 Contribution due: *₱%.2f*\n\n" +
                "Driver: %s\n" +
                (ride.getNotes() != null ? "📝 Note: " + ride.getNotes() : ""),
                ride.getOriginHub().getName(),
                ride.getDestinationHub().getName(),
                pickupPoint,
                dropoffPoint,
                TIME_FMT.format(ride.getDepartureTime().atZone(ZoneId.of("Asia/Manila"))),
                booking.getSeatsReserved(),
                booking.getContributionDue(),
                ride.getDriver().getFullName());
    }
}
