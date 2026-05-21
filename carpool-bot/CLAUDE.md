# carpool-bot — Module Guide

Telegram bot module. Depends on `carpool-service`. All bot interaction logic lives here.

## Command Registry + BotContext

`CallbackHandler` maintains a `Map<String, BotCommand>` registered via `@PostConstruct`. Adding a new callback = one new `commands.put("ACTION", ctx -> handler.method(ctx))` entry — `CallbackHandler` itself never changes.

`BotContext` is an immutable record `(chatId, carpoolUserId, telegramId, state, payload, parts, bot, messageId)` passed to every command. `ctx.entityId()` parses `payload` as `Long`.

`MessageHandler` routes text input based on `UserState.flow` (a `BotFlow` enum).

## Conversation State

`StateManager` holds per-chatId `UserState` in a Caffeine cache (30-minute write-TTL, max 10k entries). `UserState` is an immutable record with `with*()` builder methods. `BotFlow` enum defines all possible conversation steps. `BotFlowHelper.showMainMenu()` calls `stateManager.reset()`, which wipes all flow state including `direction`.

**Stale button hazard:** Telegram messages stay interactive forever. A user can tap a button from an old message after their state has been reset (e.g., by returning to the main menu). All hub-selection and confirmation callbacks in `PostRideHandler` guard against this by checking that required state fields (`direction`, `originHubId`) are non-null before proceeding — missing fields mean the button is stale and a session-expired message is shown instead of crashing. `handleVehicleSelect` additionally checks that `departureTime`, `originHubId`, `seats`, and `contribution` are all non-null before calling `showConfirmation()` (which auto-unboxes `seats` and `contribution` and would NPE on null).

## Session Recovery

`SessionRecoveryHandler.isFlowSensitive(action)` guards against stale buttons after a bot restart. Flow-sensitive actions (post-ride steps, rating steps, custom hub confirmation) show a context-aware "session expired" message instead of crashing. Non-flow-sensitive actions (`MY_PROFILE`, `PENDING_HUBS`, etc.) get a fresh `UserState.initial()` and proceed normally.

## Departure Time Guard

`DriverHandler.handleStartRide()` enforces a 1-hour early-start window. If the current time is more than 60 minutes before `ride.getDepartureTime()`, the handler rejects the tap and sends a countdown message ("You can start the ride in X hours Y minutes") instead of transitioning the ride to DEPARTED.

## Vehicle Selection in the Post-Ride Flow

After the notes step, the post-ride flow calls `ProfileHandler.showVehicleSelectStep()` instead of going directly to confirmation. This shows the driver's saved vehicles as inline buttons (`VEHICLE_SELECT:{id}`), plus "➕ Add New Vehicle" if fewer than 3 exist. If no vehicles are saved yet, the flow jumps directly to `SET_VEHICLE_COLOR` (the add-vehicle input flow). The Cancel button on these screens uses `CANCEL_POST_RIDE`.

`handleVehicleSelect(ctx)` resolves the chosen vehicle, builds a display label (`color + model | plate`), stores it as `selectedVehicleId` and `selectedVehicleLabel` in `UserState`, then calls `postRideHelper.showConfirmation()`. **Stale button guard:** if `departureTime`, `originHubId`, `seats`, or `contribution` is null (stale button after session reset), a session-expired message is shown and the state is reset — avoids NPE when `showConfirmation` auto-unboxes `state.getSeats()`.

`handleAddVehicle(ctx)` starts the `SET_VEHICLE_COLOR → SET_VEHICLE_MODEL → SET_VEHICLE_PLATE → SET_VEHICLE_CAPACITY → VEHICLE_CONFIRM_SAVE` input flow. It checks `ctx.state().getDepartureTime() != null` to choose the cancel button: `CANCEL_POST_RIDE` (post-ride context) or `VEHICLE_CHANGE` (standalone vehicle management). The same context check applies in `showVehicleConfirmation()` for its Cancel button.

