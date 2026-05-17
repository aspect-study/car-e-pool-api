package com.carpool.bot.util;

import com.carpool.domain.enums.RideDirection;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds an inline time picker keyboard for departure time selection.
 * <p>
 * Each page covers a fixed 5-hour block (300 min) with no overlap between pages.
 * windowStart is the block's starting minute: 0, 300, 600, 900, or 1200.
 * Only valid, future slots are shown — no placeholders, no next-day wrap.
 * <p>
 * No static state — windowStart is passed in from UserState on every render.
 * Thread-safe by design.
 */
public class BotTimePickerUtil {

    private BotTimePickerUtil() {}

    private static final ZoneId MANILA           = ZoneId.of("Asia/Manila");

    private static final int SLOTS_PER_PAGE     = 20;
    private static final int SLOT_INCREMENT_MIN  = 15;
    private static final int BUTTONS_PER_ROW    = 4;

    /** Size of one page in minutes (20 slots × 15 min = 300 min = 5 hours). */
    public  static final int PAGE_SIZE_MIN       = SLOTS_PER_PAGE * SLOT_INCREMENT_MIN;

    /** Last valid page start — 20:00 (1200 min). */
    private static final int MAX_WINDOW_START    = 1200;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Default page start for future dates.
     * HOME_TO_WORK → 300 (5 AM page), WORK_TO_HOME → 900 (3 PM page).
     */
    public static int defaultWindowStart(RideDirection direction) {
        return direction == RideDirection.HOME_TO_WORK ? 300 : 900;
    }

    /**
     * Default page start adjusted for today — opens on the page that contains
     * the next available 15-min slot rather than the direction default.
     */
    public static int defaultWindowStart(RideDirection direction, LocalDate selectedDate) {
        if (!LocalDate.now(MANILA).equals(selectedDate)) return defaultWindowStart(direction);
        LocalTime now     = LocalTime.now(MANILA);
        int nowMin        = now.getHour() * 60 + now.getMinute();
        int nextSlotMin   = ((nowMin / SLOT_INCREMENT_MIN) + 1) * SLOT_INCREMENT_MIN;
        int page          = nextSlotMin / PAGE_SIZE_MIN;
        return Math.min(MAX_WINDOW_START, page * PAGE_SIZE_MIN);
    }

    /**
     * Advances {@code windowStart} forward until the page has at least one available
     * slot, then returns it. No-op for future dates.
     * Call this in navigation handlers before saving to UserState so the stored
     * value always matches what is displayed.
     */
    public static int adjustWindowForToday(int windowStart, LocalDate selectedDate) {
        if (!LocalDate.now(MANILA).equals(selectedDate)) return windowStart;
        LocalTime now = LocalTime.now(MANILA);
        while (collectSlots(windowStart, now).isEmpty() && windowStart + PAGE_SIZE_MIN <= MAX_WINDOW_START) {
            windowStart += PAGE_SIZE_MIN;
        }
        return windowStart;
    }

    /**
     * Builds the time picker inline keyboard for the given page.
     *
     * @param windowStart  page start in minutes (0, 300, 600, 900, 1200)
     * @param selectedDate selected date — past slots are omitted when this is today
     */
    public static InlineKeyboardMarkup buildTimePicker(int windowStart,
                                                       LocalDate selectedDate) {
        windowStart = Math.min(windowStart, MAX_WINDOW_START);
        boolean isToday = LocalDate.now(MANILA).equals(selectedDate);
        LocalTime now   = isToday ? LocalTime.now(MANILA) : null;

        // For today: advance to the first page with available slots (safety net for
        // callers that pass a stale windowStart without calling adjustWindowForToday)
        if (isToday) windowStart = adjustWindowForToday(windowStart, selectedDate);
        List<int[]> slots = collectSlots(windowStart, now);

        // Build slot rows
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> currentRow = new ArrayList<>();

        for (int[] slot : slots) {
            currentRow.add(button(formatLabel(slot[0], slot[1]),
                                  String.format("RIDE_TIME:%02d:%02d", slot[0], slot[1])));
            if (currentRow.size() == BUTTONS_PER_ROW) {
                rows.add(new ArrayList<>(currentRow));
                currentRow = new ArrayList<>();
            }
        }
        if (!currentRow.isEmpty()) rows.add(new ArrayList<>(currentRow));

        if (slots.isEmpty()) {
            rows.add(List.of(button("No times available here", "NOOP")));
        }

        // Navigation row
        rows.add(navRow(windowStart, isToday, now));

        // Menu button
        rows.add(List.of(button("🏠 Menu", "MAIN_MENU")));

        List<InlineKeyboardRow> keyboardRows = new ArrayList<>();
        for (List<InlineKeyboardButton> row : rows) {
            keyboardRows.add(new InlineKeyboardRow(row));
        }

        return InlineKeyboardMarkup.builder().keyboard(keyboardRows).build();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Returns valid slots for the given page. Passes {@code now=null} for future dates. */
    private static List<int[]> collectSlots(int windowStart, LocalTime now) {
        List<int[]> slots = new ArrayList<>();
        int pageEnd = Math.min(windowStart + PAGE_SIZE_MIN, 24 * 60);
        for (int m = windowStart; m < pageEnd; m += SLOT_INCREMENT_MIN) {
            int hour   = m / 60;
            int minute = m % 60;
            if (now != null && !LocalTime.of(hour, minute).isAfter(now)) continue;
            slots.add(new int[]{hour, minute});
        }
        return slots;
    }

    private static List<InlineKeyboardButton> navRow(int windowStart, boolean isToday, LocalTime now) {
        boolean hasPrev = windowStart > 0;
        // For today: hide Earlier if the previous page has no available slots
        if (hasPrev && isToday) {
            hasPrev = !collectSlots(windowStart - PAGE_SIZE_MIN, now).isEmpty();
        }
        boolean hasNext = windowStart + PAGE_SIZE_MIN < 24 * 60;

        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(hasPrev ? button("◀️ Earlier", "TIME_NAV:EARLIER") : button(" ", "NOOP"));
        row.add(hasNext ? button("Later ▶️",   "TIME_NAV:LATER")   : button(" ", "NOOP"));
        return row;
    }

    private static String formatLabel(int hour, int minute) {
        String period = hour < 12 ? "AM" : "PM";
        int display   = hour % 12;
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
