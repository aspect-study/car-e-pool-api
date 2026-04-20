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
import com.carpool.service.dto.request.CreateBookingRequest;
import com.carpool.service.dto.response.BookingResponse;
import com.carpool.service.dto.response.HubResponse;
import com.carpool.service.dto.response.RideResponse;
import com.carpool.service.note.DriverNoteService;
import com.carpool.service.ride.RideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    private final DriverNoteService driverNoteService;
    private final PostRideHelper    postRideHelper;

    private static final DateTimeFormatter ETD_FMT =
            DateTimeFormatter.ofPattern("MM/dd HH:mm");

    private static final Set<String> GREETINGS = Set.of(
            "hi", "hello", "hey", "start", "helo", "uy", "oi",
            "kumusta", "kamusta", "sup", "yo", "hoy", "musta",
            "zup", "ey", "help", "bot", "pre", "mars");

    public void handle(Message message, CarpoolBot bot) {
        Long chatId     = message.getChatId();
        Long telegramId = message.getFrom().getId();
        String text     = message.getText().trim();

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

        UserState state = stateManager.get(chatId);
        if (state == null) {
            state = UserState.initial(carpoolUserId);
            stateManager.save(chatId, state);
        }

        if (text.startsWith("/")) {
            handleCommand(text, chatId, carpoolUserId, state, bot);
            return;
        }

        if (text.equals("🏠 Home to Work") || text.equals("🏢 Work to Home")) {
            RideDirection direction = text.equals("🏠 Home to Work")
                    ? RideDirection.HOME_TO_WORK
                    : RideDirection.WORK_TO_HOME;

            if (state.getFlow() == BotFlow.SEARCH_SELECT_DIRECTION) {
                showRidesForDirection(chatId, carpoolUserId, direction, state, bot);
                return;
            }
            if (state.getFlow() == BotFlow.POST_RIDE_DIRECTION) {
                UserState updated = state.withDirection(direction)
                        .withFlow(BotFlow.POST_RIDE_DEPARTURE_TIME);
                stateManager.save(chatId, updated);
                askForEtd(chatId, updated, bot);
                return;
            }
            handleDirectionSelected(chatId, carpoolUserId, direction, state, bot);
            return;
        }

        if (GREETINGS.contains(text.toLowerCase().trim())) {
            showMainMenu(chatId, carpoolUserId, state, bot);
            return;
        }

        switch (state.getFlow()) {
            case POST_RIDE_DEPARTURE_TIME -> handlePostRideEtd(chatId, text, state, bot);
            case POST_RIDE_ORIGIN         -> handlePostRideOrigin(chatId, text, state, bot);
            case POST_RIDE_DESTINATION    -> handlePostRideDestination(chatId, text, state, bot);
            case POST_RIDE_SEATS          -> handlePostRideSeats(chatId, text, state, bot);
            case POST_RIDE_CONTRIBUTION   -> handlePostRideContribution(chatId, text, state, bot, carpoolUserId);
            case POST_RIDE_NOTES          -> handlePostRideNotes(chatId, text, state, bot);
            case SEARCH_SELECT_TIME       -> handleCustomTimeInput(chatId, text, state, carpoolUserId, bot);
            case POST_RIDE_NOTES_WRITE    -> handlePostRideNotesWrite(chatId, text, state, bot, carpoolUserId);
            case BOOKING_MESSAGE          -> handleBookingMessage(chatId, text, state, carpoolUserId, bot);
            default                       -> showMainMenu(chatId, carpoolUserId, state, bot);
        }
    }

    // ── Commands ──────────────────────────────────────────────────────────

    private void handleCommand(String command, Long chatId, Long carpoolUserId,
                               UserState state, CarpoolBot bot) {
        String cmd = command.split(" ")[0].toLowerCase();
        switch (cmd) {
            case "/start"      -> showMainMenu(chatId, carpoolUserId, state, bot);
            case "/cancel"     -> handleCancel(chatId, bot);
            case "/myrides"    -> showMyRides(chatId, carpoolUserId, bot);
            case "/mybookings" -> showMyBookings(chatId, carpoolUserId, bot);
            case "/postride"   -> startPostRideFlow(chatId, carpoolUserId, state, bot);
            case "/findride"   -> startFindRideFlow(chatId, carpoolUserId, state, bot);
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

            long pendingCount = bookingService.countPendingRequestsForDriver(carpoolUserId);

            var rows = active.status().name().equals("DEPARTED")
                    ? List.of(
                    List.of(
                            BotMessageBuilder.button("📋 View Bookings", "RIDE_BOOKINGS:" + active.id()),
                            BotMessageBuilder.button("✅ Complete Ride",  "COMPLETE_RIDE:" + active.id())
                    )
            )
                    : pendingCount > 0
                      ? List.of(
                    List.of(
                            BotMessageBuilder.button("📋 View Bookings", "RIDE_BOOKINGS:" + active.id()),
                            BotMessageBuilder.button("🚀 Start Ride",    "DEPART_RIDE:"  + active.id())
                    ),
                    List.of(
                            BotMessageBuilder.button("⏳ Pending (" + pendingCount + ")", "PENDING_REQUESTS"),
                            BotMessageBuilder.button("❌ Cancel Ride", "CANCEL_RIDE:" + active.id())
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
                            BotMessageBuilder.button("❌ Cancel Ride", "CANCEL_RIDE:" + active.id())
                    ),
                    List.of(
                            BotMessageBuilder.button("🔍 Find a Ride", "FIND_RIDE")
                    )
            );

            bot.send(BotMessageBuilder.textWithRemoveKeyboard(chatId, msg));
            bot.send(sendWithInline(chatId, "Choose an action:", rows));
        } else {
            bot.send(BotMessageBuilder.directionSelector(chatId,
                    "👋 Welcome to <b>" + BotMessageBuilder.escape(botConfig.getCommunityName()) + "</b>!\n\n" +
                            "Where are you headed today?"));
        }
    }

    // ── Direction selected ────────────────────────────────────────────────

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
        bot.send(BotMessageBuilder.textWithCancel(chatId,
                "🕐 <b>What time are you leaving?</b>\n\n" +
                        "Format: <code>MM/DD HH:MM</code>\n" +
                        "Example: <code>" +
                        LocalDateTime.now().plusHours(1)
                                .format(DateTimeFormatter.ofPattern("MM/dd HH:mm")) +
                        "</code>"));
    }

    private void handlePostRideEtd(Long chatId, String text, UserState state, CarpoolBot bot) {
        try {
            LocalDateTime etd = LocalDateTime.parse(
                    LocalDateTime.now().getYear() + "/" + text.trim(),
                    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));

            if (etd.isBefore(LocalDateTime.now())) {
                bot.send(BotMessageBuilder.textWithCancel(chatId,
                        "⚠️ That time has already passed. Please enter a future departure time:"));
                return;
            }

            stateManager.save(chatId, state
                    .withDepartureTime(etd)
                    .withFlow(BotFlow.POST_RIDE_ORIGIN));

            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "📍 <b>Where does your ride start?</b>\n\n" +
                            "Type a nearby landmark as your pickup point.\n" +
                            "Example: <code>SM Southmall</code>"));

        } catch (DateTimeParseException e) {
            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "⚠️ Invalid format. Please use <code>MM/DD HH:MM</code>\n" +
                            "Example: <code>" +
                            LocalDateTime.now().plusHours(1)
                                    .format(DateTimeFormatter.ofPattern("MM/dd HH:mm")) +
                            "</code>"));
        }
    }

    private void handlePostRideOrigin(Long chatId, String text, UserState state, CarpoolBot bot) {
        Optional<HubResponse> hub = hubMatcher.match(text);
        if (hub.isEmpty()) {
            List<HubResponse> suggestions = hubMatcher.suggest(text);
            if (!suggestions.isEmpty()) {
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                for (HubResponse s : suggestions) {
                    rows.add(List.of(BotMessageBuilder.button(
                            s.name(), "HUB_ORIGIN:" + s.id())));
                }
                rows.add(List.of(BotMessageBuilder.button("✏️ Type again", "CANCEL_POST_RIDE")));
                bot.send(sendWithInline(chatId,
                        "⚠️ Couldn't find <b>\"" + BotMessageBuilder.escape(text) + "\"</b>.\n\n" +
                                "Did you mean one of these?",
                        rows));
            } else {
                bot.send(BotMessageBuilder.textWithCancel(chatId,
                        "⚠️ Couldn't find <b>\"" + BotMessageBuilder.escape(text) + "\"</b> in our hub list.\n\n" +
                                "Try a more specific name or nearby landmark:"));
            }
            return;
        }

        stateManager.save(chatId, state
                .withOriginHubId(hub.get().id())
                .withOriginHubName(hub.get().name())
                .withFlow(BotFlow.POST_RIDE_DESTINATION));

        bot.send(BotMessageBuilder.textWithCancel(chatId,
                "✅ Start point: <b>" + BotMessageBuilder.escape(hub.get().name()) + "</b>\n\n" +
                        "🏁 <b>Where does your ride end?</b>\n\n" +
                        "Type a nearby landmark as your drop-off point.\n" +
                        "Example: <code>BGC High Street</code>"));
    }

    private void handlePostRideDestination(Long chatId, String text, UserState state, CarpoolBot bot) {
        Optional<HubResponse> hub = hubMatcher.match(text);
        if (hub.isEmpty()) {
            List<HubResponse> suggestions = hubMatcher.suggest(text);
            if (!suggestions.isEmpty()) {
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                for (HubResponse s : suggestions) {
                    rows.add(List.of(BotMessageBuilder.button(
                            s.name(), "HUB_DEST:" + s.id())));
                }
                rows.add(List.of(BotMessageBuilder.button("✏️ Type again", "CANCEL_POST_RIDE")));
                bot.send(sendWithInline(chatId,
                        "⚠️ Couldn't find <b>\"" + BotMessageBuilder.escape(text) + "\"</b>.\n\n" +
                                "Did you mean one of these?",
                        rows));
            } else {
                bot.send(BotMessageBuilder.textWithCancel(chatId,
                        "⚠️ Couldn't find <b>\"" + BotMessageBuilder.escape(text) + "\"</b> in our hub list.\n\n" +
                                "Try a more specific name or nearby landmark:"));
            }
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

        bot.send(BotMessageBuilder.textWithCancel(chatId,
                "✅ End point: <b>" + BotMessageBuilder.escape(hub.get().name()) + "</b>\n\n" +
                        "🪑 <b>How many passengers can you take?</b> (1-8)"));
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
                    "✅ Passenger slots: <b>" + seats + "</b>\n\n" +
                            "💵 <b>How much is the contribution per seat?</b>\n" +
                            "Enter <code>0</code> if it's a free ride.\n" +
                            "Example: <code>50</code>"));

        } catch (NumberFormatException e) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Please enter a valid number (1-8):"));
        }
    }

    private void handlePostRideContribution(Long chatId, String text, UserState state,
                                            CarpoolBot bot, Long carpoolUserId) {
        try {
            BigDecimal amount = new BigDecimal(text.trim());
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                bot.send(BotMessageBuilder.textWithCancel(chatId,
                        "⚠️ Contribution cannot be negative:"));
                return;
            }

            stateManager.save(chatId, state
                    .withContribution(amount)
                    .withFlow(BotFlow.POST_RIDE_NOTES));

            bot.send(BotMessageBuilder.textNoMenu(chatId,
                    "✅ Contribution: <b>₱" + amount + " / seat</b>"));
            postRideHelper.showNotesPrompt(chatId, carpoolUserId, state.withContribution(amount), bot);

        } catch (NumberFormatException e) {
            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "⚠️ Please enter a valid amount (e.g. <code>100</code> or <code>0</code>):"));
        }
    }

    private void handlePostRideNotes(Long chatId, String text, UserState state, CarpoolBot bot) {
        // Fallback — user typed directly instead of tapping a button
        // Treat as custom note, proceed to confirmation without saving to DB
        String notes = text.trim();
        UserState updated = state.withNotes(notes).withFlow(BotFlow.POST_RIDE_CONFIRM);
        stateManager.save(chatId, updated);
        postRideHelper.showConfirmation(chatId, updated, bot);
    }

    private void handleBookingMessage(Long chatId, String text,
                                      UserState state, Long carpoolUserId,
                                      CarpoolBot bot) {
        Long rideId = state.getSelectedRideId();
        if (rideId == null) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Session expired. Please try booking again."));
            stateManager.reset(chatId);
            return;
        }

        String message = text.trim();
        stateManager.save(chatId, state.withFlow(BotFlow.IDLE));

        try {
            bookingService.createBooking(rideId,
                    new CreateBookingRequest(1, null, null, message),
                    carpoolUserId);

            bot.send(sendWithInline(chatId,
                    "⏳ <b>Booking Request Sent!</b>\n\n" +
                            "Waiting for the driver to accept your request.\n" +
                            "You'll be notified once the driver responds.",
                    List.of(
                            List.of(
                                    BotMessageBuilder.button("📜 My Bookings", "MY_BOOKINGS"),
                                    BotMessageBuilder.button("🏠 Menu",        "MAIN_MENU")
                            )
                    )));

        } catch (Exception e) {
            log.error("Booking failed: rideId={} userId={} error={}",
                    rideId, carpoolUserId, e.getMessage());
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Could not book this ride: " +
                            BotMessageBuilder.escape(e.getMessage())));
        }
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
        stateManager.save(chatId, state
                .withDirection(direction)
                .withFlow(BotFlow.SEARCH_SELECT_TIME));
        askForTimeWindow(chatId, bot);
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

    // ── My rides / bookings ───────────────────────────────────────────────

    private void showMyRides(Long chatId, Long carpoolUserId, CarpoolBot bot) {
        List<RideResponse> rides = rideService.getMyRides(carpoolUserId);
        bot.send(BotMessageBuilder.rideList(chatId, rides, "🚗 <b>My Rides</b>"));
    }

    private void showMyBookings(Long chatId, Long carpoolUserId, CarpoolBot bot) {
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

                String statusPrefix = b.status() == com.carpool.domain.enums.BookingStatus.PENDING
                        ? "⏳ " : "🔍 ";
                rows.add(List.of(InlineKeyboardButton.builder()
                        .text(String.format("%s%s → %s | %s",
                                statusPrefix,
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
                    .text("📂 Booking History (" + pastBookings.size() + ")")
                    .callbackData("PAST_BOOKINGS")
                    .build()));
        }

        rows.add(List.of(InlineKeyboardButton.builder()
                .text("🏠 Menu")
                .callbackData("MAIN_MENU")
                .build()));

        bot.send(sendWithInline(chatId, sb.toString(), rows));
    }

    // ── Custom time input ─────────────────────────────────────────────────

    private void handleCustomTimeInput(Long chatId, String text, UserState state,
                                       Long carpoolUserId, CarpoolBot bot) {
        try {
            LocalTime time = LocalTime.parse(text.trim(),
                    DateTimeFormatter.ofPattern("HH:mm"));

            LocalDateTime from = LocalDateTime.now().toLocalDate().atTime(time);
            LocalDateTime to   = from.plusHours(2);

            List<RideResponse> rides = rideService.getRidesByDirection(
                    state.getDirection(), carpoolUserId, from, to);

            String dirLabel = state.getDirection() == RideDirection.HOME_TO_WORK
                    ? "🏠 Home → Work" : "🏢 Work → Home";

            stateManager.save(chatId, state
                    .withSearchFrom(from)
                    .withSearchTo(to)
                    .withFlow(BotFlow.SEARCH_RESULTS));

            bot.send(BotMessageBuilder.rideList(chatId, rides,
                    "🔍 <b>Available Rides — " + dirLabel +
                            "</b> (around " + text.trim() + ")"));

        } catch (Exception e) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Invalid format. Please use <code>HH:MM</code>\n" +
                            "Example: <code>07:30</code>"));
        }
    }

    /**
     * Handles custom note text input — saves to DB then proceeds to confirmation.
     */
    private void handlePostRideNotesWrite(Long chatId, String text,
                                          UserState state, CarpoolBot bot,
                                          Long carpoolUserId) {
        String notes = text.trim();

        // Save to DB — saveOrUpdate handles dedup + LRU replacement
        driverNoteService.saveOrUpdate(carpoolUserId, notes);

        UserState updated = state.withNotes(notes).withFlow(BotFlow.POST_RIDE_CONFIRM);
        stateManager.save(chatId, updated);

        postRideHelper.showConfirmation(chatId, updated, bot);
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
}