`VEHICLE_SELECT` is listed in `SessionRecoveryHandler.POST_RIDE_ACTIONS` so stale buttons after a bot restart show a context-aware "session expired" message. `ADD_VEHICLE` is intentionally excluded — `handleAddVehicle` handles both post-ride and standalone contexts via the `departureTime` null check, so it works safely with a fresh `UserState`.

## Time Picker (BotTimePickerUtil)

`BotTimePickerUtil` renders an inline keyboard time picker for departure time selection. Key design rules:

- **Fixed non-overlapping pages:** each page covers exactly 300 minutes (5 hours). `PAGE_SIZE_MIN = 300`. Page starts are multiples of 300: 0, 300, 600, 900, 1200.
- **20 slots × 15-minute increments per page.** `SLOTS_PER_PAGE = 20`, `SLOT_INCREMENT_MIN = 15`.
- **Today filtering:** past slots are omitted when `selectedDate == today` (Asia/Manila). `buildTimePicker` auto-advances to the first page with available slots via `adjustWindowForToday`.
- **Direction defaults:** `defaultWindowStart(direction)` returns 300 (HOME_TO_WORK) or 900 (WORK_TO_HOME). `defaultWindowStart(direction, selectedDate)` returns the page containing the next 15-min slot for today.
- **`adjustWindowForToday(windowStart, selectedDate)`** advances `windowStart` forward until the page has at least one available slot. No-op for future dates.
- **`buildTimePicker(int windowStart, LocalDate selectedDate)`** — direction parameter was removed; it was never read inside the method. Callers use `defaultWindowStart(direction)` separately.

**Critical invariant:** `UserState.timeWindowStart` must always be set to `adjustWindowForToday(defaultWindowStart(direction), selectedDate)` — never the raw `defaultWindowStart` — so the stored value always matches the page `buildTimePicker` displays. Divergence causes the first Later/Earlier tap to re-render the same page (Telegram rejects the edit with "message not modified").

This invariant is enforced in:
- `RideSearchHandler.handleDateSelected` — initial page on date selection
- `PostRideHandler.getUserState` — initial page for repost flow
- `PostRideHandler.handleTimeNavigation` — null-fallback case when `getTimeWindowStart() == null`

## Repost Edit Screen

`PostRideHandler.handleRepostRide()` pre-fills origin, destination, direction, seats, contribution, and notes from the original ride into `UserState`, sets `repostEditMode = true`, and shows an inline edit screen via `showRepostEditScreen()`. The driver can tap any field button (📍 Edit Start, 🏁 Edit End, 🪑 Edit Seats, ⛽ Edit Share, 📝 Edit Note) to edit it; each edit returns to the same edit screen. Tapping "✅ Continue" calls `handleRepostProceed()` which shows the calendar picker. After date selection, the flow goes to vehicle selection (`showVehicleSelectStep`), then confirmation — the same path as a new ride.

## AI Natural Language Ride Posting

`com.carpool.service.ai.AiService` is a thin Spring AI `ChatClient` wrapper around the local Ollama LLM (model `llama3.1:8b-instruct-q4_K_M`). `parseRideRequest(String userMessage)` sends the message to Ollama with a structured system prompt and maps the JSON response to `ParsedRideRequest` — a record with fields `direction`, `departureTime` (HH:mm), `departureDate` (YYYY-MM-DD or "tomorrow"), `seats`, `contribution`, `originHint`, `destinationHint`, and `notes`. On any failure (model unreachable, null entity, bad JSON) it returns `ParsedRideRequest.empty()` — the bot flow is never hard-dependent on AI availability.

