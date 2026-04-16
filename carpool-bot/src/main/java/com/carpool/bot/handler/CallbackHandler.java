package com.carpool.bot.handler;

import com.carpool.bot.CarpoolBot;
import com.carpool.bot.state.BotFlow;
import com.carpool.bot.state.StateManager;
import com.carpool.bot.state.UserState;
import com.carpool.bot.util.BotMessageBuilder;
import com.carpool.domain.enums.RideDirection;
import com.carpool.domain.enums.RideStatus;
import com.carpool.repository.UserRepository;
import com.carpool.service.dto.request.CreateBookingRequest;
import com.carpool.service.dto.request.CreateRideRequest;
import com.carpool.service.dto.request.UpdateRideStatusRequest;
import com.carpool.service.dto.response.BookingResponse;
import com.carpool.service.dto.response.RideResponse;
import com.carpool.service.booking.BookingService;
import com.carpool.service.ride.RideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.springframework.stereotype.Component;
import com.carpool.domain.enums.BookingStatus;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Handles all inline button callbacks (callbackQuery).
 * Callback data format: "ACTION" or "ACTION:id" or "ACTION:string"
 * Examples:
 *   "POST_RIDE"
 *   "FIND_RIDE"
 *   "CONFIRM_POST_RIDE"
 *   "VIEW_RIDE:123"
 *   "BOOK_RIDE:123"
 *   "CANCEL_RIDE:123"
 *   "RIDE_BOOKINGS:123"
 *   "MY_BOOKINGS"
 *   "TIME:MORNING"
 *   "MAIN_MENU"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CallbackHandler {

    private final StateManager   stateManager;
    private final UserRepository userRepository;
    private final RideService    rideService;
    private final BookingService bookingService;

    public void handle(CallbackQuery callback, CarpoolBot bot) {
        Long chatId     = callback.getMessage().getChatId();
        Long telegramId = callback.getFrom().getId();
        String data     = callback.getData();

        bot.answerCallback(callback.getId());

        var userOpt = userRepository.findByTelegramId(telegramId);
        if (userOpt.isEmpty()) {
            bot.send(BotMessageBuilder.text(chatId, "⚠️ Please register first via /start"));
            return;
        }

        Long carpoolUserId = userOpt.get().getId();
        UserState state    = stateManager.get(chatId);
        if (state == null) {
            state = UserState.initial(carpoolUserId);
        }

        // payload can be a numeric ID (VIEW_RIDE:123) or a string (TIME:MORNING)
        String[] parts   = data.split(":");
        String   action  = parts[0];
        String   payload = parts.length > 1 ? parts[1] : null;

        // Parse numeric ID only when needed — avoid parsing string payloads
        Long entityId = null;
        if (payload != null) {
            try {
                entityId = Long.parseLong(payload);
            } catch (NumberFormatException e) {
                // payload is a string value, not a numeric ID — handled per case
            }
        }

        switch (action) {
            case "MAIN_MENU" -> {
                stateManager.reset(chatId);
                bot.send(BotMessageBuilder.directionSelector(chatId,
                        "👋 What would you like to do?"));
            }
            case "POST_RIDE"         -> handleStartPostRide(chatId, carpoolUserId, state, bot);
            case "FIND_RIDE"         -> handleStartFindRide(chatId, carpoolUserId, state, bot);
            case "CONFIRM_POST_RIDE" -> handleConfirmPostRide(chatId, carpoolUserId, state, bot);
            case "CANCEL_POST_RIDE"  -> handleCancelPostRide(chatId, bot);
            case "VIEW_RIDE"         -> handleViewRide(chatId, entityId, carpoolUserId, state, bot);
            case "BOOK_RIDE"         -> handleBookRide(chatId, entityId, carpoolUserId, state, bot);
            case "CANCEL_RIDE"       -> handleCancelRide(chatId, entityId, carpoolUserId, bot);
            case "RIDE_BOOKINGS"     -> handleRideBookings(chatId, entityId, carpoolUserId, bot);
            case "MY_BOOKINGS"       -> handleMyBookings(chatId, carpoolUserId, bot);
            case "REPOST_RIDE"       -> handleRepostRide(chatId, entityId, carpoolUserId, state, bot);
            case "VIEW_BOOKING"      -> handleViewBooking(chatId, entityId, carpoolUserId, state, bot);
            case "PAST_BOOKINGS"     -> handlePastBookings(chatId, carpoolUserId, bot);
            case "DRIVER_BOOKINGS"   -> handleDriverBookings(chatId, carpoolUserId, bot);
            case "TIME"              -> handleTimeSelection(chatId, payload, carpoolUserId, state, bot);
            default -> {
                log.warn("Unknown callback action: {} from chatId={}", action, chatId);
                bot.send(BotMessageBuilder.text(chatId, "⚠️ Unknown action."));
            }
        }
    }

    // ── Post ride ─────────────────────────────────────────────────────────

    private void handleStartPostRide(Long chatId, Long carpoolUserId,
                                     UserState state, CarpoolBot bot) {
        bot.send(BotMessageBuilder.textWithRemoveKeyboard(chatId,
                """
                        🕐 *When is your departure time?*
                        
                        Format: `MM/DD HH:MM`
                        Example: `04/16 07:30`
                        
                        Type /cancel to abort."""));

        stateManager.save(chatId, state
                .withCarpoolUserId(carpoolUserId)
                .withFlow(BotFlow.POST_RIDE_DEPARTURE_TIME));
    }

    private void handleCancelPostRide(Long chatId, CarpoolBot bot) {
        stateManager.reset(chatId);
        bot.send(BotMessageBuilder.text(chatId,
                "❌ Ride posting cancelled. Use /start to go back."));
    }

    private void handleConfirmPostRide(Long chatId, Long carpoolUserId,
                                       UserState state, CarpoolBot bot) {
        if (state == null || state.getOriginHubId() == null) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Session expired. Please start again with /start."));
            return;
        }

        try {
            // Auto-upgrade role to BOTH if currently PASSENGER
            userRepository.findById(carpoolUserId).ifPresent(user -> {
                if (user.getRole() == com.carpool.domain.enums.UserRole.PASSENGER) {
                    user.setRole(com.carpool.domain.enums.UserRole.BOTH);
                    userRepository.save(user);
                    log.info("Auto-upgraded userId={} role to BOTH on first ride post", carpoolUserId);
                }
            });

            CreateRideRequest request = new CreateRideRequest(
                    state.getOriginHubId(),
                    state.getDestinationHubId(),
                    state.getDirection(),
                    state.getDepartureTime(),
                    state.getSeats(),
                    state.getContribution() != null ? state.getContribution() : BigDecimal.ZERO,
                    state.getNotes(),
                    null
            );

            RideResponse ride = rideService.createRide(request, carpoolUserId);

            // Auto-publish: DRAFT → ACTIVE
            rideService.updateRideStatus(ride.id(),
                    new UpdateRideStatusRequest(RideStatus.ACTIVE), carpoolUserId);

            stateManager.save(chatId, state
                    .withLastPostedRideId(ride.id())
                    .withFlow(BotFlow.IDLE));

            bot.send(BotMessageBuilder.text(chatId,
                    "✅ *Ride posted successfully!*\n\n" +
                            BotMessageBuilder.formatRideCard(ride) +
                            "\n\nPassengers can now find and book your ride."));

        } catch (Exception e) {
            log.error("Failed to post ride for userId={}: {}", carpoolUserId, e.getMessage());
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Failed to post ride: " + e.getMessage() +
                            "\n\nPlease try again with /start."));
            stateManager.reset(chatId);
        }
    }

    // ── Find ride ─────────────────────────────────────────────────────────

    private void handleStartFindRide(Long chatId, Long carpoolUserId,
                                     UserState state, CarpoolBot bot) {
        bot.send(BotMessageBuilder.directionSelector(chatId,
                "🔍 *Find a Ride*\n\nWhich direction are you looking for?"));
        stateManager.save(chatId, state
                .withCarpoolUserId(carpoolUserId)
                .withFlow(BotFlow.SEARCH_SELECT_DIRECTION));
    }

    // ── Time selection ────────────────────────────────────────────────────

    private void handleTimeSelection(Long chatId, String timeSlot, Long carpoolUserId,
                                     UserState state, CarpoolBot bot) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from;
        LocalDateTime to;

        switch (timeSlot) {
            case "EARLY_MORNING" -> {
                from = now.toLocalDate().atTime(5, 0);
                to   = now.toLocalDate().atTime(7, 0);
            }
            case "MORNING" -> {
                from = now.toLocalDate().atTime(7, 0);
                to   = now.toLocalDate().atTime(9, 0);
            }
            case "MID_MORNING" -> {
                from = now.toLocalDate().atTime(9, 0);
                to   = now.toLocalDate().atTime(11, 0);
            }
            case "AFTERNOON" -> {
                from = now.toLocalDate().atTime(15, 0);
                to   = now.toLocalDate().atTime(19, 0);
            }
            case "ALL_TODAY" -> {
                from = now;
                to   = now.toLocalDate().atTime(23, 59);
            }
            default -> {
                // CUSTOM — ask user to type time
                bot.send(BotMessageBuilder.text(chatId,
                        """
                                🕐 *Enter your preferred departure time:*
                                
                                Format: `HH:MM`
                                Example: `07:30`"""));
                stateManager.save(chatId, state.withFlow(BotFlow.SEARCH_SELECT_TIME));
                return;
            }
        }

        showFilteredRides(chatId, carpoolUserId, state, from, to, bot);
    }

    private void showFilteredRides(Long chatId, Long carpoolUserId, UserState state,
                                   LocalDateTime from, LocalDateTime to, CarpoolBot bot) {
        if (state.getDirection() == null) {
            bot.send(BotMessageBuilder.text(chatId, "⚠️ Session expired. Use /start."));
            return;
        }

        List<RideResponse> rides = rideService.getRidesByDirection(
                state.getDirection(), carpoolUserId, from, to);

        String dirLabel = state.getDirection() == RideDirection.HOME_TO_WORK
                ? "🏠 Home → Work" : "🏢 Work → Home";

        stateManager.save(chatId, state
                .withSearchFrom(from)
                .withSearchTo(to)
                .withFlow(BotFlow.SEARCH_RESULTS));

        bot.send(BotMessageBuilder.rideList(chatId, rides,
                "🔍 *Available Rides — " + dirLabel + "*"));
    }

    // ── View ride detail ──────────────────────────────────────────────────

    private void handleViewRide(Long chatId, Long rideId, Long carpoolUserId,
                                UserState state, CarpoolBot bot) {
        try {
            RideResponse ride = rideService.getRideById(rideId);
            stateManager.save(chatId, state.withSelectedRideId(rideId));

            String card = BotMessageBuilder.formatRideCard(ride);
            boolean isDriver = ride.driver().id().equals(carpoolUserId);

            List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> rows;

            if (isDriver) {
                rows = List.of(List.of(
                        BotMessageBuilder.button("📋 Bookings", "RIDE_BOOKINGS:" + rideId),
                        BotMessageBuilder.button("❌ Cancel",   "CANCEL_RIDE:"   + rideId)
                ));
            } else {
                rows = List.of(List.of(
                        BotMessageBuilder.button("✅ Book This Ride", "BOOK_RIDE:" + rideId)
                ));
            }

            bot.send(org.telegram.telegrambots.meta.api.methods.send.SendMessage.builder()
                    .chatId(chatId)
                    .text(card)
                    .parseMode("Markdown")
                    .replyMarkup(BotMessageBuilder.inlineButtons(rows))
                    .build());

        } catch (Exception e) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Could not load ride details. It may have been cancelled."));
        }
    }

    // ── Book ride ─────────────────────────────────────────────────────────

    private void handleBookRide(Long chatId, Long rideId, Long carpoolUserId,
                                UserState state, CarpoolBot bot) {
        try {
            CreateBookingRequest request = new CreateBookingRequest(1, null, null);
            bookingService.createBooking(rideId, request, carpoolUserId);

            bot.send(BotMessageBuilder.text(chatId,
                    """
                            ✅ *Booking Confirmed!*
                            
                            Your seat has been reserved. The driver has been notified.
                            
                            Use /mybookings to view your bookings."""));

        } catch (Exception e) {
            log.error("Booking failed: rideId={} userId={} error={}", rideId, carpoolUserId, e.getMessage());
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Could not book this ride: " + e.getMessage()));
        }
    }

    // ── Cancel ride ───────────────────────────────────────────────────────

    private void handleCancelRide(Long chatId, Long rideId, Long carpoolUserId, CarpoolBot bot) {
        try {
            rideService.updateRideStatus(rideId,
                    new UpdateRideStatusRequest(RideStatus.CANCELLED), carpoolUserId);
            stateManager.reset(chatId);
            bot.send(BotMessageBuilder.text(chatId,
                    "✅ Ride cancelled. All passengers have been notified."));

        } catch (Exception e) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Could not cancel ride: " + e.getMessage()));
        }
    }

    // ── Ride bookings (driver view) ───────────────────────────────────────

    private void handleRideBookings(Long chatId, Long rideId, Long carpoolUserId, CarpoolBot bot) {
        handleDriverBookings(chatId, carpoolUserId, bot);
    }

    // ── My bookings (passenger view) ──────────────────────────────────────

    private void handleMyBookings(Long chatId, Long carpoolUserId, CarpoolBot bot) {
        List<BookingResponse> active = bookingService.getMyBookings(carpoolUserId);
        List<BookingResponse> past   = bookingService.getMyPastBookings(carpoolUserId);

        if (active.isEmpty() && past.isEmpty()) {
            bot.send(BotMessageBuilder.textWithBackButton(chatId,
                    """
                            📜 *My Bookings*
                            
                            _You have no bookings yet._
                            
                            Use 🔍 Find a Ride to book a carpool."""));
            return;
        }

        StringBuilder sb = new StringBuilder("📜 *My Active Bookings*\n\n");
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        if (active.isEmpty()) {
            sb.append("_No active bookings._\n\n");
        } else {
            for (int i = 0; i < active.size(); i++) {
                BookingResponse b = active.get(i);
                sb.append(String.format("*%d.* %s → %s | 🕐 %s | ₱%.2f\n",
                        i + 1,
                        b.ride().originHub().name(),
                        b.ride().destinationHub().name(),
                        b.ride().departureTime()
                                .atZone(ZoneId.of("Asia/Manila"))
                                .format(DateTimeFormatter.ofPattern("MMM d h:mma")),
                        b.contributionDue()));

                rows.add(List.of(InlineKeyboardButton.builder()
                        .text("View #" + (i + 1))
                        .callbackData("VIEW_BOOKING:" + b.id())
                        .build()));
            }
        }

        if (!past.isEmpty()) {
            rows.add(List.of(InlineKeyboardButton.builder()
                    .text("📂 Past Bookings (" + past.size() + ")")
                    .callbackData("PAST_BOOKINGS")
                    .build()));
        }

        rows.add(List.of(InlineKeyboardButton.builder()
                .text("🏠 Menu")
                .callbackData("MAIN_MENU")
                .build()));

        bot.send(SendMessage.builder()
                .chatId(chatId)
                .text(sb.toString())
                .parseMode("Markdown")
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboard(rows.stream().map(InlineKeyboardRow::new).toList())
                        .build())
                .build());
    }

    // ── Repost ride ───────────────────────────────────────────────────────

    private void handleRepostRide(Long chatId, Long rideId, Long carpoolUserId,
                                  UserState state, CarpoolBot bot) {
        try {
            RideResponse original = rideService.getRideById(rideId);

            bot.send(BotMessageBuilder.text(chatId,
                    "🔄 *Repost Ride*\n\n" +
                            "Previous ride: *" + original.originHub().name() +
                            " → " + original.destinationHub().name() + "*\n\n" +
                            "🕐 *What time is your departure?*\n" +
                            "Format: `MM/DD HH:MM`\n\n" +
                            "Type /cancel to abort."));

            stateManager.save(chatId, state
                    .withOriginHubId(original.originHub().id())
                    .withOriginHubName(original.originHub().name())
                    .withDestinationHubId(original.destinationHub().id())
                    .withDestinationHubName(original.destinationHub().name())
                    .withDirection(original.direction())
                    .withSeats(original.totalSeats())
                    .withContribution(original.contributionAmount())
                    .withNotes(original.notes())
                    .withFlow(BotFlow.POST_RIDE_DEPARTURE_TIME));

        } catch (Exception e) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Could not load previous ride for repost."));
        }
    }

    private void handleViewBooking(Long chatId, Long bookingId, Long carpoolUserId,
                                   UserState state, CarpoolBot bot) {
        try {
            BookingResponse b = bookingService.getBookingById(bookingId);

            String statusLabel = switch (b.status().name()) {
                case "CONFIRMED"              -> "✅ Confirmed";
                case "PENDING"                -> "⏳ Pending";
                case "CANCELLED_BY_PASSENGER" -> "❌ Cancelled by you";
                case "CANCELLED_BY_DRIVER"    -> "❌ Cancelled by driver";
                case "COMPLETED"              -> "🏁 Completed";
                default                       -> b.status().name();
            };

            String pickup  = b.pickupWaypoint()  != null
                    ? b.pickupWaypoint().hub().name()
                    : b.ride().originHub().name();
            String dropOff = b.dropoffWaypoint() != null
                    ? b.dropoffWaypoint().hub().name()
                    : b.ride().destinationHub().name();

            String detail = String.format(
                    """
                            📋 *Booking Details*
                            
                            🚗 %s → %s
                            🕐 %s
                            🚏 Pickup: *%s*
                            🏁 Drop off: *%s*
                            🪑 Seats: %d
                            💵 Contribution: ₱%.2f
                            👤 Driver: %s%s
                            📊 Status: %s""",
                    b.ride().originHub().name(),
                    b.ride().destinationHub().name(),
                    b.ride().departureTime()
                            .atZone(ZoneId.of("Asia/Manila"))
                            .format(DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a")),
                    pickup, dropOff,
                    b.seatsReserved(),
                    b.contributionDue(),
                    b.ride().driver().fullName(),
                    b.ride().driver().telegramHandle() != null
                            ? " (@" + b.ride().driver().telegramHandle() + ")" : "",
                    statusLabel);

            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            // Only show cancel if booking is still active
            if (b.status() == BookingStatus.CONFIRMED || b.status() == BookingStatus.PENDING) {
                rows.add(List.of(InlineKeyboardButton.builder()
                        .text("❌ Cancel Booking")
                        .callbackData("CANCEL_BOOKING:" + bookingId)
                        .build()));
            }

            rows.add(List.of(InlineKeyboardButton.builder()
                    .text("◀️ Back to My Bookings")
                    .callbackData("MY_BOOKINGS")
                    .build()));

            bot.send(SendMessage.builder()
                    .chatId(chatId)
                    .text(detail)
                    .parseMode("Markdown")
                    .replyMarkup(InlineKeyboardMarkup.builder()
                            .keyboard(rows.stream().map(InlineKeyboardRow::new).toList())
                            .build())
                    .build());

        } catch (Exception e) {
            bot.send(BotMessageBuilder.text(chatId, "⚠️ Could not load booking details."));
        }
    }

    private void handlePastBookings(Long chatId, Long carpoolUserId, CarpoolBot bot) {
        List<BookingResponse> past = bookingService.getMyPastBookings(carpoolUserId);

        if (past.isEmpty()) {
            bot.send(BotMessageBuilder.text(chatId, "📂 *Past Bookings*\n\n_No past bookings._"));
            return;
        }

        StringBuilder sb = new StringBuilder("📂 *Past Bookings*\n\n");
        for (int i = 0; i < past.size(); i++) {
            BookingResponse b = past.get(i);
            String statusLabel = switch (b.status().name()) {
                case "CANCELLED_BY_PASSENGER" -> "❌ You cancelled";
                case "CANCELLED_BY_DRIVER"    -> "❌ Driver cancelled";
                case "COMPLETED"              -> "🏁 Completed";
                default                       -> b.status().name();
            };
            sb.append(String.format("*%d.* %s → %s | %s\n",
                    i + 1,
                    b.ride().originHub().name(),
                    b.ride().destinationHub().name(),
                    statusLabel));
        }

        var rows = List.of(List.of(InlineKeyboardButton.builder()
                .text("◀️ Back to My Bookings")
                .callbackData("MY_BOOKINGS")
                .build()));

        bot.send(SendMessage.builder()
                .chatId(chatId)
                .text(sb.toString())
                .parseMode("Markdown")
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboard(rows.stream().map(InlineKeyboardRow::new).toList())
                        .build())
                .build());
    }

    private void handleDriverBookings(Long chatId, Long carpoolUserId, CarpoolBot bot) {
        List<BookingResponse> bookings = bookingService.getBookingsForDriver(carpoolUserId);

        if (bookings.isEmpty()) {
            bot.send(BotMessageBuilder.text(chatId,
                    "📋 *Ride Bookings*\n\n_No passengers have booked your ride yet._"));
            return;
        }

        StringBuilder sb = new StringBuilder("📋 *Passengers on your ride*\n\n");
        for (int i = 0; i < bookings.size(); i++) {
            BookingResponse b = bookings.get(i);
            sb.append(String.format("*%d.* %s%s | 🪑 %d seat(s) | ₱%.2f\n",
                    i + 1,
                    b.passenger().fullName(),
                    b.passenger().telegramHandle() != null
                            ? " (@" + b.passenger().telegramHandle() + ")" : "",
                    b.seatsReserved(),
                    b.contributionDue()));
        }

        var rows = List.of(List.of(InlineKeyboardButton.builder()
                .text("🏠 Menu")
                .callbackData("MAIN_MENU")
                .build()));

        bot.send(SendMessage.builder()
                .chatId(chatId)
                .text(sb.toString())
                .parseMode("Markdown")
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboard(rows.stream().map(InlineKeyboardRow::new).toList())
                        .build())
                .build());
    }
}