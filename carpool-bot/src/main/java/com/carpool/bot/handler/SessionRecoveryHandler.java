package com.carpool.bot.handler;

import com.carpool.bot.CarpoolBot;
import com.carpool.bot.util.BotMessageBuilder;
import com.carpool.bot.util.ButtonStyle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Handles session expiry gracefully when UserState is lost after bot restart.
 * <p>
 * Detects which flow the user was in based on the callback action,
 * then shows a context-aware message with appropriate recovery buttons.
 * <p>
 * This keeps CallbackHandler clean — session recovery is a distinct concern.
 */
@Slf4j
@Component
public class SessionRecoveryHandler {

    // ── Flow classification ───────────────────────────────────────────────

    private static final Set<String> POST_RIDE_ACTIONS = Set.of(
            "HUB_ORIGIN", "HUB_DEST", "RETYPE_ORIGIN", "RETYPE_DEST",
            "CONFIRM_CUSTOM_ORIGIN", "CONFIRM_CUSTOM_DEST",
            "CONFIRM_POST_RIDE", "CANCEL_POST_RIDE", "VEHICLE_CONFIRM_YES",
            "VEHICLE_CONFIRM_SAVE", "NOTE_PREVIEW",
            "NOTE_APPLY", "NOTE_WRITE", "NOTE_CHOOSE_OTHER", "SKIP_NOTES",
            "RIDE_TIME", "TIME_NAV",
            "REPOST_EDIT_ORIGIN", "REPOST_EDIT_DEST", "REPOST_EDIT_SEATS",
            "REPOST_EDIT_CONTRIBUTION", "REPOST_EDIT_NOTES",
            "REPOST_PROCEED", "REPOST_BACK_TO_EDIT",
            "VEHICLE_SELECT"
    );

    private static final Set<String> RATING_ACTIONS = Set.of(
            "RATE_STARS",
            "SUBMIT_RATING",
            "SKIP_RATING"
    );

    // EDIT_RIDE_TIME is intentionally excluded — it's the entry point and reads rideId
    // from the callback payload, not from UserState, so it works with a fresh session.
    private static final Set<String> EDIT_TIME_ACTIONS = Set.of(
            "CAL_NAV_EDIT_TIME",
            "CAL_DATE_EDIT_TIME",
            "RIDE_TIME_EDIT",
            "TIME_NAV_EDIT",
            "CONFIRM_EDIT_RIDE_TIME"
    );

    // EDIT_RIDE_ROUTE is intentionally excluded — it's the entry point and reads rideId
    // from the callback payload, not from UserState, so it works with a fresh session.
    private static final Set<String> EDIT_ROUTE_ACTIONS = Set.of(
            "EDIT_ROUTE_ORIGIN", "EDIT_ROUTE_DEST",
            "EDIT_HUB_ORIGIN", "EDIT_HUB_DEST",
            "RETYPE_EDIT_ORIGIN", "RETYPE_EDIT_DEST",
            "EDIT_CONFIRM_CUSTOM_ORIGIN", "EDIT_CONFIRM_CUSTOM_DEST"
    );

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Returns true if the action requires active state to handle correctly.
     * Safe actions (MAIN_MENU, MY_PROFILE, etc.) return false — they proceed normally.
     */
    public boolean isFlowSensitive(String action) {
        return POST_RIDE_ACTIONS.contains(action)
                || RATING_ACTIONS.contains(action)
                || EDIT_TIME_ACTIONS.contains(action)
                || EDIT_ROUTE_ACTIONS.contains(action);
    }

    /**
     * Shows a context-aware session expired message with recovery buttons.
     * Called when state is null and action is flow-sensitive.
     */
    public void handleExpiredSession(Long chatId, Long carpoolUserId,
                                     String action, CarpoolBot bot) {
        String message = buildMessage(action);
        List<List<InlineKeyboardButton>> rows = buildRecoveryButtons(action);
        bot.send(buildWithInline(chatId, message, rows));
        log.warn("Session expired: userId={} action={} — recovery message shown",
                carpoolUserId, action);
    }

    private String buildMessage(String action) {
        if (POST_RIDE_ACTIONS.contains(action)) {
            return """
                    ⏳ <b>Session expired.</b>

                    Sorry for the interruption! 🙏

                    Would you like to post a new ride?""";
        }
        if (RATING_ACTIONS.contains(action)) {
            return """
                    ⏳ <b>Session expired.</b>

                    Sorry for the interruption! 🙏

                    The rating session has expired. Please go to the main menu.""";
        }
        if (EDIT_TIME_ACTIONS.contains(action)) {
            return """
                    ⏳ <b>Session expired.</b>

                    Sorry for the interruption! 🙏

                    Please tap your ride again and use <b>✏️ Edit Time</b> to try again.""";
        }
        return """
                ⏳ <b>Session expired.</b>

                Sorry for the interruption! 🙏

                Please start again from the main menu.""";
    }

    private List<List<InlineKeyboardButton>> buildRecoveryButtons(String action) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (POST_RIDE_ACTIONS.contains(action)) {
            rows.add(List.of(
                    BotMessageBuilder.button("🚗 Post a New Ride", "POST_RIDE", ButtonStyle.SUCCESS.toString()),
                    BotMessageBuilder.button("🏠 Menu", "MAIN_MENU", null)
            ));
        } else {
            rows.add(List.of(
                    BotMessageBuilder.button("🏠 Menu", "MAIN_MENU", null)
            ));
        }
        return rows;
    }

    private SendMessage buildWithInline(
            Long chatId, String text,
            List<List<InlineKeyboardButton>> rows) {
        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .replyMarkup(BotMessageBuilder.inlineButtons(rows))
                .build();
    }
}