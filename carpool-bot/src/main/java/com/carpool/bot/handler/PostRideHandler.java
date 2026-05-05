package com.carpool.bot.handler;

import com.carpool.bot.CarpoolBot;
import com.carpool.bot.state.BotFlow;
import com.carpool.bot.state.StateManager;
import com.carpool.bot.state.UserState;
import com.carpool.bot.util.BotMessageBuilder;
import com.carpool.bot.util.HubMatcher;
import com.carpool.common.util.HtmlEscapeUtil;
import com.carpool.domain.entity.DriverNote;
import com.carpool.domain.enums.RideDirection;
import com.carpool.domain.enums.RideStatus;
import com.carpool.domain.enums.UserRole;
import com.carpool.repository.UserRepository;
import com.carpool.service.dto.request.CreateRideRequest;
import com.carpool.service.dto.request.UpdateRideStatusRequest;
import com.carpool.service.dto.response.HubResponse;
import com.carpool.service.dto.response.RideResponse;
import com.carpool.service.hub.HubService;
import com.carpool.service.note.DriverNoteService;
import com.carpool.service.ride.RideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles the entire post ride creation sub-flow.
 * Covers: POST_RIDE start, direction, ETD, origin hub, destination hub,
 * seats, contribution, notes, note write, vehicle confirm, ride confirm/cancel,
 * hub selection callbacks, retype origin/dest, and repost ride.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostRideHandler {

    private final StateManager      stateManager;
    private final UserRepository    userRepository;
    private final RideService       rideService;
    private final HubService        hubService;
    private final HubMatcher        hubMatcher;
    private final DriverNoteService driverNoteService;
    private final ProfileHandler    profileHandler;
    private final PostRideHelper    postRideHelper;
    private final BotFlowHelper     flowHelper;

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    // ── Start post ride ───────────────────────────────────────────────────

    public void handleStartPostRide(BotContext ctx) {
        if (ctx.state().getDirection() == null) {
            ctx.bot().send(BotMessageBuilder.directionSelector(ctx.chatId(),
                    "🚗 <b>Post a Ride</b>\n\nWhich direction is this ride?"));
            stateManager.save(ctx.chatId(), ctx.state()
                    .withCarpoolUserId(ctx.carpoolUserId())
                    .withFlow(BotFlow.POST_RIDE_DIRECTION));
            return;
        }
        askForEtd(ctx.chatId(), ctx.state(), ctx.bot());
    }

    public void askForEtd(Long chatId, UserState state, CarpoolBot bot) {
        stateManager.save(chatId, state.withFlow(BotFlow.POST_RIDE_DEPARTURE_TIME));
        bot.send(BotMessageBuilder.textWithCancel(chatId,
                "🕐 <b>What time are you leaving? (Start pickup time)</b>\n\n" +
                        "Format: <code>MM/DD HH:MM</code>\n" +
                        "Example: <code>" +
                        flowHelper.etdExample(state.getDirection()) + "</code>"));
    }

    // ── Departure time input ──────────────────────────────────────────────

    public void handlePostRideEtd(Long chatId, String text,
                                  UserState state, CarpoolBot bot) {
        try {
            LocalDateTime manila = LocalDateTime.now(MANILA);
            LocalDateTime etd = LocalDateTime.parse(
                    manila.getYear() + "/" + text.trim(),
                    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));

            if (etd.isBefore(manila)) {
                boolean isRepost = state.getOriginHubId() != null
                        && state.getDestinationHubId() != null;
                bot.send(BotMessageBuilder.textWithCancel(chatId,
                        "⚠️ That time has already passed.\n\n" +
                                (isRepost
                                        ? "Please enter a future departure time " +
                                          "for your reposted ride:\n"
                                        : "Please enter a future departure time:\n") +
                                "Example: <code>" +
                                flowHelper.etdExample(state.getDirection()) + "</code>"));
                return;
            }

            // Repost flow — hubs already set, skip to confirmation
            if (state.getOriginHubId() != null && state.getDestinationHubId() != null) {
                UserState updated = state
                        .withDepartureTime(etd)
                        .withFlow(BotFlow.POST_RIDE_CONFIRM);
                stateManager.save(chatId, updated);
                postRideHelper.showConfirmation(chatId, updated, bot);
                return;
            }

            stateManager.save(chatId, state
                    .withDepartureTime(etd)
                    .withFlow(BotFlow.POST_RIDE_ORIGIN));

            String originExample = state.getDirection() == RideDirection.HOME_TO_WORK
                    ? "SM Southmall" : "BGC";
            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "📍 <b>Where does your ride start?</b>\n\n" +
                            "Type a nearby landmark as your pickup point.\n" +
                            "Example: <code>" + originExample + "</code>"));

        } catch (DateTimeParseException e) {
            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "⚠️ Invalid format. Please use <code>MM/DD HH:MM</code>\n" +
                            "Example: <code>" +
                            flowHelper.etdExample(state.getDirection()) + "</code>"));
        }
    }

    // ── Origin hub text input ─────────────────────────────────────────────

    public void handlePostRideOrigin(Long chatId, String text,
                                     UserState state, CarpoolBot bot) {
        if (text.trim().length() < 3) {
            String hint = state.getDirection() == RideDirection.HOME_TO_WORK
                    ? "SM Southmall" : "BGC";
            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "⚠️ Please type at least 3 characters to search.\n\n" +
                            "Example: <code>" + hint + "</code>"));
            return;
        }

        List<HubResponse> suggestions = hubMatcher.suggest(text);

        if (suggestions.isEmpty()) {
            List<HubResponse> recentHubs =
                    hubService.getRecentHubsForUser(state.getCarpoolUserId());
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            for (HubResponse h : recentHubs) {
                rows.add(List.of(BotMessageBuilder.button(
                        "🕐 " + h.name(), "HUB_ORIGIN:" + h.id())));
            }
            rows.add(List.of(BotMessageBuilder.button(
                    "✏️ Try different name", "RETYPE_ORIGIN")));
            bot.send(flowHelper.sendWithInline(chatId,
                    "⚠️ Couldn't find <b>\"" +
                            HtmlEscapeUtil.escape(text) + "\"</b>.\n\n" +
                            (recentHubs.isEmpty()
                                    ? "Try a more specific name or nearby landmark:"
                                    : "Here are your recent locations:"),
                    rows));
            return;
        }

        bot.send(flowHelper.sendWithInline(chatId,
                "📍 <b>Select your start point:</b>\n\nResults for \"" +
                        HtmlEscapeUtil.escape(text) + "\":",
                buildHubButtonRows(suggestions, "HUB_ORIGIN", "RETYPE_ORIGIN")));
    }

    // ── Destination hub text input ────────────────────────────────────────

    public void handlePostRideDestination(Long chatId, String text,
                                          UserState state, CarpoolBot bot) {
        if (text.trim().length() < 3) {
            String hint = state.getDirection() == RideDirection.HOME_TO_WORK
                    ? "BGC" : "SM Southmall";
            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "⚠️ Please type at least 3 characters to search.\n\n" +
                            "Example: <code>" + hint + "</code>"));
            return;
        }

        List<HubResponse> suggestions = hubMatcher.suggest(text);

        if (suggestions.isEmpty()) {
            List<HubResponse> recentHubs =
                    hubService.getRecentHubsForUser(state.getCarpoolUserId());
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            for (HubResponse h : recentHubs) {
                rows.add(List.of(BotMessageBuilder.button(
                        "🕐 " + h.name(), "HUB_DEST:" + h.id())));
            }
            rows.add(List.of(BotMessageBuilder.button(
                    "✏️ Try different name", "RETYPE_DEST")));
            bot.send(flowHelper.sendWithInline(chatId,
                    "⚠️ Couldn't find <b>\"" +
                            HtmlEscapeUtil.escape(text) + "\"</b>.\n\n" +
                            (recentHubs.isEmpty()
                                    ? "Try a more specific name or nearby landmark:"
                                    : "Here are your recent locations:"),
                    rows));
            return;
        }

        // Remove origin hub from destination suggestions
        List<HubResponse> filtered = suggestions.stream()
                .filter(s -> !s.id().equals(state.getOriginHubId()))
                .toList();

        if (filtered.isEmpty()) {
            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "⚠️ Destination cannot be the same as pickup. Try again:"));
            return;
        }

        bot.send(flowHelper.sendWithInline(chatId,
                "🏁 <b>Select your end point:</b>\n\nResults for \"" +
                        HtmlEscapeUtil.escape(text) + "\":",
                buildHubButtonRows(filtered, "HUB_DEST", "RETYPE_DEST")));
    }

    /**
     * Builds hub button rows — 2 columns for short names (≤20 chars),
     * 1 column for long names.
     */
    private List<List<InlineKeyboardButton>> buildHubButtonRows(
            List<HubResponse> hubs, String callbackPrefix, String retypeAction) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> pair = new ArrayList<>();

        for (HubResponse s : hubs) {
            InlineKeyboardButton btn = BotMessageBuilder.button(
                    s.name(), callbackPrefix + ":" + s.id());
            if (s.name().length() > 20) {
                if (!pair.isEmpty()) {
                    rows.add(new ArrayList<>(pair));
                    pair = new ArrayList<>();
                }
                rows.add(List.of(btn));
            } else {
                pair.add(btn);
                if (pair.size() == 2) {
                    rows.add(List.of(pair.get(0), pair.get(1)));
                    pair = new ArrayList<>();
                }
            }
        }
        if (!pair.isEmpty()) rows.add(new ArrayList<>(pair));
        rows.add(List.of(BotMessageBuilder.button("✏️ Type again", retypeAction)));
        return rows;
    }

    // ── Hub callback selections ───────────────────────────────────────────

    public void handleHubOriginSelected(BotContext ctx) {
        try {
            var hub = rideService.getHubById(ctx.entityId());
            UserState updated = ctx.state()
                    .withOriginHubId(hub.id())
                    .withOriginHubName(hub.name())
                    .withFlow(BotFlow.POST_RIDE_DESTINATION);
            stateManager.save(ctx.chatId(), updated);

            String destExample = ctx.state().getDirection() == RideDirection.HOME_TO_WORK
                    ? "BGC" : "SM Southmall";
            ctx.bot().send(BotMessageBuilder.textWithCancel(ctx.chatId(),
                    "✅ Start point: <b>" +
                            HtmlEscapeUtil.escape(hub.name()) + "</b>\n\n" +
                            "🏁 <b>Where does your ride end?</b>\n\n" +
                            "Type a nearby landmark as your drop-off point.\n" +
                            "Example: <code>" + destExample + "</code>"));
        } catch (Exception e) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not load hub. Please try again."));
        }
    }

    public void handleHubDestSelected(BotContext ctx) {
        try {
            var hub = rideService.getHubById(ctx.entityId());
            if (hub.id().equals(ctx.state().getOriginHubId())) {
                ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                        "⚠️ Destination cannot be the same as pickup. Try again:"));
                return;
            }
            UserState updated = ctx.state()
                    .withDestinationHubId(hub.id())
                    .withDestinationHubName(hub.name())
                    .withFlow(BotFlow.POST_RIDE_SEATS);
            stateManager.save(ctx.chatId(), updated);

            ctx.bot().send(BotMessageBuilder.textWithCancel(ctx.chatId(),
                    "✅ End point: <b>" +
                            HtmlEscapeUtil.escape(hub.name()) + "</b>\n\n" +
                            "🪑 <b>How many passengers can you take?</b> (1-8)"));
        } catch (Exception e) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not load hub. Please try again."));
        }
    }

    public void handleRetypeOrigin(BotContext ctx) {
        stateManager.save(ctx.chatId(),
                ctx.state().withFlow(BotFlow.POST_RIDE_ORIGIN));
        String example = ctx.state().getDirection() == RideDirection.HOME_TO_WORK
                ? "SM Southmall" : "BGC";
        ctx.bot().send(BotMessageBuilder.textWithCancel(ctx.chatId(),
                "📍 <b>Where does your ride start?</b>\n\n" +
                        "Type a nearby landmark as your pickup point.\n" +
                        "Example: <code>" + example + "</code>"));
    }

    public void handleRetypeDest(BotContext ctx) {
        stateManager.save(ctx.chatId(),
                ctx.state().withFlow(BotFlow.POST_RIDE_DESTINATION));
        String example = ctx.state().getDirection() == RideDirection.HOME_TO_WORK
                ? "BGC" : "SM Southmall";
        ctx.bot().send(BotMessageBuilder.textWithCancel(ctx.chatId(),
                "🏁 <b>Where does your ride end?</b>\n\n" +
                        "Type a nearby landmark as your drop-off point.\n" +
                        "Example: <code>" + example + "</code>"));
    }

    // ── Seats / Contribution ──────────────────────────────────────────────

    public void handlePostRideSeats(Long chatId, String text,
                                    UserState state, CarpoolBot bot) {
        try {
            int seats = Integer.parseInt(text.trim());
            if (seats < 1 || seats > 8) {
                bot.send(BotMessageBuilder.text(chatId,
                        "⚠️ Please enter a number between 1 and 8:"));
                return;
            }
            stateManager.save(chatId, state
                    .withSeats(seats)
                    .withFlow(BotFlow.POST_RIDE_CONTRIBUTION));
            bot.send(BotMessageBuilder.text(chatId,
                    "✅ Passenger slots: <b>" + seats + "</b>\n\n" +
                            "⛽ <b>What's the suggested gas share per seat?</b>\n" +
                            "Enter <code>0</code> if it's a free ride.\n" +
                            "Example: <code>50</code>"));
        } catch (NumberFormatException e) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Please enter a valid number (1-8):"));
        }
    }

    public void handlePostRideContribution(Long chatId, String text,
                                           UserState state, Long carpoolUserId,
                                           CarpoolBot bot) {
        try {
            BigDecimal amount = new BigDecimal(text.trim());
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                bot.send(BotMessageBuilder.textWithCancel(chatId,
                        "⚠️ Contribution cannot be negative:"));
                return;
            }
            stateManager.save(chatId, state
                    .withContribution(amount)
                    .withFlow(BotFlow.POST_RIDE_NOTES));
            bot.send(BotMessageBuilder.textNoMenu(chatId,
                    "✅ Gas share: <b>₱" + amount + " / seat</b>"));
            postRideHelper.showNotesPrompt(
                    chatId, carpoolUserId, state.withContribution(amount), bot);
        } catch (NumberFormatException e) {
            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "⚠️ Please enter a valid amount " +
                            "(e.g. <code>100</code> or <code>0</code>):"));
        }
    }

    // ── Notes ─────────────────────────────────────────────────────────────

    public void handlePostRideNotes(Long chatId, String text,
                                    UserState state, CarpoolBot bot) {
        if (text.trim().length() > 1000) {
            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "⚠️ Note is too long (max 1000 characters). " +
                            "Please shorten it and try again."));
            return;
        }
        UserState updated = state.withNotes(text.trim());
        stateManager.save(chatId, updated);
        profileHandler.showVehicleConfirmStep(
                chatId, state.getCarpoolUserId(), updated, bot);
    }

    public void handlePostRideNotesWrite(Long chatId, String text,
                                         UserState state, Long carpoolUserId,
                                         CarpoolBot bot) {
        String notes = text.trim();
        if (notes.length() > 1000) {
            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "⚠️ Note is too long (max 1000 characters). " +
                            "Please shorten it and try again."));
            return;
        }
        driverNoteService.saveOrUpdate(carpoolUserId, notes);
        UserState updated = state.withNotes(notes);
        stateManager.save(chatId, updated);
        profileHandler.showVehicleConfirmStep(chatId, carpoolUserId, updated, bot);
    }

    public void handleNotePreview(BotContext ctx) {
        try {
            DriverNote note = driverNoteService.getById(ctx.entityId());
            stateManager.save(ctx.chatId(),
                    ctx.state().withSelectedNoteId(ctx.entityId()));
            var rows = List.of(
                    List.of(BotMessageBuilder.button(
                            "✅ Use this", "NOTE_APPLY:" + ctx.entityId())),
                    List.of(
                            BotMessageBuilder.button("🔄 Other notes", "NOTE_CHOOSE_OTHER"),
                            BotMessageBuilder.button("✏️ Write new",   "NOTE_WRITE")
                    ),
                    List.of(
                            BotMessageBuilder.button("⏭️ Skip",   "SKIP_NOTES"),
                            BotMessageBuilder.button("❌ Cancel", "CANCEL_POST_RIDE")
                    )
            );
            ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                    "📌 <b>Selected reminder:</b>\n\n\"" +
                            HtmlEscapeUtil.escape(note.getContent()) + "\"",
                    rows));
        } catch (Exception e) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not load note. Please try again."));
        }
    }

    public void handleNoteApply(BotContext ctx) {
        try {
            DriverNote note = driverNoteService.markUsed(ctx.entityId());
            UserState updated = ctx.state()
                    .withNotes(note.getContent())
                    .withSelectedNoteId(null);
            stateManager.save(ctx.chatId(), updated);
            profileHandler.showVehicleConfirmStep(
                    ctx.chatId(), ctx.carpoolUserId(), updated, ctx.bot());
        } catch (Exception e) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not apply note. Please try again."));
        }
    }

    public void handleNoteWrite(BotContext ctx) {
        stateManager.save(ctx.chatId(),
                ctx.state().withFlow(BotFlow.POST_RIDE_NOTES_WRITE));
        ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                "✏️ <b>Add details for your passengers:</b>\n\n" +
                        "<i>Include pickup spot, stops along the way, drop-off point, or any reminders.</i>",
                List.of(List.of(
                        BotMessageBuilder.button("⏭️ Skip",   "SKIP_NOTES"),
                        BotMessageBuilder.button("❌ Cancel", "CANCEL_POST_RIDE")
                ))));
    }

    public void handleNoteChooseOther(BotContext ctx) {
        postRideHelper.showNotesPrompt(ctx.chatId(), ctx.carpoolUserId(),
                ctx.state().withSelectedNoteId(null), ctx.bot());
    }

    public void handleSkipNotes(BotContext ctx) {
        UserState updated = ctx.state().withNotes(null);
        stateManager.save(ctx.chatId(), updated);
        profileHandler.showVehicleConfirmStep(
                ctx.chatId(), ctx.carpoolUserId(), updated, ctx.bot());
    }

    // ── Confirm / Cancel ──────────────────────────────────────────────────

    public void handleConfirmPostRide(BotContext ctx) {
        if (ctx.state() == null || ctx.state().getOriginHubId() == null) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Session expired. Please start again with /start."));
            return;
        }
        try {
            userRepository.findById(ctx.carpoolUserId()).ifPresent(user -> {
                if (user.getRole() == UserRole.PASSENGER) {
                    user.setRole(UserRole.BOTH);
                    userRepository.save(user);
                    log.info("Auto-upgraded userId={} role to BOTH",
                            ctx.carpoolUserId());
                }
            });

            CreateRideRequest request = new CreateRideRequest(
                    ctx.state().getOriginHubId(),
                    ctx.state().getDestinationHubId(),
                    ctx.state().getDirection(),
                    ctx.state().getDepartureTime(),
                    ctx.state().getSeats(),
                    ctx.state().getContribution() != null
                            ? ctx.state().getContribution() : BigDecimal.ZERO,
                    ctx.state().getNotes(),
                    null);

            RideResponse ride = rideService.createRide(request, ctx.carpoolUserId());
            rideService.updateRideStatus(ride.id(),
                    new UpdateRideStatusRequest(RideStatus.ACTIVE),
                    ctx.carpoolUserId());

            stateManager.save(ctx.chatId(), ctx.state()
                    .withLastPostedRideId(ride.id())
                    .withFlow(BotFlow.IDLE));

            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "✅ <b>Ride posted successfully!</b>\n\n" +
                            BotMessageBuilder.formatRideCard(ride) +
                            "\n\nPassengers can now find and book your ride."));

        } catch (Exception e) {
            log.error("Failed to post ride for userId={}: {}",
                    ctx.carpoolUserId(), e.getMessage());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Failed to post ride: " +
                            HtmlEscapeUtil.escape(e.getMessage())));
            stateManager.reset(ctx.chatId());
        }
    }

    public void handleCancelPostRide(BotContext ctx) {
        stateManager.reset(ctx.chatId());
        ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                "❌ Ride posting cancelled."));
    }

    // ── Repost ride ───────────────────────────────────────────────────────

    public void handleRepostRide(BotContext ctx) {
        try {
            RideResponse original = rideService.getRideById(ctx.entityId());
            String dirLabel = original.direction() == RideDirection.HOME_TO_WORK
                    ? "🏠 Home → Work" : "🏢 Work → Home";

            UserState updated = ctx.state()
                    .withOriginHubId(original.originHub().id())
                    .withOriginHubName(original.originHub().name())
                    .withDestinationHubId(original.destinationHub().id())
                    .withDestinationHubName(original.destinationHub().name())
                    .withDirection(original.direction())
                    .withSeats(original.totalSeats())
                    .withContribution(original.contributionAmount())
                    .withNotes(original.notes())
                    .withFlow(BotFlow.POST_RIDE_DEPARTURE_TIME);
            stateManager.save(ctx.chatId(), updated);

            String notesLine = original.notes() != null && !original.notes().isBlank()
                    ? "📝 Notes: " +
                    HtmlEscapeUtil.escape(original.notes()) + "\n\n" : "";

            ctx.bot().send(BotMessageBuilder.textWithCancel(ctx.chatId(),
                    "🔄 <b>Review Ride to Repost</b>\n\n" +
                            "Direction: <b>" + dirLabel + "</b>\n" +
                            "📍 <b>" +
                            HtmlEscapeUtil.escape(original.originHub().name()) +
                            " → " +
                            HtmlEscapeUtil.escape(original.destinationHub().name()) +
                            "</b>\n" +
                            "🪑 " + original.totalSeats() + " seat(s)\n" +
                            "⛽ ₱" + original.contributionAmount().toPlainString() +
                            " gas share/seat\n" + notesLine +
                            "<i>Only the departure time will be updated.</i>\n\n" +
                            "🕐 <b>What time are you leaving? (Start pickup time)</b>\n" +
                            "Format: <code>MM/DD HH:MM</code>\n" +
                            "Example: <code>" +
                            flowHelper.etdExample(original.direction()) + "</code>"));

        } catch (Exception e) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not load ride details for repost."));
        }
    }

    // ── Direction callback ────────────────────────────────────────────────

    public void handleDirectionCallback(BotContext ctx) {
        RideDirection direction = "HOME_TO_WORK".equals(ctx.payload())
                ? RideDirection.HOME_TO_WORK : RideDirection.WORK_TO_HOME;

        if (ctx.state().getFlow() == BotFlow.POST_RIDE_DIRECTION) {
            UserState updated = ctx.state()
                    .withDirection(direction)
                    .withCarpoolUserId(ctx.carpoolUserId())
                    .withFlow(BotFlow.POST_RIDE_DEPARTURE_TIME);
            stateManager.save(ctx.chatId(), updated);
            ctx.bot().send(BotMessageBuilder.textWithCancel(ctx.chatId(),
                    "🕐 <b>What time are you leaving? (Start pickup time)</b>\n\n" +
                            "Format: <code>MM/DD HH:MM</code>\n" +
                            "Example: <code>" +
                            flowHelper.etdExample(direction) + "</code>"));
            return;
        }

        if (ctx.state().getFlow() == BotFlow.SEARCH_SELECT_DIRECTION) {
            UserState updated = ctx.state()
                    .withDirection(direction)
                    .withCarpoolUserId(ctx.carpoolUserId())
                    .withFlow(BotFlow.SEARCH_SELECT_TIME);
            YearMonth month = YearMonth.now(MANILA);
            stateManager.save(ctx.chatId(), updated
                    .withCalendarMonth(month)
                    .withFlow(BotFlow.SEARCH_SELECT_DATE));
            flowHelper.showCalendar(ctx.chatId(), null, month, ctx.bot());
            return;
        }

        // Default — direction from main menu
        flowHelper.handleDirectionSelected(
                ctx.chatId(), ctx.carpoolUserId(), direction, ctx.state(), ctx.bot());
    }
}