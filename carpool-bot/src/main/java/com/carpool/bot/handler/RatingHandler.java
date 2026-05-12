package com.carpool.bot.handler;

import com.carpool.bot.handler.context.BotContext;
import com.carpool.bot.handler.helper.BotFlowHelper;
import com.carpool.bot.state.BotFlow;
import com.carpool.bot.state.StateManager;
import com.carpool.bot.state.UserState;
import com.carpool.bot.util.BotMessageBuilder;
import com.carpool.bot.util.ButtonStyle;
import com.carpool.common.util.HtmlEscapeUtil;
import com.carpool.domain.entity.RideRating;
import com.carpool.domain.entity.User;
import com.carpool.repository.UserRepository;
import com.carpool.service.favorite.FavoriteService;
import com.carpool.service.rating.RatingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Handles the full rating and favorite flow.
 * Triggered after ride completion for both driver and passenger.
 * Flow: star tap → optional comment → save as favorite prompt.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RatingHandler {

    private final StateManager   stateManager;
    private final RatingService  ratingService;
    private final FavoriteService favoriteService;
    private final UserRepository userRepository;
    private final BotFlowHelper flowHelper;

    // ── Show rating prompt ────────────────────────────────────────────────

    /**
     * Shows the star rating screen.
     * Called by NotificationService indirectly via bot message,
     * or directly after ride completion callback.
     */
    public void showRatingPrompt(Long chatId, Long rideId,
                                 Long rateeId, String rateeName,
                                 UserState state, com.carpool.bot.CarpoolBot bot) {
        UserState updated = state
                .withPendingRatingRideId(rideId)
                .withPendingRateeId(rateeId)
                .withPendingStars(null)
                .withFlow(BotFlow.RATING_STARS);
        stateManager.save(chatId, updated);

        var rows = List.of(
                List.of(
                        BotMessageBuilder.button("⭐",     "RATE_STARS:1:" + rideId + ":" + rateeId, ButtonStyle.DANGER.toString()),
                        BotMessageBuilder.button("⭐⭐",   "RATE_STARS:2:" + rideId + ":" + rateeId, ButtonStyle.DANGER.toString()),
                        BotMessageBuilder.button("⭐⭐⭐", "RATE_STARS:3:" + rideId + ":" + rateeId, ButtonStyle.PRIMARY.toString())
                ),
                List.of(
                        BotMessageBuilder.button("⭐⭐⭐⭐",   "RATE_STARS:4:" + rideId + ":" + rateeId, ButtonStyle.PRIMARY.toString()),
                        BotMessageBuilder.button("⭐⭐⭐⭐⭐", "RATE_STARS:5:" + rideId + ":" + rateeId, ButtonStyle.SUCCESS.toString())
                ),
                List.of(
                        BotMessageBuilder.button("⏭️ Skip", "SKIP_RATING:" + rideId, ButtonStyle.PRIMARY.toString())
                )
        );

        bot.send(flowHelper.sendWithInline(chatId,
                "⭐ <b>Rate Your Experience</b>\n\n" +
                        "How was your ride with <b>" +
                        HtmlEscapeUtil.escape(rateeName) + "</b>?\n\n" +
                        "Tap a star to rate:",
                rows));
    }

    // ── Star selected ─────────────────────────────────────────────────────

    /**
     * Driver or passenger tapped a star rating.
     * parts[0]=RATE_STARS, parts[1]=stars, parts[2]=rideId, parts[3]=rateeId
     */
    public void handleStarSelected(BotContext ctx) {
        if (ctx.parts().length < 4) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Invalid rating. Please try again."));
            return;
        }

        int stars;
        long rideId;
        long rateeId;
        try {
            stars   = Integer.parseInt(ctx.parts()[1]);
            rideId  = Long.parseLong(ctx.parts()[2]);
            rateeId = Long.parseLong(ctx.parts()[3]);
        } catch (NumberFormatException e) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Invalid rating data. Please try again."));
            return;
        }

        String starDisplay = "⭐".repeat(stars);

        UserState updated = ctx.state()
                .withPendingRatingRideId(rideId)
                .withPendingRateeId(rateeId)
                .withPendingStars(stars)
                .withFlow(BotFlow.RATING_COMMENT);
        stateManager.save(ctx.chatId(), updated);

        var rows = List.of(
                List.of(
                        BotMessageBuilder.button("⏭️ Skip comment", "SUBMIT_RATING:" + rideId, ButtonStyle.PRIMARY.toString()),
                        BotMessageBuilder.button("❌ Cancel",        "SKIP_RATING:"  + rideId, ButtonStyle.DANGER.toString())
                )
        );

        ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                starDisplay + " <b>Nice!</b>\n\n" +
                        "Would you like to leave a comment? " +
                        "(max 1000 characters)\n\n" +
                        "<i>Type your comment below or tap Skip.</i>",
                rows));
    }

    // ── Comment text input ────────────────────────────────────────────────

    /**
     * Handles free-text comment during RATING_COMMENT flow.
     */
    public void handleRatingComment(Long chatId, String text,
                                    UserState state, Long carpoolUserId,
                                    com.carpool.bot.CarpoolBot bot) {
        if (state.getPendingRatingRideId() == null
                || state.getPendingRateeId() == null
                || state.getPendingStars() == null) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Session expired. Rating cancelled."));
            stateManager.reset(chatId);
            return;
        }

        String comment = text.trim();
        if (comment.length() > 1000) {
            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "⚠️ Comment is too long (max 1000 characters). " +
                            "Please shorten it and try again."));
            return;
        }

        submitRating(chatId, carpoolUserId, state,
                comment, bot);
    }

    // ── Submit rating (no comment) ────────────────────────────────────────

    /**
     * Submits rating without comment — triggered by "Skip comment" button.
     * parts[0]=SUBMIT_RATING, parts[1]=rideId
     */
    public void handleSubmitRating(BotContext ctx) {
        if (ctx.state().getPendingRatingRideId() == null
                || ctx.state().getPendingRateeId() == null
                || ctx.state().getPendingStars() == null) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Session expired. Rating cancelled."));
            stateManager.reset(ctx.chatId());
            return;
        }
        submitRating(ctx.chatId(), ctx.carpoolUserId(),
                ctx.state(), null, ctx.bot());
    }

    // ── Core submit logic ─────────────────────────────────────────────────

    private void submitRating(Long chatId, Long carpoolUserId,
                              UserState state, String comment,
                              com.carpool.bot.CarpoolBot bot) {
        try {
            RideRating saved = ratingService.submitRating(
                    state.getPendingRatingRideId(),
                    carpoolUserId,
                    state.getPendingRateeId(),
                    state.getPendingStars(),
                    comment);

            String starDisplay = "⭐".repeat(saved.getStars());
            String rateeName   = saved.getRatee().getFullName();

            // Clear rating state
            UserState updated = state
                    .withPendingRatingRideId(null)
                    .withPendingRateeId(null)
                    .withPendingStars(null)
                    .withFlow(BotFlow.RATING_FAVORITE);
            stateManager.save(chatId, updated);

            // Only show favorite prompt when passenger rated a driver —
            // saving a passenger as favorite serves no purpose in current system.
            boolean raterIsPassenger = saved.getRaterRole().equals("PASSENGER");

            if (raterIsPassenger) {
                boolean alreadyFavorite = favoriteService.isFavorite(
                        carpoolUserId, saved.getRatee().getId());

                if (alreadyFavorite) {
                    stateManager.reset(chatId);
                    bot.send(flowHelper.sendWithInline(chatId,
                            starDisplay + " <b>Rating submitted!</b>\n\n" +
                                    "Thanks for rating <b>" +
                                    HtmlEscapeUtil.escape(rateeName) + "</b>.\n" +
                                    HtmlEscapeUtil.escape(rateeName) +
                                    " is already in your favorites. ⭐",
                            List.of(List.of(
                                    BotMessageBuilder.button("🏠 Menu", "MAIN_MENU", ButtonStyle.PRIMARY.toString())
                            ))));
                    return;
                }

                bot.send(flowHelper.sendWithInline(chatId,
                        starDisplay + " <b>Rating submitted!</b>\n\n" +
                                "Thanks for rating <b>" +
                                HtmlEscapeUtil.escape(rateeName) + "</b>.\n\n" +
                                "Would you like to save <b>" +
                                HtmlEscapeUtil.escape(rateeName) +
                                "</b> as a favorite?\n" +
                                "<i>You'll be notified when they post a new ride.</i>",
                        List.of(List.of(
                                BotMessageBuilder.button(
                                        "⭐ Save as Favorite",
                                        "SAVE_FAVORITE:" + saved.getRatee().getId(), ButtonStyle.SUCCESS.toString()),
                                BotMessageBuilder.button("Skip", "SKIP_FAVORITE", ButtonStyle.PRIMARY.toString())
                        ))));
            } else {
                // Driver rated passenger — just confirm and go to menu
                stateManager.reset(chatId);
                bot.send(flowHelper.sendWithInline(chatId,
                        starDisplay + " <b>Rating submitted!</b>\n\n" +
                                "Thanks for rating <b>" +
                                HtmlEscapeUtil.escape(rateeName) + "</b>. 🙏",
                        List.of(List.of(
                                BotMessageBuilder.button("🏠 Menu", "MAIN_MENU", ButtonStyle.PRIMARY.toString())
                        ))));
            }

        } catch (Exception e) {
            log.error("Rating submission failed: userId={} error={}",
                    carpoolUserId, e.getMessage());
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Could not submit rating. Please try again."));
            stateManager.reset(chatId);
        }
    }

    // ── Skip rating ───────────────────────────────────────────────────────

    /**
     * User skipped the rating — clear state and go to menu.
     */
    public void handleSkipRating(BotContext ctx) {
        UserState cleared = ctx.state()
                .withPendingRatingRideId(null)
                .withPendingRateeId(null)
                .withPendingStars(null);
        stateManager.reset(ctx.chatId());
        flowHelper.showMainMenu(
                ctx.chatId(), ctx.carpoolUserId(), cleared, ctx.bot());
    }

    /**
     * Driver selected a specific passenger to rate from the selection screen.
     * parts[0]=RATE_PASSENGER, parts[1]=rideId, parts[2]=rateeId
     */
    public void handleRatePassenger(BotContext ctx) {
        if (ctx.parts().length < 3) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Invalid request. Please try again."));
            return;
        }
        Long rideId;
        Long rateeId;
        try {
            rideId  = Long.parseLong(ctx.parts()[1]);
            rateeId = Long.parseLong(ctx.parts()[2]);
        } catch (NumberFormatException e) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Invalid data. Please try again."));
            return;
        }

        try {
            var ratee = userRepository.findById(rateeId).orElseThrow();
            showRatingPrompt(ctx.chatId(), rideId, rateeId,
                    ratee.getFullName(), ctx.state(), ctx.bot());
        } catch (Exception e) {
            log.error("handleRatePassenger failed: rideId={} rateeId={} error={}",
                    rideId, rateeId, e.getMessage());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not load passenger. Please try again."));
        }
    }

    // ── Save favorite ─────────────────────────────────────────────────────

    /**
     * User tapped Save as Favorite after rating.
     * parts[0]=SAVE_FAVORITE, parts[1]=favoriteId
     */
    public void handleSaveFavorite(BotContext ctx) {
        Long favoriteId = ctx.entityId();
        if (favoriteId == null) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Invalid request. Please try again."));
            return;
        }

        try {
            favoriteService.saveFavorite(ctx.carpoolUserId(), favoriteId);

            var favUser = userRepository.findById(favoriteId).orElse(null);
            String favName = favUser != null
                    ? HtmlEscapeUtil.escape(favUser.getFullName()) : "this user";

            stateManager.reset(ctx.chatId());
            ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                    "⭐ <b>" + favName + " saved as favorite!</b>\n\n" +
                            "You'll be notified when they post a new ride. 🔔",
                    List.of(List.of(
                            BotMessageBuilder.button("🏠 Menu", "MAIN_MENU", ButtonStyle.PRIMARY.toString())
                    ))));

        } catch (Exception e) {
            log.error("Save favorite failed: userId={} favoriteId={} error={}",
                    ctx.carpoolUserId(), favoriteId, e.getMessage());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not save favorite. Please try again."));
        }
    }

    // ── Unfollow driver (from favorite alert notification) ────────────────

    public void handleUnfollowDriver(BotContext ctx) {
        Long favoriteId = ctx.entityId();
        if (favoriteId == null) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(), "⚠️ Invalid request."));
            return;
        }

        try {
            favoriteService.removeFavorite(ctx.carpoolUserId(), favoriteId);

            var favUser = userRepository.findById(favoriteId).orElse(null);
            String favName = favUser != null
                    ? HtmlEscapeUtil.escape(favUser.getFullName()) : "this driver";

            ctx.bot().edit(EditMessageText.builder()
                    .chatId(ctx.chatId())
                    .messageId(ctx.messageId())
                    .parseMode("HTML")
                    .text("🔕 <b>Unfollowed " + favName + "</b>\n\nYou'll no longer receive alerts when they post a ride.")
                    .replyMarkup(BotMessageBuilder.inlineButtons(List.of(List.of(
                            BotMessageBuilder.button("🏠 Menu", "MAIN_MENU", ButtonStyle.PRIMARY.toString())
                    ))))
                    .build());

        } catch (Exception e) {
            log.error("Unfollow failed: userId={} favoriteId={} error={}",
                    ctx.carpoolUserId(), favoriteId, e.getMessage());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(), "⚠️ Could not unfollow. Please try again."));
        }
    }

    // ── Skip favorite ─────────────────────────────────────────────────────

    public void handleSkipFavorite(BotContext ctx) {
        stateManager.reset(ctx.chatId());
        flowHelper.showMainMenu(
                ctx.chatId(), ctx.carpoolUserId(), ctx.state(), ctx.bot());
    }

    // ── Rate from callback (RATE_RIDE) ────────────────────────────────────

    /**
     * Driver or passenger taps Rate button from completed ride notification.
     * parts[0]=RATE_RIDE, parts[1]=rideId
     * If driver has multiple passengers — shows selection screen first.
     * If single ratee — goes straight to star rating.
     */
    public void handleRateRide(BotContext ctx) {
        Long rideId = ctx.entityId();
        if (rideId == null) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Invalid ride. Please try again."));
            return;
        }

        if (!ratingService.canRate(rideId, ctx.carpoolUserId())) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ You have already rated this ride " +
                            "or it is not eligible for rating."));
            return;
        }

        try {
            List<Long> rateeIds = ratingService.getRateeIds(rideId, ctx.carpoolUserId());

            if (rateeIds.size() == 1) {
                // Single ratee — go straight to star rating
                Long rateeId = rateeIds.getFirst();
                var ratee    = userRepository.findById(rateeId).orElseThrow();
                showRatingPrompt(ctx.chatId(), rideId, rateeId,
                        ratee.getFullName(), ctx.state(), ctx.bot());
            } else {
                // Multiple passengers — show selection screen
                showPassengerSelectionScreen(ctx, rideId, rateeIds);
            }

        } catch (Exception e) {
            log.error("handleRateRide failed: rideId={} userId={} error={}",
                    rideId, ctx.carpoolUserId(), e.getMessage());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not load rating. Please try again."));
        }
    }

    /**
     * Shows a passenger selection screen when driver has multiple passengers.
     * Driver picks which passenger to rate first.
     */
    private void showPassengerSelectionScreen(BotContext ctx,
                                              Long rideId,
                                              List<Long> rateeIds) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<User> passengers = userRepository.findAllById(rateeIds);
        Set<Long> alreadyRated = ratingService.getRatedPassengerIds(rideId, ctx.carpoolUserId());

        for (User passenger : passengers) {
            if (alreadyRated.contains(passenger.getId())) continue;
            String handle = passenger.getTelegramHandle() != null
                    ? " (@" + HtmlEscapeUtil.escape(passenger.getTelegramHandle()) + ")" : "";
            rows.add(List.of(BotMessageBuilder.button(
                    "👤 " + HtmlEscapeUtil.escape(passenger.getFullName()) + handle,
                    "RATE_PASSENGER:" + rideId + ":" + passenger.getId(), ButtonStyle.PRIMARY.toString())));
        }

        if (rows.isEmpty()) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "✅ You have already rated all passengers for this ride."));
            return;
        }

        rows.add(List.of(BotMessageBuilder.button("⏭️ Skip", "SKIP_RATING:" + rideId, ButtonStyle.PRIMARY.toString())));

        ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                """
                        ⭐ <b>Rate Your Passengers</b>
                        
                        Select a passenger to rate:""",
                rows));
    }
}