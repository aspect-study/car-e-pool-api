package com.carpool.bot.handler;

import com.carpool.bot.CarpoolBot;
import com.carpool.bot.handler.context.BotContext;
import com.carpool.bot.handler.helper.BotFlowHelper;
import com.carpool.bot.state.BotFlow;
import com.carpool.bot.state.StateManager;
import com.carpool.bot.util.BotMessageBuilder;
import com.carpool.bot.util.BotTimePickerUtil;
import com.carpool.bot.util.ButtonStyle;
import com.carpool.common.exception.InvalidRideStateException;
import com.carpool.common.exception.NotRideOwnerException;
import com.carpool.common.util.HtmlEscapeUtil;
import com.carpool.domain.enums.BookingStatus;
import com.carpool.domain.enums.RideDirection;
import com.carpool.domain.enums.RideStatus;
import com.carpool.service.booking.BookingService;
import com.carpool.service.dto.request.UpdateRideStatusRequest;
import com.carpool.service.dto.response.BookingResponse;
import com.carpool.service.dto.response.RideResponse;
import com.carpool.service.profile.ProfileService;
import com.carpool.service.rating.RatingService;
import com.carpool.service.ride.RideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
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
    private final ProfileService profileService;
    private final RatingService  ratingService;
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
                    """
                            🚗 <b>My Rides</b>
                            
                            <i>No completed or cancelled rides yet.</i>"""));
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
                    """
                            📋 <b>Ride Bookings</b>
                            
                            <i>No passengers have booked your ride yet.</i>"""));
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
                sb.append(String.format(
                        "<b>%d.</b> %s%s\n    🪑 %d | ⛽ ₱%.2f share\n",
                        index,
                        HtmlEscapeUtil.escape(b.passenger().fullName()),
                        paxHandle,
                        b.seatsReserved(),
                        b.contributionDue()));
                rows.add(List.of(InlineKeyboardButton.builder()
                        .text("⏳ #" + index + " — " + b.passenger().fullName())
                        .callbackData("VIEW_PENDING:" + b.id())
                        .build()));
                index++;
            }
        }

        rows.add(List.of(BotMessageBuilder.menuButtonRow().getFirst()));

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
            BookingResponse b = bookingService.getBookingById(ctx.entityId(), ctx.carpoolUserId());

            String badge = "";
            try {
                var stats  = profileService.getProfileStats(b.passenger().id());
                var rating = ratingService.getPassengerRatingLabel(b.passenger().id());
                badge = "\n" + BotMessageBuilder.buildPassengerBadge(stats, rating);
            } catch (Exception e) {
                log.warn("Could not load passenger profile for booking {}", ctx.entityId());
            }

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
                    """
                            👤 <b>Passenger Details</b>

                            Name: <b>%s</b>%s%s
                            🪑 Seats: %d
                            ⛽ Share: ₱%.2f
                            💳 Settlement: %s
                            %s📊 Status: %s""",
                    HtmlEscapeUtil.escape(b.passenger().fullName()),
                    b.passenger().telegramHandle() != null
                            ? " (@" + HtmlEscapeUtil.escape(
                            b.passenger().telegramHandle()) + ")" : "",
                    badge,
                    b.seatsReserved(),
                    b.contributionDue(),
                    paymentLabel,
                    b.passengerMessage() != null
                            ? "💬 Passenger's note: \"" +
                            HtmlEscapeUtil.escape(b.passengerMessage()) + "\"\n" : "",
                    statusLabel);

            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            if (b.status() == BookingStatus.CONFIRMED) {
                rows.add(List.of(BotMessageBuilder.button(
                        "🚫 Remove Passenger",
                        "REMOVE_PASSENGER:" + ctx.entityId(), ButtonStyle.DANGER.toString())));
            }
            rows.add(List.of(InlineKeyboardButton.builder()
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
        Long bookingId = ctx.entityId();
        Long driverId  = ctx.carpoolUserId();
        try {
            var result = bookingService.acceptBooking(bookingId, driverId);
            log.info("Booking accepted: bookingId={} driverId={} passengerId={}",
                    bookingId, driverId, result.passenger().id());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    """
                            ✅ <b>Booking Accepted!</b>

                            The passenger has been notified and their seat is confirmed."""));
        } catch (InvalidRideStateException e) {
            // Driver tapped Accept twice — booking is already confirmed, not an error
            log.warn("Duplicate accept attempt ignored: bookingId={} driverId={}",
                    bookingId, driverId);
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "✅ This booking has already been accepted."));
        } catch (Exception e) {
            log.error("Accept booking failed: bookingId={} driverId={} error={}",
                    bookingId, driverId, e.getMessage());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not accept booking. Please try again."));
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
                    """
                            ❌ <b>Booking Declined</b>
                            
                            The passenger has been notified and their seat has been released."""));
        } catch (Exception e) {
            log.error("Decline booking failed: bookingId={} userId={} error={}",
                    bookingId, ctx.carpoolUserId(), e.getMessage());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not decline booking. Please try again."));
        }
    }

    // ── Pending requests ──────────────────────────────────────────────────

    public void handlePendingRequests(BotContext ctx) {
        List<BookingResponse> pending =
                bookingService.getPendingRequestsForDriver(ctx.carpoolUserId());

        if (pending.isEmpty()) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    """
                            ⏳ <b>Pending Requests</b>
                            
                            <i>No pending booking requests.</i>"""));
            return;
        }

        StringBuilder sb = new StringBuilder("⏳ <b>Pending Requests (")
                .append(pending.size()).append(")</b>\n\n");
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (int i = 0; i < pending.size(); i++) {
            BookingResponse b = pending.get(i);
            sb.append(String.format(
                    "<b>%d.</b> %s | 🪑 %d | ⛽ ₱%.2f share\n",
                    i + 1,
                    HtmlEscapeUtil.escape(b.passenger().fullName()),
                    b.seatsReserved(),
                    b.contributionDue()));
            rows.add(List.of(InlineKeyboardButton.builder()
                    .text("View #" + (i + 1) + " — " + b.passenger().fullName())
                    .callbackData("VIEW_PENDING:" + b.id())
                    .build()));
        }
        rows.add(List.of(BotMessageBuilder.menuButtonRow().getFirst()));

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
            BookingResponse b = bookingService.getBookingById(ctx.entityId(), ctx.carpoolUserId());
            String paxHandle = b.passenger().telegramHandle() != null
                    ? " (@" + HtmlEscapeUtil.escape(
                    b.passenger().telegramHandle()) + ")" : "";

            String badge = "";
            try {
                var stats  = profileService.getProfileStats(b.passenger().id());
                var rating = ratingService.getPassengerRatingLabel(b.passenger().id());
                badge = "\n" + BotMessageBuilder.buildPassengerBadge(stats, rating);
            } catch (Exception e) {
                log.warn("Could not load passenger profile for booking {}", ctx.entityId());
            }

            String detail = String.format(
                    """
                            🔔 <b>Booking Request</b>

                            👤 <b>%s</b>%s%s
                            🪑 Seats: %d
                            ⛽ Suggested share: ₱%.2f
                            %s""",
                    HtmlEscapeUtil.escape(b.passenger().fullName()),
                    paxHandle,
                    badge,
                    b.seatsReserved(),
                    b.contributionDue(),
                    b.passengerMessage() != null
                            ? "💬 Message: \"" +
                            HtmlEscapeUtil.escape(b.passengerMessage()) + "\"" : "");

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
                        """
                                ⚠️ Your ride has already started.
                                
                                Please tap <b>Complete Ride</b> when you reach the destination."""));
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
                    "⚠️ Could not cancel ride. Please try again."));
        }
    }

    public void handleConfirmCancelRide(BotContext ctx) {
        if (ctx.parts().length < 3) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Invalid cancel request."));
            return;
        }
        long rideId;
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
                        """
                                ✅ <b>Ride Cancelled</b>
                                
                                No passengers were booked on this ride.""",
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
                    "⚠️ Could not cancel ride. Please try again."));
        }
    }

    // ── Remove confirmed passenger ────────────────────────────────────────

    public void handleRemovePassenger(BotContext ctx) {
        try {
            BookingResponse b = bookingService.getBookingById(ctx.entityId(), ctx.carpoolUserId());
            if (b.status() != BookingStatus.CONFIRMED) {
                ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                        "⚠️ Only confirmed bookings can be removed."));
                return;
            }
            String paxHandle = b.passenger().telegramHandle() != null
                    ? " (@" + HtmlEscapeUtil.escape(b.passenger().telegramHandle()) + ")" : "";
            String msg = String.format(
                    """
                            ⚠️ <b>Remove Passenger?</b>

                            👤 <b>%s</b>%s
                            🪑 %d seat(s) | ⛽ ₱%.2f share

                            This passenger will be notified and their seat(s) freed. This cannot be undone.""",
                    HtmlEscapeUtil.escape(b.passenger().fullName()),
                    paxHandle,
                    b.seatsReserved(),
                    b.contributionDue());
            var rows = List.of(
                    List.of(
                            BotMessageBuilder.button("✅ Yes, Remove",
                                    "CONFIRM_REMOVE_PASSENGER:" + ctx.entityId(), ButtonStyle.DANGER.toString()),
                            BotMessageBuilder.button("◀️ Cancel",
                                    "VIEW_DRIVER_BOOKING:" + ctx.entityId(), ButtonStyle.PRIMARY.toString())
                    )
            );
            ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(), msg, rows));
        } catch (Exception e) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(), "⚠️ Could not load booking."));
        }
    }

    public void handleConfirmRemovePassenger(BotContext ctx) {
        try {
            bookingService.cancelBookingByDriver(ctx.entityId(), ctx.carpoolUserId());
            ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                    """
                            ✅ <b>Passenger Removed</b>

                            The passenger has been notified and their seat(s) have been freed.
                            Your ride announcement has been updated.""",
                    List.of(List.of(
                            BotMessageBuilder.button("👥 My Passengers", "DRIVER_BOOKINGS", ButtonStyle.PRIMARY.toString()),
                            BotMessageBuilder.button("🏠 Menu", "MAIN_MENU", ButtonStyle.PRIMARY.toString())
                    ))));
        } catch (Exception e) {
            log.error("Remove passenger failed: bookingId={} driverId={} error={}",
                    ctx.entityId(), ctx.carpoolUserId(), e.getMessage());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not remove passenger. Please try again."));
        }
    }

    // ── Depart / Complete ─────────────────────────────────────────────────

    public void handleDepartRide(BotContext ctx) {
        try {
            RideResponse ride      = rideService.getRideById(ctx.entityId());
            LocalDateTime departure = ride.departureTime();
            LocalDateTime now       = LocalDateTime.now(MANILA);
            LocalDateTime earliest  = departure.minusHours(1);

            if (!ride.driver().id().equals(ctx.carpoolUserId())) {
                ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                        "⚠️ This is not your ride."));
                return;
            }

            if (ride.status() != RideStatus.ACTIVE && ride.status() != RideStatus.FULL) {
                ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                        """
                                ⚠️ This ride cannot be started.
                                
                                Only ACTIVE or FULL rides can be departed."""));
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
                    """
                            🚀 <b>Ride Started!</b>
                            
                            Your ride is now in progress.
                            Tap <b>Complete Ride</b> when you reach the destination.""",
                    List.of(List.of(
                            BotMessageBuilder.button("✅ Complete Ride",
                                    "COMPLETE_RIDE:" + ctx.entityId(), ButtonStyle.SUCCESS.toString()),
                            BotMessageBuilder.button("👥 My Passengers", "DRIVER_BOOKINGS", ButtonStyle.PRIMARY.toString())
                    ))));

        } catch (Exception e) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not start ride. Please try again."));
        }
    }

    // ── Edit ride departure time ───────────────────────────────────────────

    public void handleEditRideTime(BotContext ctx) {
        Long rideId = ctx.entityId();
        try {
            var ride = rideService.getRideById(rideId);
            if (!ride.driver().id().equals(ctx.carpoolUserId())) {
                ctx.bot().send(BotMessageBuilder.text(ctx.chatId(), "⚠️ This is not your ride."));
                return;
            }
            if (ride.status() != RideStatus.ACTIVE && ride.status() != RideStatus.FULL) {
                ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                        "⚠️ Only ACTIVE or FULL rides can have their departure time updated."));
                return;
            }
            stateManager.save(ctx.chatId(), ctx.state()
                    .withFlow(BotFlow.EDIT_RIDE_TIME_SELECT_DATE)
                    .withSelectedRideId(rideId)
                    .withDirection(ride.direction())
                    .withCalendarMonth(YearMonth.now(MANILA)));
            flowHelper.showCalendar(ctx.chatId(), ctx.messageId(),
                    YearMonth.now(MANILA), "CAL_DATE_EDIT_TIME", "CAL_NAV_EDIT_TIME", ctx.bot());
        } catch (Exception e) {
            log.error("Edit ride time error: rideId={} userId={} error={}",
                    rideId, ctx.carpoolUserId(), e.getMessage(), e);
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not load ride. Please try again."));
        }
    }

    public void handleEditRideTimeCalendarNav(BotContext ctx) {
        YearMonth current = ctx.state().getCalendarMonth() != null
                ? ctx.state().getCalendarMonth()
                : YearMonth.now(MANILA);

        YearMonth updated = "PREV".equals(ctx.payload())
                ? current.minusMonths(1)
                : current.plusMonths(1);

        stateManager.save(ctx.chatId(), ctx.state()
                .withCalendarMonth(updated)
                .withFlow(BotFlow.EDIT_RIDE_TIME_SELECT_DATE));

        flowHelper.showCalendar(ctx.chatId(), ctx.messageId(), updated,
                "CAL_DATE_EDIT_TIME", "CAL_NAV_EDIT_TIME", ctx.bot());
    }

    public void handleEditRideTimeDateSelected(BotContext ctx) {
        try {
            LocalDate selected = LocalDate.parse(ctx.payload());
            int windowStart = BotTimePickerUtil.adjustWindowForToday(
                    BotTimePickerUtil.defaultWindowStart(ctx.state().getDirection(), selected),
                    selected);

            stateManager.save(ctx.chatId(), ctx.state()
                    .withEditTimeSelectedDate(selected)
                    .withTimeWindowStart(windowStart)
                    .withFlow(BotFlow.EDIT_RIDE_TIME_PICK));

            flowHelper.showTimePicker(ctx.chatId(), ctx.messageId(), windowStart, selected,
                    "RIDE_TIME_EDIT", "TIME_NAV_EDIT", ctx.bot());
        } catch (Exception e) {
            log.error("Edit ride date selected error: userId={} error={}",
                    ctx.carpoolUserId(), e.getMessage(), e);
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not process date. Please try again."));
        }
    }

    public void handleEditRideTimePickerNav(BotContext ctx) {
        LocalDate selectedDate = ctx.state().getEditTimeSelectedDate() != null
                ? ctx.state().getEditTimeSelectedDate()
                : LocalDate.now(MANILA);

        int current = ctx.state().getTimeWindowStart() != null
                ? ctx.state().getTimeWindowStart()
                : BotTimePickerUtil.adjustWindowForToday(
                      BotTimePickerUtil.defaultWindowStart(ctx.state().getDirection(), selectedDate),
                      selectedDate);

        int updated = "EARLIER".equals(ctx.payload())
                ? Math.max(0, current - BotTimePickerUtil.PAGE_SIZE_MIN)
                : Math.min(1200, current + BotTimePickerUtil.PAGE_SIZE_MIN);

        int adjusted = BotTimePickerUtil.adjustWindowForToday(updated, selectedDate);

        stateManager.save(ctx.chatId(), ctx.state().withTimeWindowStart(adjusted));

        flowHelper.showTimePicker(ctx.chatId(), ctx.messageId(), adjusted, selectedDate,
                "RIDE_TIME_EDIT", "TIME_NAV_EDIT", ctx.bot());
    }

    public void handleEditRideTimeSelected(BotContext ctx) {
        try {
            if (ctx.parts().length < 3) {
                ctx.bot().send(BotMessageBuilder.text(ctx.chatId(), "⚠️ Invalid time selection."));
                return;
            }
            int hour   = Integer.parseInt(ctx.parts()[1]);
            int minute = Integer.parseInt(ctx.parts()[2]);

            LocalDate date  = ctx.state().getEditTimeSelectedDate();
            Long rideId     = ctx.state().getSelectedRideId();

            if (date == null || rideId == null) {
                ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                        "⏳ <b>Session expired.</b>\n\nPlease tap <b>✏️ Edit Time</b> again."));
                return;
            }

            LocalDateTime newTime = date.atTime(hour, minute);
            var ride = rideService.getRideById(rideId);

            String currentFormatted = ride.departureTime()
                    .atZone(MANILA).format(DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a"));
            String newFormatted = newTime.atZone(MANILA)
                    .format(DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a"));
            String origin = ride.originHub() != null ? ride.originHub().name() : "Origin";
            String dest   = ride.destinationHub() != null ? ride.destinationHub().name() : "Destination";

            stateManager.save(ctx.chatId(), ctx.state()
                    .withEditTimePendingDateTime(newTime)
                    .withFlow(BotFlow.EDIT_RIDE_TIME_CONFIRM));

            ctx.bot().edit(EditMessageText.builder()
                    .chatId(ctx.chatId())
                    .messageId(ctx.messageId())
                    .text(String.format("""
                            ✏️ <b>Confirm Departure Time Update</b>

                            📍 <b>%s → %s</b>

                            🕐 <b>Current:</b> %s
                            ✅ <b>New:</b>     %s

                            All confirmed passengers will be notified of this change.""",
                            origin, dest, currentFormatted, newFormatted))
                    .parseMode("HTML")
                    .replyMarkup(BotMessageBuilder.inlineButtons(List.of(List.of(
                            BotMessageBuilder.button("✅ Confirm Update", "CONFIRM_EDIT_RIDE_TIME",
                                    ButtonStyle.SUCCESS.toString()),
                            BotMessageBuilder.button("❌ Cancel", "MAIN_MENU", null)
                    ))))
                    .build());

        } catch (Exception e) {
            log.error("Edit ride time selection error: userId={} error={}",
                    ctx.carpoolUserId(), e.getMessage(), e);
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not load ride. Please try again."));
        }
    }

    public void handleConfirmEditRideTime(BotContext ctx) {
        try {
            LocalDateTime pendingTime = ctx.state().getEditTimePendingDateTime();
            Long rideId               = ctx.state().getSelectedRideId();

            if (pendingTime == null || rideId == null) {
                ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                        "⏳ <b>Session expired.</b>\n\nPlease tap <b>✏️ Edit Time</b> again."));
                return;
            }

            rideService.updateDepartureTime(rideId, pendingTime, ctx.carpoolUserId());
            stateManager.reset(ctx.chatId());

            String formatted = pendingTime.atZone(MANILA)
                    .format(DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a"));
            ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                    String.format("""
                            ✅ <b>Departure Time Updated!</b>

                            New departure time: <b>%s</b>

                            All confirmed passengers have been notified.""", formatted),
                    List.of(List.of(
                            BotMessageBuilder.button("👥 My Passengers", "DRIVER_BOOKINGS",
                                    ButtonStyle.PRIMARY.toString()),
                            BotMessageBuilder.button("🏠 Menu", "MAIN_MENU", null)
                    ))));

        } catch (InvalidRideStateException e) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(), "⚠️ " + e.getMessage()));
        } catch (NotRideOwnerException e) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(), "⚠️ This is not your ride."));
        } catch (Exception e) {
            log.error("Confirm edit ride time error: userId={} error={}",
                    ctx.carpoolUserId(), e.getMessage(), e);
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not update departure time. Please try again."));
        }
    }

    public void handleCompleteRide(BotContext ctx) {
        try {
            rideService.updateRideStatus(ctx.entityId(),
                    new UpdateRideStatusRequest(RideStatus.COMPLETED),
                    ctx.carpoolUserId());
            stateManager.reset(ctx.chatId());

            ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                    """
                            ✅ <b>Ride Completed!</b>

                            Thank you for driving! All passengers have been notified.

                            Please collect gas share contributions from your passengers.

                            Would you like to post another ride?""",
                    List.of(List.of(
                            BotMessageBuilder.button("🚗 Yes, Post New Ride", "POST_RIDE", ButtonStyle.SUCCESS.toString()),
                            BotMessageBuilder.button("❌ No, Thanks",          "MAIN_MENU", ButtonStyle.DANGER.toString())
                    ))));

        } catch (NotRideOwnerException e) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ This is not your ride."));
        } catch (Exception e) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not complete ride. Please try again."));
        }
    }
}