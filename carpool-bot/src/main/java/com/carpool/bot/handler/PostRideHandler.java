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
                    "✅ Use \"" + displayText + "\"", "CONFIRM_CUSTOM_ORIGIN")));
            for (HubResponse h : recentHubs) {
                rows.add(List.of(BotMessageBuilder.button(
                        "🕐 " + h.name(), "HUB_ORIGIN:" + h.id())));
            }
            rows.add(List.of(BotMessageBuilder.button("✏️ Type again", "RETYPE_ORIGIN")));
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
                    "✅ Use \"" + displayText + "\"", "CONFIRM_CUSTOM_DEST")));
            for (HubResponse h : recentHubs) {
                rows.add(List.of(BotMessageBuilder.button(
                        "🕐 " + h.name(), "HUB_DEST:" + h.id())));
            }
            rows.add(List.of(BotMessageBuilder.button("✏️ Type again", "RETYPE_DEST")));
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
            profileHandler.showVehicleSelectStep(
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
        profileHandler.showVehicleSelectStep(
                ctx.chatId(), ctx.carpoolUserId(), updated, ctx.bot());
    }

    // ── Confirm / Cancel ──────────────────────────────────────────────────

    public void handleConfirmPostRide(BotContext ctx) {
        if (ctx.state() == null
                || ctx.state().getOriginHubId() == null
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
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Manila"));
            int windowStart = BotTimePickerUtil.defaultWindowStart(original.direction());

            UserState updated = ctx.state()
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

            Integer msgId = showRepostEditScreen(ctx.chatId(), updated, ctx.bot());
            stateManager.save(ctx.chatId(), updated.withRepostEditMsgId(msgId));

        } catch (Exception e) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not load ride details for repost."));
        }
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
                        BotMessageBuilder.button("📍 Edit Start", "REPOST_EDIT_ORIGIN"),
                        BotMessageBuilder.button("🏁 Edit End",   "REPOST_EDIT_DEST")
                ),
                List.of(
                        BotMessageBuilder.button("🪑 Edit Seats", "REPOST_EDIT_SEATS"),
                        BotMessageBuilder.button("⛽ Edit Share", "REPOST_EDIT_CONTRIBUTION")
                ),
                List.of(BotMessageBuilder.button("📝 Edit Note", "REPOST_EDIT_NOTES")),
                List.of(
                        BotMessageBuilder.button("✅ Continue", "REPOST_PROCEED"),
                        BotMessageBuilder.button("❌ Cancel",   "MAIN_MENU")
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
                List.of(List.of(BotMessageBuilder.button("◀️ Back", "REPOST_BACK_TO_EDIT")))));
    }

    public void handleRepostEditContributionCallback(BotContext ctx) {
        ctx.bot().deleteMessage(ctx.chatId(), ctx.messageId());
        stateManager.save(ctx.chatId(), ctx.state()
                .withRepostEditMsgId(null)
                .withFlow(BotFlow.REPOST_EDIT_CONTRIBUTION));
        ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                "⛽ <b>Edit Gas Share</b>\n\nEnter the suggested gas contribution per seat.\n" +
                        "Enter <code>0</code> for a free ride. Example: <code>50</code>",
                List.of(List.of(BotMessageBuilder.button("◀️ Back", "REPOST_BACK_TO_EDIT")))));
    }

    public void handleRepostEditNotesCallback(BotContext ctx) {
        ctx.bot().deleteMessage(ctx.chatId(), ctx.messageId());
        stateManager.save(ctx.chatId(), ctx.state()
                .withRepostEditMsgId(null)
                .withFlow(BotFlow.REPOST_EDIT_NOTES));
        ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                "📝 <b>Edit Note</b>\n\nWrite a note for passengers (max 300 characters).\n" +
                        "Example: <i>AC, no pets, exits at Filinvest</i>",
                List.of(List.of(BotMessageBuilder.button("◀️ Back", "REPOST_BACK_TO_EDIT")))));
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
                        "⚠️ Please enter a number between 1 and 8:",
                        List.of(List.of(BotMessageBuilder.button("◀️ Back", "REPOST_BACK_TO_EDIT")))));
                return;
            }
            UserState updated = state.withSeats(seats).withFlow(BotFlow.IDLE);
            Integer newMsgId = showRepostEditScreen(chatId, updated, bot);
            stateManager.save(chatId, updated.withRepostEditMsgId(newMsgId));
        } catch (NumberFormatException e) {
            bot.send(flowHelper.sendWithInline(chatId,
                    "⚠️ Please enter a valid number (1–8):",
                    List.of(List.of(BotMessageBuilder.button("◀️ Back", "REPOST_BACK_TO_EDIT")))));
        }
    }

    public void handleRepostEditContribution(Long chatId, String text,
                                             UserState state, CarpoolBot bot) {
        try {
            BigDecimal amount = new BigDecimal(text.trim());
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                bot.send(flowHelper.sendWithInline(chatId,
                        "⚠️ Contribution cannot be negative:",
                        List.of(List.of(BotMessageBuilder.button("◀️ Back", "REPOST_BACK_TO_EDIT")))));
                return;
            }
            UserState updated = state.withContribution(amount).withFlow(BotFlow.IDLE);
            Integer newMsgId = showRepostEditScreen(chatId, updated, bot);
            stateManager.save(chatId, updated.withRepostEditMsgId(newMsgId));
        } catch (NumberFormatException e) {
            bot.send(flowHelper.sendWithInline(chatId,
                    "⚠️ Please enter a valid amount (e.g. <code>100</code> or <code>0</code>):",
                    List.of(List.of(BotMessageBuilder.button("◀️ Back", "REPOST_BACK_TO_EDIT")))));
        }
    }

    public void handleRepostEditNotes(Long chatId, String text, UserState state, CarpoolBot bot) {
        if (text.trim().length() > 300) {
            bot.send(flowHelper.sendWithInline(chatId,
                    "⚠️ Note is too long (max 300 characters). Please shorten it:",
                    List.of(List.of(BotMessageBuilder.button("◀️ Back", "REPOST_BACK_TO_EDIT")))));
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

    public void handleTimeNavigation(BotContext ctx) {
        int current = ctx.state().getTimeWindowStart() != null
                ? ctx.state().getTimeWindowStart()
                : BotTimePickerUtil.defaultWindowStart(ctx.state().getDirection());

        int updated = "EARLIER".equals(ctx.payload())
                ? Math.max(0, current - 2)
                : Math.min(22, current + 2);

        UserState newState = ctx.state().withTimeWindowStart(updated);
        stateManager.save(ctx.chatId(), newState);

        LocalDate selectedDate = ctx.state().getSearchDay() != null
                ? ctx.state().getSearchDay()
                : LocalDate.now(ZoneId.of("Asia/Manila"));

        flowHelper.showTimePicker(ctx.chatId(), ctx.messageId(),
                ctx.state().getDirection(), updated, selectedDate, ctx.bot());
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
                flowHelper.showTimePicker(ctx.chatId(), null,
                        ctx.state().getDirection(), windowStart, selectedSearchDate, ctx.bot());
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