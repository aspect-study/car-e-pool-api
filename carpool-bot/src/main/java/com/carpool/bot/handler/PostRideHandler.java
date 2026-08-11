package com.carpool.bot.handler;

import com.carpool.bot.CarpoolBot;
import com.carpool.bot.handler.context.BotContext;
import com.carpool.bot.handler.helper.BotFlowHelper;
import com.carpool.bot.handler.helper.PostRideHelper;
import com.carpool.bot.state.BotFlow;
import com.carpool.bot.state.StateManager;
import com.carpool.bot.state.UserState;
import com.carpool.bot.util.BotMessageBuilder;
import com.carpool.bot.util.BotTimePickerUtil;
import com.carpool.bot.util.ButtonStyle;
import com.carpool.bot.util.HubMatcher;
import com.carpool.common.util.HtmlEscapeUtil;
import com.carpool.domain.entity.DriverNote;
import com.carpool.domain.enums.RideDirection;
import com.carpool.domain.enums.RideStatus;
import com.carpool.domain.enums.UserRole;
import com.carpool.repository.UserRepository;
import com.carpool.service.dto.request.CreateRideRequest;
import com.carpool.service.dto.request.SuggestHubRequest;
import com.carpool.service.dto.request.UpdateRideStatusRequest;
import com.carpool.service.dto.response.HubResponse;
import com.carpool.service.dto.response.RideResponse;
import com.carpool.common.exception.InvalidRideStateException;
import com.carpool.common.exception.NotRideOwnerException;
import com.carpool.common.exception.SameHubException;
import com.carpool.service.booking.BookingService;
import com.carpool.service.hub.HubService;
import com.carpool.service.note.DriverNoteService;
import com.carpool.service.ride.RideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    private final BookingService    bookingService;
    private final HubService        hubService;
    private final HubMatcher        hubMatcher;
    private final DriverNoteService driverNoteService;
    private final ProfileHandler    profileHandler;
    private final PostRideHelper postRideHelper;
    private final BotFlowHelper flowHelper;

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
        if (sendConflictMessageIfAny(ctx, ctx.state().getDirection())) return;
        askForEtd(ctx.chatId(), ctx.state(), ctx.bot());
    }

    public void askForEtd(Long chatId, UserState state, CarpoolBot bot) {
        YearMonth month = YearMonth.now(ZoneId.of("Asia/Manila"));
        UserState updated = state
                .withCalendarMonth(month)
                .withFlow(BotFlow.POST_RIDE_SELECT_DATE);
        stateManager.save(chatId, updated);
        flowHelper.showCalendar(chatId, null, month, bot);
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
        if (text.trim().length() > 150) {
            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "⚠️ Hub name is too long (max 150 characters). Please try a shorter name."));
            return;
        }

        List<HubResponse> suggestions = hubMatcher.suggest(text);

        if (suggestions.isEmpty()) {
            stateManager.save(chatId, state.withOriginHubName(text.trim()));
            List<HubResponse> recentHubs =
                    hubService.getRecentHubsForUser(state.getCarpoolUserId());
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            String displayText = text.trim().length() > 38
                    ? text.trim().substring(0, 38) + "…" : text.trim();
            rows.add(List.of(BotMessageBuilder.button(
                    "✅ Use \"" + displayText + "\"", "CONFIRM_CUSTOM_ORIGIN", ButtonStyle.SUCCESS.toString())));
            for (HubResponse h : recentHubs) {
                rows.add(List.of(BotMessageBuilder.button(
                        "🕐 " + h.name(), "HUB_ORIGIN:" + h.id(),  ButtonStyle.PRIMARY.toString())));
            }
            rows.add(List.of(BotMessageBuilder.button("✏️ Type again", "RETYPE_ORIGIN",  ButtonStyle.PRIMARY.toString())));
            bot.send(flowHelper.sendWithInline(chatId,
                    "📍 <b>\"" + HtmlEscapeUtil.escape(text.trim()) +
                            "\"</b> isn't in our hub list yet.\n\n" +
                            "Use it as your start point? It will be added to our " +
                            "suggestion list for review." +
                            (recentHubs.isEmpty() ? "" :
                                    "\n\nOr choose a recent location:"),
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
        if (text.trim().length() > 150) {
            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "⚠️ Hub name is too long (max 150 characters). Please try a shorter name."));
            return;
        }

        List<HubResponse> suggestions = hubMatcher.suggest(text);

        if (suggestions.isEmpty()) {
            stateManager.save(chatId, state.withDestinationHubName(text.trim()));
            List<HubResponse> recentHubs =
                    hubService.getRecentHubsForUser(state.getCarpoolUserId());
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            String displayText = text.trim().length() > 38
                    ? text.trim().substring(0, 38) + "…" : text.trim();
            rows.add(List.of(BotMessageBuilder.button(
                    "✅ Use \"" + displayText + "\"", "CONFIRM_CUSTOM_DEST", ButtonStyle.SUCCESS.toString())));
            for (HubResponse h : recentHubs) {
                rows.add(List.of(BotMessageBuilder.button(
                        "🕐 " + h.name(), "HUB_DEST:" + h.id(), ButtonStyle.PRIMARY.toString())));
            }
            rows.add(List.of(BotMessageBuilder.button("✏️ Type again", "RETYPE_DEST",  ButtonStyle.PRIMARY.toString())));
            bot.send(flowHelper.sendWithInline(chatId,
                    "🏁 <b>\"" + HtmlEscapeUtil.escape(text.trim()) +
                            "\"</b> isn't in our hub list yet.\n\n" +
                            "Use it as your end point? It will be added to our " +
                            "suggestion list for review." +
                            (recentHubs.isEmpty() ? "" :
                                    "\n\nOr choose a recent location:"),
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

    // ── Route-edit hub search (existing hubs only — no custom-hub path in v1) ──

    /** Route-edit: driver typed a search term for the new origin hub. */
    public void handleEditRouteOriginSearch(Long chatId, String text,
                                            UserState state, CarpoolBot bot) {
        if (state.getSelectedRideId() == null) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Session expired. Please reopen the ride from the main menu."));
            stateManager.reset(chatId);
            return;
        }
        if (text.trim().length() < 3) {
            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "⚠️ Please type at least 3 characters to search."));
            return;
        }
        List<HubResponse> suggestions = hubMatcher.suggest(text);
        if (suggestions.isEmpty()) {
            stateManager.save(chatId, state.withOriginHubName(text.trim()));
            List<HubResponse> recentHubs =
                    hubService.getRecentHubsForUser(state.getCarpoolUserId());
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            String displayText = text.trim().length() > 38
                    ? text.trim().substring(0, 38) + "…" : text.trim();
            rows.add(List.of(BotMessageBuilder.button(
                    "✅ Use \"" + displayText + "\"", "EDIT_CONFIRM_CUSTOM_ORIGIN", ButtonStyle.SUCCESS.toString())));
            for (HubResponse h : recentHubs) {
                rows.add(List.of(BotMessageBuilder.button(
                        "🕐 " + h.name(), "EDIT_HUB_ORIGIN:" + h.id(), ButtonStyle.PRIMARY.toString())));
            }
            rows.add(List.of(BotMessageBuilder.button("✏️ Type again", "RETYPE_EDIT_ORIGIN", ButtonStyle.PRIMARY.toString())));
            bot.send(flowHelper.sendWithInline(chatId,
                    "📍 <b>\"" + HtmlEscapeUtil.escape(text.trim()) +
                            "\"</b> isn't in our hub list yet.\n\n" +
                            "Use it as your new start point? It will be added to our " +
                            "suggestion list for review." +
                            (recentHubs.isEmpty() ? "" : "\n\nOr choose a recent location:"),
                    rows));
            return;
        }
        bot.send(flowHelper.sendWithInline(chatId,
                "📍 <b>Select the new start point:</b>\n\nResults for \"" +
                        HtmlEscapeUtil.escape(text) + "\":",
                buildHubButtonRows(suggestions, "EDIT_HUB_ORIGIN", "RETYPE_EDIT_ORIGIN")));
    }

    /** Route-edit: driver typed a search term for the new destination hub. */
    public void handleEditRouteDestSearch(Long chatId, String text,
                                          UserState state, CarpoolBot bot) {
        if (state.getSelectedRideId() == null) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Session expired. Please reopen the ride from the main menu."));
            stateManager.reset(chatId);
            return;
        }
        if (text.trim().length() < 3) {
            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "⚠️ Please type at least 3 characters to search."));
            return;
        }
        List<HubResponse> suggestions = hubMatcher.suggest(text);
        if (suggestions.isEmpty()) {
            stateManager.save(chatId, state.withDestinationHubName(text.trim()));
            List<HubResponse> recentHubs =
                    hubService.getRecentHubsForUser(state.getCarpoolUserId());
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            String displayText = text.trim().length() > 38
                    ? text.trim().substring(0, 38) + "…" : text.trim();
            rows.add(List.of(BotMessageBuilder.button(
                    "✅ Use \"" + displayText + "\"", "EDIT_CONFIRM_CUSTOM_DEST", ButtonStyle.SUCCESS.toString())));
            for (HubResponse h : recentHubs) {
                rows.add(List.of(BotMessageBuilder.button(
                        "🕐 " + h.name(), "EDIT_HUB_DEST:" + h.id(), ButtonStyle.PRIMARY.toString())));
            }
            rows.add(List.of(BotMessageBuilder.button("✏️ Type again", "RETYPE_EDIT_DEST", ButtonStyle.PRIMARY.toString())));
            bot.send(flowHelper.sendWithInline(chatId,
                    "🏁 <b>\"" + HtmlEscapeUtil.escape(text.trim()) +
                            "\"</b> isn't in our hub list yet.\n\n" +
                            "Use it as your new end point? It will be added to our " +
                            "suggestion list for review." +
                            (recentHubs.isEmpty() ? "" : "\n\nOr choose a recent location:"),
                    rows));
            return;
        }
        bot.send(flowHelper.sendWithInline(chatId,
                "🏁 <b>Select the new end point:</b>\n\nResults for \"" +
                        HtmlEscapeUtil.escape(text) + "\":",
                buildHubButtonRows(suggestions, "EDIT_HUB_DEST", "RETYPE_EDIT_DEST")));
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
                    s.name(), callbackPrefix + ":" + s.id(), null);
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
        rows.add(List.of(BotMessageBuilder.button("✏️ Type again", retypeAction, ButtonStyle.PRIMARY.toString())));
        return rows;
    }

    // ── Hub callback selections ───────────────────────────────────────────

    public void handleHubOriginSelected(BotContext ctx) {
        if (ctx.state().getDirection() == null) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Session expired. Please start a new ride from the main menu."));
            stateManager.reset(ctx.chatId());
            return;
        }
        try {
            var hub = rideService.getHubById(ctx.entityId());
            if (ctx.state().isRepostEditMode()) {
                UserState updated = ctx.state()
                        .withOriginHubId(hub.id())
                        .withOriginHubName(hub.name())
                        .withFlow(BotFlow.IDLE);
                Integer newMsgId = showRepostEditScreen(ctx.chatId(), updated, ctx.bot());
                stateManager.save(ctx.chatId(), updated.withRepostEditMsgId(newMsgId));
                return;
            }
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
            log.warn("Could not load origin hub id={}: {}", ctx.entityId(), e.getMessage());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not load hub. Please try again."));
        }
    }

    public void handleHubDestSelected(BotContext ctx) {
        if (ctx.state().getDirection() == null || ctx.state().getOriginHubId() == null) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Session expired. Please start a new ride from the main menu."));
            stateManager.reset(ctx.chatId());
            return;
        }
        try {
            var hub = rideService.getHubById(ctx.entityId());
            if (hub.id().equals(ctx.state().getOriginHubId())) {
                ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                        "⚠️ Destination cannot be the same as pickup. Try again:"));
                return;
            }
            if (ctx.state().isRepostEditMode()) {
                UserState updated = ctx.state()
                        .withDestinationHubId(hub.id())
                        .withDestinationHubName(hub.name())
                        .withFlow(BotFlow.IDLE);
                Integer newMsgId = showRepostEditScreen(ctx.chatId(), updated, ctx.bot());
                stateManager.save(ctx.chatId(), updated.withRepostEditMsgId(newMsgId));
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
                            "🪑 <b>How many passengers can you take?</b> (1-7)"));
        } catch (Exception e) {
            log.warn("Could not load destination hub id={}: {}", ctx.entityId(), e.getMessage());
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
            if (seats < 1 || seats > 7) {
                bot.send(BotMessageBuilder.text(chatId,
                        "⚠️ Please enter a number between 1 and 7:"));
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
        if (text.trim().length() > 300) {
            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "⚠️ Note is too long (max 300 characters). " +
                            "Please shorten it and try again."));
            return;
        }
        UserState updated = state.withNotes(text.trim());
        stateManager.save(chatId, updated);
        profileHandler.showVehicleSelectStep(
                chatId, state.getCarpoolUserId(), updated, bot);
    }

    public void handlePostRideNotesWrite(Long chatId, String text,
                                         UserState state, Long carpoolUserId,
                                         CarpoolBot bot) {
        String notes = text.trim();
        if (notes.length() > 300) {
            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "⚠️ Note is too long (max 300 characters). " +
                            "Please shorten it and try again."));
            return;
        }
        driverNoteService.saveOrUpdate(carpoolUserId, notes);
        UserState updated = state.withNotes(notes);
        stateManager.save(chatId, updated);
        profileHandler.showVehicleSelectStep(chatId, carpoolUserId, updated, bot);
    }

    public void handleNotePreview(BotContext ctx) {
        try {
            DriverNote note = driverNoteService.getById(ctx.entityId());
            stateManager.save(ctx.chatId(),
                    ctx.state().withSelectedNoteId(ctx.entityId()));
            var rows = List.of(
                    List.of(BotMessageBuilder.button(
                            "✅ Use this", "NOTE_APPLY:" + ctx.entityId(), ButtonStyle.SUCCESS.toString())),
                    List.of(
                            BotMessageBuilder.button("🔄 Other notes", "NOTE_CHOOSE_OTHER",  ButtonStyle.PRIMARY.toString()),
                            BotMessageBuilder.button("✏️ Write new",   "NOTE_WRITE", ButtonStyle.PRIMARY.toString())
                    ),
                    List.of(
                            BotMessageBuilder.button("⏭️ Skip",   "SKIP_NOTES", ButtonStyle.PRIMARY.toString()),
                            BotMessageBuilder.button("❌ Cancel", "CANCEL_POST_RIDE", ButtonStyle.DANGER.toString())
                    )
            );
            ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                    "📌 <b>Selected reminder:</b>\n\n\"" +
                            HtmlEscapeUtil.escape(note.getContent()) + "\"",
                    rows));
        } catch (Exception e) {
            log.warn("Could not load note id={}: {}", ctx.entityId(), e.getMessage());
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
            profileHandler.showVehicleSelectStep(
                    ctx.chatId(), ctx.carpoolUserId(), updated, ctx.bot());
        } catch (Exception e) {
            log.warn("Could not apply note id={}: {}", ctx.entityId(), e.getMessage());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not apply note. Please try again."));
        }
    }

    public void handleNoteWrite(BotContext ctx) {
        stateManager.save(ctx.chatId(),
                ctx.state().withFlow(BotFlow.POST_RIDE_NOTES_WRITE));
        ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                """
                        ✏️ <b>Add details for your passengers:</b>
                        
                        <i>Include pickup spot, stops along the way, drop-off point, or any reminders.</i>""",
                List.of(List.of(
                        BotMessageBuilder.button("⏭️ Skip",   "SKIP_NOTES", ButtonStyle.PRIMARY.toString()),
                        BotMessageBuilder.button("❌ Cancel", "CANCEL_POST_RIDE", ButtonStyle.DANGER.toString())
                ))));
    }

    public void handleNoteChooseOther(BotContext ctx) {
        postRideHelper.showNotesPrompt(ctx.chatId(), ctx.carpoolUserId(),
                ctx.state().withSelectedNoteId(null), ctx.bot());
    }

    public void handleSkipNotes(BotContext ctx) {
        UserState updated = ctx.state().withNotes(null);
        stateManager.save(ctx.chatId(), updated);
        profileHandler.showVehicleSelectStep(
                ctx.chatId(), ctx.carpoolUserId(), updated, ctx.bot());
    }

    // ── Confirm / Cancel ──────────────────────────────────────────────────

    public void handleConfirmPostRide(BotContext ctx) {
        if (ctx.state() == null
                || ctx.state().getOriginHubId() == null
                || ctx.state().getDestinationHubId() == null
                || ctx.state().getDirection() == null) {
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
                    null,
                    ctx.state().getSelectedVehicleId());

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

        } catch (InvalidRideStateException e) {
            log.warn("Ride post blocked for userId={}: {}", ctx.carpoolUserId(), e.getMessage());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(), "⚠️ " + e.getMessage()));
            stateManager.reset(ctx.chatId());
        } catch (Exception e) {
            log.error("Failed to post ride for userId={}: {}",
                    ctx.carpoolUserId(), e.getMessage());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Failed to post ride. Please try again."));
            stateManager.reset(ctx.chatId());
        }
    }

    public void handleCancelPostRide(BotContext ctx) {
        stateManager.reset(ctx.chatId());
        ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                "❌ Ride posting cancelled."));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Checks for a same-direction active driver ride or active passenger booking.
     * Sends the conflict message and resets state if a conflict is found.
     * Returns true if a conflict was found (caller should return immediately).
     */
    private boolean sendConflictMessageIfAny(BotContext ctx, RideDirection direction) {
        if (rideService.hasActiveRide(ctx.carpoolUserId(), direction)) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "You already have an active ride post for this direction. Please cancel it first."));
            stateManager.reset(ctx.chatId());
            return true;
        }

        boolean hasActiveBooking = bookingService.getMyBookings(ctx.carpoolUserId()).stream()
                .anyMatch(b -> b.ride() != null && b.ride().direction() == direction);

        if (hasActiveBooking) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ You have an active booking as a passenger on a " + direction.label() +
                    " ride. Cancel it first before posting a new one."));
            stateManager.reset(ctx.chatId());
            return true;
        }

        return false;
    }

    // ── Repost ride ───────────────────────────────────────────────────────

    public void handleRepostRide(BotContext ctx) {
        try {
            RideResponse original = rideService.getRideById(ctx.entityId());

            if (rideService.hasActiveRide(ctx.carpoolUserId(), original.direction())) {
                ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                        "You already have an active ride post for this direction. Please cancel it first."));
                return;
            }

            LocalDate today = LocalDate.now(ZoneId.of("Asia/Manila"));
            UserState updated = getUserState(ctx, original, today);

            Integer msgId = showRepostEditScreen(ctx.chatId(), updated, ctx.bot());
            stateManager.save(ctx.chatId(), updated.withRepostEditMsgId(msgId));

        } catch (Exception e) {
            log.warn("Could not load ride for repost id={}: {}", ctx.entityId(), e.getMessage());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not load ride details for repost."));
        }
    }

    private static UserState getUserState(BotContext ctx, RideResponse original, LocalDate today) {
        int windowStart = BotTimePickerUtil.adjustWindowForToday(
                BotTimePickerUtil.defaultWindowStart(original.direction()), today);

        return ctx.state()
                .withOriginHubId(original.originHub().id())
                .withOriginHubName(original.originHub().name())
                .withDestinationHubId(original.destinationHub().id())
                .withDestinationHubName(original.destinationHub().name())
                .withDirection(original.direction())
                .withSeats(original.totalSeats())
                .withContribution(original.contributionAmount())
                .withNotes(original.notes())
                .withSearchDay(today)
                .withTimeWindowStart(windowStart)
                .withRepostEditMode(true)
                .withFlow(BotFlow.IDLE);
    }

    // ── Repost edit screen ────────────────────────────────────────────────

    private Integer showRepostEditScreen(Long chatId, UserState state, CarpoolBot bot) {
        String dirLabel = state.getDirection() == RideDirection.HOME_TO_WORK
                ? "🏠 Home → Work" : "🏢 Work → Home";
        String notesValue = (state.getNotes() != null && !state.getNotes().isBlank())
                ? HtmlEscapeUtil.escape(state.getNotes()) : "<i>None</i>";

        String seatsValue      = state.getSeats()         != null ? String.valueOf(state.getSeats()) : "?";
        String gasValue        = state.getContribution()   != null ? "₱" + state.getContribution().toPlainString() + "/seat" : "?";

        String text = "🔄 <b>Edit Ride for Repost</b>\n\n" +
                "↔️ Direction: <b>" + dirLabel + "</b>\n" +
                "📍 <b>From:</b> " + HtmlEscapeUtil.escape(state.getOriginHubName()) + "\n" +
                "🏁 <b>To:</b> " + HtmlEscapeUtil.escape(state.getDestinationHubName()) + "\n" +
                "🪑 <b>Seats:</b> " + seatsValue + "\n" +
                "⛽ <b>Gas share:</b> " + gasValue + "\n" +
                "📝 <b>Note:</b> " + notesValue + "\n\n" +
                "<i>Edit any field, then tap Continue to pick a departure time.</i>";

        List<List<InlineKeyboardButton>> rows = List.of(
                List.of(
                        BotMessageBuilder.button("📍 Edit Start", "REPOST_EDIT_ORIGIN", ButtonStyle.PRIMARY.toString()),
                        BotMessageBuilder.button("🏁 Edit End",   "REPOST_EDIT_DEST", ButtonStyle.PRIMARY.toString())
                ),
                List.of(
                        BotMessageBuilder.button("🪑 Edit Seats", "REPOST_EDIT_SEATS", ButtonStyle.PRIMARY.toString()),
                        BotMessageBuilder.button("⛽ Edit Share", "REPOST_EDIT_CONTRIBUTION",  ButtonStyle.PRIMARY.toString())
                ),
                List.of(BotMessageBuilder.button("📝 Edit Note", "REPOST_EDIT_NOTES", ButtonStyle.PRIMARY.toString())),
                List.of(
                        BotMessageBuilder.button("✅ Continue", "REPOST_PROCEED",  ButtonStyle.SUCCESS.toString()),
                        BotMessageBuilder.button("❌ Cancel",   "MAIN_MENU", ButtonStyle.DANGER.toString())
                )
        );

        return bot.sendReturningId(flowHelper.sendWithInline(chatId, text, rows));
    }

    // ── Repost edit callbacks ─────────────────────────────────────────────

    public void handleRepostEditOrigin(BotContext ctx) {
        String example = ctx.state().getDirection() == RideDirection.HOME_TO_WORK
                ? "SM Southmall" : "BGC";
        ctx.bot().deleteMessage(ctx.chatId(), ctx.messageId());
        stateManager.save(ctx.chatId(), ctx.state()
                .withRepostEditMsgId(null)
                .withFlow(BotFlow.POST_RIDE_ORIGIN));
        ctx.bot().send(BotMessageBuilder.textWithCancel(ctx.chatId(),
                "📍 <b>Edit Pickup Point</b>\n\n" +
                        "Type a nearby landmark as your pickup point.\n" +
                        "Example: <code>" + example + "</code>"));
    }

    public void handleRepostEditDest(BotContext ctx) {
        String example = ctx.state().getDirection() == RideDirection.HOME_TO_WORK
                ? "BGC" : "SM Southmall";
        ctx.bot().deleteMessage(ctx.chatId(), ctx.messageId());
        stateManager.save(ctx.chatId(), ctx.state()
                .withRepostEditMsgId(null)
                .withFlow(BotFlow.POST_RIDE_DESTINATION));
        ctx.bot().send(BotMessageBuilder.textWithCancel(ctx.chatId(),
                "🏁 <b>Edit Drop-off Point</b>\n\n" +
                        "Type a nearby landmark as your drop-off point.\n" +
                        "Example: <code>" + example + "</code>"));
    }

    public void handleRepostEditSeatsCallback(BotContext ctx) {
        ctx.bot().deleteMessage(ctx.chatId(), ctx.messageId());
        stateManager.save(ctx.chatId(), ctx.state()
                .withRepostEditMsgId(null)
                .withFlow(BotFlow.REPOST_EDIT_SEATS));
        ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                "🪑 <b>Edit Seats</b>\n\nHow many passengers can you take? (1–7)",
                List.of(List.of(BotMessageBuilder.button("◀️ Back", "REPOST_BACK_TO_EDIT", null)))));
    }

    public void handleRepostEditContributionCallback(BotContext ctx) {
        ctx.bot().deleteMessage(ctx.chatId(), ctx.messageId());
        stateManager.save(ctx.chatId(), ctx.state()
                .withRepostEditMsgId(null)
                .withFlow(BotFlow.REPOST_EDIT_CONTRIBUTION));
        ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                """
                        ⛽ <b>Edit Gas Share</b>
                        
                        Enter the suggested gas contribution per seat.
                        Enter <code>0</code> for a free ride. Example: <code>50</code>""",
                List.of(List.of(BotMessageBuilder.button("◀️ Back", "REPOST_BACK_TO_EDIT", null)))));
    }

    public void handleRepostEditNotesCallback(BotContext ctx) {
        ctx.bot().deleteMessage(ctx.chatId(), ctx.messageId());
        stateManager.save(ctx.chatId(), ctx.state()
                .withRepostEditMsgId(null)
                .withFlow(BotFlow.REPOST_EDIT_NOTES));
        ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                """
                        📝 <b>Edit Note</b>
                        
                        Write a note for passengers (max 300 characters).
                        Example: <i>AC, no pets, exits at Filinvest</i>""",
                List.of(List.of(BotMessageBuilder.button("◀️ Back", "REPOST_BACK_TO_EDIT", null)))));
    }

    public void handleRepostProceed(BotContext ctx) {
        YearMonth month = YearMonth.now(ZoneId.of("Asia/Manila"));
        UserState updated = ctx.state()
                .withRepostEditMode(false)
                .withRepostEditMsgId(null)
                .withCalendarMonth(month)
                .withFlow(BotFlow.POST_RIDE_SELECT_DATE);
        stateManager.save(ctx.chatId(), updated);
        flowHelper.showCalendar(ctx.chatId(), null, month, ctx.bot());
    }

    public void handleRepostBackToEdit(BotContext ctx) {
        UserState updated = ctx.state().withFlow(BotFlow.IDLE);
        Integer newMsgId = showRepostEditScreen(ctx.chatId(), updated, ctx.bot());
        stateManager.save(ctx.chatId(), updated.withRepostEditMsgId(newMsgId));
    }

    // ── Repost edit text handlers ─────────────────────────────────────────

    public void handleRepostEditSeats(Long chatId, String text, UserState state, CarpoolBot bot) {
        try {
            int seats = Integer.parseInt(text.trim());
            if (seats < 1 || seats > 7) {
                bot.send(flowHelper.sendWithInline(chatId,
                        "⚠️ Please enter a number between 1 and 7:",
                        List.of(List.of(BotMessageBuilder.button("◀️ Back", "REPOST_BACK_TO_EDIT", null)))));
                return;
            }
            UserState updated = state.withSeats(seats).withFlow(BotFlow.IDLE);
            Integer newMsgId = showRepostEditScreen(chatId, updated, bot);
            stateManager.save(chatId, updated.withRepostEditMsgId(newMsgId));
        } catch (NumberFormatException e) {
            bot.send(flowHelper.sendWithInline(chatId,
                    "⚠️ Please enter a valid number (1–8):",
                    List.of(List.of(BotMessageBuilder.button("◀️ Back", "REPOST_BACK_TO_EDIT", null)))));
        }
    }

    public void handleRepostEditContribution(Long chatId, String text,
                                             UserState state, CarpoolBot bot) {
        try {
            BigDecimal amount = new BigDecimal(text.trim());
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                bot.send(flowHelper.sendWithInline(chatId,
                        "⚠️ Contribution cannot be negative:",
                        List.of(List.of(BotMessageBuilder.button("◀️ Back", "REPOST_BACK_TO_EDIT", null)))));
                return;
            }
            UserState updated = state.withContribution(amount).withFlow(BotFlow.IDLE);
            Integer newMsgId = showRepostEditScreen(chatId, updated, bot);
            stateManager.save(chatId, updated.withRepostEditMsgId(newMsgId));
        } catch (NumberFormatException e) {
            bot.send(flowHelper.sendWithInline(chatId,
                    "⚠️ Please enter a valid amount (e.g. <code>100</code> or <code>0</code>):",
                    List.of(List.of(BotMessageBuilder.button("◀️ Back", "REPOST_BACK_TO_EDIT", null)))));
        }
    }

    public void handleRepostEditNotes(Long chatId, String text, UserState state, CarpoolBot bot) {
        if (text.trim().length() > 300) {
            bot.send(flowHelper.sendWithInline(chatId,
                    "⚠️ Note is too long (max 300 characters). Please shorten it:",
                    List.of(List.of(BotMessageBuilder.button("◀️ Back", "REPOST_BACK_TO_EDIT", null)))));
            return;
        }
        UserState updated = state.withNotes(text.trim()).withFlow(BotFlow.IDLE);
        Integer newMsgId = showRepostEditScreen(chatId, updated, bot);
        stateManager.save(chatId, updated.withRepostEditMsgId(newMsgId));
    }

    // ── Direction callback ────────────────────────────────────────────────

    public void handleDirectionCallback(BotContext ctx) {
        RideDirection direction = "HOME_TO_WORK".equals(ctx.payload())
                ? RideDirection.HOME_TO_WORK : RideDirection.WORK_TO_HOME;

        if (ctx.state().getFlow() == BotFlow.POST_RIDE_DIRECTION) {
            if (sendConflictMessageIfAny(ctx, direction)) return;
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
            boolean hasConflictingRide = rideService.getMyRides(ctx.carpoolUserId()).stream()
                    .anyMatch(r -> r.direction() == direction
                            && (r.status() == RideStatus.ACTIVE
                                || r.status() == RideStatus.FULL
                                || r.status() == RideStatus.DEPARTED));
            if (hasConflictingRide) {
                ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                        "⚠️ <b>You have an active " + direction.label() + " ride posted.</b>\n\n" +
                        "Please cancel or complete your ride first before " +
                        "looking for a ride as a passenger."));
                stateManager.reset(ctx.chatId());
                return;
            }
            boolean hasConflictingBooking = bookingService.getMyBookings(ctx.carpoolUserId()).stream()
                    .anyMatch(b -> b.ride() != null && b.ride().direction() == direction);
            if (hasConflictingBooking) {
                ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                        "⚠️ <b>You already have an active booking on a " + direction.label() + " ride.</b>\n\n" +
                        "Cancel it first before searching for a new one."));
                stateManager.reset(ctx.chatId());
                return;
            }
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

    // ── Custom hub confirmation ────────────────────────────────────────────

    public void handleConfirmCustomOrigin(BotContext ctx) {
        String customName = ctx.state().getOriginHubName();
        if (customName == null || customName.isBlank()) {
            stateManager.save(ctx.chatId(), ctx.state().withFlow(BotFlow.POST_RIDE_ORIGIN));
            ctx.bot().send(BotMessageBuilder.textWithCancel(ctx.chatId(),
                    "⚠️ Session expired. Please type your start point again:"));
            return;
        }
        try {
            HubResponse hub = hubService.suggestHub(
                    new SuggestHubRequest(customName, "Unverified"), ctx.carpoolUserId());
            if (ctx.state().isRepostEditMode()) {
                UserState updated = ctx.state()
                        .withOriginHubId(hub.id())
                        .withOriginHubName(hub.name())
                        .withFlow(BotFlow.IDLE);
                Integer newMsgId = showRepostEditScreen(ctx.chatId(), updated, ctx.bot());
                stateManager.save(ctx.chatId(), updated.withRepostEditMsgId(newMsgId));
                return;
            }
            UserState updated = ctx.state()
                    .withOriginHubId(hub.id())
                    .withOriginHubName(hub.name())
                    .withFlow(BotFlow.POST_RIDE_DESTINATION);
            stateManager.save(ctx.chatId(), updated);
            String destExample = ctx.state().getDirection() == RideDirection.HOME_TO_WORK
                    ? "BGC" : "SM Southmall";
            ctx.bot().send(BotMessageBuilder.textWithCancel(ctx.chatId(),
                    "✅ Start point: <b>" + HtmlEscapeUtil.escape(hub.name()) + "</b>\n\n" +
                            "🏁 <b>Where does your ride end?</b>\n\n" +
                            "Type a nearby landmark as your drop-off point.\n" +
                            "Example: <code>" + destExample + "</code>"));
        } catch (Exception e) {
            log.error("Failed to suggest hub for origin: {}", e.getMessage());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Something went wrong. Please try again."));
        }
    }

    public void handleConfirmCustomDest(BotContext ctx) {
        String customName = ctx.state().getDestinationHubName();
        if (customName == null || customName.isBlank()) {
            stateManager.save(ctx.chatId(), ctx.state().withFlow(BotFlow.POST_RIDE_DESTINATION));
            ctx.bot().send(BotMessageBuilder.textWithCancel(ctx.chatId(),
                    "⚠️ Session expired. Please type your end point again:"));
            return;
        }
        try {
            HubResponse hub = hubService.suggestHub(
                    new SuggestHubRequest(customName, "Unverified"), ctx.carpoolUserId());
            if (ctx.state().isRepostEditMode()) {
                UserState updated = ctx.state()
                        .withDestinationHubId(hub.id())
                        .withDestinationHubName(hub.name())
                        .withFlow(BotFlow.IDLE);
                Integer newMsgId = showRepostEditScreen(ctx.chatId(), updated, ctx.bot());
                stateManager.save(ctx.chatId(), updated.withRepostEditMsgId(newMsgId));
                return;
            }
            UserState updated = ctx.state()
                    .withDestinationHubId(hub.id())
                    .withDestinationHubName(hub.name())
                    .withFlow(BotFlow.POST_RIDE_SEATS);
            stateManager.save(ctx.chatId(), updated);
            ctx.bot().send(BotMessageBuilder.textWithCancel(ctx.chatId(),
                    "✅ End point: <b>" + HtmlEscapeUtil.escape(hub.name()) + "</b>\n\n" +
                            "🪑 <b>How many passengers can you take?</b> (1-7)"));
        } catch (Exception e) {
            log.error("Failed to suggest hub for destination: {}", e.getMessage());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Something went wrong. Please try again."));
        }
    }

    // ── Route-edit custom hub confirmation ──────────────────────────────────

    public void handleEditConfirmCustomOrigin(BotContext ctx) {
        applyCustomRouteHub(ctx, ctx.state().getOriginHubName(), true);
    }

    public void handleEditConfirmCustomDest(BotContext ctx) {
        applyCustomRouteHub(ctx, ctx.state().getDestinationHubName(), false);
    }

    private void applyCustomRouteHub(BotContext ctx, String customName, boolean isOrigin) {
        Long rideId = ctx.state().getSelectedRideId();
        if (customName == null || customName.isBlank() || rideId == null) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Session expired. Please reopen the ride from the main menu."));
            stateManager.reset(ctx.chatId());
            return;
        }
        try {
            HubResponse hub = hubService.suggestHub(
                    new SuggestHubRequest(customName, "Unverified"), ctx.carpoolUserId());
            RideResponse updated = isOrigin
                    ? rideService.updateRoute(rideId, hub.id(), null, ctx.carpoolUserId())
                    : rideService.updateRoute(rideId, null, hub.id(), ctx.carpoolUserId());
            stateManager.reset(ctx.chatId());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "✅ <b>Route updated</b>\n\n" +
                            "📍 " + HtmlEscapeUtil.escape(updated.originHub().name()) +
                            " → " + HtmlEscapeUtil.escape(updated.destinationHub().name()) + "\n\n" +
                            "Confirmed passengers have been notified."));
        } catch (InvalidRideStateException | SameHubException e) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(), "⚠️ " + e.getMessage()));
        } catch (NotRideOwnerException e) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(), "⚠️ This is not your ride."));
        } catch (Exception e) {
            log.error("Route update failed (custom hub) rideId={}: {}", rideId, e.getMessage(), e);
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not update the route. Please try again."));
        }
    }

    public void handleTimeNavigation(BotContext ctx) {
        LocalDate selectedDate = ctx.state().getSearchDay() != null
                ? ctx.state().getSearchDay()
                : LocalDate.now(ZoneId.of("Asia/Manila"));

        int current = ctx.state().getTimeWindowStart() != null
                ? ctx.state().getTimeWindowStart()
                : BotTimePickerUtil.adjustWindowForToday(
                      BotTimePickerUtil.defaultWindowStart(ctx.state().getDirection()),
                      selectedDate);

        int updated = "EARLIER".equals(ctx.payload())
                ? Math.max(0, current - BotTimePickerUtil.PAGE_SIZE_MIN)
                : Math.min(1200, current + BotTimePickerUtil.PAGE_SIZE_MIN);

        // For today: advance to the first page that actually has slots so the
        // stored value always matches what buildTimePicker displays
        int adjusted = BotTimePickerUtil.adjustWindowForToday(updated, selectedDate);

        UserState newState = ctx.state().withTimeWindowStart(adjusted);
        stateManager.save(ctx.chatId(), newState);

        flowHelper.showTimePicker(ctx.chatId(), ctx.messageId(), adjusted, selectedDate, ctx.bot());
    }

    public void handleRideTimeSelected(BotContext ctx) {
        try {
            LocalDate selectedDate = ctx.state().getSearchDay() != null
                    ? ctx.state().getSearchDay()
                    : LocalDate.now(ZoneId.of("Asia/Manila"));

            int hour   = Integer.parseInt(ctx.parts()[1]);
            int minute = Integer.parseInt(ctx.parts()[2]);

            LocalDateTime departure = selectedDate.atTime(hour, minute);

            if (departure.isBefore(LocalDateTime.now(ZoneId.of("Asia/Manila")))) {
                ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                        "⚠️ That time has already passed. Please select a future time."));
                int windowStart = ctx.state().getTimeWindowStart() != null
                        ? ctx.state().getTimeWindowStart()
                        : BotTimePickerUtil.defaultWindowStart(ctx.state().getDirection());
                LocalDate selectedSearchDate = ctx.state().getSearchDay() != null
                        ? ctx.state().getSearchDay()
                        : LocalDate.now(ZoneId.of("Asia/Manila"));
                flowHelper.showTimePicker(ctx.chatId(), null, windowStart, selectedSearchDate, ctx.bot());
                return;
            }

            // Repost flow — hubs already set, go to vehicle selection then confirmation
            if (ctx.state().getOriginHubId() != null
                    && ctx.state().getDestinationHubId() != null) {
                UserState updated = ctx.state()
                        .withDepartureTime(departure)
                        .withSelectedVehicleId(null)
                        .withSelectedVehicleLabel(null);
                stateManager.save(ctx.chatId(), updated);
                profileHandler.showVehicleSelectStep(
                        ctx.chatId(), ctx.carpoolUserId(), updated, ctx.bot());
                return;
            }

            // New ride flow — ask for origin hub
            UserState updated = ctx.state()
                    .withDepartureTime(departure)
                    .withFlow(BotFlow.POST_RIDE_ORIGIN);
            stateManager.save(ctx.chatId(), updated);

            String originExample = ctx.state().getDirection() == RideDirection.HOME_TO_WORK
                    ? "SM Southmall" : "BGC";
            ctx.bot().send(BotMessageBuilder.textWithCancel(ctx.chatId(),
                    "📍 <b>Where does your ride start?</b>\n\n" +
                            "Type a nearby landmark as your pickup point.\n" +
                            "Example: <code>" + originExample + "</code>"));

        } catch (Exception e) {
            log.warn("Invalid time selected: payload={} error={}", ctx.payload(), e.getMessage());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Invalid time. Please select again."));
        }
    }
}