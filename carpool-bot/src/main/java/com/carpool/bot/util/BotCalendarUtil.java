package com.carpool.bot.util;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds an inline calendar keyboard for date selection.
 *
 * Designed for ride search — only allows selection of today and up to 7 days ahead.
 * Past dates and dates beyond the allowed range are rendered as NOOP (non-selectable).
 *
 * No static state — calendarMonth is passed in from UserState on every render.
 * Thread-safe by design.
 */
public class BotCalendarUtil {

    private BotCalendarUtil() {}

    private static final ZoneId    MANILA          = ZoneId.of("Asia/Manila");
    private static final int       MAX_DAYS_AHEAD  = 31;
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    // Day headers — Sun first (matches PH calendar convention)
    private static final List<String> DAY_HEADERS =
            List.of("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa");

    /**
     * Builds the calendar inline keyboard for the given month.
     *
     * @param calendarMonth  month to render
     * @return               InlineKeyboardMarkup ready to attach to a SendMessage
     */
    public static InlineKeyboardMarkup buildCalendar(YearMonth calendarMonth) {
        LocalDate today      = LocalDate.now(MANILA);
        LocalDate maxDate    = today.plusDays(MAX_DAYS_AHEAD);

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // ── Row 1: Month header with Prev / Next navigation ───────────────
        String monthLabel = calendarMonth.getMonth().getDisplayName(
                TextStyle.FULL, Locale.ENGLISH)
                + " " + calendarMonth.getYear();

        YearMonth prevMonth = calendarMonth.minusMonths(1);
        YearMonth nextMonth = calendarMonth.plusMonths(1);

        // Disable Prev if previous month is entirely in the past
        boolean canGoPrev = prevMonth.atEndOfMonth().isAfter(today.minusDays(1));
        // Disable Next if next month is entirely beyond maxDate
        boolean canGoNext = nextMonth.atDay(1).isBefore(maxDate.plusDays(1));

        rows.add(List.of(
                canGoPrev
                        ? button("◀️", "CAL_NAV:PREV")
                        : button(" ", "NOOP"),
                button("📅 " + monthLabel, "NOOP"),
                canGoNext
                        ? button("▶️", "CAL_NAV:NEXT")
                        : button(" ", "NOOP")
        ));

        // ── Row 2: Day headers ────────────────────────────────────────────
        List<InlineKeyboardButton> headerRow = new ArrayList<>();
        for (String day : DAY_HEADERS) {
            headerRow.add(button(day, "NOOP"));
        }
        rows.add(headerRow);

        // ── Rows 3+: Day grid ─────────────────────────────────────────────
        // Find what day of week the 1st falls on (0=Sun, 6=Sat)
        int firstDayOfWeek = calendarMonth.atDay(1).getDayOfWeek().getValue() % 7;
        int daysInMonth    = calendarMonth.lengthOfMonth();

        List<InlineKeyboardButton> week = new ArrayList<>();

        // Leading fillers
        for (int i = 0; i < firstDayOfWeek; i++) {
            week.add(button(" ", "NOOP"));
        }

        // Day buttons
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = calendarMonth.atDay(day);

            if (date.isBefore(today) || date.isAfter(maxDate)) {
                // Past or too far ahead — non-selectable, show muted
                week.add(button("-", "NOOP"));
            } else if (date.equals(today)) {
                // Today — highlighted
                week.add(button("·" + day, "CAL_DATE:" + date.format(ISO_DATE)));
            } else {
                // Selectable future date
                week.add(button(String.valueOf(day), "CAL_DATE:" + date.format(ISO_DATE)));
            }

            // New row every 7 days
            if (week.size() == 7) {
                rows.add(new ArrayList<>(week));
                week.clear();
            }
        }

        // Trailing fillers
        if (!week.isEmpty()) {
            while (week.size() < 7) {
                week.add(button(" ", "NOOP"));
            }
            rows.add(week);
        }

        // ── Last row: Menu button ─────────────────────────────────────────
        rows.add(List.of(button("🏠 Menu", "MAIN_MENU")));

        List<InlineKeyboardRow> keyboardRows = new ArrayList<>();
        for (List<InlineKeyboardButton> row : rows) {
            keyboardRows.add(new InlineKeyboardRow(row));
        }
        return InlineKeyboardMarkup.builder().keyboard(keyboardRows).build();
    }

    private static InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }
}