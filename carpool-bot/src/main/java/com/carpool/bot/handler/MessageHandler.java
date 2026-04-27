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
import com.carpool.service.hub.HubService;
import com.carpool.service.note.DriverNoteService;
import com.carpool.service.profile.ProfileService;
import com.carpool.service.ride.RideService;
import com.carpool.service.vehicle.VehicleService;
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
    private final HubService hubService;
    private final ProfileService profileService;
    private final VehicleService vehicleService;

    private static final DateTimeFormatter ETD_FMT =DateTimeFormatter.ofPattern("MM/dd HH:mm");

    private static final Set<String> GREETINGS = Set.of("hi", "hey", "start","ey");

    public void handle(Message message, CarpoolBot bot) {
        Long chatId     = message.getChatId();
        Long telegramId = message.getFrom().getId();
        String text     = message.getText().trim();

        boolean isNewUser = !userRepository.existsByTelegramId(telegramId);

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

        // New user or not yet accepted — show welcome/terms screen
        if (isNewUser || !user.isTermsAccepted()) {
            // Re-prompt declined users weekly
            if (!isNewUser && user.isRecentlyDeclined()) {
                showTermsReminder(chatId, bot);
                return;
            }
            if (!isNewUser && !user.isTermsAccepted() && !user.isRecentlyDeclined()) {
                showTermsReminder(chatId, bot);
                return;
            }
            if (isNewUser) {
                showWelcomeScreen(chatId, user, bot);
                return;
            }
        }

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
            case SET_VEHICLE_COLOR        -> handleSetVehicleColor(chatId, text, state, bot);
            case SET_VEHICLE_MODEL        -> handleSetVehicleModel(chatId, text, state, bot);
            case SET_VEHICLE_PLATE        -> handleSetVehiclePlate(chatId, text, state, carpoolUserId, bot);
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
            case "/profile"    -> handleProfile(chatId, carpoolUserId, bot);
            case "/vehicle"    -> handleVehicleCommand(chatId, carpoolUserId, state, bot);
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
                    ),
                    List.of(
                            BotMessageBuilder.button("👤 My Profile", "MY_PROFILE")
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
                    ),
                    List.of(
                            BotMessageBuilder.button("👤 My Profile", "MY_PROFILE")
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
                    ),
                    List.of(
                            BotMessageBuilder.button("👤 My Profile", "MY_PROFILE")
                    )
            );

            bot.send(BotMessageBuilder.textWithRemoveKeyboard(chatId, msg));
            bot.send(sendWithInline(chatId, "Choose an action:", rows));
        } else {
            List<BookingResponse> myBookings = bookingService.getMyBookings(carpoolUserId);
            boolean hasPastRides = rideService.getMyRides(carpoolUserId).stream()
                    .anyMatch(r -> r.status().name().equals("COMPLETED")
                            || r.status().name().equals("CANCELLED"));

            String prompt = (!myBookings.isEmpty() && hasPastRides)
                    ? "👋 What would you like to do?"
                    : "👋 Welcome to <b>" + BotMessageBuilder.escape(botConfig.getCommunityName()) +
                      "</b>!\n\nWhere are you headed today?";

            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            rows.add(List.of(
                    BotMessageBuilder.button("🏠 Home to Work", "DIRECTION:HOME_TO_WORK"),
                    BotMessageBuilder.button("🏢 Work to Home", "DIRECTION:WORK_TO_HOME")
            ));

            if (!myBookings.isEmpty()) {
                rows.add(List.of(
                        BotMessageBuilder.button(
                                "📜 My Bookings (" + myBookings.size() + ")", "MY_BOOKINGS")
                ));
            }

            if (hasPastRides) {
                rows.add(List.of(
                        BotMessageBuilder.button("🔄 Repost a Ride", "MY_RIDES")
                ));
            }

            rows.add(List.of(
                    BotMessageBuilder.button("👤 My Profile", "MY_PROFILE")
            ));

            bot.send(sendWithInline(chatId, prompt, rows));
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
                "🕐 <b>What time are you leaving? (Start pickup time)</b>\n\n" +
                        "Format: <code>MM/DD HH:MM</code>\n" +
                        "Example: <code>" +
                        LocalDateTime.now(ZoneId.of("Asia/Manila")).plusHours(1)
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

            // Repost flow — origin and destination already set, skip hub selection
            if (state.getOriginHubId() != null && state.getDestinationHubId() != null) {
                UserState updated = state
                        .withDepartureTime(etd)
                        .withFlow(BotFlow.POST_RIDE_CONFIRM);
                stateManager.save(chatId, updated);
                postRideHelper.showConfirmation(chatId, updated, bot);
                return;
            }

            // Normal post ride flow — ask for origin hub
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
        // Minimum 3 characters required
        if (text.trim().length() < 3) {
            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "⚠️ Please type at least 3 characters to search.\n\n" +
                            "Example: <code>BGC</code> or <code>Southmall</code>"));
            return;
        }

        List<HubResponse> suggestions = hubMatcher.suggest(text);

        if (suggestions.isEmpty()) {
            // No match — show recent hubs as fallback
            List<HubResponse> recentHubs = hubService.getRecentHubsForUser(
                    state.getCarpoolUserId());

            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            if (!recentHubs.isEmpty()) {
                for (HubResponse h : recentHubs) {
                    rows.add(List.of(BotMessageBuilder.button(
                            "🕐 " + h.name(), "HUB_ORIGIN:" + h.id())));
                }
            }
            rows.add(List.of(BotMessageBuilder.button("✏️ Try different name", "RETYPE_ORIGIN")));

            bot.send(sendWithInline(chatId,
                    "⚠️ Couldn't find <b>\"" + BotMessageBuilder.escape(text) + "\"</b>.\n\n" +
                            (recentHubs.isEmpty()
                                    ? "Try a more specific name or nearby landmark:"
                                    : "Here are your recent locations:"),
                    rows));
            return;
        }

        // Show matched suggestions
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (HubResponse s : suggestions) {
            rows.add(List.of(BotMessageBuilder.button(s.name(), "HUB_ORIGIN:" + s.id())));
        }
        rows.add(List.of(BotMessageBuilder.button("✏️ Type again", "RETYPE_ORIGIN")));

        bot.send(sendWithInline(chatId,
                "📍 <b>Select your start point:</b>\n\n" +
                        "Results for \"" + BotMessageBuilder.escape(text) + "\":",
                rows));
    }

    private void handlePostRideDestination(Long chatId, String text, UserState state, CarpoolBot bot) {
        // Minimum 3 characters required
        if (text.trim().length() < 3) {
            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "⚠️ Please type at least 3 characters to search.\n\n" +
                            "Example: <code>BGC</code> or <code>Alabang</code>"));
            return;
        }

        List<HubResponse> suggestions = hubMatcher.suggest(text);

        if (suggestions.isEmpty()) {
            // No match — show recent hubs as fallback
            List<HubResponse> recentHubs = hubService.getRecentHubsForUser(
                    state.getCarpoolUserId());

            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            if (!recentHubs.isEmpty()) {
                for (HubResponse h : recentHubs) {
                    rows.add(List.of(BotMessageBuilder.button(
                            "🕐 " + h.name(), "HUB_DEST:" + h.id())));
                }
            }
            rows.add(List.of(BotMessageBuilder.button("✏️ Try different name", "RETYPE_DEST")));

            bot.send(sendWithInline(chatId,
                    "⚠️ Couldn't find <b>\"" + BotMessageBuilder.escape(text) + "\"</b>.\n\n" +
                            (recentHubs.isEmpty()
                                    ? "Try a more specific name or nearby landmark:"
                                    : "Here are your recent locations:"),
                    rows));
            return;
        }

        // Show matched suggestions
        if (suggestions.stream().anyMatch(s -> s.id().equals(state.getOriginHubId()))) {
            suggestions = suggestions.stream()
                    .filter(s -> !s.id().equals(state.getOriginHubId()))
                    .toList();
        }

        if (suggestions.isEmpty()) {
            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "⚠️ Destination cannot be the same as pickup. Try again:"));
            return;
        }

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (HubResponse s : suggestions) {
            rows.add(List.of(BotMessageBuilder.button(s.name(), "HUB_DEST:" + s.id())));
        }
        rows.add(List.of(BotMessageBuilder.button("✏️ Type again", "RETYPE_DEST")));

        bot.send(sendWithInline(chatId,
                "🏁 <b>Select your end point:</b>\n\n" +
                        "Results for \"" + BotMessageBuilder.escape(text) + "\":",
                rows));
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
                            "⛽ <b>What's the suggested gas share per seat?</b>\n" +
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
                    "✅ Gas share: <b>₱" + amount + " / seat</b>"));
            postRideHelper.showNotesPrompt(chatId, carpoolUserId, state.withContribution(amount), bot);

        } catch (NumberFormatException e) {
            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "⚠️ Please enter a valid amount (e.g. <code>100</code> or <code>0</code>):"));
        }
    }

    private void handlePostRideNotes(Long chatId, String text, UserState state, CarpoolBot bot) {
        String notes = text.trim();
        UserState updated = state.withNotes(notes);
        stateManager.save(chatId, updated);
        // Route to vehicle confirmation step via CallbackHandler helper
        // Cannot call directly — delegate via state flow change
        // MessageHandler will re-route on next update if needed
        // Instead, show vehicle confirmation inline here:
        showVehicleConfirmStepFromMessage(chatId, state.getCarpoolUserId(), updated, bot);
    }

    private void showVehicleConfirmStepFromMessage(Long chatId, Long carpoolUserId,
                                                   UserState state, CarpoolBot bot) {
        var userOpt = userRepository.findById(carpoolUserId);
        if (userOpt.isEmpty()) {
            bot.send(BotMessageBuilder.text(chatId, "⚠️ User not found."));
            return;
        }

        var user = userOpt.get();
        UserState updated = state.withFlow(BotFlow.POST_RIDE_VEHICLE_CONFIRM);
        stateManager.save(chatId, updated);

        if (user.hasVehicleInfo()) {
            String vehicleDisplay = String.format("%s%s | 🔢 %s",
                    user.getCarColor() != null
                            ? "🎨 " + BotMessageBuilder.escape(user.getCarColor()) + " "
                            : "",
                    BotMessageBuilder.escape(user.getCarModel()),
                    BotMessageBuilder.escape(user.getPlateNumber()));

            var rows = List.of(
                    List.of(
                            BotMessageBuilder.button("✅ Yes, Proceed",  "VEHICLE_CONFIRM_YES"),
                            BotMessageBuilder.button("📝 Change Vehicle", "VEHICLE_CHANGE")
                    )
            );

            bot.send(sendWithInline(chatId,
                    "🚘 <b>Vehicle Confirmation</b>\n\n" +
                            "You are currently using:\n" +
                            "<b>" + vehicleDisplay + "</b>\n\n" +
                            "Use this for your ride?",
                    rows));
        } else {
            UserState vehicleState = updated
                    .withPendingCarColor(null)
                    .withPendingCarModel(null)
                    .withPendingPlateNumber(null)
                    .withFlow(BotFlow.SET_VEHICLE_COLOR);
            stateManager.save(chatId, vehicleState);

            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "🎨 <b>What color is your vehicle?</b>\n\n" +
                            "Example: <code>Silver</code>, <code>White</code>, <code>Black</code>"));
        }
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

    private void handleProfile(Long chatId, Long carpoolUserId, CarpoolBot bot) {
        try {
            com.carpool.service.dto.response.ProfileStatsResponse stats =
                    profileService.getProfileStats(carpoolUserId);

            StringBuilder sb = new StringBuilder();
            sb.append("👤 <b>My Profile</b>\n\n");

            sb.append(String.format("<b>%s</b>%s\n",
                    BotMessageBuilder.escape(stats.fullName()),
                    stats.telegramHandle() != null
                            ? " (@" + BotMessageBuilder.escape(stats.telegramHandle()) + ")"
                            : ""));
            sb.append(stats.roleLabel()).append("\n");
            sb.append("📅 Member since: ").append(stats.memberSince()).append("\n");

            // Vehicle info
            if (stats.driverRidesPosted() != null || stats.carModel() != null) {
                if (stats.carModel() != null && stats.plateNumber() != null) {
                    sb.append(String.format("\n🚘 %s%s\n🔢 %s\n",
                            stats.carColor() != null
                                    ? "🎨 " + BotMessageBuilder.escape(stats.carColor()) + " "
                                    : "",
                            BotMessageBuilder.escape(stats.carModel()),
                            BotMessageBuilder.escape(stats.plateNumber())));
                } else {
                    sb.append("\n🚘 <i>No vehicle info yet</i>\n");
                }
            }

            if (stats.driverRidesPosted() != null) {
                sb.append("\n🏆 <b>Driver Stats</b>\n");
                if (stats.driverCompletionRate() != null) {
                    sb.append(String.format("⭐ %d%% Completion Rate\n",
                            stats.driverCompletionRate()));
                }
                sb.append(String.format("🚗 Rides posted: %d\n",      stats.driverRidesPosted()));
                sb.append(String.format("✅ Completed: %d\n",          stats.driverCompleted()));
                sb.append(String.format("👥 Passengers served: %d\n",  stats.driverPassengersServed()));
                if (stats.driverCancelled() > 0) {
                    sb.append(String.format("❌ Cancelled: %d\n", stats.driverCancelled()));
                }
            }

            if (stats.passengerBookingsMade() != null) {
                sb.append("\n🧳 <b>Passenger Stats</b>\n");
                if (stats.passengerCompletionRate() != null) {
                    sb.append(String.format("⭐ %d%% Completion Rate\n",
                            stats.passengerCompletionRate()));
                }
                sb.append(String.format("📦 Bookings made: %d\n",  stats.passengerBookingsMade()));
                sb.append(String.format("✅ Completed: %d\n",       stats.passengerCompleted()));
                if (stats.passengerCancelledByMe() > 0) {
                    sb.append(String.format("❌ Cancelled by me: %d\n", stats.passengerCancelledByMe()));
                }
            }

            if (stats.driverRidesPosted() == null && stats.passengerBookingsMade() == null) {
                sb.append("\n<i>No activity yet. Post or book a ride to get started!</i>");
            }

            bot.send(sendWithInline(chatId, sb.toString(),
                    List.of(List.of(
                            BotMessageBuilder.button("🔄 Refresh",    "MY_PROFILE"),
                            BotMessageBuilder.button("🚘 My Vehicle", "VEHICLE_CHANGE"),
                            BotMessageBuilder.button("🏠 Menu",       "MAIN_MENU")
                    ))));

        } catch (Exception e) {
            log.error("Failed to load profile for userId={}: {}", carpoolUserId, e.getMessage());
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Could not load profile. Please try again."));
        }
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
                        BotMessageBuilder.button("📅 Custom Date & Time", "TIME:CUSTOM"),
                        BotMessageBuilder.button("🔍 Show All Today",         "TIME:ALL_TODAY")
                )
        );
        bot.send(sendWithInline(chatId,
                "🕐 <b>When do you want to leave?</b>\n\nSelect a time window:", rows));
    }

    // ── My rides / bookings ───────────────────────────────────────────────

    private void showMyRides(Long chatId, Long carpoolUserId, CarpoolBot bot) {
        List<RideResponse> rides = rideService.getRecentRidesForRepost(carpoolUserId);

        if (rides.isEmpty()) {
            bot.send(BotMessageBuilder.text(chatId,
                    "🚗 <b>My Rides</b>\n\n<i>No past rides yet.</i>"));
            return;
        }

        // Show last 10 COMPLETED or CANCELLED rides only — these have repost buttons
        List<RideResponse> recent = rides.stream()
                .filter(r -> r.status().name().equals("COMPLETED")
                        || r.status().name().equals("CANCELLED"))
                .limit(3)
                .toList();

        if (recent.isEmpty()) {
            bot.send(BotMessageBuilder.text(chatId,
                    "🚗 <b>My Rides</b>\n\n<i>No completed or cancelled rides yet.</i>"));
            return;
        }

        StringBuilder sb = new StringBuilder("🚗 <b>My Rides</b>\n\n");
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (int i = 0; i < recent.size(); i++) {
            RideResponse r = recent.get(i);
            String dirEmoji = r.direction() == com.carpool.domain.enums.RideDirection.HOME_TO_WORK
                    ? "🏠" : "🏢";
            String statusLabel = switch (r.status().name()) {
                case "COMPLETED" -> "🏁";
                case "CANCELLED" -> "❌";
                default          -> "📋";
            };

            sb.append(String.format("<b>%d.</b> %s %s → %s | %s %s | ⛽ ₱%.2f share\n",
                    i + 1,
                    dirEmoji,
                    BotMessageBuilder.escape(r.originHub().name()),
                    BotMessageBuilder.escape(r.destinationHub().name()),
                    statusLabel,
                    r.departureTime()
                            .atZone(java.time.ZoneId.of("Asia/Manila"))
                            .format(DateTimeFormatter.ofPattern("MMM d h:mma")),
                    r.contributionAmount()));

            rows.add(List.of(InlineKeyboardButton.builder()
                    .text(String.format("🔄 #%d — %s → %s",
                            i + 1,
                            r.originHub().name(),
                            r.destinationHub().name()))
                    .callbackData("REPOST_RIDE:" + r.id())
                    .build()));
        }

        rows.add(List.of(InlineKeyboardButton.builder()
                .text("🏠 Menu")
                .callbackData("MAIN_MENU")
                .build()));

        bot.send(sendWithInline(chatId, sb.toString(), rows));
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
                String driverInfo = b.ride().driver().telegramHandle() != null
                        ? " (@" + BotMessageBuilder.escape(b.ride().driver().telegramHandle()) + ")"
                        : "";

                sb.append(String.format("<b>%d.</b> %s → %s | 🕐 %s | ⛽ ₱%.2f share\n👤 %s%s\n",
                        i + 1,
                        BotMessageBuilder.escape(b.ride().originHub().name()),
                        BotMessageBuilder.escape(b.ride().destinationHub().name()),
                        b.ride().departureTime()
                                .atZone(ZoneId.of("Asia/Manila"))
                                .format(DateTimeFormatter.ofPattern("MMM d h:mma")),
                        b.contributionDue(),
                        BotMessageBuilder.escape(b.ride().driver().fullName()),
                        driverInfo));

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
            String input = text.trim();
            LocalDateTime from;

            // Accept both "MM/DD HH:MM" (with date) and "HH:MM" (today)
            if (input.matches("\\d{2}/\\d{2} \\d{2}:\\d{2}")) {
                // MM/DD HH:MM — include date
                from = LocalDateTime.parse(
                        LocalDateTime.now().getYear() + "/" + input,
                        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));
            } else {
                // HH:MM only — default to today (backward compatible)
                LocalTime time = LocalTime.parse(input,
                        DateTimeFormatter.ofPattern("HH:mm"));
                from = LocalDateTime.now().toLocalDate().atTime(time);
            }

            LocalDateTime to = from.plusHours(2);

            List<RideResponse> rides = rideService.getRidesByDirection(
                    state.getDirection(), carpoolUserId, from, to,
                    state.getFilterMaxPrice(),
                    state.getFilterMinSeats(),
                    state.getFilterSortBy());

            String dirLabel = state.getDirection() == RideDirection.HOME_TO_WORK
                    ? "🏠 Home → Work" : "🏢 Work → Home";

            stateManager.save(chatId, state
                    .withSearchFrom(from)
                    .withSearchTo(to)
                    .withFlow(BotFlow.SEARCH_RESULTS));

            String timeLabel = from.atZone(ZoneId.of("Asia/Manila"))
                    .format(DateTimeFormatter.ofPattern("MMM d 'at' h:mm a"));

            bot.send(BotMessageBuilder.rideList(chatId, rides,
                    "🔍 <b>Available Rides — " + dirLabel +
                            "</b>\n<i>Around " + timeLabel + "</i>"));

        } catch (Exception e) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Invalid format.\n\n" +
                            "For today: <code>HH:MM</code> — e.g. <code>07:30</code>\n" +
                            "For another date: <code>MM/DD HH:MM</code> — e.g. <code>04/25 07:30</code>"));
        }
    }

    /**
     * Handles custom note text input — saves to DB then proceeds to confirmation.
     */
    private void handlePostRideNotesWrite(Long chatId, String text,
                                          UserState state, CarpoolBot bot,
                                          Long carpoolUserId) {
        String notes = text.trim();
        driverNoteService.saveOrUpdate(carpoolUserId, notes);

        UserState updated = state.withNotes(notes);
        stateManager.save(chatId, updated);

        showVehicleConfirmStepFromMessage(chatId, carpoolUserId, updated, bot);
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

    // ── Vehicle input handlers ────────────────────────────────────────────

    private void handleSetVehicleColor(Long chatId, String text,
                                       UserState state, CarpoolBot bot) {
        String color = text.trim();
        if (color.length() > 50) {
            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "⚠️ Color is too long. Please enter a shorter description:"));
            return;
        }

        stateManager.save(chatId, state
                .withPendingCarColor(color)
                .withFlow(BotFlow.SET_VEHICLE_MODEL));

        bot.send(BotMessageBuilder.textWithCancel(chatId,
                "🚘 <b>What's your car model?</b>\n\n" +
                        "Example: <code>Toyota Vios</code>, <code>Honda City</code>"));
    }

    private void handleSetVehicleModel(Long chatId, String text,
                                       UserState state, CarpoolBot bot) {
        String model = text.trim();
        if (model.isBlank() || model.length() > 100) {
            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "⚠️ Please enter a valid car model (max 100 characters):"));
            return;
        }

        stateManager.save(chatId, state
                .withPendingCarModel(model)
                .withFlow(BotFlow.SET_VEHICLE_PLATE));

        bot.send(BotMessageBuilder.textWithCancel(chatId,
                "🔢 <b>What's your plate number?</b>\n\n" +
                        "Example: <code>ABC 1234</code>"));
    }

    private void handleSetVehiclePlate(Long chatId, String text,
                                       UserState state, Long carpoolUserId,
                                       CarpoolBot bot) {
        String plate = text.trim().toUpperCase();
        if (plate.isBlank() || plate.length() > 20) {
            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "⚠️ Please enter a valid plate number (max 20 characters):"));
            return;
        }

        // Save pending plate to state then show confirmation
        UserState updated = state
                .withPendingPlateNumber(plate)
                .withFlow(BotFlow.POST_RIDE_VEHICLE_CONFIRM);
        stateManager.save(chatId, updated);

        showVehicleConfirmation(chatId, updated, bot);
    }

    private void showVehicleConfirmation(Long chatId, UserState state, CarpoolBot bot) {
        String vehicleDisplay = String.format("%s%s | 🔢 %s",
                state.getPendingCarColor() != null
                        ? "🎨 " + BotMessageBuilder.escape(state.getPendingCarColor()) + " "
                        : "",
                BotMessageBuilder.escape(state.getPendingCarModel()),
                BotMessageBuilder.escape(state.getPendingPlateNumber()));

        var rows = List.of(
                List.of(
                        BotMessageBuilder.button("✅ Save & Continue", "VEHICLE_CONFIRM_SAVE"),
                        BotMessageBuilder.button("✏️ Change",          "VEHICLE_CHANGE")
                ),
                List.of(
                        BotMessageBuilder.button("❌ Cancel", "CANCEL_POST_RIDE")
                )
        );

        bot.send(sendWithInline(chatId,
                "🚘 <b>Vehicle Details</b>\n\n" +
                        vehicleDisplay + "\n\n" +
                        "Save this vehicle info?",
                rows));
    }

    private void handleVehicleCommand(Long chatId, Long carpoolUserId,
                                      UserState state, CarpoolBot bot) {
        // Load current vehicle from DB
        var userOpt = userRepository.findById(carpoolUserId);
        if (userOpt.isEmpty()) {
            bot.send(BotMessageBuilder.text(chatId, "⚠️ User not found."));
            return;
        }

        var user = userOpt.get();

        if (user.hasVehicleInfo()) {
            String current = String.format("%s%s | 🔢 %s",
                    user.getCarColor() != null
                            ? "🎨 " + BotMessageBuilder.escape(user.getCarColor()) + " "
                            : "",
                    BotMessageBuilder.escape(user.getCarModel()),
                    BotMessageBuilder.escape(user.getPlateNumber()));

            var rows = List.of(
                    List.of(
                            BotMessageBuilder.button("📝 Update Vehicle", "VEHICLE_CHANGE"),
                            BotMessageBuilder.button("🗑️ Remove",         "VEHICLE_REMOVE")
                    ),
                    List.of(BotMessageBuilder.button("🏠 Menu", "MAIN_MENU"))
            );

            bot.send(sendWithInline(chatId,
                    "🚘 <b>Your Vehicle</b>\n\n" + current, rows));
        } else {
            var rows = List.of(List.of(
                    BotMessageBuilder.button("🚘 Add Vehicle", "VEHICLE_CHANGE"),
                    BotMessageBuilder.button("🏠 Menu",        "MAIN_MENU")
            ));

            bot.send(sendWithInline(chatId,
                    "🚘 <b>Your Vehicle</b>\n\n" +
                            "<i>No vehicle info yet.</i>\n\n" +
                            "Add your vehicle so passengers know what to look for.",
                    rows));
        }
    }

    /**
     * Welcome screen for brand new users — friendly intro before showing terms.
     */
    private void showWelcomeScreen(Long chatId, User user, CarpoolBot bot) {
        String firstName = user.getFullName().split(" ")[0];

        var rows = List.of(
                List.of(
                        BotMessageBuilder.button("📄 View Terms & Accept", "TERMS_WELCOME"),
                        BotMessageBuilder.button("❌ Not Now",              "TERMS_DECLINE")
                )
        );

        bot.send(sendWithInline(chatId,
                "👋 <b>Welcome, " + BotMessageBuilder.escape(firstName) + "!</b>\n\n" +
                        "You've joined the <b>" +
                        BotMessageBuilder.escape(botConfig.getCommunityName()) +
                        " Carpooling Community</b>. 🚗\n\n" +
                        "Before we get started, please review and accept our community terms " +
                        "to keep this a safe, legal, and non-profit carpooling group.\n\n" +
                        "<i>Tap below to review the terms.</i>",
                rows));
    }

    /**
     * Reminder screen for users who declined or haven't accepted yet.
     */
    private void showTermsReminder(Long chatId, CarpoolBot bot) {
        var rows = List.of(List.of(
                BotMessageBuilder.button("📄 Review Terms", "TERMS_WELCOME")
        ));

        bot.send(sendWithInline(chatId,
                "⚠️ <b>Terms Acceptance Required</b>\n\n" +
                        "You'll need to accept our community terms to use this bot.\n\n" +
                        "Tap below to review and accept.",
                rows));
    }
}