package com.carpool.bot.util;

import com.carpool.domain.enums.RideDirection;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds an inline time picker keyboard for departure time selection.
 * <p>
 * Shows 10 time slots (5 rows of 2) in 30-minute increments.
 * Direction-aware defaults — HOME_TO_WORK starts at 5 AM, WORK_TO_HOME at 4 PM.
 * Earlier/Later navigation shifts the window by 2 hours.
 * <p>
 * No static state — windowStart is passed in from UserState on every render.
 * Thread-safe by design.
 */
public class BotTimePickerUtil {

    private BotTimePickerUtil() {}

    private static final int SLOTS_PER_PAGE   = 10;
    private static final int WINDOW_SHIFT_HRS = 2;

    /**
     * Default window start hour based on direction.
     */
    public static int defaultWindowStart(RideDirection direction) {
        return direction == RideDirection.HOME_TO_WORK ? 5 : 16;
    }

    /**
     * Builds the time picker inline keyboard.
     *
     * @param ignoredDirection    ride direction — used for default window
     * @param windowStart  first hour to show (0-23)
     * @param ignoredSelectedDate selected date — for context display
     * @return             InlineKeyboardMarkup ready to attach to a message
     */
    public static InlineKeyboardMarkup buildTimePicker(RideDirection ignoredDirection,
                                                       int windowStart,
                                                       LocalDate ignoredSelectedDate) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // ── Time slots — 10 slots in 30-min increments ────────────────────
        int totalMinutes = windowStart * 60;
        List<InlineKeyboardButton> currentRow = new ArrayList<>();

        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            int hour   = (totalMinutes / 60) % 24;
            int minute = totalMinutes % 60;

            String label    = formatLabel(hour, minute);
            String callback = String.format("RIDE_TIME:%02d:%02d", hour, minute);

            currentRow.add(button(label, callback));

            if (currentRow.size() == 2) {
                rows.add(new ArrayList<>(currentRow));
                currentRow = new ArrayList<>();
            }

            totalMinutes += 30;
        }

        if (!currentRow.isEmpty()) {
            rows.add(new ArrayList<>(currentRow));
        }

        // ── Navigation row — Earlier / Later ──────────────────────────────
        int prevWindow = windowStart - WINDOW_SHIFT_HRS;
        int nextWindow = windowStart + WINDOW_SHIFT_HRS;

        List<InlineKeyboardButton> navRow = new ArrayList<>();
        navRow.add(prevWindow >= 0
                ? button("◀️ Earlier", "TIME_NAV:EARLIER")
                : button(" ", "NOOP"));
        navRow.add(nextWindow < 24
                ? button("Later ▶️", "TIME_NAV:LATER")
                : button(" ", "NOOP"));
        rows.add(navRow);

        // ── Menu button ───────────────────────────────────────────────────
        rows.add(List.of(button("🏠 Menu", "MAIN_MENU")));

        // ── Convert to InlineKeyboardRow ──────────────────────────────────
        List<InlineKeyboardRow> keyboardRows = new ArrayList<>();
        for (List<InlineKeyboardButton> row : rows) {
            keyboardRows.add(new InlineKeyboardRow(row));
        }

        return InlineKeyboardMarkup.builder().keyboard(keyboardRows).build();
    }

    /**
     * Formats hour + minute as 12-hour AM/PM label.
     * e.g. hour=6, minute=30 → "6:30 AM"
     *      hour=17, minute=0  → "5:00 PM"
     */
    private static String formatLabel(int hour, int minute) {
        String period  = hour < 12 ? "AM" : "PM";
        int display    = hour % 12;
        if (display == 0) display = 12;
        return String.format("%d:%02d %s", display, minute, period);
    }

    private static InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }
}