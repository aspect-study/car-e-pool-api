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
                        "⛽ ₱%.2f gas share/seat\n" +
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
            String driverHandle = ride.driver().telegramHandle() != null
                    ? " (@" + escape(ride.driver().telegramHandle()) + ")"
                    : "";
            String vehicleLine = buildVehicleLine(ride);
            sb.append(String.format("<b>%d.</b> %s → %s | 🕐 %s | 🪑 %d | ⛽ ₱%.2f share\n👤 %s%s\n%s",
                    i + 1,
                    escape(ride.originHub().name()),
                    escape(ride.destinationHub().name()),
                    ride.departureTime().atZone(MANILA).format(
                            DateTimeFormatter.ofPattern("MMM d h:mma")),
                    ride.availableSeats(),
                    ride.contributionAmount(),
                    escape(ride.driver().fullName()),
                    driverHandle,
                    vehicleLine));

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

    /**
     * Paginated ride list — shows 5 rides per page with prev/next buttons.
     */
    public static SendMessage paginatedRideList(Long chatId, List<RideResponse> allRides,
                                                String header, int page,
                                                String filterSummary) {
        int pageSize   = 5;
        int totalPages = (int) Math.ceil((double) allRides.size() / pageSize);
        int safePage   = Math.max(0, Math.min(page, totalPages - 1));
        int fromIdx    = safePage * pageSize;
        int toIdx      = Math.min(fromIdx + pageSize, allRides.size());

        List<RideResponse> pageRides = allRides.subList(fromIdx, toIdx);

        StringBuilder sb = new StringBuilder(header);
        sb.append(" <b>(").append(allRides.size()).append(" found)</b>");

        if (filterSummary != null && !filterSummary.isBlank()) {
            sb.append("\n").append(filterSummary);
        }

        sb.append(" — Page ").append(safePage + 1).append("/").append(Math.max(1, totalPages));
        sb.append("\n\n");

        for (int i = 0; i < pageRides.size(); i++) {
            RideResponse ride = pageRides.get(i);
            String driverHandle = ride.driver().telegramHandle() != null
                    ? " (@" + escape(ride.driver().telegramHandle()) + ")"
                    : "";
            String vehicleLine = buildVehicleLine(ride);
            sb.append(String.format("<b>%d.</b> %s → %s | 🕐 %s | 🪑 %d | ⛽ ₱%.2f share\n👤 %s%s\n%s",
                    fromIdx + i + 1,
                    escape(ride.originHub().name()),
                    escape(ride.destinationHub().name()),
                    ride.departureTime().atZone(MANILA).format(
                            DateTimeFormatter.ofPattern("MMM d h:mma")),
                    ride.availableSeats(),
                    ride.contributionAmount(),
                    escape(ride.driver().fullName()),
                    driverHandle,
                    vehicleLine));
        }

        List<InlineKeyboardRow> rows = new ArrayList<>();

        // View buttons
        for (int i = 0; i < pageRides.size(); i++) {
            rows.add(new InlineKeyboardRow(InlineKeyboardButton.builder()
                    .text("View #" + (fromIdx + i + 1))
                    .callbackData("VIEW_RIDE:" + pageRides.get(i).id())
                    .build()));
        }

        // Pagination controls
        List<InlineKeyboardButton> navButtons = new ArrayList<>();
        if (safePage > 0) {
            navButtons.add(InlineKeyboardButton.builder()
                    .text("◀️ Prev")
                    .callbackData("RIDE_PAGE:" + (safePage - 1))
                    .build());
        }
        if (totalPages > 1) {
            navButtons.add(InlineKeyboardButton.builder()
                    .text("📄 " + (safePage + 1) + "/" + totalPages)
                    .callbackData("NOOP")
                    .build());
        }
        if (safePage < totalPages - 1) {
            navButtons.add(InlineKeyboardButton.builder()
                    .text("Next ▶️")
                    .callbackData("RIDE_PAGE:" + (safePage + 1))
                    .build());
        }
        if (!navButtons.isEmpty()) {
            rows.add(new InlineKeyboardRow(navButtons));
        }

        // Filter + menu buttons
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("🔧 Filter & Sort")
                        .callbackData("SEARCH_FILTER")
                        .build(),
                InlineKeyboardButton.builder()
                        .text("🏠 Menu")
                        .callbackData("MAIN_MENU")
                        .build()));

        return SendMessage.builder()
                .chatId(chatId)
                .text(sb.toString())
                .parseMode("HTML")
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboard(rows)
                        .build())
                .build();
    }

    /**
     * Builds vehicle display line for ride cards.
     * Returns empty string if driver has no vehicle info set.
     */
    private static String buildVehicleLine(RideResponse ride) {
        String model = ride.driver().carModel();
        String color = ride.driver().carColor();
        String plate = ride.driver().plateNumber();

        if (model == null && plate == null) return "";

        StringBuilder sb = new StringBuilder("🚘 ");
        if (color != null) sb.append(escape(color)).append(" ");
        if (model != null) sb.append(escape(model));
        if (plate != null) sb.append(" | 🔢 ").append(escape(plate));
        sb.append("\n");

        return sb.toString();
    }

    private BotMessageBuilder() {}
}