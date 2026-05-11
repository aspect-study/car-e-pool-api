package com.carpool.bot.handler.helper;

import com.carpool.bot.CarpoolBot;
import com.carpool.bot.state.BotFlow;
import com.carpool.bot.state.StateManager;
import com.carpool.bot.state.UserState;
import com.carpool.bot.util.BotMessageBuilder;
import com.carpool.common.util.HtmlEscapeUtil;
import com.carpool.domain.entity.DriverNote;
import com.carpool.domain.enums.RideDirection;
import com.carpool.service.note.DriverNoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared helper for Post Ride flow — used by both MessageHandler and CallbackHandler.
 * Avoids circular dependency between the two handlers.
 */
@Component
@RequiredArgsConstructor
public class PostRideHelper {

    private final StateManager      stateManager;
    private final DriverNoteService driverNoteService;

    public void showConfirmation(Long chatId, UserState state, CarpoolBot bot) {
        String dirLabel = state.getDirection() == RideDirection.HOME_TO_WORK
                ? "🏠 Home → Work" : "🏢 Work → Home";

        String vehicleLine = state.getSelectedVehicleLabel() != null
                ? "🚘 " + state.getSelectedVehicleLabel()
                : "<i>No vehicle selected</i>";

        String confirmMsg = String.format(
                "📋 <b>Review Your Ride</b>\n\n" +
                        "Direction: %s\n" +
                        "📍 Start: <b>%s</b>\n" +
                        "🏁 End: <b>%s</b>\n" +
                        "🕐 Departure: <b>%s</b>\n" +
                        "🪑 Seats available: <b>%d</b>\n" +
                        "⛽ Gas share: <b>₱%s / seat</b>\n" +
                        "%s\n" +
                        "🚘 Vehicle: %s\n\n" +
                        "Looks good? Post this ride?",
                dirLabel,
                HtmlEscapeUtil.escape(state.getOriginHubName()),
                HtmlEscapeUtil.escape(state.getDestinationHubName()),
                state.getDepartureTime().format(DateTimeFormatter.ofPattern("MMM d 'at' h:mm a")),
                state.getSeats(),
                state.getContribution().toPlainString(),
                state.getNotes() != null
                        ? "📝 Notes: " + HtmlEscapeUtil.escape(state.getNotes()) + "\n"
                        : "",
                vehicleLine);

        var rows = List.of(List.of(
                BotMessageBuilder.button("✅ Post Ride", "CONFIRM_POST_RIDE"),
                BotMessageBuilder.button("❌ Cancel",    "CANCEL_POST_RIDE")
        ));

        bot.send(sendWithInline(chatId, confirmMsg, rows));
    }

    public void showNotesPrompt(Long chatId, Long carpoolUserId,
                                UserState state, CarpoolBot bot) {
        List<DriverNote> savedNotes = driverNoteService.getNotes(carpoolUserId);

        stateManager.save(chatId, state.withFlow(BotFlow.POST_RIDE_NOTES));

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        if (!savedNotes.isEmpty()) {
            for (DriverNote note : savedNotes) {
                String label = note.getContent().length() > 45
                        ? "📌 " + note.getContent().substring(0, 42) + "..."
                        : "📌 " + note.getContent();
                rows.add(List.of(BotMessageBuilder.button(label,
                        "NOTE_PREVIEW:" + note.getId())));
            }
            rows.add(List.of(BotMessageBuilder.button("✏️ Write new note", "NOTE_WRITE")));
            rows.add(List.of(
                    BotMessageBuilder.button("⏭️ Skip",   "SKIP_NOTES"),
                    BotMessageBuilder.button("❌ Cancel", "CANCEL_POST_RIDE")
            ));

            bot.send(sendWithInline(chatId,
                    "📝 <b>Any details for your passengers?</b>\n\n" +
                            "Tap a saved note or write a new one.\n" +
                            "<i>You can include pickup spot, stops, drop-off point, and reminders.</i>",
                    rows));
        } else {
            rows.add(List.of(BotMessageBuilder.button("✏️ Write a note", "NOTE_WRITE")));
            rows.add(List.of(
                    BotMessageBuilder.button("⏭️ Skip",   "SKIP_NOTES"),
                    BotMessageBuilder.button("❌ Cancel", "CANCEL_POST_RIDE")
            ));

            bot.send(sendWithInline(chatId,
                    "📝 <b>Any details for your passengers?</b>\n\n" +
                            "<i>e.g.\n" +
                            "📍 Pickup: In front of Mercury Drug, gate 2\n" +
                            "🛑 Stop: Alabang Town Center (quick stop)\n" +
                            "🏁 Drop-off: BGC High Street, near Fully Booked\n" +
                            "📌 Note: Exact change preferred. No strong food inside the car.</i>",
                    rows));
        }
    }

    private SendMessage sendWithInline(Long chatId, String text,
                                       List<List<InlineKeyboardButton>> rows) {
        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .replyMarkup(BotMessageBuilder.inlineButtons(rows))
                .build();
    }
}