package com.carpool.bot.service;

import com.carpool.bot.CarpoolBot;
import com.carpool.bot.config.BotConfig;
import com.carpool.common.util.HtmlEscapeUtil;
import com.carpool.domain.entity.Ride;
import com.carpool.repository.RideRepository;
import com.carpool.repository.UserFavoriteRepository;
import com.carpool.service.event.RideEvents;
import com.carpool.service.rating.RatingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import com.carpool.domain.enums.RideStatus;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * Listens for ride domain events and posts announcements to the
 * configured Telegram group topic.
 * <p>
 * Uses @TransactionalEventListener(AFTER_COMMIT) to guarantee the ride
 * is fully persisted before posting. If the DB transaction rolls back,
 * no group message is sent.
 * <p>
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
    private final RideRepository rideRepository;

    @Async
    public void handleNewMembers(Message message) {
        if (!message.getChatId().equals(botConfig.getGroupChatId())) return;

        List<User> humans = message.getNewChatMembers().stream()
                .filter(u -> !u.getIsBot())
                .toList();

        if (humans.isEmpty()) return;

        String names = humans.stream()
                .map(u -> HtmlEscapeUtil.escape(
                        u.getFirstName() + (u.getLastName() != null ? " " + u.getLastName() : "")))
                .collect(Collectors.joining(", "));

        String text = String.format(
                """
                        👋 Welcome to <b>%s</b>, <b>%s</b>!
                        
                        🚗 This is the carpool community for commuters along the <b>%s</b> corridor.
                        
                        Tap the button below to find or post a ride. 👇""",
                HtmlEscapeUtil.escape(botConfig.getCommunityName()),
                names,
                HtmlEscapeUtil.escape(botConfig.getCorridor())
        );

        carpoolBot.sendWelcomeToGroup(text);
        log.info("Welcome message sent for {} new member(s)", humans.size());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRidePosted(RideEvents.RidePostedEvent event) {
        Ride ride = rideRepository.findById(event.ride().getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Ride not found for group announcement: id=" + event.ride().getId()));
        try {
            if (ride.getGroupMessageId() != null) {
                try {
                    carpoolBot.deleteMessage(botConfig.getGroupChatId(), ride.getGroupMessageId());
                    log.info("Deleted previous group announcement before re-announce: rideId={} oldMessageId={}",
                            ride.getId(), ride.getGroupMessageId());
                } catch (Exception e) {
                    log.warn("Could not delete previous group announcement (proceeding with re-announce): rideId={} messageId={} error={}",
                            ride.getId(), ride.getGroupMessageId(), e.getMessage());
                }
            }

            // Ride is FULL — old post already removed above; skip posting a new one
            if (ride.getAvailableSeats() == 0) {
                try {
                    ride.setGroupMessageId(null);
                    ride.setGroupMessagePostedAt(null);
                    rideRepository.save(ride);
                } catch (Exception e) {
                    log.error("Failed to clear groupMessageId for FULL ride: rideId={} error={}", ride.getId(), e.getMessage());
                }
                log.info("Ride is FULL — group announcement removed, not reposted: rideId={}", ride.getId());
                return;
            }

            String message = buildRidePostedMessage(ride);
            Integer messageId = carpoolBot.sendToGroup(message, ride.getId(), ride.getDriver().getId(), resolveTopicId(ride));
            log.info("Ride announcement posted to group: rideId={}", ride.getId());

            if (messageId != null) {
                persistGroupMessageId(ride.getId(), messageId);
            }

            // ── Alert followers on first announcement only (not re-announces) ──
            if (ride.getAnnounceCount() != null && ride.getAnnounceCount() > 1) {
                return;
            }

            List<Long> followerTelegramIds = favoriteRepository
                    .findFollowerTelegramIdsByFavoriteId(ride.getDriver().getId());

            if (!followerTelegramIds.isEmpty()) {
                String departureDate = ride.getDepartureTime()
                        .format(DateTimeFormatter.ofPattern("EEE, MMM d"));
                String departureTime = ride.getDepartureTime()
                        .format(DateTimeFormatter.ofPattern("h:mm a"));

                String alertMsg = String.format(
                        """
                                🔔 <b>Your favorite driver just posted a ride!</b>
                                
                                👤 <b>%s</b>%s
                                📍 %s → %s
                                📅 %s
                                🕐 Pickup start: %s
                                
                                Tap below to view and book:""",
                        HtmlEscapeUtil.escape(ride.getDriver().getFullName()),
                        ride.getDriver().getTelegramHandle() != null
                                ? " (@" + HtmlEscapeUtil.escape(
                                ride.getDriver().getTelegramHandle()) + ")" : "",
                        HtmlEscapeUtil.escape(ride.getOriginHub().getName()),
                        HtmlEscapeUtil.escape(ride.getDestinationHub().getName()),
                        departureDate,
                        departureTime);

                Semaphore sem = new Semaphore(10);
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                    for (Long followerTelegramId : followerTelegramIds) {
                        futures.add(CompletableFuture.runAsync(() -> {
                            try {
                                sem.acquire();
                                try {
                                    carpoolBot.sendToUser(followerTelegramId, alertMsg, ride.getId(), ride.getDriver().getId());
                                } finally {
                                    sem.release();
                                }
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                log.warn("Follower alert interrupted: rideId={}", ride.getId());
                            } catch (Exception e) {
                                log.warn("Failed to send favorite alert to telegramId={} rideId={}: {}",
                                        followerTelegramId, ride.getId(), e.getMessage());
                            }
                        }, executor));
                    }
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                }
                log.info("Favorite alerts sent: rideId={} followers={}",
                        ride.getId(), followerTelegramIds.size());
            }

        } catch (Exception e) {
            log.error("Failed to post ride announcement to group: rideId={} error={}",
                    ride.getId(), e.getMessage());
        }
    }

    // ── Group message cleanup ─────────────────────────────────────────────

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRideDeparted(RideEvents.RideDepartedEvent event) {
        deleteGroupAnnouncement(event.ride());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRideCompleted(RideEvents.RideCompletedEvent event) {
        deleteGroupAnnouncement(event.ride());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRideCancelled(RideEvents.RideCancelledEvent event) {
        deleteGroupAnnouncement(event.ride());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBookingCancelledByPassenger(RideEvents.BookingCancelledByPassengerEvent event) {
        refreshGroupPostAfterSeatFreed(event.booking().getRide().getId(), "passenger cancellation");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBookingCancelledByDriver(RideEvents.BookingCancelledByDriverEvent event) {
        refreshGroupPostAfterSeatFreed(event.booking().getRide().getId(), "driver removal");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBookingAutoSynced(RideEvents.BookingAutoSyncedEvent event) {
        refreshGroupPostAfterSeatFreed(event.booking().getRide().getId(), "auto-sync cancellation");
    }

    private void refreshGroupPostAfterSeatFreed(Long rideId, String reason) {
        Ride ride = rideRepository.findById(rideId).orElse(null);
        if (ride == null || ride.getGroupMessageId() == null) return;
        if (ride.getStatus() != RideStatus.ACTIVE && ride.getStatus() != RideStatus.FULL) return;
        Instant ageReference = ride.getGroupMessagePostedAt() != null
                ? ride.getGroupMessagePostedAt() : ride.getCreatedAt();
        if (ageReference.isBefore(Instant.now().minus(48, ChronoUnit.HOURS))) {
            log.warn("48h guard triggered — stale announcement scheduler may have been down: rideId={}",
                    rideId);
            return;
        }

        try {
            try {
                carpoolBot.deleteMessage(botConfig.getGroupChatId(), ride.getGroupMessageId());
            } catch (Exception e) {
                log.warn("Could not delete old group post before refresh ({}): rideId={} error={}",
                        reason, rideId, e.getMessage());
            }

            String message = buildRidePostedMessage(ride);
            Integer messageId = carpoolBot.sendToGroup(
                    message, ride.getId(), ride.getDriver().getId(), resolveTopicId(ride));
            log.info("Group announcement refreshed after {}: rideId={}", reason, rideId);

            if (messageId != null) {
                persistGroupMessageId(rideId, messageId);
            }
        } catch (Exception e) {
            log.error("Failed to refresh group announcement after {}: rideId={} error={}",
                    reason, rideId, e.getMessage());
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBookingConfirmed(RideEvents.BookingConfirmedEvent event) {
        Long rideId = event.booking().getRide().getId();
        Ride ride = rideRepository.findById(rideId).orElse(null);
        if (ride == null || ride.getGroupMessageId() == null) return;

        try {
            if (ride.getAvailableSeats() == 0) {
                deleteGroupAnnouncement(ride);
                return;
            }

            try {
                carpoolBot.deleteMessage(botConfig.getGroupChatId(), ride.getGroupMessageId());
            } catch (Exception e) {
                log.warn("Could not delete old group post before booking repost: rideId={} error={}",
                        rideId, e.getMessage());
            }

            String message = buildRidePostedMessage(ride);
            Integer messageId = carpoolBot.sendToGroup(message, ride.getId(), ride.getDriver().getId(), resolveTopicId(ride));
            log.info("Group announcement refreshed after booking confirmed: rideId={}", rideId);

            if (messageId != null) {
                persistGroupMessageId(rideId, messageId);
            }
        } catch (Exception e) {
            log.error("Failed to refresh group announcement after booking confirmed: rideId={} error={}",
                    rideId, e.getMessage());
        }
    }

    private void deleteGroupAnnouncement(Ride ride) {
        Integer messageId = ride.getGroupMessageId();
        if (messageId == null) return;
        Instant ageReference = ride.getGroupMessagePostedAt() != null
                ? ride.getGroupMessagePostedAt() : ride.getCreatedAt();
        if (ageReference.isBefore(Instant.now().minus(48, ChronoUnit.HOURS))) {
            log.warn("48h guard triggered — stale announcement scheduler may have been down: rideId={}",
                    ride.getId());
            return;
        }
        boolean deleted = carpoolBot.deleteMessage(botConfig.getGroupChatId(), messageId);
        if (deleted) {
            log.info("Deleted group announcement: rideId={} messageId={}", ride.getId(), messageId);
            try {
                rideRepository.findById(ride.getId()).ifPresent(r -> {
                    r.setGroupMessageId(null);
                    r.setGroupMessagePostedAt(null);
                    rideRepository.save(r);
                });
            } catch (Exception e) {
                log.error("Failed to clear groupMessageId after deletion: rideId={} error={}",
                        ride.getId(), e.getMessage());
            }
        }
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refreshGroupAnnouncementForRide(Long rideId) {
        Ride ride = rideRepository.findById(rideId).orElse(null);
        if (ride == null || ride.getGroupMessageId() == null) return;
        if (ride.getStatus() != RideStatus.ACTIVE && ride.getStatus() != RideStatus.FULL) return;

        try {
            try {
                carpoolBot.deleteMessage(botConfig.getGroupChatId(), ride.getGroupMessageId());
            } catch (Exception e) {
                log.warn("Could not delete old group post before stale refresh: rideId={} error={}",
                        rideId, e.getMessage());
            }

            String message = buildRidePostedMessage(ride);
            Integer messageId = carpoolBot.sendToGroup(
                    message, ride.getId(), ride.getDriver().getId(), resolveTopicId(ride));
            log.info("Stale group announcement refreshed: rideId={}", rideId);

            if (messageId != null) {
                persistGroupMessageId(rideId, messageId);
            }
        } catch (Exception e) {
            log.error("Failed to refresh stale group announcement: rideId={} error={}",
                    rideId, e.getMessage());
        }
    }

    private void persistGroupMessageId(Long rideId, Integer messageId) {
        try {
            rideRepository.findById(rideId).ifPresent(r -> {
                r.setGroupMessageId(messageId);
                r.setGroupMessagePostedAt(Instant.now());
                rideRepository.save(r);
            });
        } catch (Exception e) {
            log.error("Failed to store groupMessageId: rideId={} messageId={} error={}",
                    rideId, messageId, e.getMessage());
        }
    }

    // ── Message formatter ─────────────────────────────────────────────────

    private String buildRidePostedMessage(Ride ride) {

        String ratingLabel = ratingService.getRideCardRatingLabel(ride.getDriver().getId());

        String departureDate = ride.getDepartureTime().format(DateTimeFormatter.ofPattern("EEE, MMM d"));
        String departureTime = ride.getDepartureTime().format(DateTimeFormatter.ofPattern("h:mm a"));

        String dirLabel = switch (ride.getDirection()) {
            case HOME_TO_WORK -> "🏠 Home → 🏢 Work";
            case WORK_TO_HOME -> "🏢 Work → 🏠 Home";
            default           -> "🚗 Carpool Ride";
        };

        String driverName = HtmlEscapeUtil.escape(ride.getDriver().getFullName());

        String notesLine = (ride.getNotes() != null && !ride.getNotes().isBlank())
                ? "\n" + HtmlEscapeUtil.escape(ride.getNotes())
                : "";

        String vehicleLine;
        if (ride.getVehicle() != null) {
            vehicleLine = String.format("%s%s\n",
                    ride.getVehicle().getColor() != null
                            ? HtmlEscapeUtil.escape(ride.getVehicle().getColor()) + " " : "",
                    HtmlEscapeUtil.escape(ride.getVehicle().getModel()));
        } else if (ride.getDriver().getCarModel() != null) {
            // Fallback for rides created before multi-vehicle migration
            vehicleLine = String.format("%s%s\n",
                    ride.getDriver().getCarColor() != null
                            ? HtmlEscapeUtil.escape(ride.getDriver().getCarColor()) + " " : "",
                    HtmlEscapeUtil.escape(ride.getDriver().getCarModel()));
        } else {
            vehicleLine = "";
        }

        return String.format(
                """
                        <b>%s</b>

                        🔖 Ride #%d — <b>%s</b>%s
                        📅 <b>%s</b>
                        🕐 Pickup: <b>%s</b> | Seats: <b>%d</b>
                        🚘 Vehicle: %s\
                        📍 Route: <b>%s → %s</b>
                        %s

                        Tap <b>Request 💺</b> to see details and book your seat. 👇""",
                dirLabel,
                ride.getId(),
                driverName,
                ratingLabel,
                departureDate,
                departureTime,
                ride.getAvailableSeats(),
                vehicleLine,
                HtmlEscapeUtil.escape(ride.getOriginHub().getName()),
                HtmlEscapeUtil.escape(ride.getDestinationHub().getName()),
                notesLine.replace("@", "")
        );
    }

    private Integer resolveTopicId(Ride ride) {
        Integer homeToWork = botConfig.getGroupHomeToWorkTopicId();
        return switch (ride.getDirection()) {
            case HOME_TO_WORK -> homeToWork;
            case WORK_TO_HOME -> botConfig.getGroupWorkToHomeTopicId();
            default           -> botConfig.getGroupHomeToWorkTopicId();
        };
    }
}