`MessageHandler.isNaturalLanguageRidePost(String text)` triggers the AI path when the message contains a posting-intent keyword (`post`, `ride`, `magpost`) **and** at least one location or time keyword (e.g., `sucat`, `bgc`, `bukas`, `7am`, `home`, `work`). When triggered, `handleNaturalLanguageRidePost()` sends an "⏳ Analyzing your message…" interim reply, calls `aiService.parseRideRequest()`, and either:
- **Has usable data:** Hydrates `UserState` with all extracted fields, shows a "✨ Got it! Here's what I found:" summary, then calls `routeToNextPostRideStep()` which jumps to whichever step is still missing (direction → departure time → origin → destination → seats → contribution → vehicle select).
- **No usable data:** Shows a fallback message directing the user to use the normal menu.

Ollama config lives in `application-local.properties` and `application-prod.properties`:
```
spring.ai.ollama.base-url=http://ollama:8082      # internal Docker network in prod
spring.ai.ollama.chat.options.model=llama3.1:8b-instruct-q4_K_M
spring.ai.ollama.client.read-timeout=60s
```
The `carpool-bot` module depends on `carpool-service` which holds `AiService`; `carpool-web` adds `spring-ai-starter-model-ollama`.

## Group Announcement — Bot Side

`CarpoolBot.sendToGroup(text, rideId, driverId, topicId)` attaches two URL button rows to every group post: `🚘#N ❯❯❯❯ | View | Request a Seat` deep-links to `?start=RIDE_{rideId}`; `⭐ Follow Driver | View Ride` deep-links to `?start=FOLLOW_RIDE_{driverId}_{rideId}`. The `FOLLOW_RIDE_` parameter is handled in `MessageHandler.handleStart()` by the private `handleFollowAndViewRide()` method. It fetches the ride from DB using only `rideId` (the `driverId` in the URL is not trusted for any operation — `ride.driver().id()` is used throughout). It calls `favoriteService.isFavorite()` before `saveFavorite()` so existing followers do not see a false "now following" confirmation. New followers see "⭐ You're now following [driver]!" + ride card + Unfollow button. Existing followers see the ride card normally. The driver tapping their own link sees the driver ride card (View Bookings). Malformed deep links throw `NumberFormatException` caught at WARN level, and the user is sent to the main menu.

`CarpoolBot.sendToUser(telegramId, text, rideId, driverId)` sends the alert DM with three inline buttons: `VIEW_RIDE:{rideId}`, `BOOK_RIDE:{rideId}`, and `UNFOLLOW_DRIVER:{driverId}`. Tapping Unfollow calls `RatingHandler.handleUnfollowDriver()`, which removes the `UserFavorite` record and edits the alert message in-place to confirm — no menu navigation needed.

**Post-completion prompt:** `DriverHandler.handleCompleteRide()` resets state then shows "Would you like to post another ride?" with two buttons: `🚗 Yes, Post New Ride` → `POST_RIDE` and `❌ No, Thanks` → `MAIN_MENU`.

For the service-side announcement lifecycle (GroupNotificationService, persistGroupMessageId, seat-freed refresh, proactive refresh), see `carpool-service/CLAUDE.md`.

## My Followers Screen

`ProfileHandler.handleMyFollowers(ctx)` is registered as the `MY_FOLLOWERS` callback. It is accessible from the driver profile screen — `handleMyProfile` appends a "👥 My Followers (N)" button whenever `stats.driverRidesPosted() != null`. The count `N` comes from `FavoriteService.getFollowerCount(driverId)`, a separate `COUNT` query called inside the `handleMyProfile` try-catch.

The handler paginates at 8 followers per page. Pagination is encoded in callback data: `MY_FOLLOWERS:0`, `MY_FOLLOWERS:1`, etc. — `ctx.payload()` gives the page number. A static `FOLLOWED_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy")` constant formats the `followedAt` date. The screen is read-only; there are no per-follower actions. `MY_FOLLOWERS` is non-flow-sensitive — `SessionRecoveryHandler` treats it like `MY_PROFILE` (fresh `UserState`, proceeds normally after a bot restart).

## Re-announce with Seat Count Edit

