package com.carpool.bot.handler;

import com.carpool.bot.CarpoolBot;
import com.carpool.bot.handler.context.BotContext;
import com.carpool.bot.handler.helper.BotFlowHelper;
import com.carpool.bot.state.BotFlow;
import com.carpool.bot.state.StateManager;
import com.carpool.bot.state.UserState;
import com.carpool.bot.util.BotMessageBuilder;
import com.carpool.domain.entity.User;
import com.carpool.domain.enums.RideDirection;
import com.carpool.domain.enums.RideStatus;
import com.carpool.repository.UserRepository;
import com.carpool.service.dto.response.ProfileStatsResponse;
import com.carpool.service.dto.response.RideResponse;
import com.carpool.service.profile.ProfileService;
import com.carpool.service.rating.RatingService;
import com.carpool.service.ride.RideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * Thin message/command router — handles text messages and bot commands.
 * Delegates all business logic to sub-handlers via BotFlowHelper.
 *
 * No business logic lives here. The flow switch maps BotFlow state to
 * the correct sub-handler method. Adding a new flow step = one new
 * case in the flow switch + implementation in the relevant sub-handler.
 *
 * Pattern: Command (flow dispatch) + Facade (sub-handler delegation)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageHandler {

    private final StateManager   stateManager;
    private final UserRepository userRepository;
    private final RideService    rideService;
    private final RatingService  ratingService;
    private final ProfileService profileService;
    private final HelpHandler    helpHandler;

    // ── Sub-handlers ──────────────────────────────────────────────────────
    private final BotFlowHelper flowHelper;
    private final PostRideHandler   postRideHandler;
    private final BookingHandler    bookingHandler;
    private final RideSearchHandler rideSearchHandler;
    private final DriverHandler     driverHandler;
    private final ProfileHandler    profileHandler;
    private final RatingHandler     ratingHandler;

    private static final ZoneId         MANILA    = ZoneId.of("Asia/Manila");
    private static final Set<String>    GREETINGS = Set.of("hi", "hey", "start", "ey");

    // ── Entry point ───────────────────────────────────────────────────────

    public void handle(Message message, CarpoolBot bot) {
        Long chatId     = message.getChatId();
        Long telegramId = message.getFrom().getId();
        String text     = message.getText().trim();

        boolean isNewUser = !userRepository.existsByTelegramId(telegramId);

        final User user = userRepository.findByTelegramId(telegramId)
                .orElseGet(() -> {
                    try {
                        String firstName = message.getFrom().getFirstName();
                        String lastName  = message.getFrom().getLastName();
                        String handle    = message.getFrom().getUserName();
                        String fullName  = (lastName != null) ? firstName + " " + lastName : firstName;
                        log.info("Auto-registered new user telegramId={} name={}",telegramId, fullName);
                        return userRepository.save(User.builder()
                                .telegramId(telegramId)
                                .telegramHandle(handle)
                                .fullName(fullName)
                                .build());
                    } catch (DataIntegrityViolationException e) {
                        return userRepository.findByTelegramId(telegramId)
                                .orElseThrow(() -> new RuntimeException("User registration failed. Please try again. /start"));
                    }
                });

        // Sync Telegram profile changes on every message — lightweight
        syncProfile(user, message);

        // Onboarding gate — terms not yet accepted
        if (isNewUser || !user.isTermsAccepted()) {
            if (!isNewUser && (user.isRecentlyDeclined() || !user.isTermsAccepted())) {
                profileHandler.showTermsReminder(chatId, bot);
                return;
            }
            if (isNewUser) {
                profileHandler.showWelcomeScreen(chatId, user, bot);
                return;
            }
        }

        Long carpoolUserId = user.getId();

        UserState state = stateManager.get(chatId);
        if (state == null) {
            state = UserState.initial(carpoolUserId);
            stateManager.save(chatId, state);
        }

        // ── Commands ──────────────────────────────────────────────────────
        if (text.startsWith("/")) {
            handleCommand(text, chatId, carpoolUserId, state, bot, telegramId);
            return;
        }

        // ── Direction tap from reply keyboard ─────────────────────────────
        if (text.equals("🏠 Home to Work") || text.equals("🏢 Work to Home")) {
            RideDirection direction = text.equals("🏠 Home to Work")
                    ? RideDirection.HOME_TO_WORK : RideDirection.WORK_TO_HOME;

            if (state.getFlow() == BotFlow.SEARCH_SELECT_DIRECTION) {
                YearMonth month = YearMonth.now(MANILA);
                stateManager.save(chatId, state
                        .withDirection(direction)
                        .withCalendarMonth(month)
                        .withFlow(BotFlow.SEARCH_SELECT_DATE));
                flowHelper.showCalendar(chatId, null, month, bot);
                return;
            }
            if (state.getFlow() == BotFlow.POST_RIDE_DIRECTION) {
                UserState updated = state.withDirection(direction);
                stateManager.save(chatId, updated);
                postRideHandler.askForEtd(chatId, updated, bot);
                return;
            }
            flowHelper.handleDirectionSelected(
                    chatId, carpoolUserId, direction, state, bot);
            return;
        }

        // ── Greeting shortcut ─────────────────────────────────────────────
        if (GREETINGS.contains(text.toLowerCase())) {
            flowHelper.showMainMenu(chatId, carpoolUserId, state, bot);
            return;
        }

        // ── Flow-based text dispatch ──────────────────────────────────────
        switch (state.getFlow()) {
            case POST_RIDE_DEPARTURE_TIME ->
                    postRideHandler.handlePostRideEtd(chatId, text, state, bot);
            case POST_RIDE_ORIGIN ->
                    postRideHandler.handlePostRideOrigin(chatId, text, state, bot);
            case POST_RIDE_DESTINATION ->
                    postRideHandler.handlePostRideDestination(chatId, text, state, bot);
            case POST_RIDE_SEATS ->
                    postRideHandler.handlePostRideSeats(chatId, text, state, bot);
            case POST_RIDE_CONTRIBUTION ->
                    postRideHandler.handlePostRideContribution(
                            chatId, text, state, carpoolUserId, bot);
            case POST_RIDE_NOTES ->
                    postRideHandler.handlePostRideNotes(chatId, text, state, bot);
            case POST_RIDE_NOTES_WRITE ->
                    postRideHandler.handlePostRideNotesWrite(
                            chatId, text, state, carpoolUserId, bot);
            case REPOST_EDIT_SEATS ->
                    postRideHandler.handleRepostEditSeats(chatId, text, state, bot);
            case REPOST_EDIT_CONTRIBUTION ->
                    postRideHandler.handleRepostEditContribution(chatId, text, state, bot);
            case REPOST_EDIT_NOTES ->
                    postRideHandler.handleRepostEditNotes(chatId, text, state, bot);
            case SEARCH_SELECT_TIME ->
                    handleCustomTimeInput(chatId, text, state, carpoolUserId, bot);
            case BOOKING_MESSAGE ->
                    bookingHandler.handleBookingMessage(
                            chatId, text, state, carpoolUserId, bot);
            case SET_VEHICLE_COLOR ->
                    handleSetVehicleColor(chatId, text, state, bot);
            case SET_VEHICLE_MODEL ->
                    handleSetVehicleModel(chatId, text, state, bot);
            case SET_VEHICLE_PLATE ->
                    handleSetVehiclePlate(chatId, text, state, carpoolUserId, bot);
            case RATING_COMMENT ->
                    ratingHandler.handleRatingComment(
                            chatId, text, state, carpoolUserId, bot);
            default ->
                    flowHelper.showMainMenu(chatId, carpoolUserId, state, bot);
        }
    }

    // ── Commands ──────────────────────────────────────────────────────────

    private void handleCommand(String command, Long chatId, Long carpoolUserId,
                               UserState state, CarpoolBot bot, Long telegramId) {
        String cmd = command.split(" ")[0].toLowerCase();
        switch (cmd) {
            case "/start"      -> handleStart(command, chatId, carpoolUserId, state, bot);
            case "/cancel"     -> handleCancel(chatId, bot);
            case "/myrides"    -> driverHandler.showMyRides(chatId, carpoolUserId, bot);
            case "/mybookings" -> bookingHandler.showMyBookings(chatId, carpoolUserId, bot);
            case "/postride"   -> postRideHandler.handleStartPostRide(
                    buildCtx(chatId, carpoolUserId, telegramId, state, null, bot));
            case "/findride"   -> rideSearchHandler.handleStartFindRide(
                    buildCtx(chatId, carpoolUserId, telegramId, state, null, bot));
            case "/profile"    -> profileHandler.handleMyProfile(
                    buildCtx(chatId, carpoolUserId, telegramId, state, null, bot));
            case "/vehicle"    -> profileHandler.handleVehicleCommand(
                    chatId, carpoolUserId, state, bot);
            case "/help"       -> helpHandler.showHelpMenu(chatId, bot);
            default -> bot.send(BotMessageBuilder.text(chatId,
                    "Unknown command. Type /help to see available commands."));
        }
    }

    /**
     * /start — plain start shows main menu.
     * /start RIDE_42 — deep-links directly to a specific ride card.
     */
    private void handleStart(String command, Long chatId, Long carpoolUserId,
                             UserState state, CarpoolBot bot) {
        String[] parts = command.split(" ");
        if (parts.length > 1 && parts[1].startsWith("RIDE_")) {
            try {
                Long rideId = Long.parseLong(parts[1].substring(5));
                RideResponse ride = rideService.getRideById(rideId);

                if (ride.status() != RideStatus.ACTIVE
                        && ride.status() != RideStatus.FULL) {
                    bot.send(flowHelper.sendWithInline(chatId,
                            "⚠️ <b>This ride is no longer available.</b>\n\n" +
                                    "It may have already departed or been cancelled.",
                            List.of(List.of(
                                    BotMessageBuilder.button("🔍 Find a Ride", "FIND_RIDE"),
                                    BotMessageBuilder.button("🏠 Menu",        "MAIN_MENU")
                            ))));
                    return;
                }

                boolean isDriver = ride.driver().id().equals(carpoolUserId);
                List<List<InlineKeyboardButton>> rows = isDriver
                        ? List.of(List.of(
                        BotMessageBuilder.button(
                                "📋 View Bookings", "RIDE_BOOKINGS:" + rideId),
                        BotMessageBuilder.button("🏠 Menu", "MAIN_MENU")))
                        : List.of(List.of(
                        BotMessageBuilder.button(
                                "✅ Book This Ride", "BOOK_RIDE:" + rideId),
                        BotMessageBuilder.button("🏠 Menu", "MAIN_MENU")));

                stateManager.save(chatId, state.withSelectedRideId(rideId));
                String ratingLabel = ratingService.getDriverRatingLabel(ride.driver().id());
                ProfileStatsResponse stats = profileService.getProfileStats(ride.driver().id());
                String memberBadge = BotMessageBuilder.buildMemberBadge(stats);
                bot.send(flowHelper.sendWithInline(chatId,
                        BotMessageBuilder.formatRideCard(ride, ratingLabel, memberBadge), rows));

            } catch (NumberFormatException e) {
                log.warn("Invalid deep link parameter: {}", parts[1]);
                flowHelper.showMainMenu(chatId, carpoolUserId, state, bot);
            } catch (Exception e) {
                log.error("Deep link failed: error={}", e.getMessage());
                bot.send(BotMessageBuilder.text(chatId,
                        "⚠️ Could not load this ride. It may no longer exist."));
                flowHelper.showMainMenu(chatId, carpoolUserId, state, bot);
            }
        } else {
            flowHelper.showMainMenu(chatId, carpoolUserId, state, bot);
        }
    }

    private void handleCancel(Long chatId, CarpoolBot bot) {
        stateManager.reset(chatId);
        bot.send(BotMessageBuilder.textWithRemoveKeyboard(chatId,
                "❌ Cancelled. Use /start to go back to the main menu."));
    }

    // ── Custom time input ─────────────────────────────────────────────────

    private void handleCustomTimeInput(Long chatId, String text, UserState state,
                                       Long carpoolUserId, CarpoolBot bot) {
        try {
            String input = text.trim();
            LocalDateTime now = LocalDateTime.now(MANILA);
            LocalDateTime from;

            if (input.matches("\\d{2}/\\d{2} \\d{2}:\\d{2}")) {
                from = LocalDateTime.parse(
                        now.getYear() + "/" + input,
                        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));
            } else {
                LocalTime time = LocalTime.parse(input,
                        DateTimeFormatter.ofPattern("HH:mm"));
                from = now.toLocalDate().atTime(time);
            }

            // HH:MM only and past time — auto-adjust to now
            if (!input.matches("\\d{2}/\\d{2} \\d{2}:\\d{2}") && from.isBefore(now)) {
                bot.send(BotMessageBuilder.textNoMenu(chatId,
                        "⚠️ That time has already passed.\n\n" +
                                "Showing available rides from <b>now</b> onwards instead."));
                from = now;
            }

            // MM/DD HH:MM with explicit past date — block
            if (input.matches("\\d{2}/\\d{2} \\d{2}:\\d{2}") && from.isBefore(now)) {
                bot.send(BotMessageBuilder.textWithCancel(chatId,
                        "⚠️ That date and time has already passed.\n\n" +
                                "Please enter a future date and time:\n" +
                                "Format: <code>MM/DD HH:MM</code>"));
                return;
            }

            // Search window: 1 hour before typed time to 2 hours after.
            // Prevents missing rides that depart slightly before the typed time.
            LocalDateTime searchFrom = from.minusHours(1);
            LocalDateTime to         = from.plusHours(2);

            stateManager.save(chatId, state
                    .withSearchFrom(searchFrom)
                    .withSearchTo(to)
                    .withFlow(BotFlow.SEARCH_RESULTS));

            rideSearchHandler.showFilteredRides(
                    chatId, carpoolUserId, state, searchFrom, to, bot);

        } catch (Exception e) {
            LocalDateTime now = LocalDateTime.now(MANILA);
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Invalid format.\n\n" +
                            "For today: <code>HH:MM</code> — e.g. <code>" +
                            now.plusHours(1).format(DateTimeFormatter.ofPattern("HH:mm")) +
                            "</code>\n" +
                            "For another date: <code>MM/DD HH:MM</code> — e.g. <code>" +
                            now.plusDays(1).format(DateTimeFormatter.ofPattern("MM/dd")) +
                            " " +
                            now.plusHours(1).format(DateTimeFormatter.ofPattern("HH:mm")) +
                            "</code>"));
        }
    }

    // ── Vehicle text input handlers ───────────────────────────────────────

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

    private void handleSetVehiclePlate(Long chatId, String text, UserState state,
                                       Long carpoolUserId, CarpoolBot bot) {
        String plate = text.trim().toUpperCase();
        if (plate.isBlank() || plate.length() > 20) {
            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "⚠️ Please enter a valid plate number (max 20 characters):"));
            return;
        }
        UserState updated = state
                .withPendingPlateNumber(plate)
                .withFlow(BotFlow.POST_RIDE_VEHICLE_CONFIRM);
        stateManager.save(chatId, updated);
        profileHandler.showVehicleConfirmation(chatId, updated, bot);
    }

    // ── Profile sync ──────────────────────────────────────────────────────

    /**
     * Syncs Telegram name and handle changes on every message.
     * Only writes to DB when something actually changed.
     */
    private void syncProfile(User user, Message message) {
        String latestHandle = message.getFrom().getUserName();
        String latestFirst  = message.getFrom().getFirstName();
        String latestLast   = message.getFrom().getLastName();
        String latestName   = latestLast != null ? latestFirst + " " + latestLast : latestFirst;

        boolean handleChanged = latestHandle != null
                && !latestHandle.equals(user.getTelegramHandle());
        boolean nameChanged   = !latestName.equals(user.getFullName());

        if (handleChanged || nameChanged) {
            if (handleChanged) user.setTelegramHandle(latestHandle);
            if (nameChanged)   user.setFullName(latestName);
            userRepository.save(user);
            log.info("Synced profile for userId={} handleChanged={} nameChanged={}",
                    user.getId(), handleChanged, nameChanged);
        }
    }

    // ── Context builder helper ────────────────────────────────────────────

    private BotContext buildCtx(Long chatId, Long carpoolUserId, Long telegramId,
                                UserState state, String payload, CarpoolBot bot) {
        return new BotContext(chatId, carpoolUserId, telegramId, state, payload,
                payload != null ? new String[]{payload} : new String[]{}, bot, null);
    }
}