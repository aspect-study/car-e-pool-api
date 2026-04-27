package com.carpool.bot.handler;

import com.carpool.bot.CarpoolBot;
import com.carpool.bot.util.BotMessageBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Handles session expiry gracefully when UserState is lost after bot restart.
 *
 * Detects which flow the user was in based on the callback action,
 * then shows a context-aware message with appropriate recovery buttons.
 *
 * This keeps CallbackHandler clean — session recovery is a distinct concern.
 */
@Slf4j
@Component
public class SessionRecoveryHandler {

    // ── Flow classification ───────────────────────────────────────────────

    private static final Set<String> POST_RIDE_ACTIONS = Set.of(
            "HUB_ORIGIN", "HUB_DEST", "RETYPE_ORIGIN", "RETYPE_DEST",
            "CONFIRM_POST_RIDE", "CANCEL_POST_RIDE", "VEHICLE_CONFIRM_YES",
            "VEHICLE_CONFIRM_SAVE", "VEHICLE_CHANGE", "NOTE_PREVIEW",
            "NOTE_APPLY", "NOTE_WRITE", "NOTE_CHOOSE_OTHER", "SKIP_NOTES"
    );

    private static final Set<String> BOOKING_ACTIONS = Set.of(
            "BOOK_RIDE", "VIEW_RIDE", "BOOK_NOW"
    );

    private static final Set<String> APPROVAL_ACTIONS = Set.of(
            "ACCEPT_BOOKING", "DECLINE_BOOKING", "DECLINE_BOOKING_REASON"
    );

    private static final Set<String> CANCEL_ACTIONS = Set.of(
            "CONFIRM_CANCEL_RIDE", "CANCEL_BOOKING", "CANCEL_BOOKING_REASON"
    );

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Returns true if the action requires active state to handle correctly.
     * Safe actions (MAIN_MENU, MY_PROFILE, etc.) return false — they proceed normally.
     */
    public boolean isFlowSensitive(String action) {
        return POST_RIDE_ACTIONS.contains(action)
                || BOOKING_ACTIONS.contains(action)
                || APPROVAL_ACTIONS.contains(action)
                || CANCEL_ACTIONS.contains(action);
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

    // ── Private helpers ───────────────────────────────────────────────────

    private String buildMessage(String action) {
        if (POST_RIDE_ACTIONS.contains(action)) {
            return "⏳ <b>Session expired.</b>\n\n" +
                    "Sorry for the interruption! 🙏\n\n" +
                    "Would you like to post a new ride?";
        }
        if (BOOKING_ACTIONS.contains(action)) {
            return "⏳ <b>Session expired.</b>\n\n" +
                    "Sorry for the interruption! 🙏\n\n" +
                    "Please search for the ride again.";
        }
        if (APPROVAL_ACTIONS.contains(action)) {
            return "⏳ <b>Session expired.</b>\n\n" +
                    "Sorry for the interruption! 🙏\n\n" +
                    "Please check your pending bookings and try again.";
        }
        if (CANCEL_ACTIONS.contains(action)) {
            return "⏳ <b>Session expired.</b>\n\n" +
                    "Sorry for the interruption! 🙏\n\n" +
                    "Please go to the main menu and try again.";
        }
        return "⏳ <b>Session expired.</b>\n\n" +
                "Sorry for the interruption! 🙏\n\n" +
                "Please start again from the main menu.";
    }

    private List<List<InlineKeyboardButton>> buildRecoveryButtons(String action) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        if (POST_RIDE_ACTIONS.contains(action)) {
            rows.add(List.of(
                    BotMessageBuilder.button("🚗 Post a New Ride", "POST_RIDE"),
                    BotMessageBuilder.button("🏠 Menu", "MAIN_MENU")
            ));
        } else if (BOOKING_ACTIONS.contains(action)) {
            rows.add(List.of(
                    BotMessageBuilder.button("🔍 Find a Ride", "FIND_RIDE"),
                    BotMessageBuilder.button("🏠 Menu", "MAIN_MENU")
            ));
        } else if (APPROVAL_ACTIONS.contains(action)) {
            rows.add(List.of(
                    BotMessageBuilder.button("📋 View Pending", "PENDING_REQUESTS"),
                    BotMessageBuilder.button("🏠 Menu", "MAIN_MENU")
            ));
        } else {
            rows.add(List.of(
                    BotMessageBuilder.button("🏠 Menu", "MAIN_MENU")
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