When a driver taps **📢 Re-announce** from the main menu, the flow enters `BotFlow.REANNOUNCE_EDIT_SEATS`. The bot prompts: *"How many available seats do you want to show?"* The driver types a number.

`ProfileHandler.handleReannounceEditSeatsText()` parses the input, calls `rideService.updateAvailableSeats(rideId, newSeats, carpoolUserId)` to persist the new count (this also transitions the ride status: 0 seats → FULL, ≥1 seat → ACTIVE), then branches. State is reset to IDLE only **after** `reannounceRide()` returns successfully in each branch — if the call throws, the user remains in `REANNOUNCE_EDIT_SEATS` and can retry cleanly.

- **`newSeats == 0`:** Calls `rideService.reannounceRide(rideId, ...)` which fires `RidePostedEvent`. `onRidePosted` detects 0 available seats, deletes the old group post, clears `groupMessageId`, and returns without posting a new announcement. Bot confirms: "🚫 Ride Marked as Full — group announcement has been removed."
- **`newSeats > 0`:** Calls `rideService.reannounceRide(rideId, ...)` which fires `RidePostedEvent`. `onRidePosted` deletes old post and reposts with updated seat count. Bot confirms: "📢 Ride Re-announced! Seat count updated to N and ride posted to group. X re-announcements remaining."

The remaining count shown in the confirmation message and the `📢 Re-announce (N left)` button label both use `Math.max(0, 10 - ride.announceCount())`.

## Passenger Mini-Profile Badge — Bot Side

`BotMessageBuilder` exposes a static delegate method `buildPassengerBadge()` that carpool-bot callers use — it delegates to `ProfileBadgeBuilder` in `carpool-service`. This is the single source of truth; do not duplicate badge-building logic in carpool-bot.

Badge is shown in:
- **View Pending Request screen** (`DriverHandler.handleViewPendingRequest`) — appended after the passenger name.
- **View Confirmed Booking screen** (`DriverHandler.handleViewDriverBooking`) — same badge on confirmed booking detail.

**Best-effort pattern:** Profile lookup is wrapped in a separate inner try-catch in all bot handler locations. A failure logs a warning and defaults `badge` to `""` — never blocks the booking view.

## Vehicle Plate Privacy — Bot Side

Plate numbers are withheld from all public-facing bot messages. Only confirmed passengers see the plate.

**Hidden (plate omitted, color + model still shown):**
- `BotMessageBuilder.paginatedRideList()` — ride search list shows color + model only
- `BotMessageBuilder.formatRideCard()` — ride detail card does not show vehicle info at all

**Revealed to confirmed passengers only:**
- `BookingHandler.handleViewBooking()` — full vehicle line (color + model + plate) shown only when `b.status() == CONFIRMED || b.status() == COMPLETED`

Both Entity and legacy fallback handled: each location checks `ride.getVehicle()` first, then falls back to `ride.getDriver().getCarModel()` legacy fields.

## Ride Card: Member Verification Badge

`BotMessageBuilder.formatRideCard(ride, ratingLabel, memberBadge)` renders a badge line between the driver name and "Posted X ago". The badge is built by `BotMessageBuilder.buildMemberBadge(ProfileStatsResponse)` and shows role, completed ride count, and member-since date. Applied in both `RideSearchHandler.handleViewRide()` (bot search flow) and `MessageHandler.handleStart()` (group deep-link flow).

## Schedulers

One scheduler in `carpool-bot/scheduler/`:
- `StaleAnnouncementRefreshScheduler` — every 4 hours (`initialDelay` 10 min): queries ACTIVE rides where `groupMessagePostedAt < now - 36h` via `RideRepository.findRidesWithStaleGroupAnnouncement`; calls `GroupNotificationService.refreshGroupAnnouncementForRide()` per ride (each dispatched `@Async`). The 36h threshold gives a 12-hour safety buffer before Telegram's 48h message-deletion limit.

Uses `fixedDelay` (not `fixedRate`) with staggered `initialDelay` to prevent startup overlap.
