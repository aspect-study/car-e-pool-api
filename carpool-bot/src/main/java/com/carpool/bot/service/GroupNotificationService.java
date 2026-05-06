package com.carpool.bot.service;

import com.carpool.bot.CarpoolBot;
import com.carpool.bot.config.BotConfig;
import com.carpool.bot.util.BotMessageBuilder;
import com.carpool.common.util.HtmlEscapeUtil;
import com.carpool.domain.entity.Ride;
import com.carpool.repository.UserFavoriteRepository;
import com.carpool.repository.UserRepository;
import com.carpool.service.event.RideEvents;
import com.carpool.service.rating.RatingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;
import java.util.List;

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
    private final UserFavoriteRepository favoriteRepository;
    private final RatingService ratingService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRidePosted(RideEvents.RidePostedEvent event) {
        Ride ride = event.ride();
        try {
            String message = buildRidePostedMessage(ride);
            carpoolBot.sendToGroup(message, ride.getId(), resolveTopicId(ride));
            log.info("Ride announcement posted to group: rideId={}", ride.getId());

            // ── Alert followers that a favorite driver posted a ride ──────
            List<Long> followerTelegramIds = favoriteRepository
                    .findFollowerTelegramIdsByFavoriteId(ride.getDriver().getId());

            if (!followerTelegramIds.isEmpty()) {
                String departureDate = ride.getDepartureTime()
                        .format(DateTimeFormatter.ofPattern("EEE, MMM d"));
                String departureTime = ride.getDepartureTime()
                        .format(DateTimeFormatter.ofPattern("h:mm a"));

                String alertMsg = String.format(
                        "🔔 <b>Your favorite driver just posted a ride!</b>\n\n" +
                                "👤 <b>%s</b>%s\n" +
                                "📍 %s → %s\n" +
                                "📅 %s\n" +
                                "🕐 Pickup start: %s\n\n" +
                                "Tap below to view and book:",
                        HtmlEscapeUtil.escape(ride.getDriver().getFullName()),
                        ride.getDriver().getTelegramHandle() != null
                                ? " (@" + HtmlEscapeUtil.escape(
                                ride.getDriver().getTelegramHandle()) + ")" : "",
                        HtmlEscapeUtil.escape(ride.getOriginHub().getName()),
                        HtmlEscapeUtil.escape(ride.getDestinationHub().getName()),
                        departureDate,
                        departureTime);

                for (Long followerTelegramId : followerTelegramIds) {
                    try {
                        carpoolBot.sendToUser(followerTelegramId, alertMsg, ride.getId());
                    } catch (Exception e) {
                        log.warn("Failed to send favorite alert to telegramId={} rideId={}: {}",
                                followerTelegramId, ride.getId(), e.getMessage());
                    }
                }
                log.info("Favorite alerts sent: rideId={} followers={}",
                        ride.getId(), followerTelegramIds.size());
            }

        } catch (Exception e) {
            log.error("Failed to post ride announcement to group: rideId={} error={}",
                    ride.getId(), e.getMessage());
        }
    }

    // ── Message formatter ─────────────────────────────────────────────────

    private String buildRidePostedMessage(Ride ride) {

        String ratingLabel = ratingService.getRideCardRatingLabel(ride.getDriver().getId());

        String departureDate = ride.getDepartureTime().format(DateTimeFormatter.ofPattern("EEE, MMM d"));
        String departureTime = ride.getDepartureTime().format(DateTimeFormatter.ofPattern("h:mm a"));

        String dirLabel = switch (ride.getDirection()) {
            case HOME_TO_WORK -> "🏠 Home → Work";
            case WORK_TO_HOME -> "🏢 Work → Home";
            default           -> "🚗 Carpool Ride";
        };

        String driverName = HtmlEscapeUtil.escape(ride.getDriver().getFullName());

        String notesLine = (ride.getNotes() != null && !ride.getNotes().isBlank())
                ? "\n\n" + HtmlEscapeUtil.escape(ride.getNotes())
                : "";

        String vehicleLine = (ride.getDriver().getCarModel() != null
                && ride.getDriver().getPlateNumber() != null)
                ? String.format("🚘 %s%s | 🔢 %s\n",
                ride.getDriver().getCarColor() != null
                ? HtmlEscapeUtil.escape(ride.getDriver().getCarColor()) + " "
                : "",
                HtmlEscapeUtil.escape(ride.getDriver().getCarModel()),
                HtmlEscapeUtil.escape(ride.getDriver().getPlateNumber()))
                : "";

        return String.format(
                "\uD83D\uDFE2 🚗 <b>New Ride Available!</b> — %s\n\n" +
                        "👤 Driver: %s%s\n" +
                        "%s\n" +
                        "📍 <b>%s → %s</b>\n" +
                        "🕐 Pickup start: %s\n" +
                        "💺 Seats: <b>%d</b>\n" +
                        "%s" +
                        "🔖 Ride #%d" +
                        "%s\n\n" +
                        "Tap <b>View | Book a Ride</b> to see details and book your seat. 👇",
                departureDate,
                driverName,
                ratingLabel,
                dirLabel,
                HtmlEscapeUtil.escape(ride.getOriginHub().getName()),
                HtmlEscapeUtil.escape(ride.getDestinationHub().getName()),
                departureTime,
                ride.getAvailableSeats(),
                vehicleLine,
                ride.getId(),
                notesLine.replace("@", "")
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