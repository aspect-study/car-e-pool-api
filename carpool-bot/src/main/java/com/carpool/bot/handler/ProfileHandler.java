package com.carpool.bot.handler;

import com.carpool.bot.CarpoolBot;
import com.carpool.bot.config.BotConfig;
import com.carpool.bot.handler.context.BotContext;
import com.carpool.bot.handler.helper.BotFlowHelper;
import com.carpool.bot.handler.helper.PostRideHelper;
import com.carpool.bot.state.BotFlow;
import com.carpool.bot.state.StateManager;
import com.carpool.bot.state.UserState;
import com.carpool.bot.util.BotMessageBuilder;
import com.carpool.common.util.HtmlEscapeUtil;
import com.carpool.domain.entity.User;
import com.carpool.repository.UserRepository;
import com.carpool.service.admin.AdminStatsService;
import com.carpool.service.dto.response.HubResponse;
import com.carpool.service.dto.response.ProfileStatsResponse;
import com.carpool.service.dto.response.RideResponse;
import com.carpool.service.hub.HubService;
import com.carpool.service.profile.ProfileService;
import com.carpool.service.rating.RatingService;
import com.carpool.service.ride.RideService;
import com.carpool.service.vehicle.VehicleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles all profile, vehicle, terms, admin stats, and onboarding flows.
 * Covers: MY_PROFILE, VEHICLE_*, TERMS_*, ADMIN_STATS, REANNOUNCE_RIDE,
 * welcome screen, terms reminder, /profile, /vehicle commands.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileHandler {

    private final StateManager      stateManager;
    private final UserRepository    userRepository;
    private final ProfileService    profileService;
    private final VehicleService    vehicleService;
    private final AdminStatsService adminStatsService;
    private final RideService       rideService;
    private final BotFlowHelper flowHelper;
    private final PostRideHelper postRideHelper;
    private final BotConfig         botConfig;
    private final RatingService     ratingService;
    private final HubService        hubService;

    // ── Profile ───────────────────────────────────────────────────────────

    public void handleMyProfile(BotContext ctx) {
        try {
            ProfileStatsResponse stats = profileService.getProfileStats(ctx.carpoolUserId());

            StringBuilder sb = new StringBuilder("👤 <b>My Profile</b>\n\n");

            sb.append(String.format("<b>%s</b>%s\n",
                    HtmlEscapeUtil.escape(stats.fullName()),
                    stats.telegramHandle() != null
                            ? " (@" + HtmlEscapeUtil.escape(stats.telegramHandle()) + ")"
                            : ""));
            sb.append(stats.roleLabel()).append("\n");
            sb.append("📅 Member since: ").append(stats.memberSince()).append("\n");

            if (stats.driverRidesPosted() != null || stats.carModel() != null) {
                if (stats.carModel() != null && stats.plateNumber() != null) {
                    sb.append(String.format("\n🚘 %s%s\n🔢 %s\n",
                            stats.carColor() != null
                                    ? "🎨 " + HtmlEscapeUtil.escape(stats.carColor()) + " "
                                    : "",
                            HtmlEscapeUtil.escape(stats.carModel()),
                            HtmlEscapeUtil.escape(stats.plateNumber())));
                } else {
                    sb.append("\n🚘 <i>No vehicle info yet</i>\n");
                }
            }

            if (stats.driverRidesPosted() != null) {
                sb.append("\n🏆 <b>Driver Stats</b>\n");

                // Add driver rating
                String driverRating = ratingService.getDriverRatingLabel(ctx.carpoolUserId());
                if (driverRating != null) {
                    sb.append(driverRating).append("\n");
                }

                if (stats.driverCompletionRate() != null) {
                    sb.append(String.format("⭐ %d%% Completion Rate\n",
                            stats.driverCompletionRate()));
                }
                sb.append(String.format("🚗 Rides posted: %d\n",     stats.driverRidesPosted()));
                sb.append(String.format("✅ Completed: %d\n",         stats.driverCompleted()));
                sb.append(String.format("👥 Passengers served: %d\n", stats.driverPassengersServed()));
                if (stats.driverCancelled() > 0) {
                    sb.append(String.format("❌ Cancelled: %d\n", stats.driverCancelled()));
                }
            }

            if (stats.passengerBookingsMade() != null) {
                sb.append("\n🧳 <b>Passenger Stats</b>\n");

                // Add passenger rating
                String passengerRating = ratingService.getPassengerRatingLabel(ctx.carpoolUserId());
                if (passengerRating != null) {
                    sb.append(passengerRating).append("\n");
                }

                if (stats.passengerCompletionRate() != null) {
                    sb.append(String.format("⭐ %d%% Completion Rate\n",
                            stats.passengerCompletionRate()));
                }
                sb.append(String.format("📦 Bookings made: %d\n", stats.passengerBookingsMade()));
                sb.append(String.format("✅ Completed: %d\n",      stats.passengerCompleted()));
                if (stats.passengerCancelledByMe() > 0) {
                    sb.append(String.format("❌ Cancelled by me: %d\n",
                            stats.passengerCancelledByMe()));
                }
            }

            if (stats.driverRidesPosted() == null && stats.passengerBookingsMade() == null) {
                sb.append("\n<i>No activity yet. Post or book a ride to get started!</i>");
            }

            boolean isAdmin = botConfig.isAdmin(ctx.telegramId());
            List<List<InlineKeyboardButton>> profileRows = new ArrayList<>();
            profileRows.add(List.of(
                    BotMessageBuilder.button("🔄 Refresh",    "MY_PROFILE"),
                    BotMessageBuilder.button("🚘 My Vehicle", "VEHICLE_CHANGE"),
                    BotMessageBuilder.button("🏠 Menu",       "MAIN_MENU")
            ));
            if (isAdmin) {
                profileRows.add(List.of(
                        BotMessageBuilder.button("📊 Admin Stats",   "ADMIN_STATS"),
                        BotMessageBuilder.button("🏘️ Pending Hubs", "PENDING_HUBS")));
            }

            ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(), sb.toString(), profileRows));

        } catch (Exception e) {
            log.error("Failed to load profile for userId={}: {}",
                    ctx.carpoolUserId(), e.getMessage());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not load profile. Please try again."));
        }
    }

    // ── Vehicle flows ─────────────────────────────────────────────────────

    public void handleVehicleChange(BotContext ctx) {
        UserState updated = ctx.state()
                .withPendingCarColor(null)
                .withPendingCarModel(null)
                .withPendingPlateNumber(null)
                .withFlow(BotFlow.SET_VEHICLE_COLOR);
        stateManager.save(ctx.chatId(), updated);

        ctx.bot().send(BotMessageBuilder.textWithCancel(ctx.chatId(),
                "🎨 <b>What color is your vehicle?</b>\n\n" +
                        "Example: <code>Silver</code>, <code>White</code>, <code>Black</code>"));
    }

    public void handleVehicleConfirmYes(BotContext ctx) {
        // Guard — VEHICLE_CONFIRM_YES is only valid inside the post ride flow
        if (ctx.state().getDepartureTime() == null) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ This action is no longer valid. Please start again from the main menu."));
            stateManager.reset(ctx.chatId());
            return;
        }
        var userOpt = userRepository.findById(ctx.carpoolUserId());
        if (userOpt.isEmpty()) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(), "⚠️ User not found."));
            return;
        }
        var user = userOpt.get();
        UserState updated = ctx.state()
                .withPendingCarColor(user.getCarColor())
                .withPendingCarModel(user.getCarModel())
                .withPendingPlateNumber(user.getPlateNumber())
                .withFlow(BotFlow.POST_RIDE_CONFIRM);
        stateManager.save(ctx.chatId(), updated);
        postRideHelper.showConfirmation(ctx.chatId(), updated, ctx.bot());
    }

    public void handleVehicleConfirmSave(BotContext ctx) {
        if (ctx.state().getPendingCarModel() == null
                || ctx.state().getPendingPlateNumber() == null) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Session expired. Please start again with /start."));
            return;
        }
        try {
            vehicleService.updateVehicle(
                    ctx.carpoolUserId(),
                    ctx.state().getPendingCarColor() != null
                            ? ctx.state().getPendingCarColor() : "",
                    ctx.state().getPendingCarModel(),
                    ctx.state().getPendingPlateNumber());

            UserState updated = ctx.state().withFlow(BotFlow.POST_RIDE_CONFIRM);
            stateManager.save(ctx.chatId(), updated);

            ctx.bot().send(BotMessageBuilder.textNoMenu(ctx.chatId(),
                    "✅ Vehicle saved: " +
                            (ctx.state().getPendingCarColor() != null
                                    ? "🎨 " + HtmlEscapeUtil.escape(ctx.state().getPendingCarColor()) + " "
                                    : "") +
                            "🚘 " + HtmlEscapeUtil.escape(ctx.state().getPendingCarModel()) +
                            " | 🔢 " + HtmlEscapeUtil.escape(ctx.state().getPendingPlateNumber())));

            if (ctx.state().getOriginHubId() != null
                    && ctx.state().getDepartureTime() != null) {
                postRideHelper.showConfirmation(ctx.chatId(), updated, ctx.bot());
            } else {
                stateManager.reset(ctx.chatId());
                flowHelper.showMainMenu(ctx.chatId(), ctx.carpoolUserId(), updated, ctx.bot());
            }

        } catch (Exception e) {
            log.error("Failed to save vehicle for userId={}: {}",
                    ctx.carpoolUserId(), e.getMessage());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not save vehicle info. " +
                            "Plate number may already be in use."));
        }
    }

    public void handleVehicleRemove(BotContext ctx) {
        try {
            vehicleService.clearVehicle(ctx.carpoolUserId());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(), "✅ Vehicle info removed."));
        } catch (Exception e) {
            log.error("Failed to remove vehicle for userId={}: {}",
                    ctx.carpoolUserId(), e.getMessage());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not remove vehicle info. Please try again."));
        }
    }

    /**
     * Handles /vehicle command — shows current vehicle with update/remove options.
     */
    public void handleVehicleCommand(Long chatId, Long carpoolUserId,
                                     UserState state, CarpoolBot bot) {
        var userOpt = userRepository.findById(carpoolUserId);
        if (userOpt.isEmpty()) {
            bot.send(BotMessageBuilder.text(chatId, "⚠️ User not found."));
            return;
        }
        var user = userOpt.get();

        if (user.hasVehicleInfo()) {
            String current = String.format("%s%s | 🔢 %s",
                    user.getCarColor() != null
                            ? "🎨 " + HtmlEscapeUtil.escape(user.getCarColor()) + " "
                            : "",
                    HtmlEscapeUtil.escape(user.getCarModel()),
                    HtmlEscapeUtil.escape(user.getPlateNumber()));

            var rows = List.of(
                    List.of(
                            BotMessageBuilder.button("📝 Update Vehicle", "VEHICLE_CHANGE"),
                            BotMessageBuilder.button("🗑️ Remove",         "VEHICLE_REMOVE")
                    ),
                    List.of(BotMessageBuilder.button("🏠 Menu", "MAIN_MENU"))
            );
            bot.send(flowHelper.sendWithInline(chatId,
                    "🚘 <b>Your Vehicle</b>\n\n" + current, rows));
        } else {
            var rows = List.of(List.of(
                    BotMessageBuilder.button("🚘 Add Vehicle", "VEHICLE_CHANGE"),
                    BotMessageBuilder.button("🏠 Menu",        "MAIN_MENU")
            ));
            bot.send(flowHelper.sendWithInline(chatId,
                    "🚘 <b>Your Vehicle</b>\n\n" +
                            "<i>No vehicle info yet.</i>\n\n" +
                            "Add your vehicle so passengers know what to look for.",
                    rows));
        }
    }

    /**
     * Shows vehicle confirmation screen after plate input.
     */
    public void showVehicleConfirmation(Long chatId, UserState state, CarpoolBot bot) {
        String vehicleDisplay = String.format("%s%s | 🔢 %s",
                state.getPendingCarColor() != null
                        ? "🎨 " + HtmlEscapeUtil.escape(state.getPendingCarColor()) + " "
                        : "",
                HtmlEscapeUtil.escape(state.getPendingCarModel()),
                HtmlEscapeUtil.escape(state.getPendingPlateNumber()));

        var rows = List.of(
                List.of(
                        BotMessageBuilder.button("✅ Save & Continue", "VEHICLE_CONFIRM_SAVE"),
                        BotMessageBuilder.button("✏️ Change",          "VEHICLE_CHANGE")
                ),
                List.of(BotMessageBuilder.button("❌ Cancel", "CANCEL_POST_RIDE"))
        );
        bot.send(flowHelper.sendWithInline(chatId,
                "🚘 <b>Vehicle Details</b>\n\n" +
                        vehicleDisplay + "\n\nSave this vehicle info?",
                rows));
    }

    /**
     * Vehicle confirmation step in post ride flow.
     * If no saved vehicle → goes straight to vehicle input.
     */
    public void showVehicleConfirmStep(Long chatId, Long carpoolUserId,
                                       UserState state, CarpoolBot bot) {
        var userOpt = userRepository.findById(carpoolUserId);
        if (userOpt.isEmpty()) {
            bot.send(BotMessageBuilder.text(chatId, "⚠️ User not found."));
            return;
        }
        var user = userOpt.get();
        UserState updated = state.withFlow(BotFlow.POST_RIDE_VEHICLE_CONFIRM);
        stateManager.save(chatId, updated);

        if (user.hasVehicleInfo()) {
            String vehicleDisplay = String.format("%s%s | 🔢 %s",
                    user.getCarColor() != null
                            ? "🎨 " + HtmlEscapeUtil.escape(user.getCarColor()) + " "
                            : "",
                    HtmlEscapeUtil.escape(user.getCarModel()),
                    HtmlEscapeUtil.escape(user.getPlateNumber()));

            var rows = List.of(List.of(
                    BotMessageBuilder.button("✅ Yes, Proceed",  "VEHICLE_CONFIRM_YES"),
                    BotMessageBuilder.button("📝 Change Vehicle", "VEHICLE_CHANGE")
            ));
            bot.send(flowHelper.sendWithInline(chatId,
                    "🚘 <b>Vehicle Confirmation</b>\n\n" +
                            "You are currently using:\n<b>" + vehicleDisplay +
                            "</b>\n\nUse this for your ride?",
                    rows));
        } else {
            UserState vehicleState = updated
                    .withPendingCarColor(null)
                    .withPendingCarModel(null)
                    .withPendingPlateNumber(null)
                    .withFlow(BotFlow.SET_VEHICLE_COLOR);
            stateManager.save(chatId, vehicleState);
            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "🎨 <b>What color is your vehicle?</b>\n\n" +
                            "Example: <code>Silver</code>, <code>White</code>, <code>Black</code>"));
        }
    }

    // ── Terms ─────────────────────────────────────────────────────────────

    /**
     * Shows terms screen with group invite link prominently at the top.
     */
    public void handleTermsWelcome(Long chatId, CarpoolBot bot) {
        String termsText =
                "━━━━━━━━━━━━━━━━━━━━━\n" +
                        "👥 <b>JOIN OUR COMMUNITY GROUP</b>\n" +
                        "━━━━━━━━━━━━━━━━━━━━━\n" +
                        "All rides are posted and announced here:\n" +
                        "🔗 <b>" + botConfig.getGroupInviteLink() + "</b>\n" +
                        "👆 <i>Join the group so you never miss a ride!</i>\n" +
                        "━━━━━━━━━━━━━━━━━━━━━\n\n" +
                        "📋 <b>Terms & Community Guidelines</b>\n\n" +
                        "Please read and accept before using this bot:\n\n" +
                        "🚫 <b>Non-Commercial Use</b>\n" +
                        "This is a peer-to-peer carpooling tool — not a ride-hailing business.\n\n" +
                        "⛽ <b>Cost-Recovery Only</b>\n" +
                        "Contributions cover fuel, tolls, and parking only. No profit allowed.\n\n" +
                        "📜 <b>Legal Compliance</b>\n" +
                        "Drivers must follow LTFRB Carpooling Guidelines: 2-trip/day limit " +
                        "and required permits/QR codes.\n\n" +
                        "🛡️ <b>Safety First</b>\n" +
                        "Obey traffic laws and prioritize passenger safety. " +
                        "The bot owner/admin is not liable for any incidents.\n\n" +
                        "🚨 <b>Zero Tolerance</b>\n" +
                        "Overcharging, random pickups, or operating without permits " +
                        "(\"Colorum\" behavior) = permanent ban.\n\n" +
                        "Do you accept these terms?";

        var rows = List.of(
                List.of(
                        BotMessageBuilder.urlButton("👥 Join the Group",
                                botConfig.getGroupInviteLink()),
                        BotMessageBuilder.button("✅ I Accept",  "TERMS_ACCEPT"),
                        BotMessageBuilder.button("❌ Decline",   "TERMS_DECLINE")
                )
        );
        bot.send(flowHelper.sendWithInline(chatId, termsText, rows));
    }

    /**
     * Terms accepted — save version + timestamp, show welcome then group join prompt.
     */
    public void handleTermsAccept(BotContext ctx) {
        try {
            userRepository.findById(ctx.carpoolUserId()).ifPresent(user -> {
                user.setTermsVersionAccepted(BotConfig.CURRENT_TERMS_VERSION);
                user.setTermsAcceptedAt(LocalDateTime.now());
                user.setTermsDeclinedAt(null);
                userRepository.save(user);
                log.info("Terms accepted: userId={} version={}",
                        ctx.carpoolUserId(), BotConfig.CURRENT_TERMS_VERSION);
            });

            ctx.bot().send(BotMessageBuilder.textNoMenu(ctx.chatId(),
                    "🎉 <b>Welcome to the community!</b>\n\n" +
                            "Thank you for accepting the terms. You're now part of the " +
                            "Car-e-Pool Carpooling Community.\n\n" +
                            "Let's get started! 🚗"));

            ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                    "━━━━━━━━━━━━━━━━━━━━━\n" +
                            "👥 <b>JOIN OUR COMMUNITY GROUP</b>\n" +
                            "━━━━━━━━━━━━━━━━━━━━━\n\n" +
                            "🚗 Rides are posted in the group in real time.\n" +
                            "📢 Drivers announce their rides there.\n" +
                            "🔍 Passengers spot rides before they fill up.\n\n" +
                            "<b>Don't miss out — join now!</b>",
                    List.of(
                            List.of(BotMessageBuilder.urlButton(
                                    "👥 Join the Group",
                                    botConfig.getGroupInviteLink())),
                            List.of(BotMessageBuilder.button(
                                    "▶️ I'm already in — Continue", "MAIN_MENU"))
                    )));

        } catch (Exception e) {
            log.error("Failed to save terms acceptance for userId={}: {}",
                    ctx.carpoolUserId(), e.getMessage());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Something went wrong. Please try again."));
        }
    }

    /**
     * Terms declined — store declined_at for weekly re-prompt logic.
     */
    public void handleTermsDecline(BotContext ctx) {
        UserState state = stateManager.get(ctx.chatId());
        if (state != null) {
            userRepository.findById(state.getCarpoolUserId()).ifPresent(user -> {
                user.setTermsDeclinedAt(LocalDateTime.now());
                userRepository.save(user);
            });
        }
        var rows = List.of(List.of(
                BotMessageBuilder.button("🔁 Review Terms Again", "TERMS_VIEW_AGAIN")
        ));
        ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                "We understand if you're not ready. 🙏\n\n" +
                        "You'll need to accept the terms to use this bot. " +
                        "You can review them again anytime.",
                rows));
    }

    // ── Onboarding ────────────────────────────────────────────────────────

    /**
     * Welcome screen for brand new users before showing terms.
     */
    public void showWelcomeScreen(Long chatId, User user, CarpoolBot bot) {
        String firstName = user.getFullName().split(" ")[0];
        var rows = List.of(
                List.of(
                        BotMessageBuilder.button("📄 View Terms & Accept", "TERMS_WELCOME"),
                        BotMessageBuilder.button("❌ Not Now",              "TERMS_DECLINE")
                )
        );
        bot.send(flowHelper.sendWithInline(chatId,
                "👋 <b>Welcome, " + HtmlEscapeUtil.escape(firstName) + "!</b>\n\n" +
                        "You've joined the <b>" +
                        HtmlEscapeUtil.escape(botConfig.getCommunityName()) +
                        " Carpooling Community</b>. 🚗\n\n" +
                        "Before we get started, please review and accept our community terms " +
                        "to keep this a safe, legal, and non-profit carpooling group.\n\n" +
                        "<i>Tap below to review the terms.</i>",
                rows));
    }

    /**
     * Terms reminder for users who declined or haven't accepted yet.
     */
    public void showTermsReminder(Long chatId, CarpoolBot bot) {
        var rows = List.of(List.of(
                BotMessageBuilder.button("📄 Review Terms", "TERMS_WELCOME")
        ));
        bot.send(flowHelper.sendWithInline(chatId,
                "⚠️ <b>Terms Acceptance Required</b>\n\n" +
                        "You'll need to accept our community terms to use this bot.\n\n" +
                        "Tap below to review and accept.",
                rows));
    }

    // ── Admin ─────────────────────────────────────────────────────────────

    public void handleAdminStats(BotContext ctx) {
        if (!botConfig.isAdmin(ctx.telegramId())) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ You don't have permission to view this."));
            return;
        }

        AdminStatsService.AdminStats s = adminStatsService.getStats();

        String report = String.format(
                "📊 <b>Admin Stats</b>\n<i>%s</i>\n\n" +
                        "👥 <b>Users</b>\nTotal: <b>%d</b> | New today: <b>%d</b>\n\n" +
                        "🚗 <b>Rides</b>\nActive now: <b>%d</b> | Posted today: <b>%d</b>\n" +
                        "Total: <b>%d</b> | Completed: <b>%d</b> | Cancelled: <b>%d</b>\n\n" +
                        "📋 <b>Bookings</b>\nPending now: <b>%d</b> | Made today: <b>%d</b>\n" +
                        "Total: <b>%d</b> | Completed: <b>%d</b>",
                LocalDateTime.now(ZoneId.of("Asia/Manila"))
                        .format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")),
                s.totalUsers(), s.newUsersToday(),
                s.activeRidesNow(), s.ridesPostedToday(),
                s.totalRides(), s.completedRides(), s.cancelledRides(),
                s.pendingBookingsNow(), s.bookingsMadeToday(),
                s.totalBookings(), s.completedBookings());

        ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(), report,
                List.of(
                        List.of(
                                BotMessageBuilder.button("🔄 Refresh",       "ADMIN_STATS"),
                                BotMessageBuilder.button("🏘️ Pending Hubs", "PENDING_HUBS"),
                                BotMessageBuilder.button("🏠 Menu",          "MAIN_MENU")
                        )
                )));
    }

    public void handleReannounceRide(BotContext ctx) {
        try {
            RideResponse ride = rideService.reannounceRide(ctx.entityId(), ctx.carpoolUserId());
            int remaining = 3 - ride.announceCount();
            String remainingText = remaining == 0
                    ? "No more re-announcements available."
                    : remaining + " re-announcement" +
                      (remaining == 1 ? "" : "s") + " remaining.";

            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "📢 <b>Ride Re-announced!</b>\n\n" +
                            "Your ride has been posted to the group again.\n" +
                            "<i>" + remainingText + "</i>"));
        } catch (Exception e) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not re-announce ride: " +
                            HtmlEscapeUtil.escape(e.getMessage())));
        }
    }

    // ── Hub admin ─────────────────────────────────────────────────────────

    private static final int HUB_PAGE_SIZE = 5;

    public void handlePendingHubs(BotContext ctx) {
        if (!botConfig.isAdmin(ctx.telegramId())) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ You don't have permission to view this."));
            return;
        }

        int page = 0;
        try {
            if (ctx.payload() != null) page = Integer.parseInt(ctx.payload());
        } catch (NumberFormatException ignored) {}

        List<HubResponse> pending = hubService.getPendingHubs();

        if (pending.isEmpty()) {
            ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                    "🏘️ <b>Pending Hub Suggestions</b>\n\n<i>No pending hubs at the moment.</i>",
                    List.of(List.of(
                            BotMessageBuilder.button("🔄 Refresh", "PENDING_HUBS"),
                            BotMessageBuilder.button("🏠 Menu",    "MAIN_MENU")
                    ))));
            return;
        }

        int totalPages = (int) Math.ceil((double) pending.size() / HUB_PAGE_SIZE);
        int safePage   = Math.max(0, Math.min(page, totalPages - 1));
        int fromIdx    = safePage * HUB_PAGE_SIZE;
        int toIdx      = Math.min(fromIdx + HUB_PAGE_SIZE, pending.size());
        List<HubResponse> pageItems = pending.subList(fromIdx, toIdx);

        StringBuilder sb = new StringBuilder("🏘️ <b>Pending Hub Suggestions (")
                .append(pending.size()).append(")</b>");
        sb.append(" — Page ").append(safePage + 1).append("/").append(totalPages);
        sb.append("\n\n");

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int i = 0; i < pageItems.size(); i++) {
            HubResponse hub = pageItems.get(i);
            int globalNum = fromIdx + i + 1;
            sb.append(String.format("%d. <b>%s</b> — %s\n   ID: %d\n\n",
                    globalNum,
                    HtmlEscapeUtil.escape(hub.name()),
                    HtmlEscapeUtil.escape(hub.area()),
                    hub.id()));
            rows.add(List.of(
                    BotMessageBuilder.button("✅ #" + globalNum, "APPROVE_HUB:" + hub.id()),
                    BotMessageBuilder.button("❌ #" + globalNum, "REJECT_HUB:"  + hub.id())
            ));
        }

        // Pagination nav row
        if (totalPages > 1) {
            List<InlineKeyboardButton> nav = new ArrayList<>();
            if (safePage > 0) {
                nav.add(BotMessageBuilder.button("◀️ Prev", "PENDING_HUBS:" + (safePage - 1)));
            }
            nav.add(BotMessageBuilder.button(
                    "📄 " + (safePage + 1) + "/" + totalPages, "NOOP"));
            if (safePage < totalPages - 1) {
                nav.add(BotMessageBuilder.button("Next ▶️", "PENDING_HUBS:" + (safePage + 1)));
            }
            rows.add(nav);
        }

        rows.add(List.of(
                BotMessageBuilder.button("🔄 Refresh", "PENDING_HUBS:" + safePage),
                BotMessageBuilder.button("🏠 Menu",    "MAIN_MENU")
        ));

        ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(), sb.toString().trim(), rows));
    }

    public void handleApproveHub(BotContext ctx) {
        if (!botConfig.isAdmin(ctx.telegramId())) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ You don't have permission to do this."));
            return;
        }
        Long hubId = ctx.entityId();
        if (hubId == null) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(), "⚠️ Invalid hub ID."));
            return;
        }
        try {
            HubResponse hub = hubService.approveHub(hubId, null);
            ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                    String.format("✅ <b>Hub Approved!</b>\n\n" +
                            "📍 <b>%s</b> — %s\n🔑 Code: <code>%s</code>",
                            HtmlEscapeUtil.escape(hub.name()),
                            HtmlEscapeUtil.escape(hub.area()),
                            hub.code()),
                    List.of(List.of(
                            BotMessageBuilder.button("🏘️ View Pending", "PENDING_HUBS"),
                            BotMessageBuilder.button("🏠 Menu",          "MAIN_MENU")
                    ))));
        } catch (Exception e) {
            log.error("Failed to approve hub id={}: {}", hubId, e.getMessage());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not approve hub. " + HtmlEscapeUtil.escape(e.getMessage())));
        }
    }

    public void handleRejectHub(BotContext ctx) {
        if (!botConfig.isAdmin(ctx.telegramId())) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ You don't have permission to do this."));
            return;
        }
        Long hubId = ctx.entityId();
        if (hubId == null) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(), "⚠️ Invalid hub ID."));
            return;
        }
        try {
            hubService.rejectHub(hubId);
            ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                    "❌ <b>Hub Rejected.</b>\n\nThe suggestion has been marked as rejected.",
                    List.of(List.of(
                            BotMessageBuilder.button("🏘️ View Pending", "PENDING_HUBS"),
                            BotMessageBuilder.button("🏠 Menu",          "MAIN_MENU")
                    ))));
        } catch (Exception e) {
            log.error("Failed to reject hub id={}: {}", hubId, e.getMessage());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not reject hub. " + HtmlEscapeUtil.escape(e.getMessage())));
        }
    }
}