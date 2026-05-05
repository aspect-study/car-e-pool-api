package com.carpool.bot.handler;

import com.carpool.bot.CarpoolBot;
import com.carpool.bot.config.BotConfig;
import com.carpool.bot.state.StateManager;
import com.carpool.bot.state.UserState;
import com.carpool.bot.util.BotCalendarUtil;
import com.carpool.bot.util.BotMessageBuilder;
import com.carpool.bot.util.BotTimePickerUtil;
import com.carpool.common.util.HtmlEscapeUtil;
import com.carpool.domain.enums.RideDirection;
import com.carpool.domain.enums.RideStatus;
import com.carpool.service.booking.BookingService;
import com.carpool.service.dto.response.BookingResponse;
import com.carpool.service.dto.response.RideResponse;
import com.carpool.service.ride.RideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared stateful bot flows used by multiple sub-handlers.
 * Eliminates duplication of showMainMenu, askForTimeWindow, etdExample,
 * handleDirectionSelected, buildFilterSummary, buildTimeContext, sendWithInline
 * which previously existed in both CallbackHandler and MessageHandler.
 *
 * No handler dependencies — only services and infrastructure.
 * This is the lowest layer in the handler hierarchy.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BotFlowHelper {

    private final StateManager   stateManager;
    private final RideService    rideService;
    private final BookingService bookingService;
    private final BotConfig      botConfig;

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    // ── Main menu ─────────────────────────────────────────────────────────

    /**
     * Renders the main menu — context-aware based on driver active ride state.
     * Single source of truth for main menu logic across all handlers.
     */
    public void showMainMenu(Long chatId, Long carpoolUserId, UserState state, CarpoolBot bot) {
        stateManager.reset(chatId);

        List<RideResponse> myRides = rideService.getMyRides(carpoolUserId);
        boolean hasActiveRide = myRides.stream()
                .anyMatch(r -> r.status() == RideStatus.ACTIVE
                        || r.status() == RideStatus.FULL
                        || r.status() == RideStatus.DEPARTED);

        if (hasActiveRide) {
            RideResponse active = myRides.stream()
                    .filter(r -> r.status() == RideStatus.ACTIVE
                            || r.status() == RideStatus.FULL
                            || r.status() == RideStatus.DEPARTED)
                    .findFirst().orElseThrow();

            String msg = "🚗 <b>Your Active Ride</b>\n\n" +
                    BotMessageBuilder.formatRideCard(active) +
                    "\n\nWhat would you like to do?";

            long pendingCount = bookingService.countPendingRequestsForDriver(carpoolUserId);
            boolean canReannounce = active.announceCount() != null && active.announceCount() < 3;

            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            if (active.status() == RideStatus.DEPARTED) {
                rows.add(List.of(
                        BotMessageBuilder.button("📋 View Bookings", "RIDE_BOOKINGS:" + active.id()),
                        BotMessageBuilder.button("✅ Complete Ride",  "COMPLETE_RIDE:" + active.id())
                ));
                rows.add(List.of(BotMessageBuilder.button("👤 My Profile", "MY_PROFILE")));

            } else if (pendingCount > 0) {
                rows.add(List.of(
                        BotMessageBuilder.button("📋 View Bookings", "RIDE_BOOKINGS:" + active.id()),
                        BotMessageBuilder.button("🚀 Start Ride",    "DEPART_RIDE:"  + active.id())
                ));
                rows.add(List.of(
                        BotMessageBuilder.button("⏳ Pending (" + pendingCount + ")", "PENDING_REQUESTS"),
                        BotMessageBuilder.button("❌ Cancel Ride", "CANCEL_RIDE:" + active.id())
                ));
                if (canReannounce) {
                    rows.add(List.of(BotMessageBuilder.button(
                            "📢 Re-announce (" + (3 - active.announceCount()) + " left)",
                            "REANNOUNCE_RIDE:" + active.id())));
                }
                rows.add(List.of(BotMessageBuilder.button("🔍 Find a Ride", "FIND_RIDE")));
                rows.add(List.of(BotMessageBuilder.button("👤 My Profile",  "MY_PROFILE")));

            } else {
                rows.add(List.of(
                        BotMessageBuilder.button("📋 View Bookings", "RIDE_BOOKINGS:" + active.id()),
                        BotMessageBuilder.button("🚀 Start Ride",    "DEPART_RIDE:"  + active.id())
                ));
                rows.add(List.of(BotMessageBuilder.button("❌ Cancel Ride", "CANCEL_RIDE:" + active.id())));
                if (canReannounce) {
                    rows.add(List.of(BotMessageBuilder.button(
                            "📢 Re-announce (" + (3 - active.announceCount()) + " left)",
                            "REANNOUNCE_RIDE:" + active.id())));
                }
                rows.add(List.of(BotMessageBuilder.button("🔍 Find a Ride", "FIND_RIDE")));
                rows.add(List.of(BotMessageBuilder.button("👤 My Profile",  "MY_PROFILE")));
            }

            bot.send(BotMessageBuilder.textWithRemoveKeyboard(chatId, msg));
            bot.send(sendWithInline(chatId, "Choose an action:", rows));

        } else {
            List<BookingResponse> myBookings = bookingService.getMyBookings(carpoolUserId);
            boolean hasPastRides = myRides.stream()
                    .anyMatch(r -> r.status() == RideStatus.COMPLETED
                            || r.status() == RideStatus.CANCELLED);

            String prompt = (!myBookings.isEmpty() && hasPastRides)
                    ? "👋 What would you like to do?"
                    : "👋 Welcome to <b>" + HtmlEscapeUtil.escape(botConfig.getCommunityName()) +
                      "</b>!\n\nWhere are you headed today?";

            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            rows.add(List.of(
                    BotMessageBuilder.button("🏠 Home to Work", "DIRECTION:HOME_TO_WORK"),
                    BotMessageBuilder.button("🏢 Work to Home", "DIRECTION:WORK_TO_HOME")
            ));
            if (!myBookings.isEmpty()) {
                rows.add(List.of(BotMessageBuilder.button(
                        "📜 My Bookings (" + myBookings.size() + ")", "MY_BOOKINGS")));
            }
            if (hasPastRides) {
                rows.add(List.of(BotMessageBuilder.button("🔄 Repost a Ride", "MY_RIDES")));
            }
            rows.add(List.of(BotMessageBuilder.button("👤 My Profile", "MY_PROFILE")));

            bot.send(sendWithInline(chatId, prompt, rows));
        }
    }

    // ── Direction selected ────────────────────────────────────────────────

    /**
     * Handles direction selected from main menu — shows Post/Find/Bookings options.
     */
    public void handleDirectionSelected(Long chatId, Long carpoolUserId,
                                        RideDirection direction, UserState state,
                                        CarpoolBot bot) {
        UserState updated = state.withDirection(direction).withCarpoolUserId(carpoolUserId);
        stateManager.save(chatId, updated);

        String dirLabel = direction == RideDirection.HOME_TO_WORK
                ? "🏠 Home → Work" : "🏢 Work → Home";

        List<BookingResponse> myBookings = bookingService.getMyBookings(carpoolUserId);

        var rows = myBookings.isEmpty()
                ? List.of(List.of(
                BotMessageBuilder.button("🚗 Post a Ride", "POST_RIDE"),
                BotMessageBuilder.button("🔍 Find a Ride", "FIND_RIDE")
        ))
                : List.of(
                List.of(
                        BotMessageBuilder.button("🚗 Post a Ride", "POST_RIDE"),
                        BotMessageBuilder.button("🔍 Find a Ride", "FIND_RIDE")
                ),
                List.of(BotMessageBuilder.button(
                        "📜 My Bookings (" + myBookings.size() + ")", "MY_BOOKINGS"))
        );

        bot.send(sendWithInline(chatId,
                "Direction: <b>" + dirLabel + "</b>\n\nWhat would you like to do?", rows));
    }

    /**
     * Shows the inline calendar for date selection.
     * calendarMonth comes from UserState — no static state.
     */
    public void showCalendar(Long chatId, Integer messageId, YearMonth calendarMonth, CarpoolBot bot) {
        InlineKeyboardMarkup calendar = BotCalendarUtil.buildCalendar(calendarMonth);

        if (messageId != null) {
            bot.edit(EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .text("📅 <b>Select a departure date:</b>")
                    .parseMode("HTML")
                    .replyMarkup(calendar)
                    .build());
        } else {
            bot.send(SendMessage.builder()
                    .chatId(chatId)
                    .text("📅 <b>Select a departure date:</b>")
                    .parseMode("HTML")
                    .replyMarkup(calendar)
                    .build());
        }
    }

    /**
     * Shows the inline time picker for departure time selection.
     * Used in both Post Ride and Repost Ride flows.
     * Edits the existing message if messageId is provided, otherwise sends a new one.
     */
    public void showTimePicker(Long chatId, Integer messageId, RideDirection direction,
                               int windowStart, LocalDate selectedDate, CarpoolBot bot) {
        LocalDate today = LocalDate.now(MANILA);
        String dateLabel = selectedDate.equals(today)
                ? "Today, " + selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))
                : selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM d"));

        String text = "🕐 <b>What time are you leaving?</b>\n📅 " + dateLabel +
                "\n\nSelect your departure time:";

        InlineKeyboardMarkup markup = BotTimePickerUtil.buildTimePicker(
                direction, windowStart, selectedDate);

        if (messageId != null) {
            bot.edit(EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .text(text)
                    .parseMode("HTML")
                    .replyMarkup(markup)
                    .build());
        } else {
            bot.send(SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .parseMode("HTML")
                    .replyMarkup(markup)
                    .build());
        }
    }

    // ── Time window ───────────────────────────────────────────────────────

    /**
     * Shows time window selection after a date has been picked from the calendar.
     */
    public void askForTimeWindow(Long chatId, Integer messageId, LocalDate selectedDate, CarpoolBot bot) {
        LocalDate today = LocalDate.now(MANILA);
        String dateLabel = selectedDate.equals(today)
                ? "Today, " + selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))
                : selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM d"));

        String text = "🕐 <b>When do you want to leave?</b>\n📅 " + dateLabel +
                "\n\nSelect a time window:";

        var rows = List.of(
                List.of(
                        BotMessageBuilder.button("🌙 Early Bird (1-4 AM)",    "TIME:EARLY_BIRD"),
                        BotMessageBuilder.button("🌙 Early Morning (4-6 AM)", "TIME:EARLY_MORNING")
                ),
                List.of(
                        BotMessageBuilder.button("🌅 Morning Rush (6-9 AM)",  "TIME:MORNING"),
                        BotMessageBuilder.button("☀️ Late Morning (9-12 PM)", "TIME:MID_MORNING")
                ),
                List.of(
                        BotMessageBuilder.button("🌤️ Noon (12-3 PM)",        "TIME:NOON"),
                        BotMessageBuilder.button("🌇 Afternoon (3-6 PM)",     "TIME:AFTERNOON")
                ),
                List.of(
                        BotMessageBuilder.button("🌆 Evening (6-12 PM)",      "TIME:EVENING")
                ),
                List.of(
                        BotMessageBuilder.button("📅 Custom Date & Time",     "TIME:CUSTOM"),
                        BotMessageBuilder.button("🔍 Show All",               "TIME:ALL_TODAY")
                ),
                List.of(
                        BotMessageBuilder.button("🏠 Menu",                   "MAIN_MENU")
                )
        );

        if (messageId != null) {
            bot.edit(EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .text(text)
                    .parseMode("HTML")
                    .replyMarkup(BotMessageBuilder.inlineButtons(rows))
                    .build());
        } else {
            bot.send(sendWithInline(chatId, text, rows));
        }
    }

    // ── ETD example ───────────────────────────────────────────────────────

    /**
     * Returns a dynamic sample departure time string based on direction.
     * Always uses today's date — eliminates hardcoded date examples.
     */
    public String etdExample(RideDirection direction) {
        int hour = direction == RideDirection.HOME_TO_WORK ? 7  : 18;
        int min  = direction == RideDirection.HOME_TO_WORK ? 30 : 0;
        return LocalDateTime.now(MANILA)
                .withHour(hour).withMinute(min)
                .format(DateTimeFormatter.ofPattern("MM/dd HH:mm"));
    }

    // ── Filter summary ────────────────────────────────────────────────────

    /**
     * Builds a human-readable filter summary string from current state.
     */
    public String buildFilterSummary(UserState state) {
        List<String> parts = new ArrayList<>();
        if (state.getFilterSortBy() != null) {
            parts.add(switch (state.getFilterSortBy()) {
                case "CHEAPEST"   -> "💰 Cheapest first";
                case "MOST_SEATS" -> "🪑 Most seats first";
                default           -> "🕐 Earliest first";
            });
        }
        if (state.getFilterMinSeats() != null) {
            parts.add("🪑 " + state.getFilterMinSeats() + "+ seats");
        }
        if (state.getFilterMaxPrice() != null) {
            parts.add("⛽ Max ₱" + state.getFilterMaxPrice().toPlainString() + " share");
        }
        return parts.isEmpty() ? "" : "<i>Filters: " + String.join(" | ", parts) + "</i>";
    }

    // ── Time context ──────────────────────────────────────────────────────

    /**
     * Builds a human-readable time context string for empty ride list messages.
     * Example: "Today, 7:00 AM – 9:00 AM"
     */
    public String buildTimeContext(LocalDateTime from, LocalDateTime to) {
        LocalDate today    = LocalDate.now(MANILA);
        LocalDate fromDate = from.toLocalDate();

        String datePart = fromDate.equals(today)
                ? "Today"
                : from.format(DateTimeFormatter.ofPattern("MMM d"));

        String timePart = from.format(DateTimeFormatter.ofPattern("h:mm a"))
                + " – "
                + to.format(DateTimeFormatter.ofPattern("h:mm a"));

        return datePart + ", " + timePart;
    }

    // ── Send helper ───────────────────────────────────────────────────────

    /**
     * Builds a SendMessage with inline keyboard.
     * Single source of truth — eliminates the duplicate private helper
     * that existed in both CallbackHandler and MessageHandler.
     */
    public SendMessage sendWithInline(Long chatId, String text,
                                      List<List<InlineKeyboardButton>> rows) {
        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .replyMarkup(BotMessageBuilder.inlineButtons(rows))
                .build();
    }
}