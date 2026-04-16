package com.carpool.bot.handler;

import com.carpool.bot.CarpoolBot;
import com.carpool.bot.config.BotConfig;
import com.carpool.bot.state.BotFlow;
import com.carpool.bot.state.StateManager;
import com.carpool.bot.state.UserState;
import com.carpool.bot.util.BotMessageBuilder;
import com.carpool.bot.util.HubMatcher;
import com.carpool.domain.entity.User;
import com.carpool.domain.enums.RideDirection;
import com.carpool.repository.UserRepository;
import com.carpool.service.booking.BookingService;
import com.carpool.service.dto.response.BookingResponse;
import com.carpool.service.dto.response.HubResponse;
import com.carpool.service.dto.response.RideResponse;
import com.carpool.service.ride.RideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * Handles all incoming text messages and commands.
 * Command routing:
 *   /start   → welcome + direction selector
 *   /cancel  → reset state, return to IDLE
 *   /myrides → show driver's active rides
 *   /mybookings → show passenger's bookings
 *
 * Flow routing (based on UserState.flow):
 *   POST_RIDE_* → ride posting steps
 *   SEARCH_*    → ride search steps
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageHandler {

    private final StateManager   stateManager;
    private final BotConfig      botConfig;
    private final UserRepository userRepository;
    private final RideService    rideService;
    private final HubMatcher     hubMatcher;
    private final BookingService bookingService;

    private static final DateTimeFormatter ETD_FMT =
            DateTimeFormatter.ofPattern("MM/dd HH:mm");

    // ── Natural language triggers ─────────────────────────────────────
    private static final java.util.Set<String> GREETINGS = java.util.Set.of(
            "hi", "hello", "hey", "start", "helo", "uy", "oi",
            "kumusta", "kamusta", "sup", "yo", "hoy", "musta",
            "zup", "ey", "help", "bot", "pre", "mars");

    public void handle(Message message, CarpoolBot bot) {
        Long chatId    = message.getChatId();
        Long telegramId = message.getFrom().getId();
        String text    = message.getText().trim();

        // ── Resolve or register user ──────────────────────────────────────
        User user = userRepository.findByTelegramId(telegramId)
                .orElseGet(() -> {
                    String firstName = message.getFrom().getFirstName();
                    String lastName  = message.getFrom().getLastName();
                    String handle    = message.getFrom().getUserName();
                    String fullName  = lastName != null ? firstName + " " + lastName : firstName;
                    User saved = userRepository.save(User.builder()
                            .telegramId(telegramId)
                            .telegramHandle(handle)
                            .fullName(fullName)
                            .build());
                    log.info("Auto-registered new user telegramId={} name={}", telegramId, saved.getFullName());
                    return saved;
                });

        Long carpoolUserId = user.getId();

        // ── Get or create state ───────────────────────────────────────────
        UserState state = stateManager.get(chatId);
        if (state == null) {
            state = UserState.initial(carpoolUserId);
            stateManager.save(chatId, state);
        }

        // ── Command handling (always takes priority over flow) ────────────
        if (text.startsWith("/")) {
            handleCommand(text, chatId, carpoolUserId, state, bot);
            return;
        }

        // ── Direction button shortcuts ────────────────────────────────────
        if (text.equals("🏠 Home to Work") || text.equals("🏢 Work to Home")) {
            RideDirection direction = text.equals("🏠 Home to Work")
                    ? RideDirection.HOME_TO_WORK
                    : RideDirection.WORK_TO_HOME;

            // If user is in search flow — show rides, not the post/find menu
            if (state.getFlow() == BotFlow.SEARCH_SELECT_DIRECTION) {
                showRidesForDirection(chatId, carpoolUserId, direction, state, bot);
                return;
            }

            // If user is in post ride flow — save direction and ask ETD
            if (state.getFlow() == BotFlow.POST_RIDE_DIRECTION) {
                UserState updated = state.withDirection(direction).withFlow(BotFlow.POST_RIDE_DEPARTURE_TIME);
                stateManager.save(chatId, updated);
                askForEtd(chatId, updated, bot);
                return;
            }

            // Default — main menu direction selection
            handleDirectionSelected(chatId, carpoolUserId, direction, state, bot);
            return;
        }

        // ── Natural language triggers ─────────────────────────────────────
        if (GREETINGS.contains(text.toLowerCase().trim())) {
            showMainMenu(chatId, carpoolUserId, state, bot);
            return;
        }

        // ── Flow-based handling ───────────────────────────────────────────
        switch (state.getFlow()) {
            case POST_RIDE_DEPARTURE_TIME -> handlePostRideEtd(chatId, text, state, bot);
            case POST_RIDE_ORIGIN         -> handlePostRideOrigin(chatId, text, state, bot);
            case POST_RIDE_DESTINATION    -> handlePostRideDestination(chatId, text, state, bot);
            case POST_RIDE_SEATS          -> handlePostRideSeats(chatId, text, state, bot);
            case POST_RIDE_CONTRIBUTION   -> handlePostRideContribution(chatId, text, state, bot);
            case POST_RIDE_NOTES          -> handlePostRideNotes(chatId, text, state, bot);
            case SEARCH_SELECT_TIME       -> handleCustomTimeInput(chatId, text, state, carpoolUserId, bot);
            default -> {
                // Unknown state — show main menu
                showMainMenu(chatId, carpoolUserId, state, bot);
            }
        }
    }

    // ── Command handlers ──────────────────────────────────────────────────

    private void handleCommand(String command, Long chatId, Long carpoolUserId,
                               UserState state, CarpoolBot bot) {
        String cmd = command.split(" ")[0].toLowerCase();
        switch (cmd) {
            case "/start"       -> showMainMenu(chatId, carpoolUserId, state, bot);
            case "/cancel"      -> handleCancel(chatId, bot);
            case "/myrides"     -> showMyRides(chatId, carpoolUserId, bot);
            case "/mybookings"  -> showMyBookings(chatId, carpoolUserId, bot);
            case "/postride"    -> startPostRideFlow(chatId, carpoolUserId, state, bot);
            case "/findride"    -> startFindRideFlow(chatId, carpoolUserId, state, bot);
            default -> bot.send(BotMessageBuilder.text(chatId,
                    "Unknown command. Use /start to see the main menu."));
        }
    }

    private void handleCancel(Long chatId, CarpoolBot bot) {
        stateManager.reset(chatId);
        bot.send(BotMessageBuilder.textWithRemoveKeyboard(chatId,
                "❌ Cancelled. Use /start to go back to the main menu."));
    }

    // ── Main menu ─────────────────────────────────────────────────────────

    private void showMainMenu(Long chatId, Long carpoolUserId,
                              UserState state, CarpoolBot bot) {
        stateManager.reset(chatId);

        // Check if driver has active ride
        List<RideResponse> myRides = rideService.getMyRides(carpoolUserId);
        boolean hasActiveRide = myRides.stream()
                .anyMatch(r -> r.status().name().equals("ACTIVE")
                        || r.status().name().equals("FULL"));

        if (hasActiveRide) {
            // Driver mode — show active ride card + options
            RideResponse active = myRides.stream()
                    .filter(r -> r.status().name().equals("ACTIVE")
                            || r.status().name().equals("FULL"))
                    .findFirst().orElseThrow();

            String msg = "🚗 *Your Active Ride*\n\n" +
                    BotMessageBuilder.formatRideCard(active) +
                    "\n\nWhat would you like to do?";

            var rows = List.of(
                    List.of(
                            BotMessageBuilder.button("📋 View Bookings", "RIDE_BOOKINGS:" + active.id()),
                            BotMessageBuilder.button("❌ Cancel Ride",   "CANCEL_RIDE:"  + active.id())
                    ),
                    List.of(
                            BotMessageBuilder.button("🔍 Find a Ride", "FIND_RIDE"),
                            BotMessageBuilder.button("📜 My Bookings", "MY_BOOKINGS")
                    )
            );

            bot.send(BotMessageBuilder.textWithRemoveKeyboard(chatId, msg));
            bot.send(SendMessageWithInline(chatId, "Choose an action:", rows));

        } else {
            // No active ride — show direction selector
            bot.send(BotMessageBuilder.directionSelector(chatId,
                    "👋 Welcome to *" + botConfig.getCommunityName() + "*!\n\n" +
                            "Are you going *Home to Work* or *Work to Home* today?"));
        }
    }

    // ── Direction selected → decide: post ride or find ride ───────────────

    private void handleDirectionSelected(Long chatId, Long carpoolUserId,
                                         RideDirection direction, UserState state,
                                         CarpoolBot bot) {
        // Save direction, ask: post or find?
        UserState updated = state
                .withDirection(direction)
                .withCarpoolUserId(carpoolUserId);
        stateManager.save(chatId, updated);

        String dirLabel = direction == RideDirection.HOME_TO_WORK
                ? "🏠 Home → Work" : "🏢 Work → Home";

        var rows = List.of(
                List.of(
                        BotMessageBuilder.button("🚗 Post a Ride",  "POST_RIDE"),
                        BotMessageBuilder.button("🔍 Find a Ride",  "FIND_RIDE")
                ),
                List.of(
                        BotMessageBuilder.button("📜 My Bookings", "MY_BOOKINGS")
                )
        );

        bot.send(SendMessageWithInline(chatId,
                "Direction: *" + dirLabel + "*\n\nWhat would you like to do?", rows));
    }

    // ── Post ride flow ────────────────────────────────────────────────────

    private void startPostRideFlow(Long chatId, Long carpoolUserId,
                                   UserState state, CarpoolBot bot) {
        if (state.getDirection() == null) {
            bot.send(BotMessageBuilder.directionSelector(chatId,
                    "Which direction is this ride?"));
            stateManager.save(chatId, state.withFlow(BotFlow.POST_RIDE_DIRECTION));
            return;
        }
        askForEtd(chatId, state, bot);
    }

    private void askForEtd(Long chatId, UserState state, CarpoolBot bot) {
        stateManager.save(chatId, state.withFlow(BotFlow.POST_RIDE_DEPARTURE_TIME));
        bot.send(BotMessageBuilder.textWithRemoveKeyboard(chatId,
                "🕐 *When is your departure time?*\n\n" +
                        "Format: `MM/DD HH:MM`\n" +
                        "Example: `04/16 07:30`\n\n" +
                        "Type /cancel to abort."));
    }

    private void handlePostRideEtd(Long chatId, String text, UserState state, CarpoolBot bot) {
        try {
            LocalDateTime etd = LocalDateTime.parse(
                    LocalDateTime.now().getYear() + "/" + text.trim(),
                    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));

            if (etd.isBefore(LocalDateTime.now())) {
                bot.send(BotMessageBuilder.text(chatId,
                        "⚠️ Departure time must be in the future. Try again:"));
                return;
            }

            stateManager.save(chatId, state
                    .withDepartureTime(etd)
                    .withFlow(BotFlow.POST_RIDE_ORIGIN));

            bot.send(BotMessageBuilder.text(chatId,
                    "📍 *Where will you pick up passengers?*\n\n" +
                            "Type a landmark name (e.g. `SM Southmall`, `BF Resort`)\n\n" +
                            "Type /cancel to abort."));

        } catch (DateTimeParseException e) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Invalid format. Please use `MM/DD HH:MM`\nExample: `04/16 07:30`"));
        }
    }

    private void handlePostRideOrigin(Long chatId, String text, UserState state, CarpoolBot bot) {
        Optional<HubResponse> hub = hubMatcher.match(text);
        if (hub.isEmpty()) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Couldn't find a hub matching *\"" + text + "\"*.\n" +
                            "Try a different landmark name:"));
            return;
        }

        stateManager.save(chatId, state
                .withOriginHubId(hub.get().id())
                .withOriginHubName(hub.get().name())
                .withFlow(BotFlow.POST_RIDE_DESTINATION));

        bot.send(BotMessageBuilder.text(chatId,
                "✅ Pickup: *" + hub.get().name() + "*\n\n" +
                        "🏁 *Where is your final destination?*\n\n" +
                        "Type a landmark name (e.g. `BGC High Street`, `Ayala MRT`)\n\n" +
                        "Type /cancel to abort."));
    }

    private void handlePostRideDestination(Long chatId, String text, UserState state, CarpoolBot bot) {
        Optional<HubResponse> hub = hubMatcher.match(text);
        if (hub.isEmpty()) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Couldn't find a hub matching *\"" + text + "\"*.\n" +
                            "Try a different landmark name:"));
            return;
        }

        if (hub.get().id().equals(state.getOriginHubId())) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Destination cannot be the same as pickup. Try again:"));
            return;
        }

        stateManager.save(chatId, state
                .withDestinationHubId(hub.get().id())
                .withDestinationHubName(hub.get().name())
                .withFlow(BotFlow.POST_RIDE_SEATS));

        bot.send(BotMessageBuilder.text(chatId,
                "✅ Destination: *" + hub.get().name() + "*\n\n" +
                        "🪑 *How many passenger slots are available?* (1-8)"));
    }

    private void handlePostRideSeats(Long chatId, String text, UserState state, CarpoolBot bot) {
        try {
            int seats = Integer.parseInt(text.trim());
            if (seats < 1 || seats > 8) {
                bot.send(BotMessageBuilder.text(chatId,
                        "⚠️ Please enter a number between 1 and 8:"));
                return;
            }

            stateManager.save(chatId, state
                    .withSeats(seats)
                    .withFlow(BotFlow.POST_RIDE_CONTRIBUTION));

            bot.send(BotMessageBuilder.text(chatId,
                    "✅ Slots: *" + seats + "*\n\n" +
                            "💵 *Contribution per seat?* (Enter 0 for free)\n" +
                            "Example: `50` or `0`"));

        } catch (NumberFormatException e) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Please enter a valid number (1-8):"));
        }
    }

    private void handlePostRideContribution(Long chatId, String text, UserState state, CarpoolBot bot) {
        try {
            BigDecimal amount = new BigDecimal(text.trim());
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                bot.send(BotMessageBuilder.text(chatId,
                        "⚠️ Contribution cannot be negative:"));
                return;
            }

            stateManager.save(chatId, state
                    .withContribution(amount)
                    .withFlow(BotFlow.POST_RIDE_NOTES));

            bot.send(BotMessageBuilder.text(chatId,
                    "✅ Contribution: *₱" + amount + " / seat*\n\n" +
                            "📝 *Any notes for passengers?*\n" +
                            "_(e.g. stops, landmarks, instructions)_\n\n" +
                            "Type `skip` if none."));

        } catch (NumberFormatException e) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Please enter a valid amount (e.g. `50` or `0`):"));
        }
    }

    private void handlePostRideNotes(Long chatId, String text, UserState state, CarpoolBot bot) {
        String notes = text.equalsIgnoreCase("skip") ? null : text;

        UserState updated = state
                .withNotes(notes)
                .withFlow(BotFlow.POST_RIDE_CONFIRM);
        stateManager.save(chatId, updated);

        // Show confirmation card
        String dirLabel = state.getDirection() == RideDirection.HOME_TO_WORK
                ? "🏠 Home → Work" : "🏢 Work → Home";

        String confirmMsg = String.format(
                "📋 *Confirm Your Ride*\n\n" +
                        "Direction: %s\n" +
                        "📍 From: *%s*\n" +
                        "🏁 To: *%s*\n" +
                        "🕐 Departure: *%s*\n" +
                        "🪑 Slots: *%d*\n" +
                        "💵 Contribution: *₱%s / seat*\n" +
                        "%s\n\n" +
                        "Post this ride?",
                dirLabel,
                state.getOriginHubName(),
                state.getDestinationHubName(),
                state.getDepartureTime().format(DateTimeFormatter.ofPattern("MMM d 'at' h:mm a")),
                state.getSeats(),
                state.getContribution().toPlainString(),
                notes != null ? "📝 Notes: " + notes : "");

        var rows = List.of(
                List.of(
                        BotMessageBuilder.button("✅ Post Ride", "CONFIRM_POST_RIDE"),
                        BotMessageBuilder.button("❌ Cancel",    "CANCEL_POST_RIDE")
                )
        );

        bot.send(SendMessageWithInline(chatId, confirmMsg, rows));
    }

    // ── Find ride flow ────────────────────────────────────────────────────

    private void startFindRideFlow(Long chatId, Long carpoolUserId,
                                   UserState state, CarpoolBot bot) {
        if (state.getDirection() == null) {
            bot.send(BotMessageBuilder.directionSelector(chatId,
                    "Which direction are you looking for?"));
            stateManager.save(chatId, state.withFlow(BotFlow.SEARCH_SELECT_DIRECTION));
            return;
        }
        showRidesForDirection(chatId, carpoolUserId, state.getDirection(), state, bot);
    }

    private void showRidesForDirection(Long chatId, Long carpoolUserId,
                                       RideDirection direction, UserState state,
                                       CarpoolBot bot) {
        // Save direction, then ask for time window
        stateManager.save(chatId, state
                .withDirection(direction)
                .withFlow(BotFlow.SEARCH_SELECT_TIME));

        askForTimeWindow(chatId, bot);
    }

    private void askForTimeWindow(Long chatId, CarpoolBot bot) {
        var rows = List.of(
                List.of(
                        BotMessageBuilder.button("🌅 Early Morning (5-7 AM)",  "TIME:EARLY_MORNING"),
                        BotMessageBuilder.button("☀️ Morning (7-9 AM)",        "TIME:MORNING")
                ),
                List.of(
                        BotMessageBuilder.button("🌤️ Mid Morning (9-11 AM)",  "TIME:MID_MORNING"),
                        BotMessageBuilder.button("🌇 Afternoon (3-7 PM)",      "TIME:AFTERNOON")
                ),
                List.of(
                        BotMessageBuilder.button("🕛 Custom Time",             "TIME:CUSTOM"),
                        BotMessageBuilder.button("🔍 Show All Today",          "TIME:ALL_TODAY")
                )
        );

        bot.send(SendMessageWithInline(chatId,
                "🕐 *When do you want to leave?*\n\nSelect a time window:", rows));
    }

    // ── My rides / bookings ───────────────────────────────────────────────

    private void showMyRides(Long chatId, Long carpoolUserId, CarpoolBot bot) {
        List<RideResponse> rides = rideService.getMyRides(carpoolUserId);
        bot.send(BotMessageBuilder.rideList(chatId, rides, "🚗 *My Rides*"));
    }

    private void showMyBookings(Long chatId, Long carpoolUserId, CarpoolBot bot) {
        // Delegate to callback handler logic — same implementation
        List<BookingResponse> bookings =
                bookingService.getMyBookings(carpoolUserId);

        if (bookings.isEmpty()) {
            bot.send(BotMessageBuilder.text(chatId,
                    "📜 *My Bookings*\n\n_You have no bookings yet._\n\n" +
                            "Use 🔍 Find a Ride to book a carpool."));
            return;
        }

        StringBuilder sb = new StringBuilder("📜 *My Bookings*\n\n");
        for (int i = 0; i < bookings.size(); i++) {
            var b = bookings.get(i);
            String status = switch (b.status().name()) {
                case "CONFIRMED"               -> "✅ Confirmed";
                case "PENDING"                 -> "⏳ Pending";
                case "CANCELLED_BY_PASSENGER"  -> "❌ Cancelled by you";
                case "CANCELLED_BY_DRIVER"     -> "❌ Cancelled by driver";
                case "COMPLETED"               -> "🏁 Completed";
                default                        -> b.status().name();
            };

            sb.append(String.format(
                    "*%d.* %s → %s\n" +
                            "   🕐 %s\n" +
                            "   💵 ₱%.2f  |  %s\n\n",
                    i + 1,
                    b.ride().originHub().name(),
                    b.ride().destinationHub().name(),
                    b.ride().departureTime()
                            .atZone(java.time.ZoneId.of("Asia/Manila"))
                            .format(java.time.format.DateTimeFormatter.ofPattern("MMM d 'at' h:mm a")),
                    b.contributionDue(),
                    status));
        }

        bot.send(BotMessageBuilder.text(chatId, sb.toString()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private org.telegram.telegrambots.meta.api.methods.send.SendMessage SendMessageWithInline(
            Long chatId, String text,
            List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> rows) {
        return org.telegram.telegrambots.meta.api.methods.send.SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(BotMessageBuilder.inlineButtons(rows))
                .build();
    }

    private void handleCustomTimeInput(Long chatId, String text, UserState state,
                                       Long carpoolUserId, CarpoolBot bot) {
        try {
            // Parse HH:MM input
            LocalTime time = LocalTime.parse(text.trim(),
                    DateTimeFormatter.ofPattern("HH:mm"));

            LocalDateTime from = LocalDateTime.now().toLocalDate().atTime(time);
            LocalDateTime to   = from.plusHours(2); // 2-hour window

            // Delegate to callback handler logic via service directly
            List<RideResponse> rides = rideService.getRidesByDirection(
                    state.getDirection(), carpoolUserId, from, to);

            String dirLabel = state.getDirection() == RideDirection.HOME_TO_WORK
                    ? "🏠 Home → Work" : "🏢 Work → Home";

            stateManager.save(chatId, state
                    .withSearchFrom(from)
                    .withSearchTo(to)
                    .withFlow(BotFlow.SEARCH_RESULTS));

            bot.send(BotMessageBuilder.rideList(chatId, rides,
                    "🔍 *Available Rides — " + dirLabel + "* (around " + text.trim() + ")"));

        } catch (Exception e) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Invalid format. Please use `HH:MM`\nExample: `07:30`"));
        }
    }
}