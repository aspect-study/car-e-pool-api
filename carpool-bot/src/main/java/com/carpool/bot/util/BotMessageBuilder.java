package com.carpool.bot.util;

import com.carpool.common.util.HtmlEscapeUtil;
import com.carpool.service.dto.response.RideResponse;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

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
 * Special chars to HtmlEscapeUtil.escape: & → &amp;  < → &lt;  > → &gt;
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
                ? " (@" + HtmlEscapeUtil.escape(ride.driver().telegramHandle()) + ")"
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
                HtmlEscapeUtil.escape(ride.originHub().name()),
                HtmlEscapeUtil.escape(ride.destinationHub().name()),
                ride.departureTime().atZone(MANILA).format(DISPLAY_FMT),
                seatsInfo,
                ride.contributionAmount(),
                HtmlEscapeUtil.escape(ride.driver().fullName()),
                driverHandle,
                postedAgo,
                ride.notes() != null && !ride.notes().isBlank()
                        ? "\n📝 " + HtmlEscapeUtil.escape(ride.notes())
                        : "");
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

    /**
     * Creates an inline button that opens a URL directly.
     * No callback is fired — Telegram opens the link in-app.
     */
    public static InlineKeyboardButton urlButton(String text, String url) {
        return InlineKeyboardButton.builder()
                .text(text)
                .url(url)
                .build();
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
     * Paginated ride list — shows 3 rides per page with prev/next buttons.
     */
    public static SendMessage paginatedRideList(Long chatId, List<RideResponse> allRides,
                                                String header, int page,
                                                String filterSummary) {
        int pageSize   = 3;
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

            // Seat emojis — cap at 5
            String seatEmojis = "💺".repeat(Math.min(ride.availableSeats(), 5));

            // Rating — empty string if none
            String rating = ride.driverAvgRating() != null
                    ? " | ⭐ " + String.format("%.1f", ride.driverAvgRating())
                    : "";

            // Vehicle — compact: color + model only, no plate in list view
            String vehicle = ride.driver().carModel() != null
                    ? " | 🚘 " + (ride.driver().carColor() != null
                                 ? HtmlEscapeUtil.escape(ride.driver().carColor()) + " " : "")
                      + HtmlEscapeUtil.escape(ride.driver().carModel())
                    : "";

            sb.append(String.format(
                    "<b>%d.</b> 📍 %s → %s\n" +
                            "   🕐 %s | 🪑 %d %s | ⛽ ₱%s\n" +
                            "   👤 %s%s%s\n\n",
                    fromIdx + i + 1,
                    HtmlEscapeUtil.escape(ride.originHub().name()),
                    HtmlEscapeUtil.escape(ride.destinationHub().name()),
                    ride.departureTime().atZone(MANILA).format(
                            DateTimeFormatter.ofPattern("MMM d, h:mm a")),
                    ride.availableSeats(),
                    seatEmojis,
                    ride.contributionAmount().toPlainString(),
                    HtmlEscapeUtil.escape(ride.driver().fullName()),
                    rating,
                    vehicle));
        }

        List<InlineKeyboardRow> rows = new ArrayList<>();

        // View buttons — all on one row
        InlineKeyboardRow viewRow = new InlineKeyboardRow();
        for (int i = 0; i < pageRides.size(); i++) {
            viewRow.add(InlineKeyboardButton.builder()
                    .text("View #" + (fromIdx + i + 1))
                    .callbackData("VIEW_RIDE:" + pageRides.get(i).id())
                    .build());
        }
        rows.add(viewRow);

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
     * Builds a row of star rating buttons for the rating flow.
     * Returns two rows — first row: 1-3 stars, second row: 4-5 stars.
     * Keeps buttons readable on mobile without wrapping.
     */
    public static List<List<InlineKeyboardButton>> starRatingRows(
            Long rideId, Long rateeId) {
        String suffix = ":" + rideId + ":" + rateeId;
        return List.of(
                List.of(
                        button("⭐",     "RATE_STARS:1" + suffix),
                        button("⭐⭐",   "RATE_STARS:2" + suffix),
                        button("⭐⭐⭐", "RATE_STARS:3" + suffix)
                ),
                List.of(
                        button("⭐⭐⭐⭐",   "RATE_STARS:4" + suffix),
                        button("⭐⭐⭐⭐⭐", "RATE_STARS:5" + suffix)
                )
        );
    }

    /**
     * Overloaded version that includes driver rating label on the ride card.
     * Pass null for ratingLabel if no rating data is available.
     * Example ratingLabel: "⭐ 4.8" or ""
     */
    public static String formatRideCard(RideResponse ride, String ratingLabel) {
        String directionEmoji = switch (ride.direction()) {
            case HOME_TO_WORK -> "🏠→🏢";
            case WORK_TO_HOME -> "🏢→🏠";
            case OTHER        -> "🚗";
        };

        String seatsInfo = ride.availableSeats() + " of " +
                ride.totalSeats() + " seats available";

        String driverHandle = ride.driver().telegramHandle() != null
                ? " (@" + HtmlEscapeUtil.escape(ride.driver().telegramHandle()) + ")"
                : "";

        String rating = (ratingLabel != null && !ratingLabel.isBlank())
                ? " " + ratingLabel : "";

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
                        "👤 %s%s%s\n" +
                        "🕓 Posted %s" +
                        "%s",
                directionEmoji,
                HtmlEscapeUtil.escape(ride.originHub().name()),
                HtmlEscapeUtil.escape(ride.destinationHub().name()),
                ride.departureTime().atZone(MANILA).format(DISPLAY_FMT),
                seatsInfo,
                ride.contributionAmount(),
                HtmlEscapeUtil.escape(ride.driver().fullName()),
                driverHandle,
                rating,
                postedAgo,
                ride.notes() != null && !ride.notes().isBlank()
                        ? "\n📝 " + HtmlEscapeUtil.escape(ride.notes())
                        : "");
    }

    private BotMessageBuilder() {}
}