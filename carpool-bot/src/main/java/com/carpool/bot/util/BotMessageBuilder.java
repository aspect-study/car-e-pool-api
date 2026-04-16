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
 * Centralizes message formatting — keeps handlers clean.
 */
public class BotMessageBuilder {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a");

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    // ── Simple text messages ──────────────────────────────────────────────

    public static SendMessage text(Long chatId, String text) {
        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("Markdown")
                .build();
    }

    public static SendMessage textWithRemoveKeyboard(Long chatId, String text) {
        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(ReplyKeyboardRemove.builder().removeKeyboard(true).build())
                .build();
    }

    // ── Direction selector ────────────────────────────────────────────────

    public static SendMessage directionSelector(Long chatId, String prompt) {
        KeyboardRow row = new KeyboardRow();
        row.add("🏠 Home to Work");
        row.add("🏢 Work to Home");

        return SendMessage.builder()
                .chatId(chatId)
                .text(prompt)
                .parseMode("Markdown")
                .replyMarkup(ReplyKeyboardMarkup.builder()
                        .keyboard(List.of(row))
                        .resizeKeyboard(true)
                        .oneTimeKeyboard(true)
                        .build())
                .build();
    }

    // ── Ride card ─────────────────────────────────────────────────────────

    public static String formatRideCard(RideResponse ride) {
        String directionEmoji = switch (ride.direction()) {
            case HOME_TO_WORK -> "🏠→🏢";
            case WORK_TO_HOME -> "🏢→🏠";
            case OTHER        -> "🚗";
        };

        // Seats: available vs total
        String seatsInfo = ride.availableSeats() + " of " + ride.totalSeats() + " seats available";

        // Driver contact
        String driverHandle = ride.driver().telegramHandle() != null
                ? " (@" + ride.driver().telegramHandle() + ")"
                : "";

        // Posted how long ago
        long minutesAgo = java.time.Duration.between(ride.createdAt(), java.time.Instant.now()).toMinutes();
        String postedAgo;
        if (minutesAgo < 60) {
            postedAgo = minutesAgo + "m ago";
        } else if (minutesAgo < 1440) {
            postedAgo = (minutesAgo / 60) + "h ago";
        } else {
            postedAgo = (minutesAgo / 1440) + "d ago";
        }

        // Total contribution for 1 seat (base), note about multiple seats
        String contributionInfo = String.format("₱%.2f / seat", ride.contributionAmount());

        return String.format(
                "%s *%s → %s*\n" +
                        "🕐 %s\n" +
                        "🪑 %s\n" +
                        "💵 %s\n" +
                        "👤 %s%s\n" +
                        "🕓 Posted %s" +
                        "%s",
                directionEmoji,
                ride.originHub().name(),
                ride.destinationHub().name(),
                ride.departureTime().atZone(MANILA).format(DISPLAY_FMT),
                seatsInfo,
                contributionInfo,
                ride.driver().fullName(),
                driverHandle,
                postedAgo,
                ride.notes() != null && !ride.notes().isBlank()
                        ? "\n📝 " + ride.notes()
                        : "");
    }

    // ── Ride list with inline buttons ─────────────────────────────────────

    public static SendMessage rideList(Long chatId, List<RideResponse> rides, String header) {
        if (rides.isEmpty()) {
            var rows = List.of(
                    List.of(InlineKeyboardButton.builder()
                            .text("🏠 Back to Menu")
                            .callbackData("MAIN_MENU")
                            .build())
            );
            return SendMessage.builder()
                    .chatId(chatId)
                    .text(header + "\n\n_No rides found._")
                    .parseMode("Markdown")
                    .replyMarkup(InlineKeyboardMarkup.builder()
                            .keyboard(List.of(new InlineKeyboardRow(
                                    InlineKeyboardButton.builder()
                                            .text("🏠 Back to Menu")
                                            .callbackData("MAIN_MENU")
                                            .build())))
                            .build())
                    .build();
        }

        StringBuilder sb = new StringBuilder(header).append("\n\n");
        List<InlineKeyboardRow> keyboardRows = new ArrayList<>();

        for (int i = 0; i < rides.size(); i++) {
            RideResponse ride = rides.get(i);
            sb.append(String.format("*%d.* %s → %s | 🕐 %s | 🪑 %d | ₱%.2f\n",
                    i + 1,
                    ride.originHub().name(),
                    ride.destinationHub().name(),
                    ride.departureTime().atZone(MANILA).format(
                            DateTimeFormatter.ofPattern("MMM d h:mma")),
                    ride.availableSeats(),
                    ride.contributionAmount()));

            keyboardRows.add(new InlineKeyboardRow(InlineKeyboardButton.builder()
                    .text("View #" + (i + 1))
                    .callbackData("VIEW_RIDE:" + ride.id())
                    .build()));
        }

        return SendMessage.builder()
                .chatId(chatId)
                .text(sb.toString())
                .parseMode("Markdown")
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(keyboardRows).build())
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

    public static InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }

    private BotMessageBuilder() {}

    public static SendMessage textWithBackButton(Long chatId, String text) {
        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboard(List.of(new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("🏠 Menu")
                                        .callbackData("MAIN_MENU")
                                        .build())))
                        .build())
                .build();
    }
}