package com.carpool.bot.handler;

import com.carpool.bot.CarpoolBot;
import com.carpool.bot.state.StateManager;
import com.carpool.bot.state.UserState;
import com.carpool.bot.util.BotMessageBuilder;
import com.carpool.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import java.util.HashMap;
import java.util.Map;

/**
 * Thin callback router — parses incoming callback data, builds BotContext,
 * and delegates to the registered BotCommand.
 *
 * No business logic lives here. Adding a new callback action = one new
 * commands.put() entry in registerCommands(). This class never changes again.
 *
 * Pattern: Command (Map registry) + Facade (sub-handler delegation)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CallbackHandler {

    private final StateManager           stateManager;
    private final UserRepository         userRepository;
    private final SessionRecoveryHandler sessionRecoveryHandler;
    private final HelpHandler            helpHandler;

    // ── Sub-handlers ──────────────────────────────────────────────────────
    private final BotFlowHelper     flowHelper;
    private final PostRideHandler   postRideHandler;
    private final BookingHandler    bookingHandler;
    private final RideSearchHandler rideSearchHandler;
    private final DriverHandler     driverHandler;
    private final ProfileHandler    profileHandler;
    private final RatingHandler     ratingHandler;

    private final Map<String, BotCommand> commands = new HashMap<>();

    @PostConstruct
    private void registerCommands() {

        // ── Navigation ────────────────────────────────────────────────────
        commands.put("MAIN_MENU", ctx -> flowHelper.showMainMenu(
                ctx.chatId(), ctx.carpoolUserId(), ctx.state(), ctx.bot()));
        commands.put("NOOP", ctx -> { /* page indicator — intentional no-op */ });

        // ── Direction ─────────────────────────────────────────────────────
        commands.put("DIRECTION", ctx -> postRideHandler.handleDirectionCallback(ctx));

        // ── Post ride ─────────────────────────────────────────────────────
        commands.put("POST_RIDE",         ctx -> postRideHandler.handleStartPostRide(ctx));
        commands.put("CONFIRM_POST_RIDE", ctx -> postRideHandler.handleConfirmPostRide(ctx));
        commands.put("CANCEL_POST_RIDE",  ctx -> postRideHandler.handleCancelPostRide(ctx));
        commands.put("HUB_ORIGIN",        ctx -> postRideHandler.handleHubOriginSelected(ctx));
        commands.put("HUB_DEST",          ctx -> postRideHandler.handleHubDestSelected(ctx));
        commands.put("RETYPE_ORIGIN",     ctx -> postRideHandler.handleRetypeOrigin(ctx));
        commands.put("RETYPE_DEST",       ctx -> postRideHandler.handleRetypeDest(ctx));
        commands.put("NOTE_PREVIEW",      ctx -> postRideHandler.handleNotePreview(ctx));
        commands.put("NOTE_APPLY",        ctx -> postRideHandler.handleNoteApply(ctx));
        commands.put("NOTE_WRITE",        ctx -> postRideHandler.handleNoteWrite(ctx));
        commands.put("NOTE_CHOOSE_OTHER", ctx -> postRideHandler.handleNoteChooseOther(ctx));
        commands.put("SKIP_NOTES",        ctx -> postRideHandler.handleSkipNotes(ctx));
        commands.put("REPOST_RIDE",       ctx -> postRideHandler.handleRepostRide(ctx));
        commands.put("RIDE_TIME",         ctx -> postRideHandler.handleRideTimeSelected(ctx));  // ← add
        commands.put("TIME_NAV",          ctx -> postRideHandler.handleTimeNavigation(ctx));

        // ── Find ride ─────────────────────────────────────────────────────
        commands.put("FIND_RIDE",     ctx -> rideSearchHandler.handleStartFindRide(ctx));
        commands.put("VIEW_RIDE",     ctx -> rideSearchHandler.handleViewRide(ctx));
        commands.put("CAL_NAV",       ctx -> rideSearchHandler.handleCalendarNav(ctx));   // ← add
        commands.put("CAL_DATE",      ctx -> rideSearchHandler.handleDateSelected(ctx));
        commands.put("TIME",          ctx -> rideSearchHandler.handleTimeSelection(ctx));
        commands.put("SEARCH_FILTER", ctx -> rideSearchHandler.handleSearchFilter(ctx));
        commands.put("APPLY_FILTER",  ctx -> rideSearchHandler.handleApplyFilter(ctx));
        commands.put("RESET_FILTER",  ctx -> rideSearchHandler.handleResetFilter(ctx));
        commands.put("RIDE_PAGE",     ctx -> rideSearchHandler.handleRidePage(ctx));

        // ── Booking (passenger) ───────────────────────────────────────────
        commands.put("BOOK_RIDE",   ctx -> bookingHandler.handleBookRide(ctx));
        commands.put("BOOK_NOW",    ctx -> bookingHandler.executeBooking(
                ctx.chatId(), ctx.entityId(), ctx.carpoolUserId(), null, ctx.bot()));
        commands.put("MY_BOOKINGS",           ctx -> bookingHandler.handleMyBookings(ctx));
        commands.put("VIEW_BOOKING",          ctx -> bookingHandler.handleViewBooking(ctx));
        commands.put("PAST_BOOKINGS",         ctx -> bookingHandler.handlePastBookings(ctx));
        commands.put("CANCEL_BOOKING",        ctx -> bookingHandler.handleCancelBooking(ctx));
        commands.put("CANCEL_BOOKING_REASON", ctx -> bookingHandler.handleCancelBookingWithReason(ctx));

        // ── Driver management ─────────────────────────────────────────────
        commands.put("MY_RIDES",     ctx -> driverHandler.showMyRides(
                ctx.chatId(), ctx.carpoolUserId(), ctx.bot()));
        commands.put("RIDE_BOOKINGS",          ctx -> driverHandler.handleDriverBookings(ctx));
        commands.put("DRIVER_BOOKINGS",        ctx -> driverHandler.handleDriverBookings(ctx));
        commands.put("VIEW_DRIVER_BOOKING",    ctx -> driverHandler.handleViewDriverBooking(ctx));
        commands.put("ACCEPT_BOOKING",         ctx -> driverHandler.handleAcceptBooking(ctx));
        commands.put("DECLINE_BOOKING",        ctx -> driverHandler.handleDeclineBooking(ctx));
        commands.put("DECLINE_BOOKING_REASON", ctx -> driverHandler.handleDeclineBookingWithReason(ctx));
        commands.put("PENDING_REQUESTS",       ctx -> driverHandler.handlePendingRequests(ctx));
        commands.put("VIEW_PENDING",           ctx -> driverHandler.handleViewPendingRequest(ctx));
        commands.put("CANCEL_RIDE",            ctx -> driverHandler.handleCancelRide(ctx));
        commands.put("CONFIRM_CANCEL_RIDE",    ctx -> driverHandler.handleConfirmCancelRide(ctx));
        commands.put("DEPART_RIDE",            ctx -> driverHandler.handleDepartRide(ctx));
        commands.put("COMPLETE_RIDE",          ctx -> driverHandler.handleCompleteRide(ctx));

        // ── Vehicle ───────────────────────────────────────────────────────
        commands.put("VEHICLE_CONFIRM_YES",  ctx -> profileHandler.handleVehicleConfirmYes(ctx));
        commands.put("VEHICLE_CONFIRM_SAVE", ctx -> profileHandler.handleVehicleConfirmSave(ctx));
        commands.put("VEHICLE_CHANGE",       ctx -> profileHandler.handleVehicleChange(ctx));
        commands.put("VEHICLE_REMOVE",       ctx -> profileHandler.handleVehicleRemove(ctx));

        // ── Profile ───────────────────────────────────────────────────────
        commands.put("MY_PROFILE",      ctx -> profileHandler.handleMyProfile(ctx));
        commands.put("ADMIN_STATS",     ctx -> profileHandler.handleAdminStats(ctx));
        commands.put("REANNOUNCE_RIDE", ctx -> profileHandler.handleReannounceRide(ctx));

        // ── Terms ─────────────────────────────────────────────────────────
        commands.put("TERMS_WELCOME",    ctx -> profileHandler.handleTermsWelcome(
                ctx.chatId(), ctx.bot()));
        commands.put("TERMS_VIEW_AGAIN", ctx -> profileHandler.handleTermsWelcome(
                ctx.chatId(), ctx.bot()));
        commands.put("TERMS_ACCEPT",     ctx -> profileHandler.handleTermsAccept(ctx));
        commands.put("TERMS_DECLINE",    ctx -> profileHandler.handleTermsDecline(ctx));

        // ── Rating ────────────────────────────────────────────────────────────────
        commands.put("RATE_RIDE",      ctx -> ratingHandler.handleRateRide(ctx));
        commands.put("RATE_STARS",     ctx -> ratingHandler.handleStarSelected(ctx));
        commands.put("SUBMIT_RATING",  ctx -> ratingHandler.handleSubmitRating(ctx));
        commands.put("SKIP_RATING",    ctx -> ratingHandler.handleSkipRating(ctx));
        commands.put("RATE_PASSENGER", ctx -> ratingHandler.handleRatePassenger(ctx));

        // ── Favorites ─────────────────────────────────────────────────────────────
        commands.put("SAVE_FAVORITE",  ctx -> ratingHandler.handleSaveFavorite(ctx));
        commands.put("SKIP_FAVORITE",  ctx -> ratingHandler.handleSkipFavorite(ctx));

        // ── Help ──────────────────────────────────────────────────────────
        commands.put("HELP", ctx -> helpHandler.handleTopic(
                ctx.chatId(), ctx.payload(), ctx.bot()));
    }

    // ── Router ────────────────────────────────────────────────────────────

    public void handle(CallbackQuery callback, CarpoolBot bot) {
        Long chatId     = callback.getMessage().getChatId();
        Long telegramId = callback.getFrom().getId();
        String data     = callback.getData();

        bot.answerCallback(callback.getId());

        var userOpt = userRepository.findByTelegramId(telegramId);
        if (userOpt.isEmpty()) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Please register first via /start"));
            return;
        }

        Long carpoolUserId = userOpt.get().getId();
        UserState state    = stateManager.get(chatId);
        String[] parts     = data.split(":");
        String   action    = parts[0];

        if (state == null) {
            if (sessionRecoveryHandler.isFlowSensitive(action)) {
                sessionRecoveryHandler.handleExpiredSession(
                        chatId, carpoolUserId, action, bot);
                return;
            }
            state = UserState.initial(carpoolUserId);
        }

        String payload = parts.length > 1 ? parts[1] : null;

        Integer messageId = callback.getMessage().getMessageId();
        BotContext ctx = new BotContext(
                chatId, carpoolUserId, telegramId, state, payload, parts, bot, messageId);

        BotCommand command = commands.getOrDefault(action, unknownAction(action));
        command.execute(ctx);
    }

    private BotCommand unknownAction(String action) {
        return ctx -> {
            log.warn("Unknown callback action: {} from chatId={}", action, ctx.chatId());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(), "⚠️ Unknown action."));
        };
    }
}