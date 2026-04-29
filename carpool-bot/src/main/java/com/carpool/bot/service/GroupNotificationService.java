package com.carpool.bot.service;

import com.carpool.bot.CarpoolBot;
import com.carpool.bot.config.BotConfig;
import com.carpool.bot.util.BotMessageBuilder;
import com.carpool.domain.entity.Ride;
import com.carpool.service.event.RideEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;

/**
 * Listens for ride domain events and posts announcements to the
 * configured Telegram group topic.
 *
 * Uses @TransactionalEventListener(AFTER_COMMIT) to guarantee the ride
 * is fully persisted before posting. If the DB transaction rolls back,
 * no group message is sent.
 *
 * Uses @Async so group posting runs on a virtual thread — the driver
 * receives their confirmation immediately without waiting for the
 * Telegram API call to complete.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupNotificationService {

    private final CarpoolBot carpoolBot;
    private final BotConfig botConfig;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRidePosted(RideEvents.RidePostedEvent event) {
        Ride ride = event.ride();
        try {
            String message = buildRidePostedMessage(ride);
            carpoolBot.sendToGroup(message, ride.getId(), resolveTopicId(ride));
            log.info("Ride announcement posted to group: rideId={}", ride.getId());
        } catch (Exception e) {
            // Never propagate — group posting failure must not affect the driver
            log.error("Failed to post ride announcement to group: rideId={} error={}",
                    ride.getId(), e.getMessage());
        }
    }

    // ── Message formatter ─────────────────────────────────────────────────

    private String buildRidePostedMessage(Ride ride) {
        String dirLabel = switch (ride.getDirection()) {
            case HOME_TO_WORK -> "🏠 Home → Work";
            case WORK_TO_HOME -> "🏢 Work → Home";
            default           -> "🚗 Carpool Ride";
        };

        String departure = ride.getDepartureTime()
                .format(DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a"));

        String driverName = BotMessageBuilder.escape(ride.getDriver().getFullName());

        String notesLine = (ride.getNotes() != null && !ride.getNotes().isBlank())
                ? "\n📝 " + BotMessageBuilder.escape(ride.getNotes())
                : "";

        String vehicleLine = (ride.getDriver().getCarModel() != null
                && ride.getDriver().getPlateNumber() != null)
                ? String.format("\n🚘 %s%s | 🔢 %s",
                ride.getDriver().getCarColor() != null
                ? BotMessageBuilder.escape(ride.getDriver().getCarColor()) + " "
                : "",
                BotMessageBuilder.escape(ride.getDriver().getCarModel()),
                BotMessageBuilder.escape(ride.getDriver().getPlateNumber()))
                : "";

        return String.format(
                "🚗 <b>New Ride Available!</b>\n\n" +
                        "%s\n" +
                        "📍 <b>%s → %s</b>\n" +
                        "🕐 %s\n" +
                        "💺 Seats: <b>%d</b>\n" +
                        "⛽ Gas share: <b>₱%s/seat</b>" +
                        "%s" +
                        "%s\n\n" +
                        "👤 Driver: %s\n" +
                        "🔖 Ride #%d\n\n" +
                        "👇 Tap <b>View Ride</b> below to book instantly.",
                dirLabel,
                BotMessageBuilder.escape(ride.getOriginHub().getName()),
                BotMessageBuilder.escape(ride.getDestinationHub().getName()),
                departure,
                ride.getAvailableSeats(),
                ride.getContributionAmount().toPlainString(),
                notesLine,
                vehicleLine,
                driverName,
                ride.getId()
        );
    }

    private Integer resolveTopicId(Ride ride) {
        return switch (ride.getDirection()) {
            case HOME_TO_WORK -> botConfig.getGroupHomeToWorkTopicId();
            case WORK_TO_HOME -> botConfig.getGroupWorkToHomeTopicId();
            default           -> botConfig.getGroupHomeToWorkTopicId();
        };
    }
}