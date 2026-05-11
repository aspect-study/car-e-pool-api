package com.carpool.bot.handler;

import com.carpool.bot.CarpoolBot;
import com.carpool.bot.handler.context.BotContext;
import com.carpool.bot.handler.helper.BotFlowHelper;
import com.carpool.bot.state.StateManager;
import com.carpool.bot.util.BotMessageBuilder;
import com.carpool.bot.util.ButtonStyle;
import com.carpool.common.util.HtmlEscapeUtil;
import com.carpool.domain.enums.BookingStatus;
import com.carpool.domain.enums.RideDirection;
import com.carpool.domain.enums.RideStatus;
import com.carpool.service.booking.BookingService;
import com.carpool.service.dto.request.UpdateRideStatusRequest;
import com.carpool.service.dto.response.BookingResponse;
import com.carpool.service.dto.response.RideResponse;
import com.carpool.service.ride.RideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles all driver-side ride management flows.
 * Covers: MY_RIDES, RIDE_BOOKINGS, VIEW_DRIVER_BOOKING, ACCEPT_BOOKING,
 * DECLINE_BOOKING, DECLINE_BOOKING_REASON, PENDING_REQUESTS, VIEW_PENDING,
 * CANCEL_RIDE, CONFIRM_CANCEL_RIDE, DEPART_RIDE, COMPLETE_RIDE, REPOST_RIDE.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DriverHandler {

    private final StateManager   stateManager;
    private final RideService    rideService;
    private final BookingService bookingService;
    private final BotFlowHelper flowHelper;

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    // ── My rides ──────────────────────────────────────────────────────────

    public void showMyRides(Long chatId, Long carpoolUserId, CarpoolBot bot) {
        List<RideResponse> rides = rideService.getRecentRidesForRepost(carpoolUserId);

        if (rides.isEmpty()) {
            bot.send(BotMessageBuilder.text(chatId,
                    "🚗 <b>My Rides</b>\n\n<i>No past rides yet.</i>"));
            return;
        }

        List<RideResponse> recent = rides.stream()
                .filter(r -> r.status() == RideStatus.COMPLETED
                        || r.status() == RideStatus.CANCELLED)
                .limit(3)
                .toList();

        if (recent.isEmpty()) {
            bot.send(BotMessageBuilder.text(chatId,
                    "🚗 <b>My Rides</b>\n\n" +
                            "<i>No completed or cancelled rides yet.</i>"));
            return;
        }

        StringBuilder sb = new StringBuilder("🚗 <b>My Rides</b>\n\n");
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (int i = 0; i < recent.size(); i++) {
            RideResponse r = recent.get(i);
            String dirEmoji    = r.direction() == RideDirection.HOME_TO_WORK ? "🏠" : "🏢";
            String statusLabel = switch (r.status().name()) {
                case "COMPLETED" -> "🏁";
                case "CANCELLED" -> "❌";
                default          -> "📋";
            };
            sb.append(String.format(
                    "<b>%d.</b> %s %s → %s | %s %s | ⛽ ₱%.2f share\n",
                    i + 1, dirEmoji,
                    HtmlEscapeUtil.escape(r.originHub().name()),
                    HtmlEscapeUtil.escape(r.destinationHub().name()),
                    statusLabel,
                    r.departureTime().atZone(MANILA)
                            .format(DateTimeFormatter.ofPattern("MMM d h:mma")),
                    r.contributionAmount()));

            rows.add(List.of(InlineKeyboardButton.builder()
                    .text(String.format("🔄 #%d — %s → %s",
                            i + 1,
                            r.originHub().name(),
                            r.destinationHub().name()))
                    .callbackData("REPOST_RIDE:" + r.id())
                    .build()));
        }
        rows.add(List.of(InlineKeyboardButton.builder()
                .text("🏠 Menu").callbackData("MAIN_MENU").build()));

        bot.send(flowHelper.sendWithInline(chatId, sb.toString(), rows));
    }

    // ── Driver bookings ───────────────────────────────────────────────────

    public void handleDriverBookings(BotContext ctx) {
        List<BookingResponse> bookings =
                bookingService.getBookingsForDriver(ctx.carpoolUserId());

        if (bookings.isEmpty()) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "📋 <b>Ride Bookings</b>\n\n" +
                            "<i>No passengers have booked your ride yet.</i>"));
            return;
        }

        List<BookingResponse> confirmed = bookings.stream()
                .filter(b -> b.status() == BookingStatus.CONFIRMED).toList();
        List<BookingResponse> pending = bookings.stream()
                .filter(b -> b.status() == BookingStatus.PENDING).toList();

        StringBuilder sb = new StringBuilder("📋 <b>Ride Bookings</b>\n\n");
        sb.append(String.format("✅ Confirmed: <b>%d</b>  ⏳ Pending: <b>%d</b>\n\n",
                confirmed.size(), pending.size()));

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        int index = 1;

        if (!confirmed.isEmpty()) {
            sb.append("─── <b>Confirmed</b> ───\n");
            for (BookingResponse b : confirmed) {
                String paxHandle = b.passenger().telegramHandle() != null
                        ? " (@" + HtmlEscapeUtil.escape(
                        b.passenger().telegramHandle()) + ")" : "";
                sb.append(String.format(
                        "<b>%d.</b> %s%s\n    🪑 %d | ⛽ ₱%.2f share\n",
                        index,
                        HtmlEscapeUtil.escape(b.passenger().fullName()),
                        paxHandle,
                        b.seatsReserved(),
                        b.contributionDue()));
                rows.add(List.of(InlineKeyboardButton.builder()
                        .text("✅ #" + index + " — " + b.passenger().fullName())
                        .callbackData("VIEW_DRIVER_BOOKING:" + b.id())
                        .build()));
                index++;
            }
            sb.append("\n");
        }

        if (!pending.isEmpty()) {
            sb.append("─── <b>Pending Approval</b> ───\n");
            for (BookingResponse b : pending) {
                String paxHandle = b.passenger().telegramHandle() != null
                        ? " (@" + HtmlEscapeUtil.escape(
                        b.passenger().telegramHandle()) + ")" : "";
                long remaining = b.expiresAt() != null
                        ? Duration.between(Instant.now(), b.expiresAt()).toMinutes() : 0;
                sb.append(String.format(
                        "<b>%d.</b> %s%s\n    🪑 %d | ⛽ ₱%.2f share | ⏰ %d min\n",
                        index,
                        HtmlEscapeUtil.escape(b.passenger().fullName()),
                        paxHandle,
                        b.seatsReserved(),
                        b.contributionDue(),
                        Math.max(0, remaining)));
                rows.add(List.of(InlineKeyboardButton.builder()
                        .text("⏳ #" + index + " — " + b.passenger().fullName())
                        .callbackData("VIEW_PENDING:" + b.id())
                        .build()));
                index++;
            }
        }

        rows.add(List.of(BotMessageBuilder.menuButtonRow().get(0)));

        ctx.bot().send(SendMessage.builder()
                .chatId(ctx.chatId())
                .text(sb.toString())
                .parseMode("HTML")
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboard(rows.stream()
                                .map(InlineKeyboardRow::new).toList())
                        .build())
                .build());
    }

    public void handleViewDriverBooking(BotContext ctx) {
        try {
            BookingResponse b = bookingService.getBookingById(ctx.entityId());

            String statusLabel = switch (b.status().name()) {
                case "CONFIRMED"              -> "✅ Confirmed";
                case "PENDING"                -> "⏳ Awaiting your approval";
                case "CANCELLED_BY_PASSENGER" -> "❌ Cancelled by passenger";
                case "COMPLETED"              -> "🏁 Completed";
                default                       -> b.status().name();
            };
            String paymentLabel = switch (b.paymentStatus().name()) {
                case "PAID"           -> "✅ Settled";
                case "PARTIALLY_PAID" -> "⚠️ Partially settled";
                default               -> "❌ Not yet settled";
            };

            String detail = String.format(
                    "👤 <b>Passenger Details</b>\n\nName: <b>%s</b>%s\n" +
                            "🪑 Seats: %d\n⛽ Share: ₱%.2f\n💳 Settlement: %s\n%s📊 Status: %s",
                    HtmlEscapeUtil.escape(b.passenger().fullName()),
                    b.passenger().telegramHandle() != null
                            ? " (@" + HtmlEscapeUtil.escape(
                            b.passenger().telegramHandle()) + ")" : "",
                    b.seatsReserved(),
                    b.contributionDue(),
                    paymentLabel,
                    b.passengerMessage() != null
                            ? "💬 Passenger's note: \"" +
                            HtmlEscapeUtil.escape(b.passengerMessage()) + "\"\n" : "",
                    statusLabel);

            var rows = List.of(List.of(InlineKeyboardButton.builder()
                    .text("◀️ Back to Bookings")
                    .callbackData("RIDE_BOOKINGS:0").build()));

            ctx.bot().send(SendMessage.builder()
                    .chatId(ctx.chatId()).text(detail).parseMode("HTML")
                    .replyMarkup(InlineKeyboardMarkup.builder()
                            .keyboard(rows.stream()
                                    .map(InlineKeyboardRow::new).toList())
                            .build())
                    .build());

        } catch (Exception e) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not load booking details."));
        }
    }

    // ── Accept / Decline ──────────────────────────────────────────────────

    public void handleAcceptBooking(BotContext ctx) {
        try {
            bookingService.acceptBooking(ctx.entityId(), ctx.carpoolUserId());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "✅ <b>Booking Accepted!</b>\n\n" +
                            "The passenger has been notified and their seat is confirmed."));
        } catch (Exception e) {
            log.error("Accept booking failed: bookingId={} userId={} error={}",
                    ctx.entityId(), ctx.carpoolUserId(), e.getMessage());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not accept booking: " +
                            HtmlEscapeUtil.escape(e.getMessage())));
        }
    }

    public void handleDeclineBooking(BotContext ctx) {
        Long bookingId = ctx.entityId();
        var rows = List.of(
                List.of(BotMessageBuilder.button("🚗 Fully booked already",
                        "DECLINE_BOOKING_REASON:" + bookingId + ":FULL", null)),
                List.of(BotMessageBuilder.button("📍 Route change",
                        "DECLINE_BOOKING_REASON:" + bookingId + ":ROUTE_CHANGE", null)),
                List.of(BotMessageBuilder.button("🔧 Vehicle issue",
                        "DECLINE_BOOKING_REASON:" + bookingId + ":VEHICLE_ISSUE", null)),
                List.of(BotMessageBuilder.button("❌ Other reason",
                        "DECLINE_BOOKING_REASON:" + bookingId + ":OTHER", null)),
                List.of(BotMessageBuilder.button("◀️ Back",
                        "VIEW_PENDING:" + bookingId, ButtonStyle.PRIMARY.toString()))
        );
        ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                "❓ <b>Why are you declining this request?</b>", rows));
    }

    public void handleDeclineBookingWithReason(BotContext ctx) {
        if (ctx.parts().length < 3) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Invalid decline request."));
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
            case "FULL"          -> "Already fully booked";
            case "ROUTE_CHANGE"  -> "Route change";
            case "VEHICLE_ISSUE" -> "Vehicle issue";
            default              -> "Other reason";
        };

        try {
            bookingService.declineBooking(bookingId, ctx.carpoolUserId(), reason);
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "❌ <b>Booking Declined</b>\n\n" +
                            "The passenger has been notified and their seat has been released."));
        } catch (Exception e) {
            log.error("Decline booking failed: bookingId={} userId={} error={}",
                    bookingId, ctx.carpoolUserId(), e.getMessage());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not decline booking: " +
                            HtmlEscapeUtil.escape(e.getMessage())));
        }
    }

    // ── Pending requests ──────────────────────────────────────────────────

    public void handlePendingRequests(BotContext ctx) {
        List<BookingResponse> pending =
                bookingService.getPendingRequestsForDriver(ctx.carpoolUserId());

        if (pending.isEmpty()) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⏳ <b>Pending Requests</b>\n\n" +
                            "<i>No pending booking requests.</i>"));
            return;
        }

        StringBuilder sb = new StringBuilder("⏳ <b>Pending Requests (")
                .append(pending.size()).append(")</b>\n\n");
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (int i = 0; i < pending.size(); i++) {
            BookingResponse b = pending.get(i);
            long remaining = b.expiresAt() != null
                    ? Duration.between(Instant.now(), b.expiresAt()).toMinutes() : 0;
            sb.append(String.format(
                    "<b>%d.</b> %s | 🪑 %d | ⛽ ₱%.2f share | ⏰ %d min\n",
                    i + 1,
                    HtmlEscapeUtil.escape(b.passenger().fullName()),
                    b.seatsReserved(),
                    b.contributionDue(),
                    Math.max(0, remaining)));
            rows.add(List.of(InlineKeyboardButton.builder()
                    .text("View #" + (i + 1) + " — " + b.passenger().fullName())
                    .callbackData("VIEW_PENDING:" + b.id())
                    .build()));
        }
        rows.add(List.of(BotMessageBuilder.menuButtonRow().get(0)));

        ctx.bot().send(SendMessage.builder()
                .chatId(ctx.chatId()).text(sb.toString()).parseMode("HTML")
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboard(rows.stream()
                                .map(InlineKeyboardRow::new).toList())
                        .build())
                .build());
    }

    public void handleViewPendingRequest(BotContext ctx) {
        try {
            BookingResponse b = bookingService.getBookingById(ctx.entityId());
            long remaining = b.expiresAt() != null
                    ? Duration.between(Instant.now(), b.expiresAt()).toMinutes() : 0;
            String paxHandle = b.passenger().telegramHandle() != null
                    ? " (@" + HtmlEscapeUtil.escape(
                    b.passenger().telegramHandle()) + ")" : "";

            String detail = String.format(
                    "🔔 <b>Booking Request</b>\n\n👤 <b>%s</b>%s\n" +
                            "🪑 Seats: %d\n⛽ Suggested share: ₱%.2f\n%s⏰ Expires in: %d minutes",
                    HtmlEscapeUtil.escape(b.passenger().fullName()),
                    paxHandle,
                    b.seatsReserved(),
                    b.contributionDue(),
                    b.passengerMessage() != null
                            ? "💬 Message: \"" +
                            HtmlEscapeUtil.escape(b.passengerMessage()) + "\"\n" : "",
                    Math.max(0, remaining));

            var rows = List.of(
                    List.of(
                            BotMessageBuilder.button("✅ Accept",
                                    "ACCEPT_BOOKING:"  + ctx.entityId(), ButtonStyle.SUCCESS.toString()),
                            BotMessageBuilder.button("❌ Decline",
                                    "DECLINE_BOOKING:" + ctx.entityId(), ButtonStyle.DANGER.toString())
                    ),
                    List.of(BotMessageBuilder.button(
                            "◀️ Back to Pending", "PENDING_REQUESTS", ButtonStyle.PRIMARY.toString()))
            );
            ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(), detail, rows));

        } catch (Exception e) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not load booking request."));
        }
    }

    // ── Cancel ride ───────────────────────────────────────────────────────

    public void handleCancelRide(BotContext ctx) {
        Long rideId = ctx.entityId();
        try {
            RideResponse ride = rideService.getRideById(rideId);
            if (ride.status() == RideStatus.DEPARTED) {
                ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                        "⚠️ Your ride has already started.\n\n" +
                                "Please tap <b>Complete Ride</b> when you reach the destination."));
                return;
            }

            List<BookingResponse> activeBookings =
                    bookingService.getActiveBookingsForRide(rideId);

            if (!activeBookings.isEmpty()) {
                StringBuilder sb = new StringBuilder(
                        "⚠️ <b>Are you sure you want to cancel?</b>\n\n");
                sb.append("The following passengers will be notified:\n\n");

                for (int i = 0; i < activeBookings.size(); i++) {
                    BookingResponse b = activeBookings.get(i);
                    String statusIcon = b.status() == BookingStatus.PENDING ? "⏳" : "✅";
                    String paxHandle  = b.passenger().telegramHandle() != null
                            ? " (@" + HtmlEscapeUtil.escape(
                            b.passenger().telegramHandle()) + ")" : "";
                    sb.append(String.format("<b>%d.</b> %s %s%s\n",
                            i + 1, statusIcon,
                            HtmlEscapeUtil.escape(b.passenger().fullName()),
                            paxHandle));
                    if (b.passengerMessage() != null) {
                        sb.append(String.format("    💬 \"%s\"\n",
                                HtmlEscapeUtil.escape(b.passengerMessage())));
                    }
                    sb.append(String.format("    🪑 %d seat(s) | ⛽ ₱%.2f share\n",
                            b.seatsReserved(), b.contributionDue()));
                }
                sb.append("\n⚠️ This cannot be undone.");

                var rows = List.of(
                        List.of(BotMessageBuilder.button("🔧 Vehicle issue",
                                "CONFIRM_CANCEL_RIDE:" + rideId + ":VEHICLE_ISSUE", null)),
                        List.of(BotMessageBuilder.button("📍 Route change",
                                "CONFIRM_CANCEL_RIDE:" + rideId + ":ROUTE_CHANGE", null)),
                        List.of(BotMessageBuilder.button("🏠 Personal reason",
                                "CONFIRM_CANCEL_RIDE:" + rideId + ":PERSONAL", null)),
                        List.of(BotMessageBuilder.button("❌ Other reason",
                                "CONFIRM_CANCEL_RIDE:" + rideId + ":OTHER", null)),
                        List.of(BotMessageBuilder.button("◀️ Keep Ride", "MAIN_MENU", ButtonStyle.PRIMARY.toString()))
                );
                ctx.bot().send(flowHelper.sendWithInline(
                        ctx.chatId(), sb.toString(), rows));
                return;
            }

            executeCancelRide(ctx.chatId(), rideId, ctx.carpoolUserId(), null, ctx.bot());

        } catch (Exception e) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not cancel ride: " +
                            HtmlEscapeUtil.escape(e.getMessage())));
        }
    }

    public void handleConfirmCancelRide(BotContext ctx) {
        if (ctx.parts().length < 3) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Invalid cancel request."));
            return;
        }
        Long rideId;
        try {
            rideId = Long.parseLong(ctx.parts()[1]);
        } catch (NumberFormatException e) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Invalid ride ID."));
            return;
        }

        String reason = switch (ctx.parts()[2]) {
            case "VEHICLE_ISSUE" -> "Vehicle issue";
            case "ROUTE_CHANGE"  -> "Route change";
            case "PERSONAL"      -> "Personal reason";
            default              -> "Other reason";
        };

        executeCancelRide(ctx.chatId(), rideId, ctx.carpoolUserId(), reason, ctx.bot());
    }

    private void executeCancelRide(Long chatId, Long rideId, Long carpoolUserId,
                                   String reason, CarpoolBot bot) {
        try {
            List<BookingResponse> affected =
                    bookingService.getActiveBookingsForRide(rideId);

            rideService.updateRideStatus(rideId,
                    new UpdateRideStatusRequest(RideStatus.CANCELLED),
                    carpoolUserId, reason);
            stateManager.reset(chatId);

            if (affected.isEmpty()) {
                bot.send(flowHelper.sendWithInline(chatId,
                        "✅ <b>Ride Cancelled</b>\n\n" +
                                "No passengers were booked on this ride.",
                        List.of(List.of(
                                BotMessageBuilder.button("🏠 Menu", "MAIN_MENU", ButtonStyle.PRIMARY.toString())))));
            } else {
                StringBuilder sb = new StringBuilder("✅ <b>Ride Cancelled</b>\n\n");
                sb.append(String.format("<b>%d passenger%s notified:</b>\n",
                        affected.size(), affected.size() > 1 ? "s" : ""));
                for (BookingResponse b : affected) {
                    String handle = b.passenger().telegramHandle() != null
                            ? " (@" + HtmlEscapeUtil.escape(
                            b.passenger().telegramHandle()) + ")" : "";
                    sb.append(String.format("• %s%s\n",
                            HtmlEscapeUtil.escape(b.passenger().fullName()), handle));
                }
                if (reason != null) {
                    sb.append("\n📝 Reason: <i>")
                            .append(HtmlEscapeUtil.escape(reason))
                            .append("</i>");
                }
                bot.send(flowHelper.sendWithInline(chatId, sb.toString(),
                        List.of(List.of(
                                BotMessageBuilder.button("🏠 Menu", "MAIN_MENU", ButtonStyle.PRIMARY.toString())))));
            }

        } catch (Exception e) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Could not cancel ride: " +
                            HtmlEscapeUtil.escape(e.getMessage())));
        }
    }

    // ── Depart / Complete ─────────────────────────────────────────────────

    public void handleDepartRide(BotContext ctx) {
        try {
            RideResponse ride      = rideService.getRideById(ctx.entityId());
            LocalDateTime departure = ride.departureTime();
            LocalDateTime now       = LocalDateTime.now(MANILA);
            LocalDateTime earliest  = departure.minusHours(1);

            if (ride.status() != RideStatus.ACTIVE && ride.status() != RideStatus.FULL) {
                ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                        "⚠️ This ride cannot be started.\n\n" +
                                "Only ACTIVE or FULL rides can be departed."));
                return;
            }

            if (now.isBefore(earliest)) {
                String formatted = departure.format(
                        DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a"));
                long hoursAway = Duration.between(now, departure).toHours();
                long minsAway  = Duration.between(now, departure).toMinutesPart();
                String timeAway = hoursAway > 0
                        ? hoursAway + "h " + minsAway + "m away"
                        : minsAway + " minutes away";

                ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                        "⚠️ <b>Too early to start this ride.</b>\n\n" +
                                "Your ride is scheduled for <b>" + formatted +
                                "</b> (" + timeAway + ").\n\n" +
                                "You can start the ride up to <b>1 hour before</b> departure."));
                return;
            }

            rideService.updateRideStatus(ctx.entityId(),
                    new UpdateRideStatusRequest(RideStatus.DEPARTED),
                    ctx.carpoolUserId());
            stateManager.reset(ctx.chatId());

            ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                    "🚀 <b>Ride Started!</b>\n\nYour ride is now in progress.\n" +
                            "Tap <b>Complete Ride</b> when you reach the destination.",
                    List.of(List.of(
                            BotMessageBuilder.button("✅ Complete Ride",
                                    "COMPLETE_RIDE:" + ctx.entityId(), ButtonStyle.SUCCESS.toString()),
                            BotMessageBuilder.button("📋 View Bookings", "DRIVER_BOOKINGS", ButtonStyle.PRIMARY.toString())
                    ))));

        } catch (Exception e) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not start ride: " +
                            HtmlEscapeUtil.escape(e.getMessage())));
        }
    }

    public void handleCompleteRide(BotContext ctx) {
        try {
            rideService.updateRideStatus(ctx.entityId(),
                    new UpdateRideStatusRequest(RideStatus.COMPLETED),
                    ctx.carpoolUserId());
            stateManager.reset(ctx.chatId());

            ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                    "✅ <b>Ride Completed!</b>\n\n" +
                            "Thank you for driving! All passengers have been notified.\n\n" +
                            "Please collect gas share contributions from your passengers.",
                    List.of(List.of(
                            BotMessageBuilder.button("🚗 My Rides", "MY_RIDES",  ButtonStyle.PRIMARY.toString()),
                            BotMessageBuilder.button("🏠 Menu",     "MAIN_MENU", null)
                    ))));

        } catch (Exception e) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not complete ride: " +
                            HtmlEscapeUtil.escape(e.getMessage())));
        }
    }
}