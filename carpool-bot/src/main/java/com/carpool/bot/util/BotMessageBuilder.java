package com.carpool.bot.util;

import com.carpool.service.dto.response.RideResponse;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for building Telegram SendMessage objects.
 * Uses HTML parse mode throughout — safer than Markdown for usernames
 * and special characters.
 *
 * HTML tags supported: <b>, <i>, <u>, <code>, <pre>
 * Special chars to escape: & → &amp;  < → &lt;  > → &gt;
 */
public class BotMessageBuilder {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a");

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    // ── Menu button shortcut ──────────────────────────────────────────────

    public static InlineKeyboardRow menuButtonRow() {
        return new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("🏠 Menu")
                        .callbackData("MAIN_MENU")
                        .build());
    }

    // ── Simple text messages ──────────────────────────────────────────────

    public static SendMessage text(Long chatId, String text) {
        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboard(List.of(menuButtonRow()))
                        .build())
                .build();
    }

    public static SendMessage textNoMenu(Long chatId, String text) {
        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .build();
    }

    public static SendMessage textWithRemoveKeyboard(Long chatId, String text) {
        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .replyMarkup(ReplyKeyboardRemove.builder().removeKeyboard(true).build())
                .build();
    }

    public static SendMessage textWithBackButton(Long chatId, String text) {
        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboard(List.of(menuButtonRow()))
                        .build())
                .build();
    }

    // ── Direction selector ────────────────────────────────────────────────

    public static SendMessage directionSelector(Long chatId, String prompt) {
        var rows = List.of(
                List.of(
                        InlineKeyboardButton.builder()
                                .text("🏠 Home to Work")
                                .callbackData("DIRECTION:HOME_TO_WORK")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("🏢 Work to Home")
                                .callbackData("DIRECTION:WORK_TO_HOME")
                                .build()
                )
        );

        return SendMessage.builder()
                .chatId(chatId)
                .text(prompt)
                .parseMode("HTML")
                .replyMarkup(inlineButtons(rows))
                .build();
    }

    // ── Ride card ─────────────────────────────────────────────────────────

    public static String formatRideCard(RideResponse ride) {
        String directionEmoji = switch (ride.direction()) {
            case HOME_TO_WORK -> "🏠→🏢";
            case WORK_TO_HOME -> "🏢→🏠";
            case OTHER        -> "🚗";
        };

        String seatsInfo = ride.availableSeats() + " of " + ride.totalSeats() + " seats available";

        String driverHandle = ride.driver().telegramHandle() != null
                ? " (@" + escape(ride.driver().telegramHandle()) + ")"
                : "";

        long minutesAgo = java.time.Duration.between(
                ride.createdAt(), java.time.Instant.now()).toMinutes();
        String postedAgo;
        if (minutesAgo < 60) {
            postedAgo = minutesAgo + "m ago";
        } else if (minutesAgo < 1440) {
            postedAgo = (minutesAgo / 60) + "h ago";
        } else {
            postedAgo = (minutesAgo / 1440) + "d ago";
        }

        return String.format(
                "%s <b>%s → %s</b>\n" +
                        "🕐 %s\n" +
                        "🪑 %s\n" +
                        "💵 ₱%.2f / seat\n" +
                        "👤 %s%s\n" +
                        "🕓 Posted %s" +
                        "%s",
                directionEmoji,
                escape(ride.originHub().name()),
                escape(ride.destinationHub().name()),
                ride.departureTime().atZone(MANILA).format(DISPLAY_FMT),
                seatsInfo,
                ride.contributionAmount(),
                escape(ride.driver().fullName()),
                driverHandle,
                postedAgo,
                ride.notes() != null && !ride.notes().isBlank()
                        ? "\n📝 " + escape(ride.notes())
                        : "");
    }

    // ── Ride list with inline buttons ─────────────────────────────────────

    public static SendMessage rideList(Long chatId, List<RideResponse> rides, String header) {
        if (rides.isEmpty()) {
            return SendMessage.builder()
                    .chatId(chatId)
                    .text(header + "\n\n<i>No rides found.</i>")
                    .parseMode("HTML")
                    .replyMarkup(InlineKeyboardMarkup.builder()
                            .keyboard(List.of(menuButtonRow()))
                            .build())
                    .build();
        }

        StringBuilder sb = new StringBuilder(header).append("\n\n");
        List<InlineKeyboardRow> keyboardRows = new ArrayList<>();

        for (int i = 0; i < rides.size(); i++) {
            RideResponse ride = rides.get(i);
            sb.append(String.format("<b>%d.</b> %s → %s | 🕐 %s | 🪑 %d | ₱%.2f\n",
                    i + 1,
                    escape(ride.originHub().name()),
                    escape(ride.destinationHub().name()),
                    ride.departureTime().atZone(MANILA).format(
                            DateTimeFormatter.ofPattern("MMM d h:mma")),
                    ride.availableSeats(),
                    ride.contributionAmount()));

            keyboardRows.add(new InlineKeyboardRow(InlineKeyboardButton.builder()
                    .text("View #" + (i + 1))
                    .callbackData("VIEW_RIDE:" + ride.id())
                    .build()));
        }

        keyboardRows.add(menuButtonRow());

        return SendMessage.builder()
                .chatId(chatId)
                .text(sb.toString())
                .parseMode("HTML")
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboard(keyboardRows)
                        .build())
                .build();
    }

    // ── Inline button helpers ─────────────────────────────────────────────

    public static InlineKeyboardMarkup inlineButtons(
            List<List<InlineKeyboardButton>> rows) {
        List<InlineKeyboardRow> keyboardRows = rows.stream()
                .map(InlineKeyboardRow::new)
                .toList();
        return InlineKeyboardMarkup.builder()
                .keyboard(keyboardRows)
                .build();
    }

    public static InlineKeyboardMarkup inlineButtonsWithMenu(
            List<List<InlineKeyboardButton>> rows) {
        List<InlineKeyboardRow> keyboardRows = new ArrayList<>(rows.stream()
                .map(InlineKeyboardRow::new)
                .toList());
        keyboardRows.add(menuButtonRow());
        return InlineKeyboardMarkup.builder()
                .keyboard(keyboardRows)
                .build();
    }

    public static InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }

    // ── HTML escape helper ────────────────────────────────────────────────

    public static String escape(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * Send a flow message with a Cancel button at the bottom.
     */
    public static SendMessage textWithCancel(Long chatId, String text) {
        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboard(List.of(
                                new InlineKeyboardRow(InlineKeyboardButton.builder()
                                        .text("❌ Cancel")
                                        .callbackData("CANCEL_POST_RIDE")
                                        .build())))
                        .build())
                .build();
    }

    /**
     * Send a flow message with Cancel + Skip buttons.
     * Used for optional fields like notes.
     */
    public static SendMessage textWithCancelAndSkip(Long chatId, String text, String skipCallback) {
        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboard(List.of(
                                new InlineKeyboardRow(
                                        InlineKeyboardButton.builder()
                                                .text("⏭️ Skip")
                                                .callbackData(skipCallback)
                                                .build(),
                                        InlineKeyboardButton.builder()
                                                .text("❌ Cancel")
                                                .callbackData("CANCEL_POST_RIDE")
                                                .build())))
                        .build())
                .build();
    }

    private BotMessageBuilder() {}
}