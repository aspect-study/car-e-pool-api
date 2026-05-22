package com.carpool.bot.handler;

import com.carpool.bot.CarpoolBot;
import com.carpool.bot.handler.command.BotCommand;
import com.carpool.bot.handler.context.BotContext;
import com.carpool.bot.handler.helper.BotFlowHelper;
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
import java.util.List;
import java.util.Map;

/**
 * Thin callback router — parses incoming callback data, builds BotContext,
 * and delegates to the registered BotCommand.
 * <p>
 * No business logic lives here. Adding a new callback action = one new
 * commands.put() entry in registerCommands(). This class never changes again.
 * <p>
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
    private final BotFlowHelper flowHelper;
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
        commands.put("NOOP", _ -> { /* page indicator — intentional no-op */ });

        // ── Direction ─────────────────────────────────────────────────────
        commands.put("DIRECTION", postRideHandler::handleDirectionCallback);

        // ── Post ride ─────────────────────────────────────────────────────
        commands.put("POST_RIDE", postRideHandler::handleStartPostRide);
        commands.put("CONFIRM_POST_RIDE", postRideHandler::handleConfirmPostRide);
        commands.put("CANCEL_POST_RIDE", postRideHandler::handleCancelPostRide);
        commands.put("HUB_ORIGIN", postRideHandler::handleHubOriginSelected);
        commands.put("HUB_DEST", postRideHandler::handleHubDestSelected);
        commands.put("RETYPE_ORIGIN", postRideHandler::handleRetypeOrigin);
        commands.put("RETYPE_DEST", postRideHandler::handleRetypeDest);
        commands.put("CONFIRM_CUSTOM_ORIGIN", postRideHandler::handleConfirmCustomOrigin);
        commands.put("CONFIRM_CUSTOM_DEST", postRideHandler::handleConfirmCustomDest);
        commands.put("NOTE_PREVIEW", postRideHandler::handleNotePreview);
        commands.put("NOTE_APPLY", postRideHandler::handleNoteApply);
        commands.put("NOTE_WRITE", postRideHandler::handleNoteWrite);
        commands.put("NOTE_CHOOSE_OTHER", postRideHandler::handleNoteChooseOther);
        commands.put("SKIP_NOTES", postRideHandler::handleSkipNotes);
        commands.put("REPOST_RIDE", postRideHandler::handleRepostRide);
        commands.put("REPOST_EDIT_ORIGIN", postRideHandler::handleRepostEditOrigin);
        commands.put("REPOST_EDIT_DEST", postRideHandler::handleRepostEditDest);
        commands.put("REPOST_EDIT_SEATS", postRideHandler::handleRepostEditSeatsCallback);
        commands.put("REPOST_EDIT_CONTRIBUTION", postRideHandler::handleRepostEditContributionCallback);
        commands.put("REPOST_EDIT_NOTES", postRideHandler::handleRepostEditNotesCallback);
        commands.put("REPOST_PROCEED", postRideHandler::handleRepostProceed);
        commands.put("REPOST_BACK_TO_EDIT", postRideHandler::handleRepostBackToEdit);
        commands.put("RIDE_TIME", postRideHandler::handleRideTimeSelected);  // ← add
        commands.put("TIME_NAV", postRideHandler::handleTimeNavigation);

        // ── Find ride ─────────────────────────────────────────────────────
        commands.put("FIND_RIDE", rideSearchHandler::handleStartFindRide);
        commands.put("VIEW_RIDE", rideSearchHandler::handleViewRide);
        commands.put("CAL_NAV", rideSearchHandler::handleCalendarNav);   // ← add
        commands.put("CAL_DATE", rideSearchHandler::handleDateSelected);
        commands.put("TIME", rideSearchHandler::handleTimeSelection);
        commands.put("SEARCH_FILTER", rideSearchHandler::handleSearchFilter);
        commands.put("APPLY_FILTER", rideSearchHandler::handleApplyFilter);
        commands.put("RESET_FILTER", rideSearchHandler::handleResetFilter);
        commands.put("RIDE_PAGE", rideSearchHandler::handleRidePage);

        // ── Booking (passenger) ───────────────────────────────────────────
        commands.put("BOOK_RIDE", bookingHandler::handleBookRide);
        commands.put("BOOK_NOW",    ctx -> bookingHandler.executeBooking(
                ctx.chatId(), ctx.entityId(), ctx.carpoolUserId(), null, ctx.bot()));
        commands.put("MY_BOOKINGS", bookingHandler::handleMyBookings);
        commands.put("VIEW_BOOKING", bookingHandler::handleViewBooking);
        commands.put("PAST_BOOKINGS", bookingHandler::handlePastBookings);
        commands.put("CANCEL_BOOKING", bookingHandler::handleCancelBooking);
        commands.put("CANCEL_BOOKING_REASON", bookingHandler::handleCancelBookingWithReason);

        // ── Driver management ─────────────────────────────────────────────
        commands.put("MY_RIDES",     ctx -> driverHandler.showMyRides(
                ctx.chatId(), ctx.carpoolUserId(), ctx.bot()));
        commands.put("RIDE_BOOKINGS", driverHandler::handleDriverBookings);
        commands.put("DRIVER_BOOKINGS", driverHandler::handleDriverBookings);
        commands.put("VIEW_DRIVER_BOOKING", driverHandler::handleViewDriverBooking);
        commands.put("ACCEPT_BOOKING", driverHandler::handleAcceptBooking);
        commands.put("DECLINE_BOOKING", driverHandler::handleDeclineBooking);
        commands.put("DECLINE_BOOKING_REASON", driverHandler::handleDeclineBookingWithReason);
        commands.put("PENDING_REQUESTS", driverHandler::handlePendingRequests);
        commands.put("VIEW_PENDING", driverHandler::handleViewPendingRequest);
        commands.put("REMOVE_PASSENGER", driverHandler::handleRemovePassenger);
        commands.put("CONFIRM_REMOVE_PASSENGER", driverHandler::handleConfirmRemovePassenger);
        commands.put("CANCEL_RIDE", driverHandler::handleCancelRide);
        commands.put("CONFIRM_CANCEL_RIDE", driverHandler::handleConfirmCancelRide);
        commands.put("DEPART_RIDE", driverHandler::handleDepartRide);
        commands.put("COMPLETE_RIDE", driverHandler::handleCompleteRide);
        commands.put("EDIT_RIDE_TIME",      driverHandler::handleEditRideTime);
        commands.put("CAL_NAV_EDIT_TIME",   driverHandler::handleEditRideTimeCalendarNav);
        commands.put("CAL_DATE_EDIT_TIME",  driverHandler::handleEditRideTimeDateSelected);
        commands.put("TIME_NAV_EDIT",       driverHandler::handleEditRideTimePickerNav);
        commands.put("RIDE_TIME_EDIT",      driverHandler::handleEditRideTimeSelected);
        commands.put("KEEP_BOOKING", ctx -> {
            ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                    "✅ <b>Got it!</b>\n\nYou're still booked on this ride. We'll remind you before departure.",
                    List.of(List.of(
                            BotMessageBuilder.button("📋 My Bookings", "MY_BOOKINGS", null),
                            BotMessageBuilder.button("🏠 Menu", "MAIN_MENU", null)
                    ))));
        });

        // ── Vehicle ───────────────────────────────────────────────────────
        commands.put("VEHICLE_CONFIRM_YES", profileHandler::handleVehicleConfirmYes);
        commands.put("VEHICLE_CONFIRM_SAVE", profileHandler::handleVehicleConfirmSave);
        commands.put("VEHICLE_CHANGE", profileHandler::handleVehicleChange);
        commands.put("VEHICLE_REMOVE", profileHandler::handleVehicleRemove);
        commands.put("VEHICLE_SELECT", profileHandler::handleVehicleSelect);
        commands.put("ADD_VEHICLE", profileHandler::handleAddVehicle);

        // ── Profile ───────────────────────────────────────────────────────
        commands.put("MY_PROFILE", profileHandler::handleMyProfile);
        commands.put("MY_FOLLOWERS", profileHandler::handleMyFollowers);
        commands.put("ADMIN_STATS", profileHandler::handleAdminStats);
        commands.put("REANNOUNCE_RIDE", profileHandler::handleReannounceRide);
        commands.put("CONFIRM_REANNOUNCE", profileHandler::handleConfirmReannounce);
        commands.put("REANNOUNCE_EDIT_SEATS", profileHandler::handleReannounceEditSeatsStart);
        commands.put("PENDING_HUBS", profileHandler::handlePendingHubs);
        commands.put("APPROVE_HUB", profileHandler::handleApproveHub);
        commands.put("REJECT_HUB", profileHandler::handleRejectHub);

        // ── Terms ─────────────────────────────────────────────────────────
        commands.put("TERMS_WELCOME",    ctx -> profileHandler.handleTermsWelcome(
                ctx.chatId(), ctx.bot()));
        commands.put("TERMS_VIEW_AGAIN", ctx -> profileHandler.handleTermsWelcome(
                ctx.chatId(), ctx.bot()));
        commands.put("TERMS_ACCEPT", profileHandler::handleTermsAccept);
        commands.put("TERMS_DECLINE", profileHandler::handleTermsDecline);

        // ── Rating ────────────────────────────────────────────────────────────────
        commands.put("RATE_RIDE", ratingHandler::handleRateRide);
        commands.put("RATE_STARS", ratingHandler::handleStarSelected);
        commands.put("SUBMIT_RATING", ratingHandler::handleSubmitRating);
        commands.put("SKIP_RATING", ratingHandler::handleSkipRating);
        commands.put("RATE_PASSENGER", ratingHandler::handleRatePassenger);

        // ── Favorites ─────────────────────────────────────────────────────────────
        commands.put("SAVE_FAVORITE", ratingHandler::handleSaveFavorite);
        commands.put("SKIP_FAVORITE", ratingHandler::handleSkipFavorite);
        commands.put("UNFOLLOW_DRIVER", ratingHandler::handleUnfollowDriver);

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