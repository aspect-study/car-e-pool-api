package com.carpool.bot.handler.helper;

import com.carpool.bot.CarpoolBot;
import com.carpool.bot.config.BotConfig;
import com.carpool.bot.state.StateManager;
import com.carpool.bot.state.UserState;
import com.carpool.bot.util.BotCalendarUtil;
import com.carpool.bot.util.BotMessageBuilder;
import com.carpool.bot.util.BotTimePickerUtil;
import com.carpool.bot.util.ButtonStyle;
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
 * <p>
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
    public void showMainMenu(Long chatId, Long carpoolUserId, UserState ignoredState, CarpoolBot bot) {
        stateManager.reset(chatId);

        List<RideResponse> myRides = rideService.getMyRides(carpoolUserId);
        List<RideResponse> activeRides = myRides.stream()
                .filter(r -> r.status() == RideStatus.ACTIVE
                        || r.status() == RideStatus.FULL
                        || r.status() == RideStatus.DEPARTED)
                .toList();

        if (activeRides.size() >= 2) {
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            for (RideResponse ride : activeRides) {
                long pending = bookingService.countPendingRequestsForRide(ride.id());
                String badge = pending > 0 ? "⏳ " + pending + " pending" : "✅ 0 pending";
                String dirLabel = switch (ride.direction()) {
                    case HOME_TO_WORK -> "🏠 Home → Work";
                    case WORK_TO_HOME -> "🏢 Work → Home";
                    default           -> ride.direction().label();
                };
                rows.add(List.of(BotMessageBuilder.button(
                        dirLabel + "  ·  " + badge,
                        "MANAGE_RIDE:" + ride.id(),
                        ButtonStyle.PRIMARY.toString())));
            }
            List<BookingResponse> myBookings = bookingService.getMyBookings(carpoolUserId);
            if (!myBookings.isEmpty()) {
                rows.add(List.of(BotMessageBuilder.button(
                        "📜 My Bookings (" + myBookings.size() + ")", "MY_BOOKINGS", ButtonStyle.SUCCESS.toString())));
            }
            rows.add(List.of(BotMessageBuilder.button("👤 My Profile", "MY_PROFILE", ButtonStyle.PRIMARY.toString())));
            bot.send(sendWithInline(chatId,
                    "🚗 You have " + activeRides.size() + " active rides. Which one would you like to manage?", rows));

        } else if (activeRides.size() == 1) {
            showRideManagementCard(chatId, carpoolUserId, activeRides.get(0).id(), bot);

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
                    BotMessageBuilder.button("🏠 Home to Work", "DIRECTION:HOME_TO_WORK", ButtonStyle.PRIMARY.toString()),
                    BotMessageBuilder.button("🏢 Work to Home", "DIRECTION:WORK_TO_HOME", ButtonStyle.PRIMARY.toString())
            ));
            if (!myBookings.isEmpty()) {
                rows.add(List.of(BotMessageBuilder.button(
                        "📜 My Bookings (" + myBookings.size() + ")", "MY_BOOKINGS",  ButtonStyle.SUCCESS.toString())));
            }
            if (hasPastRides) {
                rows.add(List.of(BotMessageBuilder.button("🔄 Repost a Ride", "MY_RIDES", ButtonStyle.SUCCESS.toString())));
            }
            rows.add(List.of(BotMessageBuilder.button("👤 My Profile", "MY_PROFILE",  ButtonStyle.PRIMARY.toString())));
            rows.add(List.of(BotMessageBuilder.button("⭐ My Ratings", "VIEW_RATINGS:" + carpoolUserId, null)));

            bot.send(sendWithInline(chatId, prompt, rows));
        }
    }

    // ── Ride management card ──────────────────────────────────────────────

    public void showRideManagementCard(Long chatId, Long carpoolUserId, Long rideId, CarpoolBot bot) {
        RideResponse active;
        try {
            active = rideService.getRideById(rideId);
        } catch (Exception e) {
            bot.send(BotMessageBuilder.text(chatId, "⚠️ This ride is no longer available."));
            return;
        }
        if (!active.driver().id().equals(carpoolUserId)) {
            bot.send(BotMessageBuilder.text(chatId, "⚠️ This is not your ride."));
            return;
        }

        String msg = "🚗 <b>Your Active Ride</b>\n\n" +
                BotMessageBuilder.formatRideCard(active) +
                "\n\nWhat would you like to do?";

        long activeRideCount = rideService.getMyRides(carpoolUserId).stream()
                .filter(r -> r.status() == RideStatus.ACTIVE
                        || r.status() == RideStatus.FULL
                        || r.status() == RideStatus.DEPARTED)
                .count();

        List<InlineKeyboardButton> repostRow = null;
        if (activeRideCount < 2
                && (active.direction() == RideDirection.HOME_TO_WORK
                    || active.direction() == RideDirection.WORK_TO_HOME)) {
            RideDirection otherDir = active.direction() == RideDirection.HOME_TO_WORK
                    ? RideDirection.WORK_TO_HOME
                    : RideDirection.HOME_TO_WORK;
            String otherLabel = otherDir == RideDirection.WORK_TO_HOME
                    ? "🏢 Work → Home"
                    : "🏠 Home → Work";
            repostRow = List.of(
                    BotMessageBuilder.button("🔄 Repost", "MY_RIDES", ButtonStyle.SUCCESS.toString()),
                    BotMessageBuilder.button(otherLabel, "DIRECTION:" + otherDir.name(), ButtonStyle.SUCCESS.toString()));
        }

        long pendingCount = bookingService.countPendingRequestsForRide(rideId);
        boolean canReannounce = active.announceCount() != null && active.announceCount() < 10;
        List<BookingResponse> myBookings = bookingService.getMyBookings(carpoolUserId);

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        if (active.status() == RideStatus.DEPARTED) {
            rows.add(List.of(
                    BotMessageBuilder.button("👥 My Passengers", "RIDE_BOOKINGS:" + active.id(), ButtonStyle.PRIMARY.toString()),
                    BotMessageBuilder.button("✅ Complete Ride",  "COMPLETE_RIDE:" + active.id(), ButtonStyle.SUCCESS.toString())
            ));
            if (!myBookings.isEmpty()) {
                rows.add(List.of(BotMessageBuilder.button(
                        "📜 My Bookings (" + myBookings.size() + ")", "MY_BOOKINGS", ButtonStyle.SUCCESS.toString())));
            }
            if (repostRow != null) {
                rows.add(repostRow);
            }
            rows.add(List.of(BotMessageBuilder.button("👤 My Profile", "MY_PROFILE", ButtonStyle.PRIMARY.toString())));

        } else if (pendingCount > 0) {
            rows.add(List.of(
                    BotMessageBuilder.button("👥 My Passengers", "RIDE_BOOKINGS:" + active.id(), ButtonStyle.PRIMARY.toString()),
                    BotMessageBuilder.button("🚀 Start Ride",    "DEPART_RIDE:"  + active.id(), ButtonStyle.SUCCESS.toString())
            ));
            rows.add(List.of(
                    BotMessageBuilder.button("⏳ Pending (" + pendingCount + ")", "PENDING_REQUESTS:" + active.id(), ButtonStyle.PRIMARY.toString()),
                    BotMessageBuilder.button("❌ Cancel Ride", "CANCEL_RIDE:" + active.id(), ButtonStyle.DANGER.toString())
            ));
            rows.add(List.of(BotMessageBuilder.button("✏️ Edit Time", "EDIT_RIDE_TIME:" + active.id(), ButtonStyle.PRIMARY.toString())));
            rows.add(List.of(BotMessageBuilder.button("🔀 Change Route", "EDIT_RIDE_ROUTE:" + active.id(), ButtonStyle.PRIMARY.toString())));
            if (canReannounce) {
                rows.add(List.of(BotMessageBuilder.button(
                        "📢 Re-announce (" + (10 - active.announceCount()) + " left)",
                        "REANNOUNCE_RIDE:" + active.id(),
                        ButtonStyle.PRIMARY.toString())));
            }
            if (!myBookings.isEmpty()) {
                rows.add(List.of(BotMessageBuilder.button(
                        "📜 My Bookings (" + myBookings.size() + ")", "MY_BOOKINGS", ButtonStyle.SUCCESS.toString())));
            }
            if (repostRow != null) {
                rows.add(repostRow);
            }
            rows.add(List.of(BotMessageBuilder.button("🔍 Find a Ride", "FIND_RIDE",  ButtonStyle.SUCCESS.toString())));
            rows.add(List.of(BotMessageBuilder.button("👤 My Profile",  "MY_PROFILE", ButtonStyle.PRIMARY.toString())));

        } else {
            rows.add(List.of(
                    BotMessageBuilder.button("👥 My Passengers", "RIDE_BOOKINGS:" + active.id(),  ButtonStyle.PRIMARY.toString()),
                    BotMessageBuilder.button("🚀 Start Ride",    "DEPART_RIDE:"  + active.id(),  ButtonStyle.SUCCESS.toString())
            ));
            rows.add(List.of(BotMessageBuilder.button("❌ Cancel Ride", "CANCEL_RIDE:" + active.id(),  ButtonStyle.DANGER.toString())));
            rows.add(List.of(BotMessageBuilder.button("✏️ Edit Time", "EDIT_RIDE_TIME:" + active.id(), ButtonStyle.SUCCESS.toString())));
            rows.add(List.of(BotMessageBuilder.button("🔀 Change Route", "EDIT_RIDE_ROUTE:" + active.id(), ButtonStyle.PRIMARY.toString())));
            if (canReannounce) {
                rows.add(List.of(BotMessageBuilder.button(
                        "📢 Re-announce (" + (10 - active.announceCount()) + " left)",
                        "REANNOUNCE_RIDE:" + active.id(),
                        ButtonStyle.PRIMARY.toString())));
            }
            if (!myBookings.isEmpty()) {
                rows.add(List.of(BotMessageBuilder.button(
                        "📜 My Bookings (" + myBookings.size() + ")", "MY_BOOKINGS", ButtonStyle.SUCCESS.toString())));
            }
            if (repostRow != null) {
                rows.add(repostRow);
            }
            rows.add(List.of(BotMessageBuilder.button("🔍 Find a Ride", "FIND_RIDE",  ButtonStyle.SUCCESS.toString())));
            rows.add(List.of(BotMessageBuilder.button("👤 My Profile",  "MY_PROFILE", ButtonStyle.PRIMARY.toString())));
        }

        bot.send(BotMessageBuilder.textWithRemoveKeyboard(chatId, msg));
        bot.send(sendWithInline(chatId, "Choose an action:", rows));
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
                BotMessageBuilder.button("🚗 Post a Ride", "POST_RIDE", ButtonStyle.PRIMARY.toString()),
                BotMessageBuilder.button("🔍 Find a Ride", "FIND_RIDE", ButtonStyle.SUCCESS.toString())
        ))
                : List.of(
                List.of(
                        BotMessageBuilder.button("🚗 Post a Ride", "POST_RIDE", ButtonStyle.PRIMARY.toString()),
                        BotMessageBuilder.button("🔍 Find a Ride", "FIND_RIDE", ButtonStyle.SUCCESS.toString())
                ),
                List.of(BotMessageBuilder.button(
                        "📜 My Bookings (" + myBookings.size() + ")", "MY_BOOKINGS",  ButtonStyle.PRIMARY.toString()))
        );

        bot.send(sendWithInline(chatId,
                "Direction: <b>" + dirLabel + "</b>\n\nWhat would you like to do?", rows));
    }

    /**
     * Shows the inline calendar for date selection.
     * Uses default callback prefixes ("CAL_DATE", "CAL_NAV").
     * calendarMonth comes from UserState — no static state.
     */
    public void showCalendar(Long chatId, Integer messageId, YearMonth calendarMonth, CarpoolBot bot) {
        showCalendar(chatId, messageId, calendarMonth, "CAL_DATE", "CAL_NAV", bot);
    }

    /**
     * Shows the inline calendar with custom callback prefixes.
     * Use this overload to reuse the calendar across different flows.
     */
    public void showCalendar(Long chatId, Integer messageId, YearMonth calendarMonth,
                             String datePrefix, String navPrefix, CarpoolBot bot) {
        InlineKeyboardMarkup calendar = BotCalendarUtil.buildCalendar(calendarMonth, datePrefix, navPrefix);

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
     * Uses default callback prefixes ("RIDE_TIME", "TIME_NAV").
     * Edits the existing message if messageId is provided, otherwise sends a new one.
     */
    public void showTimePicker(Long chatId, Integer messageId,
                               int windowStart, LocalDate selectedDate, CarpoolBot bot) {
        showTimePicker(chatId, messageId, windowStart, selectedDate, "RIDE_TIME", "TIME_NAV", bot);
    }

    /**
     * Shows the inline time picker with custom callback prefixes.
     * Use this overload to reuse the time picker across different flows.
     */
    public void showTimePicker(Long chatId, Integer messageId,
                               int windowStart, LocalDate selectedDate,
                               String slotPrefix, String navPrefix, CarpoolBot bot) {
        LocalDate today = LocalDate.now(MANILA);
        String dateLabel = selectedDate.equals(today)
                ? "Today, " + selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))
                : selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM d"));

        String text = "🕐 <b>What time are you leaving?</b>\n📅 " + dateLabel +
                "\n\nSelect your departure time:";

        InlineKeyboardMarkup markup = BotTimePickerUtil.buildTimePicker(
                windowStart, selectedDate, slotPrefix, navPrefix);

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
                        BotMessageBuilder.button("🌙 Early Bird (1-4 AM)",    "TIME:EARLY_BIRD", ButtonStyle.PRIMARY.toString()),
                        BotMessageBuilder.button("🌙 Early Morning (4-6 AM)", "TIME:EARLY_MORNING", ButtonStyle.PRIMARY.toString())
                ),
                List.of(
                        BotMessageBuilder.button("🌅 Morning Rush (6-9 AM)",  "TIME:MORNING", ButtonStyle.PRIMARY.toString()),
                        BotMessageBuilder.button("☀️ Late Morning (9-12 PM)", "TIME:MID_MORNING", ButtonStyle.PRIMARY.toString())
                ),
                List.of(
                        BotMessageBuilder.button("🌤️ Noon (12-3 PM)",        "TIME:NOON", ButtonStyle.PRIMARY.toString()),
                        BotMessageBuilder.button("🌇 Afternoon (3-6 PM)",     "TIME:AFTERNOON", ButtonStyle.PRIMARY.toString())
                ),
                List.of(
                        BotMessageBuilder.button("🌆 Evening (6-12 PM)",      "TIME:EVENING", ButtonStyle.PRIMARY.toString())
                ),
                List.of(
                        BotMessageBuilder.button("📅 Custom Date & Time",     "TIME:CUSTOM", ButtonStyle.PRIMARY.toString()),
                        BotMessageBuilder.button("🔍 Show All",               "TIME:ALL_TODAY", ButtonStyle.PRIMARY.toString())
                ),
                List.of(
                        BotMessageBuilder.button("🏠 Menu",                   "MAIN_MENU", ButtonStyle.SUCCESS.toString())
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