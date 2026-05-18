package com.carpool.bot.handler;

import com.carpool.bot.CarpoolBot;
import com.carpool.bot.handler.context.BotContext;
import com.carpool.bot.handler.helper.BotFlowHelper;
import com.carpool.bot.state.BotFlow;
import com.carpool.bot.state.StateManager;
import com.carpool.bot.state.UserState;
import com.carpool.bot.util.BotMessageBuilder;
import com.carpool.bot.util.ButtonStyle;
import com.carpool.common.util.HtmlEscapeUtil;
import com.carpool.domain.enums.BookingStatus;
import com.carpool.domain.enums.RideStatus;
import com.carpool.service.booking.BookingService;
import com.carpool.service.dto.request.CreateBookingRequest;
import com.carpool.service.dto.response.BookingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles all passenger booking flows.
 * Covers: BOOK_RIDE, BOOK_NOW, MY_BOOKINGS, VIEW_BOOKING, PAST_BOOKINGS,
 * CANCEL_BOOKING, CANCEL_BOOKING_REASON, and booking message text input.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingHandler {

    private final StateManager   stateManager;
    private final BookingService bookingService;
    private final BotFlowHelper flowHelper;

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    // ── Book ride ─────────────────────────────────────────────────────────

    public void handleBookRide(BotContext ctx) {
        Long rideId = ctx.entityId();
        UserState state = ctx.state() != null
                ? ctx.state() : UserState.initial(ctx.carpoolUserId());

        stateManager.save(ctx.chatId(), state
                .withSelectedRideId(rideId)
                .withFlow(BotFlow.BOOKING_MESSAGE));

        ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                """
                        💬 <b>Any message for the driver?</b>
                        
                        This is your chance to introduce yourself and give the driver \
                        important details before they accept your booking.
                        
                        <i>e.g. "I'll be at [landmark], [how to spot you]. \
                        [Any other info the driver should know.]"</i>""",
                List.of(List.of(
                        BotMessageBuilder.button("⏭️ Skip",   "BOOK_NOW:" + rideId, ButtonStyle.SUCCESS.toString()),
                        BotMessageBuilder.button("❌ Cancel", "MAIN_MENU",  ButtonStyle.DANGER.toString())
                ))));
    }

    public void executeBooking(Long chatId, Long rideId, Long carpoolUserId,
                               String passengerMessage, CarpoolBot bot) {
        try {
            bookingService.createBooking(rideId,
                    new CreateBookingRequest(1, null, null, passengerMessage),
                    carpoolUserId);

            bot.send(flowHelper.sendWithInline(chatId,
                    """
                            ⏳ <b>Booking Request Sent!</b>
                            
                            Waiting for the driver to accept your request.
                            You'll be notified once the driver responds.""",
                    List.of(List.of(
                            BotMessageBuilder.button("📜 My Bookings", "MY_BOOKINGS", ButtonStyle.PRIMARY.toString()),
                            BotMessageBuilder.button("🏠 Menu",        "MAIN_MENU", ButtonStyle.PRIMARY.toString())
                    ))));

        } catch (Exception e) {
            log.error("Booking failed: rideId={} userId={} error={}",
                    rideId, carpoolUserId, e.getMessage());
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Could not book this ride. Please try again."));
        }
    }

    /**
     * Handles free-text booking message input during BOOKING_MESSAGE flow.
     */
    public void handleBookingMessage(Long chatId, String text,
                                     UserState state, Long carpoolUserId,
                                     CarpoolBot bot) {
        Long rideId = state.getSelectedRideId();
        if (rideId == null) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Session expired. Please try booking again."));
            stateManager.reset(chatId);
            return;
        }
        stateManager.save(chatId, state.withFlow(BotFlow.IDLE));
        executeBooking(chatId, rideId, carpoolUserId, text.trim(), bot);
    }

    // ── My bookings ───────────────────────────────────────────────────────

    public void handleMyBookings(BotContext ctx) {
        showMyBookings(ctx.chatId(), ctx.carpoolUserId(), ctx.bot());
    }

    public void showMyBookings(Long chatId, Long carpoolUserId, CarpoolBot bot) {
        List<BookingResponse> myBookings   = bookingService.getMyBookings(carpoolUserId);
        List<BookingResponse> pastBookings = bookingService.getMyPastBookings(carpoolUserId);

        StringBuilder sb = new StringBuilder("📜 <b>My Bookings</b>\n\n");
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        if (myBookings.isEmpty()) {
            sb.append("<i>No active bookings.</i>");
        } else {
            sb.append("<b>Active</b>\n");
            for (int i = 0; i < myBookings.size(); i++) {
                BookingResponse b = myBookings.get(i);
                String driverInfo = b.ride().driver().telegramHandle() != null
                        ? " (@" + HtmlEscapeUtil.escape(
                        b.ride().driver().telegramHandle()) + ")"
                        : "";
                sb.append(String.format(
                        "<b>%d.</b> %s → %s | 🕐 %s | ⛽ ₱%.2f share\n👤 %s%s\n",
                        i + 1,
                        HtmlEscapeUtil.escape(b.ride().originHub().name()),
                        HtmlEscapeUtil.escape(b.ride().destinationHub().name()),
                        b.ride().departureTime()
                                .atZone(MANILA)
                                .format(DateTimeFormatter.ofPattern("MMM d h:mma")),
                        b.contributionDue(),
                        HtmlEscapeUtil.escape(b.ride().driver().fullName()),
                        driverInfo));

                String statusPrefix = b.status() == BookingStatus.PENDING ? "⏳ " : "🔍 ";
                rows.add(List.of(InlineKeyboardButton.builder()
                        .text(statusPrefix +
                                b.ride().originHub().name() + " → " +
                                b.ride().destinationHub().name() + " | " +
                                b.ride().departureTime()
                                        .atZone(MANILA)
                                        .format(DateTimeFormatter.ofPattern("MMM d h:mma")))
                        .callbackData("VIEW_BOOKING:" + b.id())
                        .build()));
            }
        }

        if (!pastBookings.isEmpty()) {
            rows.add(List.of(InlineKeyboardButton.builder()
                    .text("📂 Booking History (" + pastBookings.size() + ")")
                    .callbackData("PAST_BOOKINGS")
                    .build()));
        }
        rows.add(List.of(InlineKeyboardButton.builder()
                .text("🏠 Menu").callbackData("MAIN_MENU").build()));

        bot.send(flowHelper.sendWithInline(chatId, sb.toString(), rows));
    }

    // ── View booking detail ───────────────────────────────────────────────

    public void handleViewBooking(BotContext ctx) {
        try {
            BookingResponse b = bookingService.getBookingById(ctx.entityId(), ctx.carpoolUserId());

            String statusLabel = switch (b.status().name()) {
                case "CONFIRMED"              -> "✅ Confirmed";
                case "PENDING"                -> "⏳ Waiting for driver approval";
                case "CANCELLED_BY_PASSENGER" -> "❌ Cancelled by you";
                case "CANCELLED_BY_DRIVER"    -> "❌ Cancelled by driver";
                case "COMPLETED"              -> "🏁 Completed";
                case "DECLINED"               -> "❌ Declined by driver";
                case "TIMED_OUT"              -> "⏰ Expired — driver did not respond";
                default                       -> b.status().name();
            };

            String pickup  = b.pickupWaypoint()  != null
                    ? b.pickupWaypoint().hub().name()
                    : b.ride().originHub().name();
            String dropoff = b.dropoffWaypoint() != null
                    ? b.dropoffWaypoint().hub().name()
                    : b.ride().destinationHub().name();

            String detail = String.format(
                    """
                            📋 <b>Booking Details</b>

                            🚗 %s → %s
                            🕐 %s
                            🚏 Pickup: <b>%s</b>
                            🏁 Dropoff: <b>%s</b>
                            🪑 Seats: %d
                            ⛽ Suggested share: ₱%.2f
                            👤 Driver: %s%s
                            %s📊 Status: %s""",
                    HtmlEscapeUtil.escape(b.ride().originHub().name()),
                    HtmlEscapeUtil.escape(b.ride().destinationHub().name()),
                    b.ride().departureTime()
                            .atZone(MANILA)
                            .format(DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a")),
                    HtmlEscapeUtil.escape(pickup),
                    HtmlEscapeUtil.escape(dropoff),
                    b.seatsReserved(),
                    b.contributionDue(),
                    HtmlEscapeUtil.escape(b.ride().driver().fullName()),
                    b.ride().driver().telegramHandle() != null
                            ? " (@" + HtmlEscapeUtil.escape(
                            b.ride().driver().telegramHandle()) + ")" : "",
                    b.passengerMessage() != null
                            ? "💬 Your message: \"" +
                            HtmlEscapeUtil.escape(b.passengerMessage()) + "\"\n" : "",
                    statusLabel);

            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            boolean rideNotStarted = b.ride().status() != RideStatus.DEPARTED
                    && b.ride().status() != RideStatus.COMPLETED;

            if ((b.status() == BookingStatus.CONFIRMED
                    || b.status() == BookingStatus.PENDING) && rideNotStarted) {
                rows.add(List.of(InlineKeyboardButton.builder()
                        .text("❌ Cancel Booking")
                        .callbackData("CANCEL_BOOKING:" + ctx.entityId())
                        .style(ButtonStyle.DANGER.toString())
                        .build()));
            }
            rows.add(List.of(InlineKeyboardButton.builder()
                    .text("◀️ Back to My Bookings")
                    .callbackData("MY_BOOKINGS")
                    .style(ButtonStyle.SUCCESS.toString())
                    .build()));

            ctx.bot().send(SendMessage.builder()
                    .chatId(ctx.chatId())
                    .text(detail)
                    .parseMode("HTML")
                    .replyMarkup(InlineKeyboardMarkup.builder()
                            .keyboard(rows.stream().map(InlineKeyboardRow::new).toList())
                            .build())
                    .build());

        } catch (Exception e) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not load booking details."));
        }
    }

    // ── Past bookings ─────────────────────────────────────────────────────

    public void handlePastBookings(BotContext ctx) {
        List<BookingResponse> past = bookingService.getMyPastBookings(ctx.carpoolUserId());

        if (past.isEmpty()) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "📂 <b>Booking History</b>\n\n<i>No booking history yet.</i>"));
            return;
        }

        StringBuilder sb = new StringBuilder("📂 <b>Booking History</b>\n\n");
        for (int i = 0; i < past.size(); i++) {
            BookingResponse b = past.get(i);
            String statusLabel = switch (b.status().name()) {
                case "CANCELLED_BY_PASSENGER" -> "❌ You cancelled";
                case "CANCELLED_BY_DRIVER"    -> "❌ Driver cancelled";
                case "COMPLETED"              -> "🏁 Completed";
                case "DECLINED"               -> "❌ Declined by driver";
                case "TIMED_OUT"              -> "⏰ Expired";
                default                       -> b.status().name();
            };
            sb.append(String.format("<b>%d.</b> %s → %s | %s\n",
                    i + 1,
                    HtmlEscapeUtil.escape(b.ride().originHub().name()),
                    HtmlEscapeUtil.escape(b.ride().destinationHub().name()),
                    statusLabel));
        }

        ctx.bot().send(SendMessage.builder()
                .chatId(ctx.chatId())
                .text(sb.toString())
                .parseMode("HTML")
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboard(List.of(new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("◀️ Back to My Bookings")
                                        .callbackData("MY_BOOKINGS")
                                        .style(ButtonStyle.SUCCESS.toString())
                                        .build())))
                        .build())
                .build());
    }

    // ── Cancel booking ────────────────────────────────────────────────────

    public void handleCancelBooking(BotContext ctx) {
        Long bookingId = ctx.entityId();
        var rows = List.of(
                List.of(BotMessageBuilder.button("🔄 Found another ride",
                        "CANCEL_BOOKING_REASON:" + bookingId + ":FOUND_OTHER", ButtonStyle.PRIMARY.toString())),
                List.of(BotMessageBuilder.button("📅 Change of plans",
                        "CANCEL_BOOKING_REASON:" + bookingId + ":CHANGE_PLANS", ButtonStyle.PRIMARY.toString())),
                List.of(BotMessageBuilder.button("⏰ Running late",
                        "CANCEL_BOOKING_REASON:" + bookingId + ":RUNNING_LATE", ButtonStyle.PRIMARY.toString())),
                List.of(BotMessageBuilder.button("❌ Other reason",
                        "CANCEL_BOOKING_REASON:" + bookingId + ":OTHER", ButtonStyle.PRIMARY.toString())),
                List.of(BotMessageBuilder.button("◀️ Back",
                        "VIEW_BOOKING:" + bookingId, ButtonStyle.PRIMARY.toString()))
        );
        ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                "❓ <b>Why are you cancelling?</b>", rows));
    }

    public void handleCancelBookingWithReason(BotContext ctx) {
        // parts[0]=CANCEL_BOOKING_REASON, parts[1]=bookingId, parts[2]=reasonCode
        if (ctx.parts().length < 3) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Invalid cancel request."));
            return;
        }
        Long bookingId;
        try {
            bookingId = Long.parseLong(ctx.parts()[1]);
        } catch (NumberFormatException e) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Invalid booking ID."));
            return;
        }

        String reason = switch (ctx.parts()[2]) {
            case "FOUND_OTHER"  -> "Found another ride";
            case "CHANGE_PLANS" -> "Change of plans";
            case "RUNNING_LATE" -> "Running late";
            default             -> "Other reason";
        };

        try {
            bookingService.cancelBooking(bookingId, ctx.carpoolUserId(), reason);
            stateManager.reset(ctx.chatId());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    """
                            ✅ <b>Booking Cancelled</b>
                            
                            Your booking has been cancelled. The driver has been notified."""));
        } catch (Exception e) {
            log.error("Cancel booking failed: bookingId={} userId={} error={}",
                    bookingId, ctx.carpoolUserId(), e.getMessage());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not cancel booking. Please try again."));
        }
    }
}