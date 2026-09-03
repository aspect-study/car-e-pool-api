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
import com.carpool.bot.util.ButtonStyle;
import com.carpool.common.exception.InvalidRideStateException;
import com.carpool.common.util.HtmlEscapeUtil;
import com.carpool.domain.entity.User;
import com.carpool.repository.UserRepository;
import com.carpool.service.admin.AdminStatsService;
import com.carpool.service.dto.response.FollowerResponse;
import com.carpool.service.dto.response.HubResponse;
import com.carpool.service.dto.response.ProfileStatsResponse;
import com.carpool.service.dto.response.RideResponse;
import com.carpool.service.favorite.FavoriteService;
import com.carpool.service.hub.HubService;
import com.carpool.service.profile.ProfileService;
import com.carpool.service.rating.RatingService;
import com.carpool.service.ride.RideService;
import com.carpool.service.vehicle.VehicleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
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
    private final BotFlowHelper     flowHelper;
    private final PostRideHelper    postRideHelper;
    private final BotConfig         botConfig;
    private final RatingService     ratingService;
    private final HubService        hubService;
    private final FavoriteService   favoriteService;

    private static final int               FOLLOWERS_PAGE_SIZE = 8;
    private static final DateTimeFormatter FOLLOWED_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy");

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
                    BotMessageBuilder.button("🔄 Refresh",    "MY_PROFILE", null),
                    BotMessageBuilder.button("🚘 My Vehicle", "VEHICLE_CHANGE", ButtonStyle.PRIMARY.toString()),
                    BotMessageBuilder.button("🏠 Menu",       "MAIN_MENU", ButtonStyle.PRIMARY.toString())
            ));
            if (stats.driverRidesPosted() != null) {
                long followerCount = favoriteService.getFollowerCount(ctx.carpoolUserId());
                profileRows.add(List.of(
                        BotMessageBuilder.button(
                                "👥 My Followers (" + followerCount + ")",
                                "MY_FOLLOWERS:0", ButtonStyle.PRIMARY.toString())));
            }
            if (isAdmin) {
                profileRows.add(List.of(
                        BotMessageBuilder.button("📊 Admin Stats",   "ADMIN_STATS",null),
                        BotMessageBuilder.button("🏘️ Pending Hubs", "PENDING_HUBS", null)));
            }

            ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(), sb.toString(), profileRows));

        } catch (Exception e) {
            log.error("Failed to load profile for userId={}: {}",
                    ctx.carpoolUserId(), e.getMessage());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not load profile. Please try again."));
        }
    }

    // ── Followers ─────────────────────────────────────────────────────────

    public void handleMyFollowers(BotContext ctx) {
        try {
            int page = 0;
            try {
                if (ctx.payload() != null) page = Integer.parseInt(ctx.payload());
            } catch (NumberFormatException ignored) {}

            List<FollowerResponse> all = favoriteService.getFollowers(ctx.carpoolUserId());

            if (all.isEmpty()) {
                ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                        """
                                👥 <b>My Followers</b>
                                
                                <i>No followers yet.</i>
                                
                                Followers are passengers who have saved you as a favorite driver. \
                                They receive a notification whenever you post a new ride.""",
                        List.of(List.of(
                                BotMessageBuilder.button("👤 My Profile", "MY_PROFILE", ButtonStyle.PRIMARY.toString()),
                                BotMessageBuilder.button("🏠 Menu",       "MAIN_MENU", null)
                        ))));
                return;
            }

            int totalPages = (int) Math.ceil((double) all.size() / FOLLOWERS_PAGE_SIZE);
            int safePage   = Math.clamp(page, 0, totalPages - 1);
            int fromIdx    = safePage * FOLLOWERS_PAGE_SIZE;
            int toIdx      = Math.min(fromIdx + FOLLOWERS_PAGE_SIZE, all.size());
            List<FollowerResponse> pageItems = all.subList(fromIdx, toIdx);

            StringBuilder sb = new StringBuilder("👥 <b>My Followers (")
                    .append(all.size()).append(")</b>");
            sb.append(" — Page ").append(safePage + 1).append("/").append(totalPages);
            sb.append("\n\n");

            for (int i = 0; i < pageItems.size(); i++) {
                FollowerResponse f = pageItems.get(i);
                sb.append(String.format("<b>%d.</b> %s%s\n   📅 Since %s\n\n",
                        fromIdx + i + 1,
                        HtmlEscapeUtil.escape(f.fullName()),
                        f.telegramHandle() != null
                                ? " (@" + HtmlEscapeUtil.escape(f.telegramHandle()) + ")" : "",
                        f.followedAt() != null
                                ? f.followedAt().format(FOLLOWED_FMT) : "—"));
            }

            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            if (totalPages > 1) {
                List<InlineKeyboardButton> nav = new ArrayList<>();
                if (safePage > 0) {
                    nav.add(BotMessageBuilder.button("◀️ Prev", "MY_FOLLOWERS:" + (safePage - 1), null));
                }
                nav.add(BotMessageBuilder.button(
                        "📄 " + (safePage + 1) + "/" + totalPages, "NOOP", null));
                if (safePage < totalPages - 1) {
                    nav.add(BotMessageBuilder.button("Next ▶️", "MY_FOLLOWERS:" + (safePage + 1), null));
                }
                rows.add(nav);
            }

            rows.add(List.of(
                    BotMessageBuilder.button("👤 My Profile", "MY_PROFILE", null),
                    BotMessageBuilder.button("🏠 Menu",       "MAIN_MENU", ButtonStyle.PRIMARY.toString())
            ));

            ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(), sb.toString().trim(), rows));

        } catch (Exception e) {
            log.error("Failed to load followers for userId={}: {}",
                    ctx.carpoolUserId(), e.getMessage());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not load followers. Please try again."));
        }
    }

    // ── Vehicle flows ─────────────────────────────────────────────────────

    /**
     * VEHICLE_CHANGE callback — shows vehicle management screen.
     * Outside post-ride flow this lets drivers view/add/remove their vehicles.
     */
    public void handleVehicleChange(BotContext ctx) {
        showVehicleManagementScreen(ctx.chatId(), ctx.carpoolUserId(), ctx.state(), ctx.bot());
    }

    /**
     * VEHICLE_CONFIRM_YES — legacy callback, repurposed to vehicle select step.
     * Still guarded so stale buttons from old sessions fail gracefully.
     */
    public void handleVehicleConfirmYes(BotContext ctx) {
        if (ctx.state().getDepartureTime() == null) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ This action is no longer valid. Please start again from the main menu."));
            stateManager.reset(ctx.chatId());
            return;
        }
        showVehicleSelectStep(ctx.chatId(), ctx.carpoolUserId(), ctx.state(), ctx.bot());
    }

    /**
     * VEHICLE_CONFIRM_SAVE — saves the newly entered vehicle, then returns to select screen.
     */
    @CacheEvict(value = "profileStats", key = "#ctx.carpoolUserId()")
    public void handleVehicleConfirmSave(BotContext ctx) {
        if (ctx.state().getPendingCarModel() == null
                || ctx.state().getPendingPlateNumber() == null) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Session expired. Please start again with /start."));
            return;
        }
        try {
            vehicleService.addVehicle(
                    ctx.carpoolUserId(),
                    ctx.state().getPendingCarModel(),
                    ctx.state().getPendingCarColor(),
                    ctx.state().getPendingPlateNumber(),
                    ctx.state().getPendingSeatCapacity() != null
                            ? ctx.state().getPendingSeatCapacity() : 4);

            UserState cleared = ctx.state()
                    .withPendingCarColor(null)
                    .withPendingCarModel(null)
                    .withPendingPlateNumber(null)
                    .withPendingSeatCapacity(null);

            if (ctx.state().getDepartureTime() != null) {
                ctx.bot().send(BotMessageBuilder.textNoMenu(ctx.chatId(),
                        "✅ Vehicle saved! Now select it for your ride."));
                showVehicleSelectStep(ctx.chatId(), ctx.carpoolUserId(), cleared, ctx.bot());
            } else {
                ctx.bot().send(BotMessageBuilder.textNoMenu(ctx.chatId(), "✅ Vehicle saved!"));
                stateManager.save(ctx.chatId(), cleared.withFlow(BotFlow.IDLE));
                showVehicleManagementScreen(ctx.chatId(), ctx.carpoolUserId(), cleared, ctx.bot());
            }

        } catch (Exception e) {
            log.error("Failed to save vehicle for userId={}: {}",
                    ctx.carpoolUserId(), e.getMessage());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not save vehicle. Please try again."));
        }
    }

    /**
     * VEHICLE_REMOVE:{vehicleId} — soft-deletes one specific vehicle.
     */
    public void handleVehicleRemove(BotContext ctx) {
        Long vehicleId = ctx.entityId();
        if (vehicleId == null) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(), "⚠️ Invalid vehicle."));
            return;
        }
        try {
            vehicleService.removeVehicle(vehicleId, ctx.carpoolUserId());
            ctx.bot().send(BotMessageBuilder.textNoMenu(ctx.chatId(), "✅ Vehicle removed."));
            showVehicleManagementScreen(ctx.chatId(), ctx.carpoolUserId(), ctx.state(), ctx.bot());
        } catch (Exception e) {
            log.error("Failed to remove vehicleId={} for userId={}: {}",
                    vehicleId, ctx.carpoolUserId(), e.getMessage());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not remove vehicle. Please try again."));
        }
    }

    /**
     * VEHICLE_SELECT:{vehicleId} — driver picks a vehicle for the ride being posted.
     */
    public void handleVehicleSelect(BotContext ctx) {
        Long vehicleId = ctx.entityId();
        if (vehicleId == null) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(), "⚠️ Invalid vehicle."));
            return;
        }

        // Guard against stale buttons — seats/contribution must be set
        if (ctx.state().getDepartureTime() == null
                || ctx.state().getOriginHubId() == null
                || ctx.state().getDestinationHubId() == null
                || ctx.state().getSeats() == null
                || ctx.state().getContribution() == null) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ This action is no longer valid. Please start again from the main menu."));
            stateManager.reset(ctx.chatId());
            return;
        }

        var vehicles = vehicleService.getActiveVehiclesForUser(ctx.carpoolUserId());
        var selected = vehicles.stream()
                .filter(v -> v.id().equals(vehicleId))
                .findFirst()
                .orElse(null);

        if (selected == null) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Vehicle not found. Please try again."));
            return;
        }

        String label = String.format("%s%s | 🔢 %s",
                selected.color() != null
                        ? HtmlEscapeUtil.escape(selected.color()) + " " : "",
                HtmlEscapeUtil.escape(selected.model()),
                HtmlEscapeUtil.escape(selected.plateNumber()));

        UserState updated = ctx.state()
                .withSelectedVehicleId(selected.id())
                .withSelectedVehicleLabel(label)
                .withFlow(BotFlow.POST_RIDE_CONFIRM);
        stateManager.save(ctx.chatId(), updated);
        postRideHelper.showConfirmation(ctx.chatId(), updated, ctx.bot());
    }

    /**
     * ADD_VEHICLE callback — starts the vehicle input flow.
     */
    public void handleAddVehicle(BotContext ctx) {
        UserState cleared = ctx.state()
                .withPendingCarColor(null)
                .withPendingCarModel(null)
                .withPendingPlateNumber(null)
                .withPendingSeatCapacity(null)
                .withFlow(BotFlow.SET_VEHICLE_COLOR);
        stateManager.save(ctx.chatId(), cleared);
        String colorPrompt = """
                🎨 <b>What color is your vehicle?</b>
                
                Example: <code>Silver</code>, <code>White</code>, <code>Black</code>""";
        if (ctx.state().getDepartureTime() != null) {
            ctx.bot().send(BotMessageBuilder.textWithCancel(ctx.chatId(), colorPrompt));
        } else {
            ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(), colorPrompt, List.of(
                    List.of(BotMessageBuilder.button("❌ Cancel", "VEHICLE_CHANGE", null))
            )));
        }
    }

    /**
     * /vehicle command — shows multi-vehicle management screen.
     */
    public void handleVehicleCommand(Long chatId, Long carpoolUserId,
                                     UserState state, CarpoolBot bot) {
        showVehicleManagementScreen(chatId, carpoolUserId, state, bot);
    }

    /**
     * Shows the new-vehicle confirmation screen after the capacity step.
     * Displays pending vehicle details — Save & Continue or re-add.
     */
    public void showVehicleConfirmation(Long chatId, UserState state, CarpoolBot bot) {
        int capacity = state.getPendingSeatCapacity() != null ? state.getPendingSeatCapacity() : 4;
        String vehicleDisplay = String.format("%s%s | 🔢 %s | 💺 %d seats",
                state.getPendingCarColor() != null
                        ? "🎨 " + HtmlEscapeUtil.escape(state.getPendingCarColor()) + " "
                        : "",
                HtmlEscapeUtil.escape(state.getPendingCarModel()),
                HtmlEscapeUtil.escape(state.getPendingPlateNumber()),
                capacity);

        String cancelCallback = state.getDepartureTime() != null
                ? "CANCEL_POST_RIDE" : "VEHICLE_CHANGE";
        var rows = List.of(
                List.of(
                        BotMessageBuilder.button("✅ Save & Continue", "VEHICLE_CONFIRM_SAVE", ButtonStyle.SUCCESS.toString()),
                        BotMessageBuilder.button("✏️ Re-enter",        "ADD_VEHICLE", ButtonStyle.PRIMARY.toString())
                ),
                List.of(BotMessageBuilder.button("❌ Cancel", cancelCallback, ButtonStyle.DANGER.toString()))
        );
        bot.send(flowHelper.sendWithInline(chatId,
                "🚘 <b>New Vehicle</b>\n\n" + vehicleDisplay + "\n\nSave this vehicle?",
                rows));
    }

    /**
     * Vehicle selection step in the post-ride flow.
     * Shows existing vehicles as selection buttons, plus "Add New Vehicle".
     * If no vehicles saved yet → jumps straight to the vehicle input flow.
     */
    public void showVehicleSelectStep(Long chatId, Long carpoolUserId,
                                      UserState state, CarpoolBot bot) {
        var vehicles = vehicleService.getActiveVehiclesForUser(carpoolUserId);

        if (vehicles.isEmpty()) {
            // No vehicles — go straight to add flow
            UserState addState = state
                    .withPendingCarColor(null)
                    .withPendingCarModel(null)
                    .withPendingPlateNumber(null)
                    .withPendingSeatCapacity(null)
                    .withFlow(BotFlow.SET_VEHICLE_COLOR);
            stateManager.save(chatId, addState);
            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    """
                            🚘 <b>No vehicle saved yet.</b>
                            
                            Let's add your vehicle first.
                            
                            🎨 <b>What color is your vehicle?</b>
                            
                            Example: <code>Silver</code>, <code>White</code>, <code>Black</code>"""));
            return;
        }

        UserState updated = state.withFlow(BotFlow.POST_RIDE_VEHICLE_SELECT);
        stateManager.save(chatId, updated);

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (var v : vehicles) {
            String label = String.format("🚘 %s%s | 🔢 %s",
                    v.color() != null ? v.color() + " " : "",
                    v.model(),
                    v.plateNumber());
            rows.add(List.of(BotMessageBuilder.button(label, "VEHICLE_SELECT:" + v.id(), ButtonStyle.SUCCESS.toString())));
        }

        if (vehicles.size() < 3) {
            rows.add(List.of(BotMessageBuilder.button("➕ Add New Vehicle", "ADD_VEHICLE", ButtonStyle.PRIMARY.toString())));
        }

        rows.add(List.of(BotMessageBuilder.button("❌ Cancel", "CANCEL_POST_RIDE", ButtonStyle.DANGER.toString())));

        bot.send(flowHelper.sendWithInline(chatId,
                """
                        🚘 <b>Select Vehicle for This Ride</b>
                        
                        Choose one of your saved vehicles:""",
                rows));
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private void showVehicleManagementScreen(Long chatId, Long carpoolUserId,
                                             UserState ignoredState, CarpoolBot bot) {
        var vehicles = vehicleService.getActiveVehiclesForUser(carpoolUserId);

        if (vehicles.isEmpty()) {
            var rows = List.of(
                    List.of(BotMessageBuilder.button("➕ Add Vehicle", "ADD_VEHICLE", ButtonStyle.SUCCESS.toString())),
                    List.of(BotMessageBuilder.button("🏠 Menu",        "MAIN_MENU", ButtonStyle.PRIMARY.toString()))
            );
            bot.send(flowHelper.sendWithInline(chatId,
                    """
                            🚘 <b>My Vehicles</b>
                            
                            <i>No vehicles saved yet.</i>
                            
                            Add up to 3 vehicles.""",
                    rows));
            return;
        }

        StringBuilder sb = new StringBuilder("🚘 <b>My Vehicles (")
                .append(vehicles.size()).append("/3)</b>\n\n");

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int i = 0; i < vehicles.size(); i++) {
            var v = vehicles.get(i);
            sb.append(String.format("%d. %s%s | 🔢 %s · 💺 %d seats\n",
                    i + 1,
                    v.color() != null ? "🎨 " + HtmlEscapeUtil.escape(v.color()) + " " : "",
                    HtmlEscapeUtil.escape(v.model()),
                    HtmlEscapeUtil.escape(v.plateNumber()),
                    v.seatCapacity()));
            rows.add(List.of(BotMessageBuilder.button(
                    "🗑️ Remove #" + (i + 1), "VEHICLE_REMOVE:" + v.id(), ButtonStyle.DANGER.toString())));
        }

        if (vehicles.size() < 3) {
            rows.add(List.of(BotMessageBuilder.button("➕ Add Vehicle", "ADD_VEHICLE", ButtonStyle.SUCCESS.toString())));
        }
        rows.add(List.of(BotMessageBuilder.button("🏠 Menu", "MAIN_MENU", ButtonStyle.PRIMARY.toString())));

        bot.send(flowHelper.sendWithInline(chatId, sb.toString().trim(), rows));
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
                        BotMessageBuilder.button("✅ I Accept",  "TERMS_ACCEPT", ButtonStyle.SUCCESS.toString()),
                        BotMessageBuilder.button("❌ Decline",   "TERMS_DECLINE", ButtonStyle.DANGER.toString())
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
                    """
                            🎉 <b>Welcome to the community!</b>
                            
                            Thank you for accepting the terms. You're now part of the \
                            Car-e-Pool Carpooling Community.
                            
                            Let's get started! 🚗"""));

            ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                    """
                            ━━━━━━━━━━━━━━━━━━━━━
                            👥 <b>JOIN OUR COMMUNITY GROUP</b>
                            ━━━━━━━━━━━━━━━━━━━━━
                            
                            🚗 Rides are posted in the group in real time.
                            📢 Drivers announce their rides there.
                            🔍 Passengers spot rides before they fill up.
                            
                            <b>Don't miss out — join now!</b>""",
                    List.of(
                            List.of(BotMessageBuilder.urlButton(
                                    "👥 Join the Group",
                                    botConfig.getGroupInviteLink())),
                            List.of(BotMessageBuilder.button(
                                    "▶️ I'm already in — Continue", "MAIN_MENU", ButtonStyle.SUCCESS.toString()))
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
        userRepository.findById(ctx.carpoolUserId()).ifPresent(user -> {
            user.setTermsDeclinedAt(LocalDateTime.now());
            userRepository.save(user);
        });
        var rows = List.of(List.of(
                BotMessageBuilder.button("🔁 Review Terms Again", "TERMS_VIEW_AGAIN", ButtonStyle.PRIMARY.toString())
        ));
        ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                """
                        We understand if you're not ready. 🙏
                        
                        You'll need to accept the terms to use this bot. \
                        You can review them again anytime.""",
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
                        BotMessageBuilder.button("📄 View Terms & Accept", "TERMS_WELCOME",  ButtonStyle.PRIMARY.toString()),
                        BotMessageBuilder.button("❌ Not Now",              "TERMS_DECLINE",  ButtonStyle.DANGER.toString())
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
                BotMessageBuilder.button("📄 Review Terms", "TERMS_WELCOME", ButtonStyle.PRIMARY.toString())
        ));
        bot.send(flowHelper.sendWithInline(chatId,
                """
                        ⚠️ <b>Terms Acceptance Required</b>
                        
                        You'll need to accept our community terms to use this bot.
                        
                        Tap below to review and accept.""",
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

        String ratingLine = s.avgPlatformRating() == null
                ? "No ratings yet"
                : String.format("%.1f ⭐ (%d ratings)", s.avgPlatformRating(), s.totalRatings());

        String report = String.format(
                """
                        📊 <b>Admin Stats</b>
                        <i>%s</i>

                        👥 <b>Users</b>
                        Total: <b>%d</b> | New today: <b>%d</b> | New this week: <b>%d</b>

                        🚗 <b>Rides</b>
                        Active now: <b>%d</b> | Posted today: <b>%d</b>
                        Total: <b>%d</b> | Completed: <b>%d</b> | Cancelled: <b>%d</b> (%.1f%%)

                        📋 <b>Bookings</b>
                        Pending now: <b>%d</b> | Made today: <b>%d</b>
                        Total: <b>%d</b> | Completed: <b>%d</b> (%.1f%%)
                        Declined: <b>%d</b> | Cancelled (driver/passenger): <b>%d</b>/<b>%d</b> | Timed out: <b>%d</b>

                        🏘️ <b>Community</b>
                        Pending hub suggestions: <b>%d</b>
                        Avg rating: <b>%s</b>

                        💙 <b>Donations</b>
                        GCash button taps: <b>%d</b> (<b>%d</b> unique users)""",
                LocalDateTime.now(ZoneId.of("Asia/Manila"))
                        .format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")),
                s.totalUsers(), s.newUsersToday(), s.newUsersThisWeek(),
                s.activeRidesNow(), s.ridesPostedToday(),
                s.totalRides(), s.completedRides(), s.cancelledRides(), s.cancellationRate(),
                s.pendingBookingsNow(), s.bookingsMadeToday(),
                s.totalBookings(), s.completedBookings(), s.bookingCompletionRate(),
                s.declinedBookings(), s.cancelledByDriverBookings(), s.cancelledByPassengerBookings(), s.timedOutBookings(),
                s.pendingHubSuggestions(), ratingLine,
                s.gcashButtonClicks(), s.gcashCuriousUsers());

        ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(), report,
                List.of(
                        List.of(
                                BotMessageBuilder.button("🔄 Refresh",       "ADMIN_STATS", ButtonStyle.PRIMARY.toString()),
                                BotMessageBuilder.button("🏘️ Pending Hubs", "PENDING_HUBS", ButtonStyle.PRIMARY.toString()),
                                BotMessageBuilder.button("🏠 Menu",          "MAIN_MENU", ButtonStyle.SUCCESS.toString())
                        )
                )));
    }

    private int remainingAnnouncements(Integer announceCount) {
        return Math.max(0, 10 - announceCount);
    }

    private String remainingAnnouncementsText(Integer announceCount) {
        int remaining = remainingAnnouncements(announceCount);
        return remaining == 0
                ? "No more re-announcements available."
                : remaining + " re-announcement" + (remaining == 1 ? "" : "s") + " remaining.";
    }

    public void handleReannounceRide(BotContext ctx) {
        try {
            RideResponse ride = rideService.getRideById(ctx.entityId());
            if (!ride.driver().id().equals(ctx.carpoolUserId())) {
                ctx.bot().send(BotMessageBuilder.text(ctx.chatId(), "⚠️ This is not your ride."));
                return;
            }
            int remaining = remainingAnnouncements(ride.announceCount());
            String prompt = String.format(
                    """
                            📢 <b>Re-announce Ride</b>

                            Currently showing: <b>%d seat(s) available</b>

                            Re-announce as-is, or update the seat count first?
                            <i>%d re-announcement(s) remaining.</i>""",
                    ride.availableSeats(), remaining);
            var rows = List.of(
                    List.of(
                            BotMessageBuilder.button(
                                    "📢 Re-announce (" + ride.availableSeats() + " seats)",
                                    "CONFIRM_REANNOUNCE:" + ctx.entityId(), ButtonStyle.SUCCESS.toString()),
                            BotMessageBuilder.button("✏️ Edit Seats",
                                    "REANNOUNCE_EDIT_SEATS:" + ctx.entityId(), ButtonStyle.PRIMARY.toString())
                    ),
                    List.of(
                            BotMessageBuilder.button("🚘 Update Total Seats",
                                    "REANNOUNCE_UPDATE_TOTAL_SEATS:" + ctx.entityId(), ButtonStyle.PRIMARY.toString())
                    ),
                    List.of(BotMessageBuilder.button("◀️ Cancel", "MAIN_MENU", ButtonStyle.DANGER.toString()))
            );
            ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(), prompt, rows));
        } catch (Exception e) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not load ride. Please try again."));
        }
    }

    public void handleConfirmReannounce(BotContext ctx) {
        try {
            RideResponse ride = rideService.reannounceRide(ctx.entityId(), ctx.carpoolUserId());
            String remainingText = remainingAnnouncementsText(ride.announceCount());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "📢 <b>Ride Re-announced!</b>\n\n" +
                            "Your ride has been posted to the group again.\n" +
                            "<i>" + remainingText + "</i>"));
        } catch (Exception e) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not re-announce ride. Please try again."));
        }
    }

    public void handleReannounceEditSeatsStart(BotContext ctx) {
        try {
            RideResponse ride = rideService.getRideById(ctx.entityId());
            if (!ride.driver().id().equals(ctx.carpoolUserId())) {
                ctx.bot().send(BotMessageBuilder.text(ctx.chatId(), "⚠️ This is not your ride."));
                return;
            }
            stateManager.save(ctx.chatId(), ctx.state()
                    .withSelectedRideId(ctx.entityId())
                    .withFlow(BotFlow.REANNOUNCE_EDIT_SEATS));
            ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                    String.format(
                            "✏️ <b>Edit Seat Count</b>\n\n" +
                            "Current: <b>%d available</b> of %d total\n\n" +
                            "Enter the new available seat count (0–%d):",
                            ride.availableSeats(), ride.totalSeats(), ride.availableSeats()),
                    List.of(List.of(
                            BotMessageBuilder.button("◀️ Cancel", "MAIN_MENU", ButtonStyle.PRIMARY.toString())
                    ))));
        } catch (Exception e) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not load ride. Please try again."));
        }
    }

    public void handleReannounceEditSeatsText(Long chatId, String text,
                                              UserState state, Long carpoolUserId,
                                              CarpoolBot bot) {
        Long rideId = state.getSelectedRideId();
        if (rideId == null) {
            stateManager.reset(chatId);
            bot.send(BotMessageBuilder.text(chatId, "⚠️ Session expired. Please try again."));
            return;
        }
        try {
            int newSeats = Integer.parseInt(text.trim());
            rideService.updateAvailableSeats(rideId, newSeats, carpoolUserId);

            if (newSeats == 0) {
                // Ride is now FULL — reannounce fires RidePostedEvent which removes the group post
                rideService.reannounceRide(rideId, carpoolUserId);
                stateManager.save(chatId, state.withFlow(BotFlow.IDLE).withSelectedRideId(null));
                bot.send(BotMessageBuilder.text(chatId,
                        "🚫 <b>Ride Marked as Full</b>\n\n" +
                                "Your ride now shows <b>0 available seats</b>.\n" +
                                "The group announcement has been removed."));
            } else {
                RideResponse ride = rideService.reannounceRide(rideId, carpoolUserId);
                stateManager.save(chatId, state.withFlow(BotFlow.IDLE).withSelectedRideId(null));
                String remainingText = remainingAnnouncementsText(ride.announceCount());
                bot.send(BotMessageBuilder.text(chatId,
                        "📢 <b>Ride Re-announced!</b>\n\n" +
                                "Seat count updated to <b>" + newSeats + "</b> and ride posted to group.\n" +
                                "<i>" + remainingText + "</i>"));
            }
        } catch (NumberFormatException e) {
            bot.send(BotMessageBuilder.text(chatId, "⚠️ Please enter a valid number."));
        } catch (InvalidRideStateException e) {
            bot.send(BotMessageBuilder.text(chatId, "⚠️ " + e.getMessage()));
        } catch (Exception e) {
            log.warn("Reannounce seat edit failed for rideId={}: {}", rideId, e.getMessage());
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Could not update seat count. Please try again."));
        }
    }

    public void handleReannounceUpdateTotalSeatsStart(BotContext ctx) {
        try {
            RideResponse ride = rideService.getRideById(ctx.entityId());
            if (!ride.driver().id().equals(ctx.carpoolUserId())) {
                ctx.bot().send(BotMessageBuilder.text(ctx.chatId(), "⚠️ This is not your ride."));
                return;
            }
            int reservedSeats = rideService.getReservedSeatsCount(ctx.entityId());
            int minSeats = Math.max(1, reservedSeats);
            stateManager.save(ctx.chatId(), ctx.state()
                    .withSelectedRideId(ctx.entityId())
                    .withFlow(BotFlow.REANNOUNCE_UPDATE_TOTAL_SEATS));
            ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                    String.format(
                            "🚘 <b>Update Total Seats</b>\n\n" +
                            "Current: <b>%d total</b> (%d reserved, %d available)\n\n" +
                            "Enter the new total seat count (min %d, max 8):",
                            ride.totalSeats(), reservedSeats, ride.availableSeats(), minSeats),
                    List.of(List.of(
                            BotMessageBuilder.button("◀️ Cancel", "MAIN_MENU", ButtonStyle.PRIMARY.toString())
                    ))));
        } catch (Exception e) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not load ride. Please try again."));
        }
    }

    public void handleReannounceUpdateTotalSeatsText(Long chatId, String text,
                                                      UserState state, Long carpoolUserId,
                                                      CarpoolBot bot) {
        Long rideId = state.getSelectedRideId();
        if (rideId == null) {
            stateManager.reset(chatId);
            bot.send(BotMessageBuilder.text(chatId, "⚠️ Session expired. Please try again."));
            return;
        }
        try {
            int newTotalSeats = Integer.parseInt(text.trim());
            RideResponse ride = rideService.updateTotalSeatsAndReannounce(rideId, newTotalSeats, carpoolUserId);
            stateManager.save(chatId, state.withFlow(BotFlow.IDLE).withSelectedRideId(null));

            String remainingText = remainingAnnouncementsText(ride.announceCount());
            bot.send(BotMessageBuilder.text(chatId,
                    "🚘 <b>Total Seats Updated!</b>\n\n" +
                            "Ride now has <b>" + newTotalSeats + " total</b> seat(s), <b>" +
                            ride.availableSeats() + " available</b>, and has been posted to group.\n" +
                            "<i>" + remainingText + "</i>"));
        } catch (NumberFormatException e) {
            bot.send(BotMessageBuilder.text(chatId, "⚠️ Please enter a valid number."));
        } catch (InvalidRideStateException e) {
            bot.send(BotMessageBuilder.text(chatId, "⚠️ " + e.getMessage()));
        } catch (Exception e) {
            log.warn("Reannounce total seats update failed for rideId={}: {}", rideId, e.getMessage());
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Could not update total seats. Please try again."));
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
                            BotMessageBuilder.button("🔄 Refresh", "PENDING_HUBS", null),
                            BotMessageBuilder.button("🏠 Menu",    "MAIN_MENU", null)
                    ))));
            return;
        }

        int totalPages = (int) Math.ceil((double) pending.size() / HUB_PAGE_SIZE);
        int safePage   = Math.clamp(page, 0, totalPages - 1);
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
                    BotMessageBuilder.button("✅ #" + globalNum, "APPROVE_HUB:" + hub.id(), null),
                    BotMessageBuilder.button("❌ #" + globalNum, "REJECT_HUB:"  + hub.id(), null)
            ));
        }

        // Pagination nav row
        if (totalPages > 1) {
            List<InlineKeyboardButton> nav = new ArrayList<>();
            if (safePage > 0) {
                nav.add(BotMessageBuilder.button("◀️ Prev", "PENDING_HUBS:" + (safePage - 1), null));
            }
            nav.add(BotMessageBuilder.button(
                    "📄 " + (safePage + 1) + "/" + totalPages, "NOOP", null));
            if (safePage < totalPages - 1) {
                nav.add(BotMessageBuilder.button("Next ▶️", "PENDING_HUBS:" + (safePage + 1), null));
            }
            rows.add(nav);
        }

        rows.add(List.of(
                BotMessageBuilder.button("✅ Approve All (" + pending.size() + ")", "APPROVE_ALL_HUBS", null)
        ));
        rows.add(List.of(
                BotMessageBuilder.button("🔄 Refresh", "PENDING_HUBS:" + safePage, null),
                BotMessageBuilder.button("🏠 Menu",    "MAIN_MENU", null)
        ));

        ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(), sb.toString().trim(), rows));
    }

    public void handleApproveAllHubs(BotContext ctx) {
        if (!botConfig.isAdmin(ctx.telegramId())) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ You don't have permission to do this."));
            return;
        }

        int pendingCount = hubService.getPendingHubs().size();
        if (pendingCount == 0) {
            ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                    "🏘️ <b>Pending Hub Suggestions</b>\n\n<i>No pending hubs at the moment.</i>",
                    List.of(List.of(
                            BotMessageBuilder.button("🔄 Refresh", "PENDING_HUBS", null),
                            BotMessageBuilder.button("🏠 Menu",    "MAIN_MENU", null)
                    ))));
            return;
        }

        ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                String.format("⚠️ <b>Approve all %d pending hubs?</b>\n\n" +
                        "Each will be auto-assigned a code and become active immediately. This cannot be undone from this menu.",
                        pendingCount),
                List.of(List.of(
                        BotMessageBuilder.button("✅ Yes, Approve All", "CONFIRM_APPROVE_ALL_HUBS", null),
                        BotMessageBuilder.button("❌ Cancel", "PENDING_HUBS", null)
                ))));
    }

    public void handleConfirmApproveAllHubs(BotContext ctx) {
        if (!botConfig.isAdmin(ctx.telegramId())) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ You don't have permission to do this."));
            return;
        }

        try {
            List<HubResponse> approved = hubService.approveAllPendingHubs();
            if (approved.isEmpty()) {
                ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                        "🏘️ <b>Pending Hub Suggestions</b>\n\n<i>No pending hubs at the moment.</i>",
                        List.of(List.of(
                                BotMessageBuilder.button("🔄 Refresh", "PENDING_HUBS", null),
                                BotMessageBuilder.button("🏠 Menu",    "MAIN_MENU", null)
                        ))));
                return;
            }

            StringBuilder sb = new StringBuilder(String.format(
                    "✅ <b>Approved %d Hub%s!</b>\n\n", approved.size(), approved.size() == 1 ? "" : "s"));
            for (HubResponse hub : approved) {
                sb.append(String.format("• <b>%s</b> — <code>%s</code>\n",
                        HtmlEscapeUtil.escape(hub.name()), hub.code()));
            }

            ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(), sb.toString().trim(),
                    List.of(List.of(
                            BotMessageBuilder.button("🏘️ View Pending", "PENDING_HUBS", null),
                            BotMessageBuilder.button("🏠 Menu",          "MAIN_MENU", null)
                    ))));
        } catch (Exception e) {
            log.error("Failed to bulk-approve pending hubs: {}", e.getMessage(), e);
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not approve all hubs. Please try again."));
        }
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
                    String.format("""
                                    ✅ <b>Hub Approved!</b>
                                    
                                    📍 <b>%s</b> — %s
                                    🔑 Code: <code>%s</code>""",
                            HtmlEscapeUtil.escape(hub.name()),
                            HtmlEscapeUtil.escape(hub.area()),
                            hub.code()),
                    List.of(List.of(
                            BotMessageBuilder.button("🏘️ View Pending", "PENDING_HUBS", null),
                            BotMessageBuilder.button("🏠 Menu",          "MAIN_MENU", null)
                    ))));
        } catch (Exception e) {
            log.error("Failed to approve hub id={}: {}", hubId, e.getMessage());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not approve hub. Please try again."));
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
                            BotMessageBuilder.button("🏘️ View Pending", "PENDING_HUBS", null),
                            BotMessageBuilder.button("🏠 Menu",          "MAIN_MENU", null)
                    ))));
        } catch (Exception e) {
            log.error("Failed to reject hub id={}: {}", hubId, e.getMessage());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not reject hub. Please try again."));
        }
    }
}