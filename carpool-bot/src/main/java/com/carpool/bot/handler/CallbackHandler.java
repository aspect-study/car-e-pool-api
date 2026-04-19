package com.carpool.bot.handler;

import com.carpool.bot.CarpoolBot;
import com.carpool.bot.state.BotFlow;
import com.carpool.bot.state.StateManager;
import com.carpool.bot.state.UserState;
import com.carpool.bot.util.BotMessageBuilder;
import com.carpool.domain.enums.BookingStatus;
import com.carpool.domain.enums.RideDirection;
import com.carpool.domain.enums.RideStatus;
import com.carpool.repository.UserRepository;
import com.carpool.service.booking.BookingService;
import com.carpool.service.dto.request.CreateBookingRequest;
import com.carpool.service.dto.request.CreateRideRequest;
import com.carpool.service.dto.request.UpdateRideStatusRequest;
import com.carpool.service.dto.response.BookingResponse;
import com.carpool.service.dto.response.RideResponse;
import com.carpool.service.ride.RideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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

        String[] parts   = data.split(":");
        String   action  = parts[0];
        String   payload = parts.length > 1 ? parts[1] : null;

        Long entityId = null;
        if (payload != null) {
            try {
                entityId = Long.parseLong(payload);
            } catch (NumberFormatException e) {
                // string payload — handled per case
            }
        }

        switch (action) {
            case "MAIN_MENU"         -> handleMainMenu(chatId, carpoolUserId, state, bot);
            case "POST_RIDE"         -> handleStartPostRide(chatId, carpoolUserId, state, bot);
            case "FIND_RIDE"         -> handleStartFindRide(chatId, carpoolUserId, state, bot);
            case "CONFIRM_POST_RIDE" -> handleConfirmPostRide(chatId, carpoolUserId, state, bot);
            case "CANCEL_POST_RIDE"  -> handleCancelPostRide(chatId, bot);
            case "VIEW_RIDE"         -> handleViewRide(chatId, entityId, carpoolUserId, state, bot);
            case "BOOK_RIDE"         -> handleBookRide(chatId, entityId, carpoolUserId, bot);
            case "CANCEL_RIDE"         -> handleCancelRide(chatId, entityId, carpoolUserId, bot);
            case "CONFIRM_CANCEL_RIDE" -> executeCancelRide(chatId, entityId, carpoolUserId, bot);
            case "RIDE_BOOKINGS"     -> handleDriverBookings(chatId, carpoolUserId, bot);
            case "MY_BOOKINGS"       -> handleMyBookings(chatId, carpoolUserId, bot);
            case "REPOST_RIDE"       -> handleRepostRide(chatId, entityId, carpoolUserId, state, bot);
            case "VIEW_BOOKING"      -> handleViewBooking(chatId, entityId, carpoolUserId, state, bot);
            case "PAST_BOOKINGS"     -> handlePastBookings(chatId, carpoolUserId, bot);
            case "DRIVER_BOOKINGS"   -> handleDriverBookings(chatId, carpoolUserId, bot);
            case "TIME"              -> handleTimeSelection(chatId, payload, carpoolUserId, state, bot);
            case "CANCEL_BOOKING"    -> handleCancelBooking(chatId, entityId, carpoolUserId, bot);
            case "DEPART_RIDE"   -> handleDepartRide(chatId, entityId, carpoolUserId, bot);
            case "COMPLETE_RIDE" -> handleCompleteRide(chatId, entityId, carpoolUserId, bot);
            case "VIEW_DRIVER_BOOKING" -> handleViewDriverBooking(chatId, entityId, carpoolUserId, bot);
            case "SKIP_NOTES" -> handleSkipNotes(chatId, carpoolUserId, state, bot);
            case "DIRECTION" -> handleDirectionCallback(chatId, payload, carpoolUserId, state, bot);
            default -> {
                log.warn("Unknown callback action: {} from chatId={}", action, chatId);
                bot.send(BotMessageBuilder.text(chatId, "⚠️ Unknown action."));
            }
        }
    }

    // ── Main menu ─────────────────────────────────────────────────────────

    private void handleMainMenu(Long chatId, Long carpoolUserId,
                                UserState state, CarpoolBot bot) {
        stateManager.reset(chatId);
        List<RideResponse> myRides = rideService.getMyRides(carpoolUserId);
        boolean hasActiveRide = myRides.stream()
                .anyMatch(r -> r.status().name().equals("ACTIVE")
                        || r.status().name().equals("FULL")
                        || r.status().name().equals("DEPARTED"));

        if (hasActiveRide) {
            RideResponse active = myRides.stream()
                    .filter(r -> r.status().name().equals("ACTIVE")
                            || r.status().name().equals("FULL")
                            || r.status().name().equals("DEPARTED"))
                    .findFirst().orElseThrow();

            String msg = "🚗 <b>Your Active Ride</b>\n\n" +
                    BotMessageBuilder.formatRideCard(active) +
                    "\n\nWhat would you like to do?";

            var rows = active.status().name().equals("DEPARTED")
                    ? List.of(
                    List.of(
                            BotMessageBuilder.button("📋 View Bookings", "RIDE_BOOKINGS:" + active.id()),
                            BotMessageBuilder.button("✅ Complete Ride",  "COMPLETE_RIDE:" + active.id())
                    ),
                    List.of(
                            BotMessageBuilder.button("🔍 Find a Ride", "FIND_RIDE")
                    )
            )
                    : List.of(
                    List.of(
                            BotMessageBuilder.button("📋 View Bookings", "RIDE_BOOKINGS:" + active.id()),
                            BotMessageBuilder.button("🚀 Start Ride",    "DEPART_RIDE:"  + active.id())
                    ),
                    List.of(
                            BotMessageBuilder.button("❌ Cancel Ride",   "CANCEL_RIDE:"  + active.id())
                    ),
                    List.of(
                            BotMessageBuilder.button("🔍 Find a Ride",  "FIND_RIDE")
                    )
            );

            bot.send(sendWithInline(chatId, msg, rows));
        } else {
            bot.send(BotMessageBuilder.directionSelector(chatId,
                    "👋 Where are you headed today?"));
        }
    }

    // ── Post ride ─────────────────────────────────────────────────────────

    private void handleStartPostRide(Long chatId, Long carpoolUserId,
                                     UserState state, CarpoolBot bot) {
        // Direction is required — ask if not yet set in state
        if (state.getDirection() == null) {
            bot.send(BotMessageBuilder.directionSelector(chatId,
                    "🚗 <b>Post a Ride</b>\n\nWhich direction is this ride?"));
            stateManager.save(chatId, state
                    .withCarpoolUserId(carpoolUserId)
                    .withFlow(BotFlow.POST_RIDE_DIRECTION));
            return;
        }

        bot.send(BotMessageBuilder.textWithRemoveKeyboard(chatId,
                "🕐 <b>What time are you leaving?</b>\n\n" +
                        "Format: <code>MM/DD HH:MM</code>\n" +
                        "Example: <code>" +
                        LocalDateTime.now().plusHours(1)
                                .format(DateTimeFormatter.ofPattern("MM/dd HH:mm")) +
                        "</code>\n\n" +
                        "Type /cancel to go back."));
        stateManager.save(chatId, state
                .withCarpoolUserId(carpoolUserId)
                .withFlow(BotFlow.POST_RIDE_DEPARTURE_TIME));
    }

    private void handleCancelPostRide(Long chatId, CarpoolBot bot) {
        stateManager.reset(chatId);
        bot.send(BotMessageBuilder.text(chatId,
                "❌ Ride posting cancelled."));
    }

    private void handleConfirmPostRide(Long chatId, Long carpoolUserId,
                                       UserState state, CarpoolBot bot) {
        if (state == null || state.getOriginHubId() == null) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Session expired. Please start again with /start."));
            return;
        }

        try {
            userRepository.findById(carpoolUserId).ifPresent(user -> {
                if (user.getRole() == com.carpool.domain.enums.UserRole.PASSENGER) {
                    user.setRole(com.carpool.domain.enums.UserRole.BOTH);
                    userRepository.save(user);
                    log.info("Auto-upgraded userId={} role to BOTH", carpoolUserId);
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
                    null);

            RideResponse ride = rideService.createRide(request, carpoolUserId);
            rideService.updateRideStatus(ride.id(),
                    new UpdateRideStatusRequest(RideStatus.ACTIVE), carpoolUserId);

            stateManager.save(chatId, state
                    .withLastPostedRideId(ride.id())
                    .withFlow(BotFlow.IDLE));

            bot.send(BotMessageBuilder.text(chatId,
                    "✅ <b>Ride posted successfully!</b>\n\n" +
                            BotMessageBuilder.formatRideCard(ride) +
                            "\n\nPassengers can now find and book your ride."));

        } catch (Exception e) {
            log.error("Failed to post ride for userId={}: {}", carpoolUserId, e.getMessage());
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Failed to post ride: " +
                            BotMessageBuilder.escape(e.getMessage())));
            stateManager.reset(chatId);
        }
    }

    // ── Find ride ─────────────────────────────────────────────────────────

    private void handleStartFindRide(Long chatId, Long carpoolUserId,
                                     UserState state, CarpoolBot bot) {
        bot.send(BotMessageBuilder.directionSelector(chatId,
                "🔍 <b>Find a Ride</b>\n\nWhich direction are you looking for?"));
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
            case "EARLY_MORNING" -> { from = now.toLocalDate().atTime(5, 0);  to = now.toLocalDate().atTime(7, 0); }
            case "MORNING"       -> { from = now.toLocalDate().atTime(7, 0);  to = now.toLocalDate().atTime(9, 0); }
            case "MID_MORNING"   -> { from = now.toLocalDate().atTime(9, 0);  to = now.toLocalDate().atTime(11, 0); }
            case "AFTERNOON"     -> { from = now.toLocalDate().atTime(15, 0); to = now.toLocalDate().atTime(19, 0); }
            case "ALL_TODAY"     -> { from = now; to = now.toLocalDate().atTime(23, 59); }
            default -> {
                bot.send(BotMessageBuilder.text(chatId,
                        "🕐 <b>Enter your preferred departure time:</b>\n\n" +
                                "Format: <code>HH:MM</code>\nExample: <code>07:30</code>"));
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
                "🔍 <b>Available Rides — " + dirLabel + "</b>"));
    }

    // ── View ride detail ──────────────────────────────────────────────────

    private void handleViewRide(Long chatId, Long rideId, Long carpoolUserId,
                                UserState state, CarpoolBot bot) {
        try {
            RideResponse ride = rideService.getRideById(rideId);
            stateManager.save(chatId, state.withSelectedRideId(rideId));

            boolean isDriver = ride.driver().id().equals(carpoolUserId);
            List<List<InlineKeyboardButton>> rows;

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

            bot.send(sendWithInline(chatId, BotMessageBuilder.formatRideCard(ride), rows));

        } catch (Exception e) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Could not load ride details. It may have been cancelled."));
        }
    }

    // ── Book ride ─────────────────────────────────────────────────────────

    private void handleBookRide(Long chatId, Long rideId, Long carpoolUserId,
                                CarpoolBot bot) {
        try {
            bookingService.createBooking(rideId, new CreateBookingRequest(1, null, null),
                    carpoolUserId);
            bot.send(BotMessageBuilder.text(chatId,
                    "✅ <b>Booking Confirmed!</b>\n\n" +
                            "Your seat has been reserved. The driver has been notified.\n\n" +
                            "Tap 📜 My Bookings to view your booking details."));

        } catch (Exception e) {
            log.error("Booking failed: rideId={} userId={} error={}", rideId, carpoolUserId, e.getMessage());
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Could not book this ride: " +
                            BotMessageBuilder.escape(e.getMessage())));
        }
    }

    // ── Cancel ride ───────────────────────────────────────────────────────

    private void handleCancelRide(Long chatId, Long rideId, Long carpoolUserId, CarpoolBot bot) {
        try {
            List<BookingResponse> activeBookings = bookingService.getBookingsByRideId(rideId);

            if (!activeBookings.isEmpty()) {
                var rows = List.of(List.of(
                        BotMessageBuilder.button(
                                "⚠️ Yes, Cancel Ride", "CONFIRM_CANCEL_RIDE:" + rideId),
                        BotMessageBuilder.button(
                                "◀️ Keep Ride", "MAIN_MENU")
                ));

                bot.send(sendWithInline(chatId,
                        "⚠️ <b>Are you sure?</b>\n\n" +
                                "Your ride has <b>" + activeBookings.size() + " active passenger(s)</b>.\n\n" +
                                "Cancelling will notify all passengers and remove their bookings.\n\n" +
                                "This cannot be undone.",
                        rows));
                return;
            }

            executeCancelRide(chatId, rideId, carpoolUserId, bot);

        } catch (Exception e) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Could not cancel ride: " +
                            BotMessageBuilder.escape(e.getMessage())));
        }
    }

    private void executeCancelRide(Long chatId, Long rideId, Long carpoolUserId, CarpoolBot bot) {
        try {
            rideService.updateRideStatus(rideId,
                    new UpdateRideStatusRequest(RideStatus.CANCELLED), carpoolUserId);
            stateManager.reset(chatId);
            bot.send(BotMessageBuilder.text(chatId,
                    "✅ Ride cancelled. All passengers have been notified."));
        } catch (Exception e) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Could not cancel ride: " +
                            BotMessageBuilder.escape(e.getMessage())));
        }
    }

    private void handleDepartRide(Long chatId, Long rideId, Long carpoolUserId, CarpoolBot bot) {
        try {
            rideService.updateRideStatus(rideId,
                    new UpdateRideStatusRequest(RideStatus.DEPARTED), carpoolUserId);
            stateManager.reset(chatId);

            var rows = List.of(List.of(
                    BotMessageBuilder.button("✅ Complete Ride", "COMPLETE_RIDE:" + rideId),
                    BotMessageBuilder.button("📜 My Bookings",  "MY_BOOKINGS")
            ));

            bot.send(sendWithInline(chatId,
                    "🚀 <b>Ride Started!</b>\n\n" +
                            "Your ride is now in progress.\n" +
                            "Tap <b>Complete Ride</b> when you reach the destination.",
                    rows));

        } catch (Exception e) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Could not start ride: " +
                            BotMessageBuilder.escape(e.getMessage())));
        }
    }

    private void handleCompleteRide(Long chatId, Long rideId, Long carpoolUserId, CarpoolBot bot) {
        try {
            rideService.updateRideStatus(rideId,
                    new UpdateRideStatusRequest(RideStatus.COMPLETED), carpoolUserId);
            stateManager.reset(chatId);
            bot.send(BotMessageBuilder.text(chatId,
                    "✅ <b>Ride Completed!</b>\n\n" +
                            "Thank you for driving! All passengers have been notified.\n\n" +
                            "Please collect contributions from your passengers."));

        } catch (Exception e) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Could not complete ride: " +
                            BotMessageBuilder.escape(e.getMessage())));
        }
    }

    // ── My bookings ───────────────────────────────────────────────────────

    private void handleMyBookings(Long chatId, Long carpoolUserId, CarpoolBot bot) {
        List<BookingResponse> myBookings   = bookingService.getMyBookings(carpoolUserId);
        List<BookingResponse> pastBookings = bookingService.getMyPastBookings(carpoolUserId);

        StringBuilder sb = new StringBuilder("📜 <b>My Bookings</b>\n\n");
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        if (myBookings.isEmpty()) {
            sb.append("<i>No active bookings.</i>");
        } else {
            sb.append("<b>Active</b>\n");
            for (int i = 0; i < myBookings.size(); i++) {
                BookingResponse b = myBookings.get(i);
                sb.append(String.format("<b>%d.</b> %s → %s | 🕐 %s | ₱%.2f\n",
                        i + 1,
                        BotMessageBuilder.escape(b.ride().originHub().name()),
                        BotMessageBuilder.escape(b.ride().destinationHub().name()),
                        b.ride().departureTime()
                                .atZone(ZoneId.of("Asia/Manila"))
                                .format(DateTimeFormatter.ofPattern("MMM d h:mma")),
                        b.contributionDue()));

                rows.add(List.of(InlineKeyboardButton.builder()
                        .text(String.format("🔍 %s → %s | %s",
                                b.ride().originHub().name(),
                                b.ride().destinationHub().name(),
                                b.ride().departureTime()
                                        .atZone(ZoneId.of("Asia/Manila"))
                                        .format(DateTimeFormatter.ofPattern("MMM d h:mma"))))
                        .callbackData("VIEW_BOOKING:" + b.id())
                        .build()));
            }
        }

        if (!pastBookings.isEmpty()) {
            rows.add(List.of(InlineKeyboardButton.builder()
                    .text("📂 Past Bookings (" + pastBookings.size() + ")")
                    .callbackData("PAST_BOOKINGS")
                    .build()));
        }

        rows.add(List.of(InlineKeyboardButton.builder()
                .text("🏠 Menu")
                .callbackData("MAIN_MENU")
                .build()));

        bot.send(sendWithInline(chatId, sb.toString(), rows));
    }

    // ── View booking detail ───────────────────────────────────────────────

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
            String dropoff = b.dropoffWaypoint() != null
                    ? b.dropoffWaypoint().hub().name()
                    : b.ride().destinationHub().name();

            String detail = String.format(
                    "📋 <b>Booking Details</b>\n\n" +
                            "🚗 %s → %s\n" +
                            "🕐 %s\n" +
                            "🚏 Pickup: <b>%s</b>\n" +
                            "🏁 Dropoff: <b>%s</b>\n" +
                            "🪑 Seats: %d\n" +
                            "💵 Contribution: ₱%.2f\n" +
                            "👤 Driver: %s%s\n" +
                            "📊 Status: %s",
                    BotMessageBuilder.escape(b.ride().originHub().name()),
                    BotMessageBuilder.escape(b.ride().destinationHub().name()),
                    b.ride().departureTime()
                            .atZone(ZoneId.of("Asia/Manila"))
                            .format(DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a")),
                    BotMessageBuilder.escape(pickup),
                    BotMessageBuilder.escape(dropoff),
                    b.seatsReserved(),
                    b.contributionDue(),
                    BotMessageBuilder.escape(b.ride().driver().fullName()),
                    b.ride().driver().telegramHandle() != null
                            ? " (@" + BotMessageBuilder.escape(b.ride().driver().telegramHandle()) + ")" : "",
                    statusLabel);

            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            boolean rideNotStarted = !b.ride().status().name().equals("DEPARTED")
                    && !b.ride().status().name().equals("COMPLETED");

            if ((b.status() == BookingStatus.CONFIRMED || b.status() == BookingStatus.PENDING)
                    && rideNotStarted) {
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
                    .parseMode("HTML")
                    .replyMarkup(InlineKeyboardMarkup.builder()
                            .keyboard(rows.stream().map(InlineKeyboardRow::new).toList())
                            .build())
                    .build());

        } catch (Exception e) {
            bot.send(BotMessageBuilder.text(chatId, "⚠️ Could not load booking details."));
        }
    }

    // ── Past bookings ─────────────────────────────────────────────────────

    private void handlePastBookings(Long chatId, Long carpoolUserId, CarpoolBot bot) {
        List<BookingResponse> past = bookingService.getMyPastBookings(carpoolUserId);

        if (past.isEmpty()) {
            bot.send(BotMessageBuilder.text(chatId,
                    "📂 <b>Past Bookings</b>\n\n<i>No past bookings.</i>"));
            return;
        }

        StringBuilder sb = new StringBuilder("📂 <b>Past Bookings</b>\n\n");
        for (int i = 0; i < past.size(); i++) {
            BookingResponse b = past.get(i);
            String statusLabel = switch (b.status().name()) {
                case "CANCELLED_BY_PASSENGER" -> "❌ You cancelled";
                case "CANCELLED_BY_DRIVER"    -> "❌ Driver cancelled";
                case "COMPLETED"              -> "🏁 Completed";
                default                       -> b.status().name();
            };
            sb.append(String.format("<b>%d.</b> %s → %s | %s\n",
                    i + 1,
                    BotMessageBuilder.escape(b.ride().originHub().name()),
                    BotMessageBuilder.escape(b.ride().destinationHub().name()),
                    statusLabel));
        }

        bot.send(SendMessage.builder()
                .chatId(chatId)
                .text(sb.toString())
                .parseMode("HTML")
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboard(List.of(
                                new InlineKeyboardRow(InlineKeyboardButton.builder()
                                        .text("◀️ Back to My Bookings")
                                        .callbackData("MY_BOOKINGS")
                                        .build())))
                        .build())
                .build());
    }

    // ── Driver bookings ───────────────────────────────────────────────────

    private void handleDriverBookings(Long chatId, Long carpoolUserId, CarpoolBot bot) {
        List<BookingResponse> bookings = bookingService.getBookingsForDriver(carpoolUserId);

        if (bookings.isEmpty()) {
            bot.send(BotMessageBuilder.text(chatId,
                    "📋 <b>Ride Bookings</b>\n\n<i>No passengers have booked your ride yet.</i>"));
            return;
        }

        StringBuilder sb = new StringBuilder("📋 <b>Passengers on your ride</b>\n\n");
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (int i = 0; i < bookings.size(); i++) {
            BookingResponse b = bookings.get(i);
            sb.append(String.format("<b>%d.</b> %s%s | 🪑 %d seat(s) | ₱%.2f\n",
                    i + 1,
                    BotMessageBuilder.escape(b.passenger().fullName()),
                    b.passenger().telegramHandle() != null
                            ? " (@" + BotMessageBuilder.escape(b.passenger().telegramHandle()) + ")" : "",
                    b.seatsReserved(),
                    b.contributionDue()));

            rows.add(List.of(InlineKeyboardButton.builder()
                    .text("View #" + (i + 1) + " — " + b.passenger().fullName())
                    .callbackData("VIEW_DRIVER_BOOKING:" + b.id())
                    .build()));
        }

        rows.add(List.of(BotMessageBuilder.menuButtonRow().get(0)));

        bot.send(SendMessage.builder()
                .chatId(chatId)
                .text(sb.toString())
                .parseMode("HTML")
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
                    "🔄 <b>Repost Ride</b>\n\n" +
                            "Previous ride: <b>" +
                            BotMessageBuilder.escape(original.originHub().name()) + " → " +
                            BotMessageBuilder.escape(original.destinationHub().name()) + "</b>\n\n" +
                            "🕐 <b>What time are you leaving?</b>\n" +
                            "Format: <code>MM/DD HH:MM</code>\n" +
                            "Example: <code>" +
                            LocalDateTime.now().plusHours(1)
                                    .format(DateTimeFormatter.ofPattern("MM/dd HH:mm")) +
                            "</code>\n\n" +
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

    // ── Cancel booking ────────────────────────────────────────────────────

    private void handleCancelBooking(Long chatId, Long bookingId,
                                     Long carpoolUserId, CarpoolBot bot) {
        try {
            bookingService.cancelBooking(bookingId, carpoolUserId);
            stateManager.reset(chatId);
            bot.send(BotMessageBuilder.text(chatId,
                    "✅ <b>Booking Cancelled</b>\n\n" +
                            "Your booking has been cancelled. The driver has been notified."));
        } catch (Exception e) {
            log.error("Cancel booking failed: bookingId={} userId={} error={}",
                    bookingId, carpoolUserId, e.getMessage());
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Could not cancel booking: " +
                            BotMessageBuilder.escape(e.getMessage())));
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private SendMessage sendWithInline(Long chatId, String text,
                                       List<List<InlineKeyboardButton>> rows) {
        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .replyMarkup(BotMessageBuilder.inlineButtons(rows))
                .build();
    }

    private void handleViewDriverBooking(Long chatId, Long bookingId,
                                         Long carpoolUserId, CarpoolBot bot) {
        try {
            BookingResponse b = bookingService.getBookingById(bookingId);

            String pickup  = b.pickupWaypoint()  != null
                    ? b.pickupWaypoint().hub().name()
                    : b.ride().originHub().name();
            String dropoff = b.dropoffWaypoint() != null
                    ? b.dropoffWaypoint().hub().name()
                    : b.ride().destinationHub().name();

            String detail = String.format(
                    "👤 <b>Passenger Details</b>\n\n" +
                            "Name: <b>%s</b>%s\n" +
                            "🚏 Pickup: <b>%s</b>\n" +
                            "🏁 Dropoff: <b>%s</b>\n" +
                            "🪑 Seats: %d\n" +
                            "💵 Contribution: ₱%.2f\n" +
                            "📊 Status: %s",
                    BotMessageBuilder.escape(b.passenger().fullName()),
                    b.passenger().telegramHandle() != null
                            ? " (@" + BotMessageBuilder.escape(b.passenger().telegramHandle()) + ")" : "",
                    BotMessageBuilder.escape(pickup),
                    BotMessageBuilder.escape(dropoff),
                    b.seatsReserved(),
                    b.contributionDue(),
                    b.status().name());

            var rows = List.of(List.of(
                    InlineKeyboardButton.builder()
                            .text("◀️ Back to Bookings")
                            .callbackData("RIDE_BOOKINGS:0")
                            .build()
            ));

            bot.send(SendMessage.builder()
                    .chatId(chatId)
                    .text(detail)
                    .parseMode("HTML")
                    .replyMarkup(InlineKeyboardMarkup.builder()
                            .keyboard(rows.stream().map(InlineKeyboardRow::new).toList())
                            .build())
                    .build());

        } catch (Exception e) {
            bot.send(BotMessageBuilder.text(chatId, "⚠️ Could not load booking details."));
        }
    }

    private void handleSkipNotes(Long chatId, Long carpoolUserId,
                                 UserState state, CarpoolBot bot) {
        UserState updated = state.withNotes(null).withFlow(BotFlow.POST_RIDE_CONFIRM);
        stateManager.save(chatId, updated);

        String dirLabel = state.getDirection() == RideDirection.HOME_TO_WORK
                ? "🏠 Home → Work" : "🏢 Work → Home";

        String confirmMsg = String.format(
                "📋 <b>Review Your Ride</b>\n\n" +
                        "Direction: %s\n" +
                        "📍 Start: <b>%s</b>\n" +
                        "🏁 End: <b>%s</b>\n" +
                        "🕐 Departure: <b>%s</b>\n" +
                        "🪑 Seats available: <b>%d</b>\n" +
                        "💵 Contribution: <b>₱%s / seat</b>\n\n" +
                        "Looks good? Post this ride?",
                dirLabel,
                BotMessageBuilder.escape(state.getOriginHubName()),
                BotMessageBuilder.escape(state.getDestinationHubName()),
                state.getDepartureTime().format(DateTimeFormatter.ofPattern("MMM d 'at' h:mm a")),
                state.getSeats(),
                state.getContribution().toPlainString());

        var rows = List.of(List.of(
                BotMessageBuilder.button("✅ Post Ride", "CONFIRM_POST_RIDE"),
                BotMessageBuilder.button("❌ Cancel",    "CANCEL_POST_RIDE")
        ));

        bot.send(sendWithInline(chatId, confirmMsg, rows));
    }

    private void handleDirectionCallback(Long chatId, String payload, Long carpoolUserId,
                                         UserState state, CarpoolBot bot) {
        RideDirection direction = payload.equals("HOME_TO_WORK")
                ? RideDirection.HOME_TO_WORK
                : RideDirection.WORK_TO_HOME;

        // Route based on current flow
        if (state.getFlow() == BotFlow.POST_RIDE_DIRECTION) {
            UserState updated = state
                    .withDirection(direction)
                    .withCarpoolUserId(carpoolUserId)
                    .withFlow(BotFlow.POST_RIDE_DEPARTURE_TIME);
            stateManager.save(chatId, updated);

            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "🕐 <b>What time are you leaving?</b>\n\n" +
                            "Format: <code>MM/DD HH:MM</code>\n" +
                            "Example: <code>" +
                            LocalDateTime.now().plusHours(1)
                                    .format(DateTimeFormatter.ofPattern("MM/dd HH:mm")) +
                            "</code>"));
            return;
        }

        if (state.getFlow() == BotFlow.SEARCH_SELECT_DIRECTION) {
            UserState updated = state
                    .withDirection(direction)
                    .withCarpoolUserId(carpoolUserId)
                    .withFlow(BotFlow.SEARCH_SELECT_TIME);
            stateManager.save(chatId, updated);
            askForTimeWindow(chatId, bot);
            return;
        }

        // Default — direction selected from main menu
        handleDirectionSelected(chatId, carpoolUserId, direction, state, bot);
    }

    private void askForTimeWindow(Long chatId, CarpoolBot bot) {
        var rows = List.of(
                List.of(
                        BotMessageBuilder.button("🌅 Early Morning (5-7 AM)", "TIME:EARLY_MORNING"),
                        BotMessageBuilder.button("☀️ Morning (7-9 AM)",       "TIME:MORNING")
                ),
                List.of(
                        BotMessageBuilder.button("🌤️ Mid Morning (9-11 AM)", "TIME:MID_MORNING"),
                        BotMessageBuilder.button("🌇 Afternoon (3-7 PM)",     "TIME:AFTERNOON")
                ),
                List.of(
                        BotMessageBuilder.button("🕛 Custom Time",            "TIME:CUSTOM"),
                        BotMessageBuilder.button("🔍 Show All Today",         "TIME:ALL_TODAY")
                )
        );
        bot.send(sendWithInline(chatId,
                "🕐 <b>When do you want to leave?</b>\n\nSelect a time window:", rows));
    }

    private void handleDirectionSelected(Long chatId, Long carpoolUserId,
                                         RideDirection direction, UserState state,
                                         CarpoolBot bot) {
        UserState updated = state.withDirection(direction).withCarpoolUserId(carpoolUserId);
        stateManager.save(chatId, updated);

        String dirLabel = direction == RideDirection.HOME_TO_WORK
                ? "🏠 Home → Work" : "🏢 Work → Home";

        List<BookingResponse> myBookings = bookingService.getMyBookings(carpoolUserId);

        var rows = myBookings.isEmpty()
                ? List.of(
                List.of(
                        BotMessageBuilder.button("🚗 Post a Ride", "POST_RIDE"),
                        BotMessageBuilder.button("🔍 Find a Ride", "FIND_RIDE")
                )
        )
                : List.of(
                List.of(
                        BotMessageBuilder.button("🚗 Post a Ride", "POST_RIDE"),
                        BotMessageBuilder.button("🔍 Find a Ride", "FIND_RIDE")
                ),
                List.of(
                        BotMessageBuilder.button(
                                "📜 My Bookings (" + myBookings.size() + ")",
                                "MY_BOOKINGS")
                )
        );

        bot.send(sendWithInline(chatId,
                "Direction: <b>" + dirLabel + "</b>\n\nWhat would you like to do?", rows));
    }
}