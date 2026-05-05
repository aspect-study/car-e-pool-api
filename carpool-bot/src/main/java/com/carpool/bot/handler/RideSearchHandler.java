package com.carpool.bot.handler;

import com.carpool.bot.CarpoolBot;
import com.carpool.bot.state.BotFlow;
import com.carpool.bot.state.StateManager;
import com.carpool.bot.state.UserState;
import com.carpool.bot.util.BotMessageBuilder;
import com.carpool.bot.util.BotTimePickerUtil;
import com.carpool.domain.enums.RideDirection;
import com.carpool.domain.enums.RideStatus;
import com.carpool.service.dto.response.RideResponse;
import com.carpool.service.rating.RatingService;
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
import java.util.List;

/**
 * Handles all ride search and discovery flows.
 * Covers: FIND_RIDE, TIME selection, search filter, pagination,
 * custom time input, and ride list display.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RideSearchHandler {

    private final StateManager  stateManager;
    private final RideService   rideService;
    private final BotFlowHelper flowHelper;
    private final RatingService ratingService;

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    // ── Find ride ─────────────────────────────────────────────────────────

    public void handleStartFindRide(BotContext ctx) {
        long activeCount = rideService.getMyRides(ctx.carpoolUserId()).stream()
                .filter(r -> r.status() == RideStatus.ACTIVE
                        || r.status() == RideStatus.FULL
                        || r.status() == RideStatus.DEPARTED)
                .count();

        if (activeCount > 0) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ <b>You have an active ride posted.</b>\n\n" +
                            "Please cancel or complete your ride first before " +
                            "looking for a ride as a passenger."));
            return;
        }

        if (ctx.state().getDirection() != null) {
            YearMonth month = YearMonth.now(MANILA);
            stateManager.save(ctx.chatId(), ctx.state()
                    .withCarpoolUserId(ctx.carpoolUserId())
                    .withCalendarMonth(month)
                    .withFlow(BotFlow.SEARCH_SELECT_DATE));
            flowHelper.showCalendar(ctx.chatId(), null, month, ctx.bot());
            return;
        }

        ctx.bot().send(BotMessageBuilder.directionSelector(ctx.chatId(),
                "🔍 <b>Find a Ride</b>\n\nWhich direction are you looking for?"));
        stateManager.save(ctx.chatId(), ctx.state()
                .withCarpoolUserId(ctx.carpoolUserId())
                .withFlow(BotFlow.SEARCH_SELECT_DIRECTION));
    }

    // ── Time selection ────────────────────────────────────────────────────

    public void handleTimeSelection(BotContext ctx) {
        String timeSlot = ctx.payload();
        LocalDateTime now       = LocalDateTime.now(MANILA);
        LocalDate     searchDay = ctx.state().getSearchDay() != null
                ? ctx.state().getSearchDay()
                : now.toLocalDate();
        LocalDateTime from;
        LocalDateTime to;

        switch (timeSlot) {
            case "EARLY_BIRD"    -> {
                from = searchDay.atTime(1,  0);
                to   = searchDay.atTime(4,  0);
            }
            case "EARLY_MORNING" -> {
                from = searchDay.atTime(4,  0);
                to   = searchDay.atTime(6,  0);
            }
            case "MORNING"       -> {
                from = searchDay.atTime(6,  0);
                to   = searchDay.atTime(9,  0);
            }
            case "MID_MORNING"   -> {
                from = searchDay.atTime(9,  0);
                to   = searchDay.atTime(11, 59);
            }
            case "NOON"          -> {
                from = searchDay.atTime(12, 0);
                to   = searchDay.atTime(15, 0);
            }
            case "AFTERNOON"     -> {
                from = searchDay.atTime(15, 0);
                to   = searchDay.atTime(18, 0);
            }
            case "EVENING"       -> {
                from = searchDay.atTime(18, 0);
                to   = searchDay.atTime(23, 59);
            }
            case "ALL_TODAY"     -> {
                from = searchDay.equals(now.toLocalDate())
                        ? now
                        : searchDay.atStartOfDay();
                to   = searchDay.atTime(23, 59);
            }
            default -> {
                ctx.bot().send(BotMessageBuilder.textWithCancel(ctx.chatId(),
                        "📅 <b>Enter date and time:</b>\n\n" +
                                "For today: <code>HH:MM</code>\n" +
                                "Example: <code>" +
                                now.plusHours(1).format(DateTimeFormatter.ofPattern("HH:mm")) +
                                "</code>\n\n" +
                                "For another date: <code>MM/DD HH:MM</code>\n" +
                                "Example: <code>" +
                                now.plusDays(1).format(DateTimeFormatter.ofPattern("MM/dd HH:mm")) +
                                "</code>"));
                stateManager.save(ctx.chatId(),
                        ctx.state().withFlow(BotFlow.SEARCH_SELECT_TIME));
                return;
            }
        }

        showFilteredRides(ctx.chatId(), ctx.carpoolUserId(), ctx.state(), from, to, ctx.bot());
    }

    public void showFilteredRides(Long chatId, Long carpoolUserId, UserState state,
                                  LocalDateTime from, LocalDateTime to, CarpoolBot bot) {
        log.info("SEARCH: direction={} from={} to={} userId={}",
                state.getDirection(), from, to, carpoolUserId);

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
        String filterSummary = flowHelper.buildFilterSummary(state);

        UserState updated = state
                .withSearchFrom(from).withSearchTo(to)
                .withSearchPage(0).withFlow(BotFlow.SEARCH_RESULTS);
        stateManager.save(chatId, updated);

        if (rides.isEmpty()) {
            String timeContext = flowHelper.buildTimeContext(from, to);
            var rows = List.of(
                    List.of(
                            BotMessageBuilder.button("🔄 Try Different Time", "FIND_RIDE"),
                            BotMessageBuilder.button("🔧 Adjust Filters",     "SEARCH_FILTER")
                    ),
                    List.of(
                            BotMessageBuilder.button("🚗 Post a Ride", "POST_RIDE"),
                            BotMessageBuilder.button("🏠 Menu",        "MAIN_MENU")
                    )
            );
            bot.send(flowHelper.sendWithInline(chatId,
                    "🔍 <b>No rides available — " + dirLabel + "</b>\n" +
                            "<i>" + timeContext + "</i>\n\n" +
                            "No drivers have posted for this time window yet.\n\n" +
                            "You can try a different time, adjust your filters, " +
                            "or be the first to offer a ride! 🚗",
                    rows));
            return;
        }

        bot.send(BotMessageBuilder.paginatedRideList(chatId, rides,
                "🔍 <b>Available Rides — " + dirLabel + "</b>",
                0, filterSummary));
    }

    // ── View ride ─────────────────────────────────────────────────────────

    public void handleViewRide(BotContext ctx) {
        try {
            RideResponse ride = rideService.getRideById(ctx.entityId());
            stateManager.save(ctx.chatId(),
                    ctx.state().withSelectedRideId(ctx.entityId()));

            boolean isDriver = ride.driver().id().equals(ctx.carpoolUserId());
            List<List<InlineKeyboardButton>> rows;

            if (isDriver) {
                rows = List.of(List.of(
                        BotMessageBuilder.button("📋 Bookings",
                                "RIDE_BOOKINGS:" + ctx.entityId()),
                        BotMessageBuilder.button("❌ Cancel",
                                "CANCEL_RIDE:"   + ctx.entityId())
                ));
            } else {
                rows = List.of(List.of(
                        BotMessageBuilder.button("✅ Book This Ride",
                                "BOOK_RIDE:" + ctx.entityId()),
                        BotMessageBuilder.button("🔍 Find a Ride",
                                "FIND_RIDE")
                ));
            }
            String ratingLabel = ratingService.getRideCardRatingLabel(ride.driver().id());
            ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                    BotMessageBuilder.formatRideCard(ride, ratingLabel), rows));

        } catch (Exception e) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not load ride details. It may have been cancelled."));
        }
    }

    // ── Search filter ─────────────────────────────────────────────────────

    public void handleSearchFilter(BotContext ctx) {
        stateManager.save(ctx.chatId(), ctx.state().withFlow(BotFlow.SEARCH_FILTER));

        String sortBy        = ctx.state().getFilterSortBy() != null
                ? ctx.state().getFilterSortBy() : "EARLIEST";
        Integer minSeats     = ctx.state().getFilterMinSeats();
        BigDecimal maxPrice  = ctx.state().getFilterMaxPrice();

        String earliestLabel  = sortBy.equals("EARLIEST")   ? "✅ 🕐 Earliest"   : "🕐 Earliest";
        String cheapestLabel  = sortBy.equals("CHEAPEST")   ? "✅ 💰 Cheapest"   : "💰 Cheapest";
        String mostSeatsLabel = sortBy.equals("MOST_SEATS") ? "✅ 🪑 Most Seats" : "🪑 Most Seats";
        String seats1Label    = Integer.valueOf(1).equals(minSeats) ? "✅ 1+" : "1+";
        String seats2Label    = Integer.valueOf(2).equals(minSeats) ? "✅ 2+" : "2+";
        String seats3Label    = Integer.valueOf(3).equals(minSeats) ? "✅ 3+" : "3+";
        String seatsAnyLabel  = minSeats == null ? "✅ Any" : "Any";
        String price50Label   = BigDecimal.valueOf(50).equals(maxPrice)  ? "✅ ₱50"  : "₱50";
        String price100Label  = BigDecimal.valueOf(100).equals(maxPrice) ? "✅ ₱100" : "₱100";
        String price150Label  = BigDecimal.valueOf(150).equals(maxPrice) ? "✅ ₱150" : "₱150";
        String priceAnyLabel  = maxPrice == null ? "✅ Any" : "Any";

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
        ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                "🔧 <b>Filter & Sort</b>", rows));
    }

    public void handleApplyFilter(BotContext ctx) {
        if (ctx.parts().length < 3) return;

        String filterType  = ctx.parts()[1];
        String filterValue = ctx.parts()[2];

        UserState updated = switch (filterType) {
            case "SORT"  -> ctx.state().withFilterSortBy(filterValue);
            case "SEATS" -> ctx.state().withFilterMinSeats(
                    filterValue.equals("ANY") ? null : Integer.parseInt(filterValue));
            case "PRICE" -> ctx.state().withFilterMaxPrice(
                    filterValue.equals("ANY") ? null : new BigDecimal(filterValue));
            default      -> ctx.state();
        };

        stateManager.save(ctx.chatId(), updated);

        if (filterType.equals("SHOW")) {
            showFilteredRides(ctx.chatId(), ctx.carpoolUserId(), updated,
                    updated.getSearchFrom(), updated.getSearchTo(), ctx.bot());
            return;
        }

        // Re-show filter screen with updated checkmarks
        handleSearchFilter(new BotContext(
                ctx.chatId(), ctx.carpoolUserId(), ctx.telegramId(),
                updated, ctx.payload(), ctx.parts(), ctx.bot(), ctx.messageId()));
    }

    public void handleResetFilter(BotContext ctx) {
        UserState reset = ctx.state()
                .withFilterSortBy(null)
                .withFilterMinSeats(null)
                .withFilterMaxPrice(null)
                .withSearchPage(0);
        stateManager.save(ctx.chatId(), reset);
        handleSearchFilter(new BotContext(
                ctx.chatId(), ctx.carpoolUserId(), ctx.telegramId(),
                reset, ctx.payload(), ctx.parts(), ctx.bot(), ctx.messageId()));
    }

    public void handleRidePage(BotContext ctx) {
        int page;
        try {
            page = Integer.parseInt(ctx.payload());
        } catch (NumberFormatException e) {
            page = 0;
        }
        stateManager.save(ctx.chatId(), ctx.state().withSearchPage(page));

        List<RideResponse> rides = rideService.getRidesByDirection(
                ctx.state().getDirection(),
                ctx.carpoolUserId(),
                ctx.state().getSearchFrom(),
                ctx.state().getSearchTo(),
                ctx.state().getFilterMaxPrice(),
                ctx.state().getFilterMinSeats(),
                ctx.state().getFilterSortBy());

        String dirLabel = ctx.state().getDirection() == RideDirection.HOME_TO_WORK
                ? "🏠 Home → Work" : "🏢 Work → Home";
        String filterSummary = flowHelper.buildFilterSummary(ctx.state());

        ctx.bot().send(BotMessageBuilder.paginatedRideList(ctx.chatId(), rides,
                "🔍 <b>Available Rides — " + dirLabel + "</b>",
                page, filterSummary));
    }

    /**
     * Handles calendar month navigation — PREV or NEXT.
     * Updates calendarMonth in state and re-renders the calendar.
     */
    public void handleCalendarNav(BotContext ctx) {
        YearMonth current = ctx.state().getCalendarMonth() != null
                ? ctx.state().getCalendarMonth()
                : YearMonth.now(MANILA);

        YearMonth updated = "PREV".equals(ctx.payload())
                ? current.minusMonths(1)
                : current.plusMonths(1);

        // Preserve original flow — POST_RIDE_SELECT_DATE or SEARCH_SELECT_DATE
        BotFlow flow = ctx.state().getFlow() == BotFlow.POST_RIDE_SELECT_DATE
                ? BotFlow.POST_RIDE_SELECT_DATE
                : BotFlow.SEARCH_SELECT_DATE;

        stateManager.save(ctx.chatId(), ctx.state()
                .withCalendarMonth(updated)
                .withFlow(flow));

        flowHelper.showCalendar(ctx.chatId(), ctx.messageId(), updated, ctx.bot());
    }

    public void handleDateSelected(BotContext ctx) {
        try {
            LocalDate selected = LocalDate.parse(ctx.payload());

            // Post ride flow — show time picker
            if (ctx.state().getFlow() == BotFlow.POST_RIDE_SELECT_DATE) {
                int windowStart = BotTimePickerUtil.defaultWindowStart(ctx.state().getDirection());
                UserState updated = ctx.state()
                        .withSearchDay(selected)
                        .withTimeWindowStart(windowStart)
                        .withFlow(BotFlow.POST_RIDE_TIME_PICK);
                stateManager.save(ctx.chatId(), updated);
                flowHelper.showTimePicker(ctx.chatId(), ctx.messageId(),
                        ctx.state().getDirection(), windowStart, selected, ctx.bot());
                return;
            }

            // Search flow — show time window
            stateManager.save(ctx.chatId(), ctx.state()
                    .withSearchDay(selected)
                    .withFlow(BotFlow.SEARCH_SELECT_TIME));
            flowHelper.askForTimeWindow(ctx.chatId(), ctx.messageId(), selected, ctx.bot());

        } catch (Exception e) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Invalid date selected. Please try again."));
            flowHelper.showCalendar(ctx.chatId(), ctx.messageId(),
                    YearMonth.now(MANILA), ctx.bot());
        }
    }
}