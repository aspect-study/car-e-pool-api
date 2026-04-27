package com.carpool.bot.handler;

import com.carpool.bot.CarpoolBot;
import com.carpool.bot.state.BotFlow;
import com.carpool.bot.state.StateManager;
import com.carpool.bot.state.UserState;
import com.carpool.bot.util.BotMessageBuilder;
import com.carpool.domain.entity.DriverNote;
import com.carpool.domain.enums.BookingStatus;
import com.carpool.domain.enums.RideDirection;
import com.carpool.domain.enums.RideStatus;
import com.carpool.repository.UserRepository;
import com.carpool.service.booking.BookingService;
import com.carpool.service.dto.request.CreateBookingRequest;
import com.carpool.service.dto.request.CreateRideRequest;
import com.carpool.service.dto.request.UpdateRideStatusRequest;
import com.carpool.service.dto.response.BookingResponse;
import com.carpool.service.dto.response.RideResponse;
import com.carpool.service.note.DriverNoteService;
import com.carpool.service.profile.ProfileService;
import com.carpool.service.ride.RideService;
import com.carpool.service.vehicle.VehicleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CallbackHandler {

    private final StateManager   stateManager;
    private final UserRepository userRepository;
    private final RideService    rideService;
    private final BookingService bookingService;
    private final PostRideHelper postRideHelper;
    private final DriverNoteService driverNoteService;
    private final ProfileService profileService;
    private final VehicleService vehicleService;

    public void handle(CallbackQuery callback, CarpoolBot bot) {
        Long chatId     = callback.getMessage().getChatId();
        Long telegramId = callback.getFrom().getId();
        String data     = callback.getData();

        bot.answerCallback(callback.getId());

        var userOpt = userRepository.findByTelegramId(telegramId);
        if (userOpt.isEmpty()) {
            bot.send(BotMessageBuilder.text(chatId, "⚠️ Please register first via /start"));
            return;
        }

        Long carpoolUserId = userOpt.get().getId();
        UserState state    = stateManager.get(chatId);
        if (state == null) {
            state = UserState.initial(carpoolUserId);
        }

        String[] parts   = data.split(":");
        String   action  = parts[0];
        String   payload = parts.length > 1 ? parts[1] : null;

        Long entityId = null;
        if (payload != null) {
            try {
                entityId = Long.parseLong(payload);
            } catch (NumberFormatException e) {
                // string payload — handled per case
            }
        }

        switch (action) {
            case "MAIN_MENU"            -> handleMainMenu(chatId, carpoolUserId, state, bot);
            case "POST_RIDE"            -> handleStartPostRide(chatId, carpoolUserId, state, bot);
            case "FIND_RIDE"            -> handleStartFindRide(chatId, carpoolUserId, state, bot);
            case "CONFIRM_POST_RIDE"    -> handleConfirmPostRide(chatId, carpoolUserId, state, bot);
            case "CANCEL_POST_RIDE"     -> handleCancelPostRide(chatId, bot);
            case "VIEW_RIDE"            -> handleViewRide(chatId, entityId, carpoolUserId, state, bot);
            case "BOOK_RIDE"            -> handleBookRide(chatId, entityId, carpoolUserId, bot);
            case "CANCEL_RIDE"          -> handleCancelRide(chatId, entityId, carpoolUserId, bot);
            case "CONFIRM_CANCEL_RIDE"  -> handleConfirmCancelRide(chatId, parts, carpoolUserId, bot);
            case "RIDE_BOOKINGS"        -> handleDriverBookings(chatId, carpoolUserId, bot);
            case "MY_BOOKINGS"          -> handleMyBookings(chatId, carpoolUserId, bot);
            case "REPOST_RIDE"          -> handleRepostRide(chatId, entityId, carpoolUserId, state, bot);
            case "VIEW_BOOKING"         -> handleViewBooking(chatId, entityId, carpoolUserId, state, bot);
            case "PAST_BOOKINGS"        -> handlePastBookings(chatId, carpoolUserId, bot);
            case "DRIVER_BOOKINGS"      -> handleDriverBookings(chatId, carpoolUserId, bot);
            case "TIME"                 -> handleTimeSelection(chatId, payload, carpoolUserId, state, bot);
            case "CANCEL_BOOKING"       -> handleCancelBooking(chatId, entityId, carpoolUserId, bot);
            case "CANCEL_BOOKING_REASON" -> handleCancelBookingWithReason(chatId, parts, carpoolUserId, bot);
            case "DEPART_RIDE"          -> handleDepartRide(chatId, entityId, carpoolUserId, bot);
            case "COMPLETE_RIDE"        -> handleCompleteRide(chatId, entityId, carpoolUserId, bot);
            case "VIEW_DRIVER_BOOKING"  -> handleViewDriverBooking(chatId, entityId, carpoolUserId, bot);
            case "DIRECTION"            -> handleDirectionCallback(chatId, payload, carpoolUserId, state, bot);
            case "NOTE_PREVIEW"         -> handleNotePreview(chatId, entityId, carpoolUserId, state, bot);
            case "NOTE_APPLY"           -> handleNoteApply(chatId, entityId, carpoolUserId, state, bot);
            case "NOTE_WRITE"           -> handleNoteWrite(chatId, carpoolUserId, state, bot);
            case "NOTE_CHOOSE_OTHER"    -> handleNoteChooseOther(chatId, carpoolUserId, state, bot);
            case "SKIP_NOTES"           -> handleSkipNotes(chatId, carpoolUserId, state, bot);
            case "ACCEPT_BOOKING"       -> handleAcceptBooking(chatId, entityId, carpoolUserId, bot);
            case "DECLINE_BOOKING"      -> handleDeclineBooking(chatId, entityId, carpoolUserId, bot);
            case "DECLINE_BOOKING_REASON" -> handleDeclineBookingWithReason(chatId, parts, carpoolUserId, bot);
            case "PENDING_REQUESTS"     -> handlePendingRequests(chatId, carpoolUserId, bot);
            case "VIEW_PENDING"         -> handleViewPendingRequest(chatId, entityId, carpoolUserId, bot);
            case "BOOK_NOW"             -> executeBooking(chatId, entityId, carpoolUserId, null, bot);
            case "HUB_ORIGIN"           -> handleHubOriginSelected(chatId, entityId, carpoolUserId, state, bot);
            case "HUB_DEST"             -> handleHubDestSelected(chatId, entityId, carpoolUserId, state, bot);
            case "RETYPE_ORIGIN"        -> handleRetypeOrigin(chatId, carpoolUserId, state, bot);
            case "RETYPE_DEST"          -> handleRetypeDest(chatId, carpoolUserId, state, bot);
            case "SEARCH_FILTER"        -> handleSearchFilter(chatId, carpoolUserId, state, bot);
            case "APPLY_FILTER"         -> handleApplyFilter(chatId, parts, carpoolUserId, state, bot);
            case "RESET_FILTER"         -> handleResetFilter(chatId, carpoolUserId, state, bot);
            case "RIDE_PAGE"            -> handleRidePage(chatId, payload, carpoolUserId, state, bot);
            case "MY_RIDES"             -> showMyRides(chatId, carpoolUserId, bot);
            case "MY_PROFILE"           -> handleMyProfile(chatId, carpoolUserId, bot);
            case "VEHICLE_CONFIRM_YES"  -> handleVehicleConfirmYes(chatId, carpoolUserId, state, bot);
            case "VEHICLE_CONFIRM_SAVE" -> handleVehicleConfirmSave(chatId, carpoolUserId, state, bot);
            case "VEHICLE_CHANGE"       -> handleVehicleChange(chatId, carpoolUserId, state, bot);
            case "VEHICLE_REMOVE"       -> handleVehicleRemove(chatId, carpoolUserId, bot);
            case "TERMS_WELCOME"        -> handleTermsWelcome(chatId, bot);
            case "TERMS_ACCEPT"         -> handleTermsAccept(chatId, carpoolUserId, bot);
            case "TERMS_DECLINE"        -> handleTermsDecline(chatId, bot);
            case "TERMS_VIEW_AGAIN"     -> handleTermsWelcome(chatId, bot);
            case "NOOP"                 -> { /* page indicator button — do nothing */ }
            default -> {
                log.warn("Unknown callback action: {} from chatId={}", action, chatId);
                bot.send(BotMessageBuilder.text(chatId, "⚠️ Unknown action."));
            }
        }
    }

    // ── Main menu ─────────────────────────────────────────────────────────

    private void handleMainMenu(Long chatId, Long carpoolUserId,
                                UserState state, CarpoolBot bot) {
        stateManager.reset(chatId);
        List<RideResponse> myRides = rideService.getMyRides(carpoolUserId);
        boolean hasActiveRide = myRides.stream()
                .anyMatch(r -> r.status().name().equals("ACTIVE")
                        || r.status().name().equals("FULL")
                        || r.status().name().equals("DEPARTED"));

        if (hasActiveRide) {
            RideResponse active = myRides.stream()
                    .filter(r -> r.status().name().equals("ACTIVE")
                            || r.status().name().equals("FULL")
                            || r.status().name().equals("DEPARTED"))
                    .findFirst().orElseThrow();

            String msg = "🚗 <b>Your Active Ride</b>\n\n" +
                    BotMessageBuilder.formatRideCard(active) +
                    "\n\nWhat would you like to do?";

            // After getting active ride, check pending count
            long pendingCount = bookingService.countPendingRequestsForDriver(carpoolUserId);

            var rows = active.status().name().equals("DEPARTED")
                    ? List.of(
                    List.of(
                            BotMessageBuilder.button("📋 View Bookings", "RIDE_BOOKINGS:" + active.id()),
                            BotMessageBuilder.button("✅ Complete Ride",  "COMPLETE_RIDE:" + active.id())
                    ),
                    List.of(
                            BotMessageBuilder.button("👤 My Profile", "MY_PROFILE")
                    )
            )
                    : pendingCount > 0
                      ? List.of(
                    List.of(
                            BotMessageBuilder.button("📋 View Bookings", "RIDE_BOOKINGS:" + active.id()),
                            BotMessageBuilder.button("🚀 Start Ride",    "DEPART_RIDE:"  + active.id())
                    ),
                    List.of(
                            BotMessageBuilder.button("⏳ Pending (" + pendingCount + ")", "PENDING_REQUESTS"),
                            BotMessageBuilder.button("❌ Cancel Ride", "CANCEL_RIDE:" + active.id())
                    ),
                    List.of(
                            BotMessageBuilder.button("🔍 Find a Ride", "FIND_RIDE")
                    ),
                    List.of(
                            BotMessageBuilder.button("👤 My Profile", "MY_PROFILE")
                    )
            )
                      : List.of(
                    List.of(
                            BotMessageBuilder.button("📋 View Bookings", "RIDE_BOOKINGS:" + active.id()),
                            BotMessageBuilder.button("🚀 Start Ride",    "DEPART_RIDE:"  + active.id())
                    ),
                    List.of(
                            BotMessageBuilder.button("❌ Cancel Ride", "CANCEL_RIDE:" + active.id())
                    ),
                    List.of(
                            BotMessageBuilder.button("🔍 Find a Ride", "FIND_RIDE")
                    ),
                    List.of(
                            BotMessageBuilder.button("👤 My Profile", "MY_PROFILE")
                    )
            );

            bot.send(sendWithInline(chatId, msg, rows));
        } else {
            List<BookingResponse> myBookings = bookingService.getMyBookings(carpoolUserId);
            boolean hasPastRides = rideService.getMyRides(carpoolUserId).stream()
                    .anyMatch(r -> r.status().name().equals("COMPLETED")
                            || r.status().name().equals("CANCELLED"));

            String prompt = (!myBookings.isEmpty() && hasPastRides)
                    ? "👋 What would you like to do?"
                    : "👋 Where are you headed today?";

            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            rows.add(List.of(
                    BotMessageBuilder.button("🏠 Home to Work", "DIRECTION:HOME_TO_WORK"),
                    BotMessageBuilder.button("🏢 Work to Home", "DIRECTION:WORK_TO_HOME")
            ));

            if (!myBookings.isEmpty()) {
                rows.add(List.of(
                        BotMessageBuilder.button(
                                "📜 My Bookings (" + myBookings.size() + ")", "MY_BOOKINGS")
                ));
            }

            if (hasPastRides) {
                rows.add(List.of(
                        BotMessageBuilder.button("🔄 Repost a Ride", "MY_RIDES")
                ));
            }

            rows.add(List.of(
                    BotMessageBuilder.button("👤 My Profile", "MY_PROFILE")
            ));

            bot.send(sendWithInline(chatId, prompt, rows));
        }
    }

    // ── Post ride ─────────────────────────────────────────────────────────

    private void handleStartPostRide(Long chatId, Long carpoolUserId,
                                     UserState state, CarpoolBot bot) {
        // Direction is required — ask if not yet set in state
        if (state.getDirection() == null) {
            bot.send(BotMessageBuilder.directionSelector(chatId,
                    "🚗 <b>Post a Ride</b>\n\nWhich direction is this ride?"));
            stateManager.save(chatId, state
                    .withCarpoolUserId(carpoolUserId)
                    .withFlow(BotFlow.POST_RIDE_DIRECTION));
            return;
        }

        String etdExample1 = state.getDirection() == RideDirection.HOME_TO_WORK
                ? LocalDateTime.now(ZoneId.of("Asia/Manila")).withHour(7).withMinute(30)
                  .format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))
                : LocalDateTime.now(ZoneId.of("Asia/Manila")).withHour(18).withMinute(0)
                  .format(DateTimeFormatter.ofPattern("MM/dd HH:mm"));

        bot.send(BotMessageBuilder.textWithCancel(chatId,
                "🕐 <b>What time are you leaving? (Start pickup time)</b>\n\n" +
                        "Format: <code>MM/DD HH:MM</code>\n" +
                        "Example: <code>" + etdExample1 + "</code>"));
        stateManager.save(chatId, state
                .withCarpoolUserId(carpoolUserId)
                .withFlow(BotFlow.POST_RIDE_DEPARTURE_TIME));
    }

    private void handleCancelPostRide(Long chatId, CarpoolBot bot) {
        stateManager.reset(chatId);
        bot.send(BotMessageBuilder.text(chatId,
                "❌ Ride posting cancelled."));
    }

    private void handleConfirmPostRide(Long chatId, Long carpoolUserId,
                                       UserState state, CarpoolBot bot) {
        if (state == null || state.getOriginHubId() == null) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Session expired. Please start again with /start."));
            return;
        }
        // Check if vehicle confirmation step was skipped — shouldn't happen normally
        // but guard against session edge cases
        try {
            userRepository.findById(carpoolUserId).ifPresent(user -> {
                if (user.getRole() == com.carpool.domain.enums.UserRole.PASSENGER) {
                    user.setRole(com.carpool.domain.enums.UserRole.BOTH);
                    userRepository.save(user);
                    log.info("Auto-upgraded userId={} role to BOTH", carpoolUserId);
                }
            });

            CreateRideRequest request = new CreateRideRequest(
                    state.getOriginHubId(),
                    state.getDestinationHubId(),
                    state.getDirection(),
                    state.getDepartureTime(),
                    state.getSeats(),
                    state.getContribution() != null ? state.getContribution() : BigDecimal.ZERO,
                    state.getNotes(),
                    null);

            RideResponse ride = rideService.createRide(request, carpoolUserId);
            rideService.updateRideStatus(ride.id(),
                    new UpdateRideStatusRequest(RideStatus.ACTIVE), carpoolUserId);

            stateManager.save(chatId, state
                    .withLastPostedRideId(ride.id())
                    .withFlow(BotFlow.IDLE));

            bot.send(BotMessageBuilder.text(chatId,
                    "✅ <b>Ride posted successfully!</b>\n\n" +
                            BotMessageBuilder.formatRideCard(ride) +
                            "\n\nPassengers can now find and book your ride."));

        } catch (Exception e) {
            log.error("Failed to post ride for userId={}: {}", carpoolUserId, e.getMessage());
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Failed to post ride: " +
                            BotMessageBuilder.escape(e.getMessage())));
            stateManager.reset(chatId);
        }
    }

    // ── Find ride ─────────────────────────────────────────────────────────

    private void handleStartFindRide(Long chatId, Long carpoolUserId,
                                     UserState state, CarpoolBot bot) {
        // Driver with active ride cannot find a ride
        long activeRideCount = rideService.getMyRides(carpoolUserId).stream()
                .filter(r -> r.status().name().equals("ACTIVE")
                        || r.status().name().equals("FULL")
                        || r.status().name().equals("DEPARTED"))
                .count();

        if (activeRideCount > 0) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ <b>You have an active ride posted.</b>\n\n" +
                            "Please cancel or complete your ride first before looking for a ride as a passenger."));
            return;
        }

        // If direction already set from main menu — skip direction selection
        if (state.getDirection() != null) {
            stateManager.save(chatId, state
                    .withCarpoolUserId(carpoolUserId)
                    .withFlow(BotFlow.SEARCH_SELECT_TIME));
            askForTimeWindow(chatId, bot);
            return;
        }

        bot.send(BotMessageBuilder.directionSelector(chatId,
                "🔍 <b>Find a Ride</b>\n\nWhich direction are you looking for?"));
        stateManager.save(chatId, state
                .withCarpoolUserId(carpoolUserId)
                .withFlow(BotFlow.SEARCH_SELECT_DIRECTION));
    }

    // ── Time selection ────────────────────────────────────────────────────

    private void handleTimeSelection(Long chatId, String timeSlot, Long carpoolUserId,
                                     UserState state, CarpoolBot bot) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Manila"));
        LocalDateTime from;
        LocalDateTime to;

        switch (timeSlot) {
            case "EARLY_MORNING" -> { from = now.toLocalDate().atTime(5, 0);  to = now.toLocalDate().atTime(7, 0); }
            case "MORNING"       -> { from = now.toLocalDate().atTime(7, 0);  to = now.toLocalDate().atTime(9, 0); }
            case "MID_MORNING"   -> { from = now.toLocalDate().atTime(9, 0);  to = now.toLocalDate().atTime(11, 0); }
            case "AFTERNOON"     -> { from = now.toLocalDate().atTime(15, 0); to = now.toLocalDate().atTime(19, 0); }
            case "ALL_TODAY"     -> { from = now; to = now.toLocalDate().atTime(23, 59); }
            default -> {
                bot.send(BotMessageBuilder.textWithCancel(chatId,
                        "📅 <b>Enter date and time:</b>\n\n" +
                                "For today: <code>HH:MM</code>\n" +
                                "Example: <code>07:30</code>\n\n" +
                                "For another date: <code>MM/DD HH:MM</code>\n" +
                                "Example: <code>04/25 07:30</code>"));
                stateManager.save(chatId, state.withFlow(BotFlow.SEARCH_SELECT_TIME));
                return;
            }
        }

        showFilteredRides(chatId, carpoolUserId, state, from, to, bot);
    }

    private void showFilteredRides(Long chatId, Long carpoolUserId, UserState state,
                                   LocalDateTime from, LocalDateTime to, CarpoolBot bot) {
        if (state.getDirection() == null) {
            bot.send(BotMessageBuilder.text(chatId, "⚠️ Session expired. Use /start."));
            return;
        }

        List<RideResponse> rides = rideService.getRidesByDirection(
                state.getDirection(), carpoolUserId, from, to,
                state.getFilterMaxPrice(),
                state.getFilterMinSeats(),
                state.getFilterSortBy());

        String dirLabel = state.getDirection() == RideDirection.HOME_TO_WORK
                ? "🏠 Home → Work" : "🏢 Work → Home";

        // Build filter summary
        String filterSummary = buildFilterSummary(state);

        UserState updated = state
                .withSearchFrom(from)
                .withSearchTo(to)
                .withSearchPage(0)
                .withFlow(BotFlow.SEARCH_RESULTS);
        stateManager.save(chatId, updated);

        if (rides.isEmpty()) {
            var rows = List.of(List.of(
                    BotMessageBuilder.button("🔧 Filter & Sort", "SEARCH_FILTER"),
                    BotMessageBuilder.button("🏠 Menu",          "MAIN_MENU")
            ));
            bot.send(sendWithInline(chatId,
                    "🔍 <b>No rides found — " + dirLabel + "</b>\n\n" +
                            "Try adjusting your filters or check back later.",
                    rows));
            return;
        }

        bot.send(BotMessageBuilder.paginatedRideList(
                chatId, rides,
                "🔍 <b>Available Rides — " + dirLabel + "</b>",
                0, filterSummary));
    }

    private String buildFilterSummary(UserState state) {
        List<String> parts = new ArrayList<>();

        if (state.getFilterSortBy() != null) {
            parts.add(switch (state.getFilterSortBy()) {
                case "CHEAPEST"   -> "💰 Cheapest first";
                case "MOST_SEATS" -> "🪑 Most seats first";
                default           -> "🕐 Earliest first";
            });
        }
        if (state.getFilterMinSeats() != null) {
            parts.add("🪑 " + state.getFilterMinSeats() + "+ seats");
        }
        if (state.getFilterMaxPrice() != null) {
            parts.add("⛽ Max ₱" + state.getFilterMaxPrice().toPlainString() + " share");
        }

        return parts.isEmpty() ? "" : "<i>Filters: " + String.join(" | ", parts) + "</i>";
    }

    // ── View ride detail ──────────────────────────────────────────────────

    private void handleViewRide(Long chatId, Long rideId, Long carpoolUserId,
                                UserState state, CarpoolBot bot) {
        try {
            RideResponse ride = rideService.getRideById(rideId);
            stateManager.save(chatId, state.withSelectedRideId(rideId));

            boolean isDriver = ride.driver().id().equals(carpoolUserId);
            List<List<InlineKeyboardButton>> rows;

            if (isDriver) {
                rows = List.of(List.of(
                        BotMessageBuilder.button("📋 Bookings", "RIDE_BOOKINGS:" + rideId),
                        BotMessageBuilder.button("❌ Cancel",   "CANCEL_RIDE:"   + rideId)
                ));
            } else {
                rows = List.of(List.of(
                        BotMessageBuilder.button("✅ Book This Ride", "BOOK_RIDE:" + rideId)
                ));
            }

            bot.send(sendWithInline(chatId, BotMessageBuilder.formatRideCard(ride), rows));

        } catch (Exception e) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Could not load ride details. It may have been cancelled."));
        }
    }

    // ── Book ride ─────────────────────────────────────────────────────────

    private void handleBookRide(Long chatId, Long rideId, Long carpoolUserId,
                                CarpoolBot bot) {
        // Save selected ride ID then ask for optional message
        UserState updated = stateManager.get(chatId);
        if (updated == null) updated = UserState.initial(carpoolUserId);

        stateManager.save(chatId, updated
                .withSelectedRideId(rideId)
                .withFlow(BotFlow.BOOKING_MESSAGE));

        bot.send(sendWithInline(chatId,
                "💬 <b>Any message for the driver?</b>\n\n" +
                        "<i>e.g. \"Please wait at gate 2\", \"I have extra luggage\"</i>",
                List.of(List.of(
                        BotMessageBuilder.button("⏭️ Skip", "BOOK_NOW:" + rideId),
                        BotMessageBuilder.button("❌ Cancel", "MAIN_MENU")
                ))));
    }

    private void executeBooking(Long chatId, Long rideId, Long carpoolUserId,
                                String passengerMessage, CarpoolBot bot) {
        try {
            bookingService.createBooking(rideId,
                    new CreateBookingRequest(1, null, null, passengerMessage),
                    carpoolUserId);

            bot.send(sendWithInline(chatId,
                    "⏳ <b>Booking Request Sent!</b>\n\n" +
                            "Waiting for the driver to accept your request.\n" +
                            "You'll be notified once the driver responds.",
                    List.of(
                            List.of(
                                    BotMessageBuilder.button("📜 My Bookings", "MY_BOOKINGS"),
                                    BotMessageBuilder.button("🏠 Menu",        "MAIN_MENU")
                            )
                    )));

        } catch (Exception e) {
            log.error("Booking failed: rideId={} userId={} error={}",
                    rideId, carpoolUserId, e.getMessage());
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Could not book this ride: " +
                            BotMessageBuilder.escape(e.getMessage())));
        }
    }

    // ── Hub suggestions ───────────────────────────────────────────────

    private void handleHubOriginSelected(Long chatId, Long hubId, Long carpoolUserId,
                                         UserState state, CarpoolBot bot) {
        try {
            var hub = rideService.getHubById(hubId);

            UserState updated = state
                    .withOriginHubId(hub.id())
                    .withOriginHubName(hub.name())
                    .withFlow(BotFlow.POST_RIDE_DESTINATION);
            stateManager.save(chatId, updated);

            String destExample1 = state.getDirection() == RideDirection.HOME_TO_WORK
                    ? "BGC" : "SM Southmall";

            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "✅ Start point: <b>" + BotMessageBuilder.escape(hub.name()) + "</b>\n\n" +
                            "🏁 <b>Where does your ride end?</b>\n\n" +
                            "Type a nearby landmark as your drop-off point.\n" +
                            "Example: <code>" + destExample1 + "</code>"));
        } catch (Exception e) {
            bot.send(BotMessageBuilder.text(chatId, "⚠️ Could not load hub. Please try again."));
        }
    }

    private void handleHubDestSelected(Long chatId, Long hubId, Long carpoolUserId,
                                       UserState state, CarpoolBot bot) {
        try {
            var hub = rideService.getHubById(hubId);

            if (hub.id().equals(state.getOriginHubId())) {
                bot.send(BotMessageBuilder.text(chatId,
                        "⚠️ Destination cannot be the same as pickup. Try again:"));
                return;
            }

            UserState updated = state
                    .withDestinationHubId(hub.id())
                    .withDestinationHubName(hub.name())
                    .withFlow(BotFlow.POST_RIDE_SEATS);
            stateManager.save(chatId, updated);

            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "✅ End point: <b>" + BotMessageBuilder.escape(hub.name()) + "</b>\n\n" +
                            "🪑 <b>How many passengers can you take?</b> (1-8)"));
        } catch (Exception e) {
            bot.send(BotMessageBuilder.text(chatId, "⚠️ Could not load hub. Please try again."));
        }
    }

    private void handleRetypeOrigin(Long chatId, Long carpoolUserId,
                                    UserState state, CarpoolBot bot) {
        stateManager.save(chatId, state.withFlow(BotFlow.POST_RIDE_ORIGIN));
        String retypeOriginExample = state.getDirection() == RideDirection.HOME_TO_WORK
                ? "SM Southmall" : "BGC";

        bot.send(BotMessageBuilder.textWithCancel(chatId,
                "📍 <b>Where does your ride start?</b>\n\n" +
                        "Type a nearby landmark as your pickup point.\n" +
                        "Example: <code>" + retypeOriginExample + "</code>"));
    }

    private void handleRetypeDest(Long chatId, Long carpoolUserId,
                                  UserState state, CarpoolBot bot) {
        stateManager.save(chatId, state.withFlow(BotFlow.POST_RIDE_DESTINATION));
        String retypeDestExample = state.getDirection() == RideDirection.HOME_TO_WORK
                ? "BGC" : "SM Southmall";

        bot.send(BotMessageBuilder.textWithCancel(chatId,
                "🏁 <b>Where does your ride end?</b>\n\n" +
                        "Type a nearby landmark as your drop-off point.\n" +
                        "Example: <code>" + retypeDestExample + "</code>"));
    }

    // ── Cancel ride ───────────────────────────────────────────────────────

    private void handleCancelRide(Long chatId, Long rideId, Long carpoolUserId, CarpoolBot bot) {
        try {
            // Cannot cancel a DEPARTED ride — must Complete it instead
            RideResponse ride = rideService.getRideById(rideId);
            if (ride.status().name().equals("DEPARTED")) {
                bot.send(BotMessageBuilder.text(chatId,
                        "⚠️ Your ride has already started.\n\n" +
                                "Please tap <b>Complete Ride</b> when you reach the destination."));
                return;
            }

            List<BookingResponse> activeBookings = bookingService.getBookingsByRideId(rideId);

            if (!activeBookings.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append("⚠️ <b>Are you sure you want to cancel?</b>\n\n");
                sb.append("The following passengers will be notified:\n\n");

                for (int i = 0; i < activeBookings.size(); i++) {
                    BookingResponse b = activeBookings.get(i);
                    String statusIcon = b.status() == BookingStatus.PENDING ? "⏳" : "✅";
                    String paxHandle = b.passenger().telegramHandle() != null
                            ? " (@" + BotMessageBuilder.escape(b.passenger().telegramHandle()) + ")"
                            : "";

                    sb.append(String.format("<b>%d.</b> %s %s%s\n",
                            i + 1,
                            statusIcon,
                            BotMessageBuilder.escape(b.passenger().fullName()),
                            paxHandle));

                    if (b.passengerMessage() != null) {
                        sb.append(String.format(
                                "    💬 \"%s\"\n",
                                BotMessageBuilder.escape(b.passengerMessage())));
                    }

                    sb.append(String.format("    🪑 %d seat(s) | ⛽ ₱%.2f share\n",
                            b.seatsReserved(),
                            b.contributionDue()));
                }

                sb.append("\n⚠️ This cannot be undone.");

                var rows = List.of(
                        List.of(BotMessageBuilder.button("🔧 Vehicle issue",   "CONFIRM_CANCEL_RIDE:" + rideId + ":VEHICLE_ISSUE")),
                        List.of(BotMessageBuilder.button("📍 Route change",    "CONFIRM_CANCEL_RIDE:" + rideId + ":ROUTE_CHANGE")),
                        List.of(BotMessageBuilder.button("🏠 Personal reason", "CONFIRM_CANCEL_RIDE:" + rideId + ":PERSONAL")),
                        List.of(BotMessageBuilder.button("❌ Other reason",     "CONFIRM_CANCEL_RIDE:" + rideId + ":OTHER")),
                        List.of(BotMessageBuilder.button("◀️ Keep Ride",       "MAIN_MENU"))
                );

                bot.send(sendWithInline(chatId, sb.toString(), rows));
                return;
            }

            executeCancelRide(chatId, rideId, carpoolUserId, null, bot);

        } catch (Exception e) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Could not cancel ride: " +
                            BotMessageBuilder.escape(e.getMessage())));
        }
    }

    private void handleConfirmCancelRide(Long chatId, String[] parts,
                                         Long carpoolUserId, CarpoolBot bot) {
        if (parts.length < 3) {
            bot.send(BotMessageBuilder.text(chatId, "⚠️ Invalid cancel request."));
            return;
        }

        Long rideId;
        try {
            rideId = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            bot.send(BotMessageBuilder.text(chatId, "⚠️ Invalid ride ID."));
            return;
        }

        String reasonCode = parts[2];
        String reason = switch (reasonCode) {
            case "VEHICLE_ISSUE" -> "Vehicle issue";
            case "ROUTE_CHANGE"  -> "Route change";
            case "PERSONAL"      -> "Personal reason";
            default              -> "Other reason";
        };

        executeCancelRide(chatId, rideId, carpoolUserId, reason, bot);
    }

    private void executeCancelRide(Long chatId, Long rideId, Long carpoolUserId,
                                   String reason, CarpoolBot bot) {
        try {
            rideService.updateRideStatus(rideId,
                    new UpdateRideStatusRequest(RideStatus.CANCELLED), carpoolUserId, reason);
            stateManager.reset(chatId);
            bot.send(BotMessageBuilder.text(chatId,
                    "✅ Ride cancelled. All passengers have been notified."));
        } catch (Exception e) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Could not cancel ride: " +
                            BotMessageBuilder.escape(e.getMessage())));
        }
    }

    private void handleDepartRide(Long chatId, Long rideId, Long carpoolUserId, CarpoolBot bot) {
        try {
            // Validate time window — prevent starting ride too early
            RideResponse ride = rideService.getRideById(rideId);

            // departure_time is LocalDateTime stored as Manila local time — use directly
            LocalDateTime departure = ride.departureTime();
            LocalDateTime now       = LocalDateTime.now();
            LocalDateTime earliest  = departure.minusHours(1);

            if (now.isBefore(earliest)) {
                String formatted = departure.format(
                        DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a"));
                long hoursAway = Duration.between(now, departure).toHours();
                long minsAway  = Duration.between(now, departure).toMinutesPart();

                String timeAway = hoursAway > 0
                        ? hoursAway + "h " + minsAway + "m away"
                        : minsAway + " minutes away";

                bot.send(BotMessageBuilder.text(chatId,
                        "⚠️ <b>Too early to start this ride.</b>\n\n" +
                                "Your ride is scheduled for <b>" + formatted + "</b> " +
                                "(" + timeAway + ").\n\n" +
                                "You can start the ride up to <b>1 hour before</b> departure."));
                return;
            }

            rideService.updateRideStatus(rideId,
                    new UpdateRideStatusRequest(RideStatus.DEPARTED), carpoolUserId);
            stateManager.reset(chatId);

            var rows = List.of(List.of(
                    BotMessageBuilder.button("✅ Complete Ride", "COMPLETE_RIDE:" + rideId),
                    BotMessageBuilder.button("📜 My Bookings",  "MY_BOOKINGS")
            ));

            bot.send(sendWithInline(chatId,
                    "🚀 <b>Ride Started!</b>\n\n" +
                            "Your ride is now in progress.\n" +
                            "Tap <b>Complete Ride</b> when you reach the destination.",
                    rows));

        } catch (Exception e) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Could not start ride: " +
                            BotMessageBuilder.escape(e.getMessage())));
        }
    }

    private void handleCompleteRide(Long chatId, Long rideId, Long carpoolUserId, CarpoolBot bot) {
        try {
            rideService.updateRideStatus(rideId,
                    new UpdateRideStatusRequest(RideStatus.COMPLETED), carpoolUserId);
            stateManager.reset(chatId);
            bot.send(sendWithInline(chatId,
                    "✅ <b>Ride Completed!</b>\n\n" +
                            "Thank you for driving! All passengers have been notified.\n\n" +
                            "Please collect gas share contributions from your passengers.",
                    List.of(List.of(
                            BotMessageBuilder.button("🚗 My Rides", "MY_RIDES"),
                            BotMessageBuilder.button("🏠 Menu",     "MAIN_MENU")
                    ))));

        } catch (Exception e) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Could not complete ride: " +
                            BotMessageBuilder.escape(e.getMessage())));
        }
    }

    // ── My bookings ───────────────────────────────────────────────────────

    private void handleMyBookings(Long chatId, Long carpoolUserId, CarpoolBot bot) {
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
                        ? " (@" + BotMessageBuilder.escape(b.ride().driver().telegramHandle()) + ")"
                        : "";

                sb.append(String.format("<b>%d.</b> %s → %s | 🕐 %s | ⛽ ₱%.2f share\n👤 %s%s\n",
                        i + 1,
                        BotMessageBuilder.escape(b.ride().originHub().name()),
                        BotMessageBuilder.escape(b.ride().destinationHub().name()),
                        b.ride().departureTime()
                                .atZone(ZoneId.of("Asia/Manila"))
                                .format(DateTimeFormatter.ofPattern("MMM d h:mma")),
                        b.contributionDue(),
                        BotMessageBuilder.escape(b.ride().driver().fullName()),
                        driverInfo));

                String statusPrefix = b.status() == BookingStatus.PENDING ? "⏳ " : "🔍 ";
                rows.add(List.of(InlineKeyboardButton.builder()
                        .text(String.format("%s%s → %s | %s",
                                statusPrefix,
                                b.ride().originHub().name(),
                                b.ride().destinationHub().name(),
                                b.ride().departureTime()
                                        .atZone(ZoneId.of("Asia/Manila"))
                                        .format(DateTimeFormatter.ofPattern("MMM d h:mma"))))
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
                .text("🏠 Menu")
                .callbackData("MAIN_MENU")
                .build()));

        bot.send(sendWithInline(chatId, sb.toString(), rows));
    }

    // ── View booking detail ───────────────────────────────────────────────

    private void handleViewBooking(Long chatId, Long bookingId, Long carpoolUserId,
                                   UserState state, CarpoolBot bot) {
        try {
            BookingResponse b = bookingService.getBookingById(bookingId);

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

            // Build expiry info for PENDING bookings
            String expiryInfo = "";
            if (b.status() == BookingStatus.PENDING && b.expiresAt() != null) {
                long remainingMinutes = Duration.between(
                        Instant.now(), b.expiresAt()).toMinutes();
                expiryInfo = "\n⏰ Auto-declines in: " + Math.max(0, remainingMinutes) + " minutes";
            }

            String detail = String.format(
                    "📋 <b>Booking Details</b>\n\n" +
                            "🚗 %s → %s\n" +
                            "🕐 %s\n" +
                            "🚏 Pickup: <b>%s</b>\n" +
                            "🏁 Dropoff: <b>%s</b>\n" +
                            "🪑 Seats: %d\n" +
                            "⛽ Suggested share: ₱%.2f\n" +
                            "👤 Driver: %s%s\n" +
                            "%s" +
                            "📊 Status: %s%s",
                    BotMessageBuilder.escape(b.ride().originHub().name()),
                    BotMessageBuilder.escape(b.ride().destinationHub().name()),
                    b.ride().departureTime()
                            .atZone(ZoneId.of("Asia/Manila"))
                            .format(DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a")),
                    BotMessageBuilder.escape(pickup),
                    BotMessageBuilder.escape(dropoff),
                    b.seatsReserved(),
                    b.contributionDue(),
                    BotMessageBuilder.escape(b.ride().driver().fullName()),
                    b.ride().driver().telegramHandle() != null
                            ? " (@" + BotMessageBuilder.escape(b.ride().driver().telegramHandle()) + ")" : "",
                    b.passengerMessage() != null
                            ? "💬 Your message: \"" + BotMessageBuilder.escape(b.passengerMessage()) + "\"\n"
                            : "",
                    statusLabel,
                    expiryInfo);

            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            boolean rideNotStarted = !b.ride().status().name().equals("DEPARTED")
                    && !b.ride().status().name().equals("COMPLETED");

            if ((b.status() == BookingStatus.CONFIRMED || b.status() == BookingStatus.PENDING)
                    && rideNotStarted) {
                rows.add(List.of(InlineKeyboardButton.builder()
                        .text("❌ Cancel Booking")
                        .callbackData("CANCEL_BOOKING:" + bookingId)
                        .build()));
            }

            rows.add(List.of(InlineKeyboardButton.builder()
                    .text("◀️ Back to My Bookings")
                    .callbackData("MY_BOOKINGS")
                    .build()));

            bot.send(SendMessage.builder()
                    .chatId(chatId)
                    .text(detail)
                    .parseMode("HTML")
                    .replyMarkup(InlineKeyboardMarkup.builder()
                            .keyboard(rows.stream().map(InlineKeyboardRow::new).toList())
                            .build())
                    .build());

        } catch (Exception e) {
            bot.send(BotMessageBuilder.text(chatId, "⚠️ Could not load booking details."));
        }
    }

    // ── Past bookings ─────────────────────────────────────────────────────

    private void handlePastBookings(Long chatId, Long carpoolUserId, CarpoolBot bot) {
        List<BookingResponse> past = bookingService.getMyPastBookings(carpoolUserId);

        if (past.isEmpty()) {
            bot.send(BotMessageBuilder.text(chatId,
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
                    BotMessageBuilder.escape(b.ride().originHub().name()),
                    BotMessageBuilder.escape(b.ride().destinationHub().name()),
                    statusLabel));
        }

        bot.send(SendMessage.builder()
                .chatId(chatId)
                .text(sb.toString())
                .parseMode("HTML")
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboard(List.of(
                                new InlineKeyboardRow(InlineKeyboardButton.builder()
                                        .text("◀️ Back to My Bookings")
                                        .callbackData("MY_BOOKINGS")
                                        .build())))
                        .build())
                .build());
    }

    // ── Driver bookings ───────────────────────────────────────────────────

    private void handleDriverBookings(Long chatId, Long carpoolUserId, CarpoolBot bot) {
        List<BookingResponse> bookings = bookingService.getBookingsForDriver(carpoolUserId);

        if (bookings.isEmpty()) {
            bot.send(BotMessageBuilder.text(chatId,
                    "📋 <b>Ride Bookings</b>\n\n<i>No passengers have booked your ride yet.</i>"));
            return;
        }

        // Separate confirmed and pending
        List<BookingResponse> confirmed = bookings.stream()
                .filter(b -> b.status() == BookingStatus.CONFIRMED)
                .toList();
        List<BookingResponse> pending = bookings.stream()
                .filter(b -> b.status() == BookingStatus.PENDING)
                .toList();

        StringBuilder sb = new StringBuilder("📋 <b>Ride Bookings</b>\n\n");

        // Summary line
        sb.append(String.format("✅ Confirmed: <b>%d</b>  ⏳ Pending: <b>%d</b>\n\n",
                confirmed.size(), pending.size()));

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        int index = 1;

        // Confirmed section
        if (!confirmed.isEmpty()) {
            sb.append("─── <b>Confirmed</b> ───\n");
            for (BookingResponse b : confirmed) {
                String paxHandle = b.passenger().telegramHandle() != null
                        ? " (@" + BotMessageBuilder.escape(b.passenger().telegramHandle()) + ")"
                        : "";
                String pickup = b.pickupWaypoint() != null
                        ? b.pickupWaypoint().hub().name()
                        : b.ride().originHub().name();

                sb.append(String.format("<b>%d.</b> %s%s\n" +
                                "    🪑 %d | ⛽ ₱%.2f share\n",
                        index,
                        BotMessageBuilder.escape(b.passenger().fullName()),
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

        // Pending section
        if (!pending.isEmpty()) {
            sb.append("─── <b>Pending Approval</b> ───\n");
            for (BookingResponse b : pending) {
                String paxHandle = b.passenger().telegramHandle() != null
                        ? " (@" + BotMessageBuilder.escape(b.passenger().telegramHandle()) + ")"
                        : "";
                long remainingMinutes = b.expiresAt() != null
                        ? Duration.between(
                        Instant.now(), b.expiresAt()).toMinutes()
                        : 0;

                sb.append(String.format("<b>%d.</b> %s%s\n" +
                                "    🪑 %d | ⛽ ₱%.2f share | ⏰ %d min\n",
                        index,
                        BotMessageBuilder.escape(b.passenger().fullName()),
                        paxHandle,
                        b.seatsReserved(),
                        b.contributionDue(),
                        Math.max(0, remainingMinutes)));

                rows.add(List.of(InlineKeyboardButton.builder()
                        .text("⏳ #" + index + " — " + b.passenger().fullName())
                        .callbackData("VIEW_PENDING:" + b.id())
                        .build()));
                index++;
            }
        }

        rows.add(List.of(BotMessageBuilder.menuButtonRow().get(0)));

        bot.send(SendMessage.builder()
                .chatId(chatId)
                .text(sb.toString())
                .parseMode("HTML")
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboard(rows.stream().map(InlineKeyboardRow::new).toList())
                        .build())
                .build());
    }

    // ── Repost ride ───────────────────────────────────────────────────────

    private void handleRepostRide(Long chatId, Long rideId, Long carpoolUserId,
                                  UserState state, CarpoolBot bot) {
        try {
            RideResponse original = rideService.getRideById(rideId);

            // Auto-detect direction from original ride
            String dirLabel = original.direction() == RideDirection.HOME_TO_WORK
                    ? "🏠 Home → Work" : "🏢 Work → Home";

            UserState updated = state
                    .withOriginHubId(original.originHub().id())
                    .withOriginHubName(original.originHub().name())
                    .withDestinationHubId(original.destinationHub().id())
                    .withDestinationHubName(original.destinationHub().name())
                    .withDirection(original.direction())
                    .withSeats(original.totalSeats())
                    .withContribution(original.contributionAmount())
                    .withNotes(original.notes())
                    .withFlow(BotFlow.POST_RIDE_DEPARTURE_TIME);
            stateManager.save(chatId, updated);

            String notesLine = original.notes() != null && !original.notes().isBlank()
                    ? "📝 Notes: " + BotMessageBuilder.escape(original.notes()) + "\n\n"
                    : "";

            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "🔄 <b>Review Ride to Repost</b>\n\n" +
                            "Direction: <b>" + dirLabel + "</b>\n" +
                            "📍 <b>" +
                            BotMessageBuilder.escape(original.originHub().name()) +
                            " → " +
                            BotMessageBuilder.escape(original.destinationHub().name()) +
                            "</b>\n" +
                            "🪑 " + original.totalSeats() + " seat(s)\n" +
                            "⛽ ₱" + original.contributionAmount().toPlainString() + " gas share/seat\n" +
                            notesLine +
                            "<i>Only the departure time will be updated.</i>\n\n" +
                            "🕐 <b>What time are you leaving? (Start pickup time)</b>\n" +
                            "Format: <code>MM/DD HH:MM</code>\n" +
                            "Example: <code>" +
                            (original.direction() == RideDirection.HOME_TO_WORK
                                    ? LocalDateTime.now(ZoneId.of("Asia/Manila")).withHour(7).withMinute(30)
                                      .format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))
                                    : LocalDateTime.now(ZoneId.of("Asia/Manila")).withHour(18).withMinute(0)
                                      .format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))) +
                            "</code>"));

        } catch (Exception e) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Could not load ride details for repost."));
        }
    }

    // ── Pending booking approval ──────────────────────────────────────

    private void handleAcceptBooking(Long chatId, Long bookingId,
                                     Long carpoolUserId, CarpoolBot bot) {
        try {
            bookingService.acceptBooking(bookingId, carpoolUserId);
            bot.send(BotMessageBuilder.text(chatId,
                    "✅ <b>Booking Accepted!</b>\n\n" +
                            "The passenger has been notified and their seat is confirmed."));
        } catch (Exception e) {
            log.error("Accept booking failed: bookingId={} userId={} error={}",
                    bookingId, carpoolUserId, e.getMessage());
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Could not accept booking: " +
                            BotMessageBuilder.escape(e.getMessage())));
        }
    }

    private void handleDeclineBooking(Long chatId, Long bookingId,
                                      Long carpoolUserId, CarpoolBot bot) {
        var rows = List.of(
                List.of(BotMessageBuilder.button("🚗 Fully booked already", "DECLINE_BOOKING_REASON:" + bookingId + ":FULL")),
                List.of(BotMessageBuilder.button("📍 Route change",          "DECLINE_BOOKING_REASON:" + bookingId + ":ROUTE_CHANGE")),
                List.of(BotMessageBuilder.button("🔧 Vehicle issue",         "DECLINE_BOOKING_REASON:" + bookingId + ":VEHICLE_ISSUE")),
                List.of(BotMessageBuilder.button("❌ Other reason",           "DECLINE_BOOKING_REASON:" + bookingId + ":OTHER")),
                List.of(BotMessageBuilder.button("◀️ Back",                  "VIEW_PENDING:" + bookingId))
        );

        bot.send(sendWithInline(chatId,
                "❓ <b>Why are you declining this request?</b>", rows));
    }

    private void handleDeclineBookingWithReason(Long chatId, String[] parts,
                                                Long carpoolUserId, CarpoolBot bot) {
        if (parts.length < 3) {
            bot.send(BotMessageBuilder.text(chatId, "⚠️ Invalid decline request."));
            return;
        }

        Long bookingId;
        try {
            bookingId = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            bot.send(BotMessageBuilder.text(chatId, "⚠️ Invalid booking ID."));
            return;
        }

        String reasonCode = parts[2];
        String reason = switch (reasonCode) {
            case "FULL"          -> "Already fully booked";
            case "ROUTE_CHANGE"  -> "Route change";
            case "VEHICLE_ISSUE" -> "Vehicle issue";
            default              -> "Other reason";
        };

        try {
            bookingService.declineBooking(bookingId, carpoolUserId, reason);
            bot.send(BotMessageBuilder.text(chatId,
                    "❌ <b>Booking Declined</b>\n\n" +
                            "The passenger has been notified and their seat has been released."));
        } catch (Exception e) {
            log.error("Decline booking failed: bookingId={} userId={} error={}",
                    bookingId, carpoolUserId, e.getMessage());
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Could not decline booking: " +
                            BotMessageBuilder.escape(e.getMessage())));
        }
    }

    private void handlePendingRequests(Long chatId, Long carpoolUserId, CarpoolBot bot) {
        List<BookingResponse> pending = bookingService.getPendingRequestsForDriver(carpoolUserId);

        if (pending.isEmpty()) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⏳ <b>Pending Requests</b>\n\n<i>No pending booking requests.</i>"));
            return;
        }

        StringBuilder sb = new StringBuilder("⏳ <b>Pending Requests (")
                .append(pending.size())
                .append(")</b>\n\n");

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (int i = 0; i < pending.size(); i++) {
            BookingResponse b = pending.get(i);

            // Compute remaining minutes
            long remainingMinutes = b.expiresAt() != null
                    ? Duration.between(
                    Instant.now(), b.expiresAt()).toMinutes()
                    : 0;

            sb.append(String.format(
                    "<b>%d.</b> %s | 🪑 %d | ⛽ ₱%.2f share | ⏰ %d min\n",
                    i + 1,
                    BotMessageBuilder.escape(b.passenger().fullName()),
                    b.seatsReserved(),
                    b.contributionDue(),
                    Math.max(0, remainingMinutes)));

            rows.add(List.of(InlineKeyboardButton.builder()
                    .text("View #" + (i + 1) + " — " + b.passenger().fullName())
                    .callbackData("VIEW_PENDING:" + b.id())
                    .build()));
        }

        rows.add(List.of(BotMessageBuilder.menuButtonRow().get(0)));

        bot.send(SendMessage.builder()
                .chatId(chatId)
                .text(sb.toString())
                .parseMode("HTML")
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboard(rows.stream().map(InlineKeyboardRow::new).toList())
                        .build())
                .build());
    }

    private void handleViewPendingRequest(Long chatId, Long bookingId,
                                          Long carpoolUserId, CarpoolBot bot) {
        try {
            BookingResponse b = bookingService.getBookingById(bookingId);

            long remainingMinutes = b.expiresAt() != null
                    ? Duration.between(
                    Instant.now(), b.expiresAt()).toMinutes()
                    : 0;

            String paxHandle = b.passenger().telegramHandle() != null
                    ? " (@" + BotMessageBuilder.escape(b.passenger().telegramHandle()) + ")"
                    : "";

            String detail = String.format(
                    "🔔 <b>Booking Request</b>\n\n" +
                            "👤 <b>%s</b>%s\n" +
                            "🪑 Seats: %d\n" +
                            "⛽ Suggested share: ₱%.2f\n" +
                            "%s" +
                            "⏰ Expires in: %d minutes",
                    BotMessageBuilder.escape(b.passenger().fullName()),
                    paxHandle,
                    b.seatsReserved(),
                    b.contributionDue(),
                    b.passengerMessage() != null
                            ? "💬 Message: \"" +
                            BotMessageBuilder.escape(b.passengerMessage()) + "\"\n"
                            : "",
                    Math.max(0, remainingMinutes));

            var rows = List.of(
                    List.of(
                            BotMessageBuilder.button("✅ Accept", "ACCEPT_BOOKING:" + bookingId),
                            BotMessageBuilder.button("❌ Decline", "DECLINE_BOOKING:" + bookingId)
                    ),
                    List.of(
                            BotMessageBuilder.button("◀️ Back to Pending", "PENDING_REQUESTS")
                    )
            );

            bot.send(sendWithInline(chatId, detail, rows));

        } catch (Exception e) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Could not load booking request."));
        }
    }

    // ── Cancel booking ────────────────────────────────────────────────────

    private void handleCancelBooking(Long chatId, Long bookingId,
                                     Long carpoolUserId, CarpoolBot bot) {
        var rows = List.of(
                List.of(BotMessageBuilder.button("🔄 Found another ride", "CANCEL_BOOKING_REASON:" + bookingId + ":FOUND_OTHER")),
                List.of(BotMessageBuilder.button("📅 Change of plans",    "CANCEL_BOOKING_REASON:" + bookingId + ":CHANGE_PLANS")),
                List.of(BotMessageBuilder.button("⏰ Running late",        "CANCEL_BOOKING_REASON:" + bookingId + ":RUNNING_LATE")),
                List.of(BotMessageBuilder.button("❌ Other reason",        "CANCEL_BOOKING_REASON:" + bookingId + ":OTHER")),
                List.of(BotMessageBuilder.button("◀️ Back",               "VIEW_BOOKING:" + bookingId))
        );

        bot.send(sendWithInline(chatId,
                "❓ <b>Why are you cancelling?</b>", rows));
    }

    private void handleCancelBookingWithReason(Long chatId, String[] parts,
                                               Long carpoolUserId, CarpoolBot bot) {
        // parts[0] = CANCEL_BOOKING_REASON, parts[1] = bookingId, parts[2] = reason
        if (parts.length < 3) {
            bot.send(BotMessageBuilder.text(chatId, "⚠️ Invalid cancel request."));
            return;
        }

        Long bookingId;
        try {
            bookingId = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            bot.send(BotMessageBuilder.text(chatId, "⚠️ Invalid booking ID."));
            return;
        }

        String reasonCode = parts[2];
        String reason = switch (reasonCode) {
            case "FOUND_OTHER"  -> "Found another ride";
            case "CHANGE_PLANS" -> "Change of plans";
            case "RUNNING_LATE" -> "Running late";
            default             -> "Other reason";
        };

        try {
            bookingService.cancelBooking(bookingId, carpoolUserId, reason);
            stateManager.reset(chatId);
            bot.send(BotMessageBuilder.text(chatId,
                    "✅ <b>Booking Cancelled</b>\n\n" +
                            "Your booking has been cancelled. The driver has been notified."));
        } catch (Exception e) {
            log.error("Cancel booking failed: bookingId={} userId={} error={}",
                    bookingId, carpoolUserId, e.getMessage());
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Could not cancel booking: " +
                            BotMessageBuilder.escape(e.getMessage())));
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private SendMessage sendWithInline(Long chatId, String text,
                                       List<List<InlineKeyboardButton>> rows) {
        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .replyMarkup(BotMessageBuilder.inlineButtons(rows))
                .build();
    }

    private void handleViewDriverBooking(Long chatId, Long bookingId,
                                         Long carpoolUserId, CarpoolBot bot) {
        try {
            BookingResponse b = bookingService.getBookingById(bookingId);

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
                    "👤 <b>Passenger Details</b>\n\n" +
                            "Name: <b>%s</b>%s\n" +
                            "🪑 Seats: %d\n" +
                            "⛽ Share: ₱%.2f\n" +
                            "💳 Settlement: %s\n" +
                            "%s" +
                            "📊 Status: %s",
                    BotMessageBuilder.escape(b.passenger().fullName()),
                    b.passenger().telegramHandle() != null
                            ? " (@" + BotMessageBuilder.escape(b.passenger().telegramHandle()) + ")" : "",
                    b.seatsReserved(),
                    b.contributionDue(),
                    paymentLabel,
                    b.passengerMessage() != null
                            ? "💬 Passenger's note: \"" + BotMessageBuilder.escape(b.passengerMessage()) + "\"\n"
                            : "",
                    statusLabel);

            var rows = List.of(List.of(
                    InlineKeyboardButton.builder()
                            .text("◀️ Back to Bookings")
                            .callbackData("RIDE_BOOKINGS:0")
                            .build()
            ));

            bot.send(SendMessage.builder()
                    .chatId(chatId)
                    .text(detail)
                    .parseMode("HTML")
                    .replyMarkup(InlineKeyboardMarkup.builder()
                            .keyboard(rows.stream().map(InlineKeyboardRow::new).toList())
                            .build())
                    .build());

        } catch (Exception e) {
            bot.send(BotMessageBuilder.text(chatId, "⚠️ Could not load booking details."));
        }
    }

    // ── Driver notes ──────────────────────────────────────────────────────

    /**
     * Show preview of selected note with action buttons.
     */
    private void handleNotePreview(Long chatId, Long noteId, Long carpoolUserId,
                                   UserState state, CarpoolBot bot) {
        try {
            com.carpool.domain.entity.DriverNote note = driverNoteService.getById(noteId);

            stateManager.save(chatId, state.withSelectedNoteId(noteId));

            var rows = List.of(
                    List.of(BotMessageBuilder.button("✅ Use this", "NOTE_APPLY:" + noteId)),
                    List.of(
                            BotMessageBuilder.button("🔄 Other notes", "NOTE_CHOOSE_OTHER"),
                            BotMessageBuilder.button("✏️ Write new",   "NOTE_WRITE")
                    ),
                    List.of(
                            BotMessageBuilder.button("⏭️ Skip",   "SKIP_NOTES"),
                            BotMessageBuilder.button("❌ Cancel", "CANCEL_POST_RIDE")
                    )
            );

            bot.send(sendWithInline(chatId,
                    "📌 <b>Selected reminder:</b>\n\n" +
                            "\"" + BotMessageBuilder.escape(note.getContent()) + "\"",
                    rows));

        } catch (Exception e) {
            bot.send(BotMessageBuilder.text(chatId, "⚠️ Could not load note. Please try again."));
        }
    }

    /**
     * Apply selected note — mark as used, save to state, proceed to confirmation.
     */
    private void handleNoteApply(Long chatId, Long noteId, Long carpoolUserId,
                                 UserState state, CarpoolBot bot) {
        try {
            DriverNote note = driverNoteService.markUsed(noteId);

            UserState updated = state
                    .withNotes(note.getContent())
                    .withSelectedNoteId(null);
            stateManager.save(chatId, updated);

            showVehicleConfirmStep(chatId, carpoolUserId, updated, bot);

        } catch (Exception e) {
            bot.send(BotMessageBuilder.text(chatId, "⚠️ Could not apply note. Please try again."));
        }
    }

    /**
     * Driver chose to write a new custom note.
     */
    private void handleNoteWrite(Long chatId, Long carpoolUserId,
                                 UserState state, CarpoolBot bot) {
        stateManager.save(chatId, state.withFlow(BotFlow.POST_RIDE_NOTES_WRITE));

        bot.send(sendWithInline(chatId,
                "✏️ <b>Type your reminder for passengers:</b>",
                List.of(List.of(
                        BotMessageBuilder.button("⏭️ Skip",   "SKIP_NOTES"),
                        BotMessageBuilder.button("❌ Cancel", "CANCEL_POST_RIDE")
                ))));
    }

    /**
     * Driver wants to choose a different note — show notes list again.
     */
    private void handleNoteChooseOther(Long chatId, Long carpoolUserId,
                                       UserState state, CarpoolBot bot) {
        postRideHelper.showNotesPrompt(chatId, carpoolUserId, state.withSelectedNoteId(null), bot);
    }

    /**
     * Driver skipped notes — proceed to confirmation with no note.
     */
    private void handleSkipNotes(Long chatId, Long carpoolUserId,
                                 UserState state, CarpoolBot bot) {
        UserState updated = state
                .withNotes(null);
        stateManager.save(chatId, updated);

        showVehicleConfirmStep(chatId, carpoolUserId, updated, bot);
    }

    /**
     * After notes step — show vehicle confirmation before final ride review.
     * If driver has saved vehicle → ask to confirm or change.
     * If no saved vehicle → ask to enter vehicle details.
     */
    private void showVehicleConfirmStep(Long chatId, Long carpoolUserId,
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
                            ? "🎨 " + BotMessageBuilder.escape(user.getCarColor()) + " "
                            : "",
                    BotMessageBuilder.escape(user.getCarModel()),
                    BotMessageBuilder.escape(user.getPlateNumber()));

            var rows = List.of(
                    List.of(
                            BotMessageBuilder.button("✅ Yes, Proceed",  "VEHICLE_CONFIRM_YES"),
                            BotMessageBuilder.button("📝 Change Vehicle", "VEHICLE_CHANGE")
                    )
            );

            bot.send(sendWithInline(chatId,
                    "🚘 <b>Vehicle Confirmation</b>\n\n" +
                            "You are currently using:\n" +
                            "<b>" + vehicleDisplay + "</b>\n\n" +
                            "Use this for your ride?",
                    rows));
        } else {
            // No saved vehicle — go straight to input
            handleVehicleChange(chatId, carpoolUserId, updated, bot);
        }
    }

    private void handleDirectionCallback(Long chatId, String payload, Long carpoolUserId,
                                         UserState state, CarpoolBot bot) {
        RideDirection direction = payload.equals("HOME_TO_WORK")
                ? RideDirection.HOME_TO_WORK
                : RideDirection.WORK_TO_HOME;

        // Route based on current flow
        if (state.getFlow() == BotFlow.POST_RIDE_DIRECTION) {
            UserState updated = state
                    .withDirection(direction)
                    .withCarpoolUserId(carpoolUserId)
                    .withFlow(BotFlow.POST_RIDE_DEPARTURE_TIME);
            stateManager.save(chatId, updated);

            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "🕐 <b>What time are you leaving? (Start pickup time)</b>\n\n" +
                            "Format: <code>MM/DD HH:MM</code>\n" +
                            "Example: <code>" +
                            (state.getDirection() == RideDirection.HOME_TO_WORK
                                    ? LocalDateTime.now(ZoneId.of("Asia/Manila")).withHour(7).withMinute(30)
                                      .format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))
                                    : LocalDateTime.now(ZoneId.of("Asia/Manila")).withHour(18).withMinute(0)
                                      .format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))) +
                            "</code>"));
            return;
        }

        if (state.getFlow() == BotFlow.SEARCH_SELECT_DIRECTION) {
            UserState updated = state
                    .withDirection(direction)
                    .withCarpoolUserId(carpoolUserId)
                    .withFlow(BotFlow.SEARCH_SELECT_TIME);
            stateManager.save(chatId, updated);
            askForTimeWindow(chatId, bot);
            return;
        }

        // Default — direction selected from main menu
        handleDirectionSelected(chatId, carpoolUserId, direction, state, bot);
    }

    private void askForTimeWindow(Long chatId, CarpoolBot bot) {
        var rows = List.of(
                List.of(
                        BotMessageBuilder.button("🌅 Early Morning (5-7 AM)", "TIME:EARLY_MORNING"),
                        BotMessageBuilder.button("☀️ Morning (7-9 AM)",       "TIME:MORNING")
                ),
                List.of(
                        BotMessageBuilder.button("🌤️ Mid Morning (9-11 AM)", "TIME:MID_MORNING"),
                        BotMessageBuilder.button("🌇 Afternoon (3-7 PM)",     "TIME:AFTERNOON")
                ),
                List.of(
                        BotMessageBuilder.button("📅 Custom Date & Time",     "TIME:CUSTOM"),
                        BotMessageBuilder.button("🔍 Show All Today",         "TIME:ALL_TODAY")
                )
        );
        bot.send(sendWithInline(chatId,
                "🕐 <b>When do you want to leave?</b>\n\nSelect a time window:", rows));
    }

    private void handleDirectionSelected(Long chatId, Long carpoolUserId,
                                         RideDirection direction, UserState state,
                                         CarpoolBot bot) {
        UserState updated = state.withDirection(direction).withCarpoolUserId(carpoolUserId);
        stateManager.save(chatId, updated);

        String dirLabel = direction == RideDirection.HOME_TO_WORK
                ? "🏠 Home → Work" : "🏢 Work → Home";

        List<BookingResponse> myBookings = bookingService.getMyBookings(carpoolUserId);

        var rows = myBookings.isEmpty()
                ? List.of(
                List.of(
                        BotMessageBuilder.button("🚗 Post a Ride", "POST_RIDE"),
                        BotMessageBuilder.button("🔍 Find a Ride", "FIND_RIDE")
                )
        )
                : List.of(
                List.of(
                        BotMessageBuilder.button("🚗 Post a Ride", "POST_RIDE"),
                        BotMessageBuilder.button("🔍 Find a Ride", "FIND_RIDE")
                ),
                List.of(
                        BotMessageBuilder.button(
                                "📜 My Bookings (" + myBookings.size() + ")",
                                "MY_BOOKINGS")
                )
        );

        bot.send(sendWithInline(chatId,
                "Direction: <b>" + dirLabel + "</b>\n\nWhat would you like to do?", rows));
    }

    // ── Search filter ─────────────────────────────────────────────────

    private void handleSearchFilter(Long chatId, Long carpoolUserId,
                                    UserState state, CarpoolBot bot) {
        stateManager.save(chatId, state.withFlow(BotFlow.SEARCH_FILTER));

        // Highlight current selections
        String sortBy   = state.getFilterSortBy()   != null ? state.getFilterSortBy()   : "EARLIEST";
        Integer minSeats = state.getFilterMinSeats();
        java.math.BigDecimal maxPrice = state.getFilterMaxPrice();

        String earliestLabel  = sortBy.equals("EARLIEST")   ? "✅ 🕐 Earliest"   : "🕐 Earliest";
        String cheapestLabel  = sortBy.equals("CHEAPEST")   ? "✅ 💰 Cheapest"   : "💰 Cheapest";
        String mostSeatsLabel = sortBy.equals("MOST_SEATS") ? "✅ 🪑 Most Seats" : "🪑 Most Seats";

        String seats1Label = Integer.valueOf(1).equals(minSeats) ? "✅ 1+" : "1+";
        String seats2Label = Integer.valueOf(2).equals(minSeats) ? "✅ 2+" : "2+";
        String seats3Label = Integer.valueOf(3).equals(minSeats) ? "✅ 3+" : "3+";
        String seatsAnyLabel = minSeats == null ? "✅ Any" : "Any";

        String price50Label  = java.math.BigDecimal.valueOf(50).equals(maxPrice)  ? "✅ ₱50"  : "₱50";
        String price100Label = java.math.BigDecimal.valueOf(100).equals(maxPrice) ? "✅ ₱100" : "₱100";
        String price150Label = java.math.BigDecimal.valueOf(150).equals(maxPrice) ? "✅ ₱150" : "₱150";
        String priceAnyLabel = maxPrice == null ? "✅ Any" : "Any";

        var rows = List.of(
                List.of(BotMessageBuilder.button("── Sort By ──", "NOOP")),
                List.of(
                        BotMessageBuilder.button(earliestLabel,  "APPLY_FILTER:SORT:EARLIEST"),
                        BotMessageBuilder.button(cheapestLabel,  "APPLY_FILTER:SORT:CHEAPEST"),
                        BotMessageBuilder.button(mostSeatsLabel, "APPLY_FILTER:SORT:MOST_SEATS")
                ),
                List.of(BotMessageBuilder.button("── Min Seats ──", "NOOP")),
                List.of(
                        BotMessageBuilder.button(seats1Label,   "APPLY_FILTER:SEATS:1"),
                        BotMessageBuilder.button(seats2Label,   "APPLY_FILTER:SEATS:2"),
                        BotMessageBuilder.button(seats3Label,   "APPLY_FILTER:SEATS:3"),
                        BotMessageBuilder.button(seatsAnyLabel, "APPLY_FILTER:SEATS:ANY")
                ),
                List.of(BotMessageBuilder.button("── Max Share ──", "NOOP")),
                List.of(
                        BotMessageBuilder.button(price50Label,  "APPLY_FILTER:PRICE:50"),
                        BotMessageBuilder.button(price100Label, "APPLY_FILTER:PRICE:100"),
                        BotMessageBuilder.button(price150Label, "APPLY_FILTER:PRICE:150"),
                        BotMessageBuilder.button(priceAnyLabel, "APPLY_FILTER:PRICE:ANY")
                ),
                List.of(
                        BotMessageBuilder.button("✅ Show Rides", "APPLY_FILTER:SHOW:NOW"),
                        BotMessageBuilder.button("🔄 Reset",      "RESET_FILTER"),
                        BotMessageBuilder.button("◀️ Back",       "MAIN_MENU")
                )
        );

        bot.send(sendWithInline(chatId, "🔧 <b>Filter & Sort</b>", rows));
    }

    private void handleApplyFilter(Long chatId, String[] parts, Long carpoolUserId,
                                   UserState state, CarpoolBot bot) {
        if (parts.length < 3) return;

        String filterType  = parts[1]; // SORT, SEATS, PRICE, SHOW
        String filterValue = parts[2]; // EARLIEST, 1, 50, NOW, etc.

        UserState updated = switch (filterType) {
            case "SORT"  -> state.withFilterSortBy(filterValue);
            case "SEATS" -> state.withFilterMinSeats(
                    filterValue.equals("ANY") ? null : Integer.parseInt(filterValue));
            case "PRICE" -> state.withFilterMaxPrice(
                    filterValue.equals("ANY") ? null
                            : new java.math.BigDecimal(filterValue));
            default      -> state;
        };

        stateManager.save(chatId, updated);

        // SHOW:NOW — apply filters and show results
        if (filterType.equals("SHOW")) {
            showFilteredRides(chatId, carpoolUserId, updated,
                    updated.getSearchFrom(), updated.getSearchTo(), bot);
            return;
        }

        // Otherwise re-show filter screen with updated selections
        handleSearchFilter(chatId, carpoolUserId, updated, bot);
    }

    private void handleResetFilter(Long chatId, Long carpoolUserId,
                                   UserState state, CarpoolBot bot) {
        UserState reset = state
                .withFilterSortBy(null)
                .withFilterMinSeats(null)
                .withFilterMaxPrice(null)
                .withSearchPage(0);
        stateManager.save(chatId, reset);
        handleSearchFilter(chatId, carpoolUserId, reset, bot);
    }

    private void handleRidePage(Long chatId, String payload, Long carpoolUserId,
                                UserState state, CarpoolBot bot) {
        int page;
        try {
            page = Integer.parseInt(payload);
        } catch (NumberFormatException e) {
            page = 0;
        }

        stateManager.save(chatId, state.withSearchPage(page));

        // Re-fetch rides with current filters
        List<RideResponse> rides = rideService.getRidesByDirection(
                state.getDirection(),
                carpoolUserId,
                state.getSearchFrom(),
                state.getSearchTo(),
                state.getFilterMaxPrice(),
                state.getFilterMinSeats(),
                state.getFilterSortBy());

        String dirLabel = state.getDirection() == RideDirection.HOME_TO_WORK
                ? "🏠 Home → Work" : "🏢 Work → Home";

        String filterSummary = buildFilterSummary(state);

        bot.send(BotMessageBuilder.paginatedRideList(
                chatId, rides,
                "🔍 <b>Available Rides — " + dirLabel + "</b>",
                page, filterSummary));
    }

    private void showMyRides(Long chatId, Long carpoolUserId, CarpoolBot bot) {
        // Delegate to MessageHandler logic — reuse via service layer
        List<RideResponse> rides = rideService.getRecentRidesForRepost(carpoolUserId);

        if (rides.isEmpty()) {
            bot.send(BotMessageBuilder.text(chatId,
                    "🚗 <b>My Rides</b>\n\n<i>No past rides yet.</i>"));
            return;
        }

        List<RideResponse> recent = rides.stream()
                .filter(r -> r.status().name().equals("COMPLETED")
                        || r.status().name().equals("CANCELLED"))
                .limit(3)
                .toList();

        if (recent.isEmpty()) {
            bot.send(BotMessageBuilder.text(chatId,
                    "🚗 <b>My Rides</b>\n\n<i>No completed or cancelled rides yet.</i>"));
            return;
        }

        StringBuilder sb = new StringBuilder("🚗 <b>My Rides</b>\n\n");
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (int i = 0; i < recent.size(); i++) {
            RideResponse r = recent.get(i);
            String dirEmoji = r.direction() == RideDirection.HOME_TO_WORK ? "🏠" : "🏢";
            String statusLabel = switch (r.status().name()) {
                case "COMPLETED" -> "🏁";
                case "CANCELLED" -> "❌";
                default          -> "📋";
            };

            sb.append(String.format("<b>%d.</b> %s %s → %s | %s %s | ⛽ ₱%.2f share\n",
                    i + 1,
                    dirEmoji,
                    BotMessageBuilder.escape(r.originHub().name()),
                    BotMessageBuilder.escape(r.destinationHub().name()),
                    statusLabel,
                    r.departureTime()
                            .atZone(ZoneId.of("Asia/Manila"))
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
                .text("🏠 Menu")
                .callbackData("MAIN_MENU")
                .build()));

        bot.send(sendWithInline(chatId, sb.toString(), rows));
    }

    // ── Vehicle confirmation flow ─────────────────────────────────────────

    /**
     * Driver confirmed to use their saved vehicle — skip vehicle input.
     * Load vehicle from DB into state then proceed to notes.
     */
    private void handleVehicleConfirmYes(Long chatId, Long carpoolUserId,
                                         UserState state, CarpoolBot bot) {
        var userOpt = userRepository.findById(carpoolUserId);
        if (userOpt.isEmpty()) {
            bot.send(BotMessageBuilder.text(chatId, "⚠️ User not found."));
            return;
        }

        var user = userOpt.get();

        UserState updated = state
                .withPendingCarColor(user.getCarColor())
                .withPendingCarModel(user.getCarModel())
                .withPendingPlateNumber(user.getPlateNumber())
                .withFlow(BotFlow.POST_RIDE_CONFIRM);
        stateManager.save(chatId, updated);
        postRideHelper.showConfirmation(chatId, updated, bot);
    }

    /**
     * Driver saved new vehicle info — persist to DB then proceed to notes.
     */
    private void handleVehicleConfirmSave(Long chatId, Long carpoolUserId,
                                          UserState state, CarpoolBot bot) {
        if (state.getPendingCarModel() == null || state.getPendingPlateNumber() == null) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Session expired. Please start again with /start."));
            return;
        }

        try {
            vehicleService.updateVehicle(
                    carpoolUserId,
                    state.getPendingCarColor() != null ? state.getPendingCarColor() : "",
                    state.getPendingCarModel(),
                    state.getPendingPlateNumber());

            UserState updated = state.withFlow(BotFlow.POST_RIDE_CONFIRM);
            stateManager.save(chatId, updated);

            bot.send(BotMessageBuilder.textNoMenu(chatId,
                    "✅ Vehicle saved: " +
                            (state.getPendingCarColor() != null
                                    ? "🎨 " + BotMessageBuilder.escape(state.getPendingCarColor()) + " "
                                    : "") +
                            "🚘 " + BotMessageBuilder.escape(state.getPendingCarModel()) +
                            " | 🔢 " + BotMessageBuilder.escape(state.getPendingPlateNumber())));

            // Only proceed to ride confirmation if in post ride flow
            // Otherwise (vehicle command / profile) — just show menu
            if (state.getOriginHubId() != null && state.getDepartureTime() != null) {
                postRideHelper.showConfirmation(chatId, updated, bot);
            } else {
                stateManager.reset(chatId);
                handleMainMenu(chatId, carpoolUserId, updated, bot);
            }

        } catch (Exception e) {
            log.error("Failed to save vehicle for userId={}: {}", carpoolUserId, e.getMessage());
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Could not save vehicle info. " +
                            "Plate number may already be in use."));
        }
    }

    /**
     * Driver wants to change vehicle — start vehicle input flow.
     * Used both from post ride flow and from /vehicle command.
     */
    private void handleVehicleChange(Long chatId, Long carpoolUserId,
                                     UserState state, CarpoolBot bot) {
        UserState updated = state
                .withPendingCarColor(null)
                .withPendingCarModel(null)
                .withPendingPlateNumber(null)
                .withFlow(BotFlow.SET_VEHICLE_COLOR);
        stateManager.save(chatId, updated);

        bot.send(BotMessageBuilder.textWithCancel(chatId,
                "🎨 <b>What color is your vehicle?</b>\n\n" +
                        "Example: <code>Silver</code>, <code>White</code>, <code>Black</code>"));
    }

    /**
     * Driver removes their vehicle info.
     */
    private void handleVehicleRemove(Long chatId, Long carpoolUserId, CarpoolBot bot) {
        try {
            vehicleService.clearVehicle(carpoolUserId);
            bot.send(BotMessageBuilder.text(chatId,
                    "✅ Vehicle info removed."));
        } catch (Exception e) {
            log.error("Failed to remove vehicle for userId={}: {}", carpoolUserId, e.getMessage());
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Could not remove vehicle info. Please try again."));
        }
    }

    // ── Profile ───────────────────────────────────────────────────────

    private void handleMyProfile(Long chatId, Long carpoolUserId, CarpoolBot bot) {
        try {
            com.carpool.service.dto.response.ProfileStatsResponse stats =
                    profileService.getProfileStats(carpoolUserId);

            StringBuilder sb = new StringBuilder();
            sb.append("👤 <b>My Profile</b>\n\n");

            // Basic info
            sb.append(String.format("<b>%s</b>%s\n",
                    BotMessageBuilder.escape(stats.fullName()),
                    stats.telegramHandle() != null
                            ? " (@" + BotMessageBuilder.escape(stats.telegramHandle()) + ")"
                            : ""));
            sb.append(stats.roleLabel()).append("\n");
            sb.append("📅 Member since: ").append(stats.memberSince()).append("\n");

            // Vehicle info — show for drivers only
            if (stats.driverRidesPosted() != null || stats.carModel() != null) {
                if (stats.carModel() != null && stats.plateNumber() != null) {
                    sb.append(String.format("\n🚘 %s%s\n🔢 %s\n",
                            stats.carColor() != null
                                    ? "🎨 " + BotMessageBuilder.escape(stats.carColor()) + " "
                                    : "",
                            BotMessageBuilder.escape(stats.carModel()),
                            BotMessageBuilder.escape(stats.plateNumber())));
                } else {
                    sb.append("\n🚘 <i>No vehicle info yet</i>\n");
                }
            }

            // Driver stats
            if (stats.driverRidesPosted() != null) {
                sb.append("\n🏆 <b>Driver Stats</b>\n");
                if (stats.driverCompletionRate() != null) {
                    sb.append(String.format("⭐ %d%% Completion Rate\n",
                            stats.driverCompletionRate()));
                }
                sb.append(String.format("🚗 Rides posted: %d\n", stats.driverRidesPosted()));
                sb.append(String.format("✅ Completed: %d\n",    stats.driverCompleted()));
                sb.append(String.format("👥 Passengers served: %d\n", stats.driverPassengersServed()));
                if (stats.driverCancelled() > 0) {
                    sb.append(String.format("❌ Cancelled: %d\n", stats.driverCancelled()));
                }
            }

            // Passenger stats
            if (stats.passengerBookingsMade() != null) {
                sb.append("\n🧳 <b>Passenger Stats</b>\n");
                if (stats.passengerCompletionRate() != null) {
                    sb.append(String.format("⭐ %d%% Completion Rate\n",
                            stats.passengerCompletionRate()));
                }
                sb.append(String.format("📦 Bookings made: %d\n",  stats.passengerBookingsMade()));
                sb.append(String.format("✅ Completed: %d\n",       stats.passengerCompleted()));
                if (stats.passengerCancelledByMe() > 0) {
                    sb.append(String.format("❌ Cancelled by me: %d\n", stats.passengerCancelledByMe()));
                }
            }

            // New member — no stats yet
            if (stats.driverRidesPosted() == null && stats.passengerBookingsMade() == null) {
                sb.append("\n<i>No activity yet. Post or book a ride to get started!</i>");
            }

            var rows = List.of(
                    List.of(
                            BotMessageBuilder.button("🔄 Refresh",      "MY_PROFILE"),
                            BotMessageBuilder.button("🚘 My Vehicle",   "VEHICLE_CHANGE"),
                            BotMessageBuilder.button("🏠 Menu",         "MAIN_MENU")
                    )
            );

            bot.send(sendWithInline(chatId, sb.toString(), rows));

        } catch (Exception e) {
            log.error("Failed to load profile for userId={}: {}", carpoolUserId, e.getMessage());
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Could not load profile. Please try again."));
        }
    }

    // ── Terms ─────────────────────────────────────────────────────────────

    /**
     * Show full terms screen with key points.
     * Called from welcome screen or "View Terms Again" button.
     */
    public void handleTermsWelcome(Long chatId, CarpoolBot bot) {
        String termsText =
                "📋 <b>Terms & Community Guidelines</b>\n\n" +
                        "Please read and accept the following before using this bot:\n\n" +
                        "🚫 <b>Non-Commercial Use</b>\n" +
                        "This is a peer-to-peer carpooling tool — not a ride-hailing business.\n\n" +
                        "⛽ <b>Cost-Recovery Only</b>\n" +
                        "Contributions cover fuel, tolls, and parking only. No profit allowed.\n\n" +
                        "📜 <b>Legal Compliance</b>\n" +
                        "Drivers must follow LTFRB Carpooling Guidelines: 2-trip/day limit and required permits/QR codes.\n\n" +
                        "🛡️ <b>Safety First</b>\n" +
                        "Obey traffic laws and prioritize passenger safety. The bot owner/admin is not liable for any incidents.\n\n" +
                        "🚨 <b>Zero Tolerance</b>\n" +
                        "Overcharging, random pickups, or operating without permits (\"Colorum\" behavior) = permanent ban.\n\n" +
                        "Do you accept these terms?";

        var rows = List.of(
                List.of(
                        BotMessageBuilder.button("✅ I Accept",  "TERMS_ACCEPT"),
                        BotMessageBuilder.button("❌ Decline",   "TERMS_DECLINE")
                )
        );

        bot.send(sendWithInline(chatId, termsText, rows));
    }

    /**
     * User accepted terms — save version + timestamp, show welcome message.
     */
    private void handleTermsAccept(Long chatId, Long carpoolUserId, CarpoolBot bot) {
        try {
            userRepository.findById(carpoolUserId).ifPresent(user -> {
                user.setTermsVersionAccepted(com.carpool.bot.config.BotConfig.CURRENT_TERMS_VERSION);
                user.setTermsAcceptedAt(LocalDateTime.now());
                user.setTermsDeclinedAt(null);
                userRepository.save(user);
                log.info("Terms accepted: userId={} version={}",
                        carpoolUserId, com.carpool.bot.config.BotConfig.CURRENT_TERMS_VERSION);
            });

            bot.send(BotMessageBuilder.textNoMenu(chatId,
                    "🎉 <b>Welcome to the community!</b>\n\n" +
                            "Thank you for accepting the terms. You're now part of the " +
                            "Car-e-Pool Carpooling Community.\n\n" +
                            "Let's get started! 🚗"));

            // Show main menu after welcome message
            UserState state = stateManager.get(chatId);
            if (state == null) state = UserState.initial(carpoolUserId);
            handleMainMenu(chatId, carpoolUserId, state, bot);

        } catch (Exception e) {
            log.error("Failed to save terms acceptance for userId={}: {}",
                    carpoolUserId, e.getMessage());
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Something went wrong. Please try again."));
        }
    }

    /**
     * User declined terms — show empathetic message with option to review again.
     * Stores declined_at for weekly re-prompt logic.
     */
    private void handleTermsDecline(Long chatId, CarpoolBot bot) {
        userRepository.findById(
                        stateManager.get(chatId) != null
                                ? stateManager.get(chatId).getCarpoolUserId()
                                : 0L)
                .ifPresent(user -> {
                    user.setTermsDeclinedAt(LocalDateTime.now());
                    userRepository.save(user);
                });

        var rows = List.of(List.of(
                BotMessageBuilder.button("🔁 Review Terms Again", "TERMS_VIEW_AGAIN")
        ));

        bot.send(sendWithInline(chatId,
                "We understand if you're not ready. 🙏\n\n" +
                        "You'll need to accept the terms to use this bot. " +
                        "You can review them again anytime.",
                rows));
    }
}