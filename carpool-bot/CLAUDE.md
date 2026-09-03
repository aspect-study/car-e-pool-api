# carpool-bot — Module Guide

Telegram bot module. Depends on `carpool-service`. All bot interaction logic lives here.

## Command Registry + BotContext

`CallbackHandler` maintains a `Map<String, BotCommand>` registered via `@PostConstruct`. Adding a new callback = one new `commands.put("ACTION", ctx -> handler.method(ctx))` entry — `CallbackHandler` itself never changes.

`BotContext` is an immutable record `(chatId, carpoolUserId, telegramId, state, payload, parts, bot, messageId)` passed to every command. `ctx.entityId()` parses `payload` as `Long`.

`MessageHandler` routes text input based on `UserState.flow` (a `BotFlow` enum).

## Conversation State

`StateManager` holds per-chatId `UserState` in a Caffeine cache (30-minute write-TTL, max 10k entries). `UserState` is an immutable record with `with*()` builder methods. `BotFlow` enum defines all possible conversation steps. `BotFlowHelper.showMainMenu()` calls `stateManager.reset()`, which wipes all flow state including `direction`.

**Stale button hazard:** Telegram messages stay interactive forever. A user can tap a button from an old message after their state has been reset (e.g., by returning to the main menu). All hub-selection and confirmation callbacks in `PostRideHandler` guard against this by checking that required state fields (`direction`, `originHubId`) are non-null before proceeding — missing fields mean the button is stale and a session-expired message is shown instead of crashing. `handleVehicleSelect` additionally checks that `departureTime`, `originHubId`, `seats`, and `contribution` are all non-null before calling `showConfirmation()` (which auto-unboxes `seats` and `contribution` and would NPE on null).

## Direction-Scoped Conflict Checks

Conflict checks are direction-scoped — a user can drive HOME_TO_WORK while holding a WORK_TO_HOME passenger booking. All bot-side checks are early-warning UX; `RideService.createRide()` and `BookingService.createBooking()` are the authoritative service-layer gates (see `carpool-service/CLAUDE.md`).

**Post-ride flow — `PostRideHandler.sendConflictMessageIfAny(BotContext ctx, RideDirection direction)`:**
Private helper called at two points: (1) `handleStartPostRide()` when direction is already in state (repost/AI pre-fill path), and (2) `handleDirectionCallback()` when flow == `POST_RIDE_DIRECTION`. Calls `rideService.hasActiveRide(carpoolUserId, direction)` for a single DB EXISTS query (replaces the previous stream over `getMyRides()`), then streams `bookingService.getMyBookings()` for same-direction passenger bookings. On any conflict, sends a message, calls `stateManager.reset()`, and returns `true` so the caller returns immediately. Always passes a non-null `direction` — `handleStartPostRide` guards with `if (direction == null) return` before calling this helper.

**Post-ride flow — `MessageHandler` reply keyboard path:**
`MessageHandler` injects both `RideService` and `BookingService`. When flow == `POST_RIDE_DIRECTION` and the user taps the persistent reply keyboard direction button (instead of the inline selector), `MessageHandler` runs the same inline conflict check before calling `postRideHandler.askForEtd()`.

**Search flow — `RideSearchHandler.handleStartFindRide()` re-entry branch:**
Triggered when direction is already in state (e.g. "Try Different Time" re-entry). Checks driver ride conflict, then passenger booking conflict. Both conflict returns call `stateManager.reset()` — without it, direction stays in state and every subsequent FIND_RIDE tap re-shows the warning with no exit. `BookingService` is injected into `RideSearchHandler` for this check.

**Search flow — direction selection paths:**
`PostRideHandler.handleDirectionCallback()` (flow == `SEARCH_SELECT_DIRECTION`) and `MessageHandler` (flow == `SEARCH_SELECT_DIRECTION` reply keyboard) both run the same driver-ride + passenger-booking inline check before showing the calendar.

**Search results — empty-results CTA:**
`RideSearchHandler.showFilteredRides()` shows a "🚗 Post a Ride" bottom button when no rides are found — a nudge for passengers to become drivers. When the user already has any ACTIVE or FULL ride (checked via `rideService.getMyRides()` stream, any direction), this button is replaced with "🔍 Find a Ride" so a driver is never prompted to post a duplicate ride from within the search flow.

## Session Recovery

`SessionRecoveryHandler.isFlowSensitive(action)` guards against stale buttons after a bot restart. Flow-sensitive actions (post-ride steps, rating steps, custom hub confirmation, edit-time steps) show a context-aware "session expired" message instead of crashing. Non-flow-sensitive actions (`MY_PROFILE`, `PENDING_HUBS`, etc.) get a fresh `UserState.initial()` and proceed normally.

Five edit-time callbacks are flow-sensitive: `CAL_NAV_EDIT_TIME`, `CAL_DATE_EDIT_TIME`, `RIDE_TIME_EDIT`, `TIME_NAV_EDIT`, `CONFIRM_EDIT_RIDE_TIME`. The entry point `EDIT_RIDE_TIME` is intentionally excluded — it reads `rideId` from the callback payload, not from `UserState`, so it works safely with a fresh session (via `UserState.initial()`).

## Main Menu — Active-Ride Routing & Ride Picker

`BotFlowHelper.showMainMenu()` filters `getMyRides()` down to `activeRides` (status `ACTIVE`, `FULL`, or `DEPARTED`) and branches on the **count**:

- **0 active rides:** unchanged inactive-ride branch (Find a Ride, My Bookings, Ratings, Profile, etc.).
- **Exactly 1:** delegates straight to `showRideManagementCard(chatId, carpoolUserId, activeRides.get(0).id(), bot)` — no picker shown.
- **2 or more** (e.g. one `HOME_TO_WORK` + one `WORK_TO_HOME`, or a `DEPARTED` ride alongside an `ACTIVE`/`FULL` one): shows a **ride picker** instead — one button per ride labeled with its direction (`🏠 Home → Work` / `🏢 Work → Home`) plus a live pending-count badge (`⏳ N pending` / `✅ 0 pending`, from `bookingService.countPendingRequestsForRide(ride.id())`). Each button routes to `MANAGE_RIDE:{rideId}`. The picker header always reports the **actual** `activeRides.size()` — never hardcode "2"; a driver can have 3+ active rides (e.g. a `DEPARTED` ride plus two `ACTIVE`/`FULL` ones).

`MANAGE_RIDE:{rideId}` is registered in `CallbackHandler` and routes to `flowHelper.showRideManagementCard(...)`.

## Ride Management Card — `showRideManagementCard`

`BotFlowHelper.showRideManagementCard(chatId, carpoolUserId, rideId, bot)` is the single-ride management view extracted from the old inline `showMainMenu` active-ride block. It is reached either directly (driver has exactly one active ride) or via the picker's `MANAGE_RIDE:{rideId}`.

**Stale-button guards (checked before rendering anything):**
- `rideService.getRideById(rideId)` wrapped in try-catch — shows "⚠️ This ride is no longer available." if the ride can't be loaded.
- `active.driver().id().equals(carpoolUserId)` ownership check — shows "⚠️ This is not your ride." on mismatch.

**Three status sub-branches** (`DEPARTED`, `pendingCount > 0`, default `ACTIVE`/`FULL`) mirror the old single-ride logic, now parameterized by `rideId`:
- `bookingService.getMyBookings(carpoolUserId)` still backs the `📜 My Bookings (N) → MY_BOOKINGS` row in all three branches whenever non-empty.
- `✏️ Edit Time → EDIT_RIDE_TIME:{rideId}` appears in the `pendingCount > 0` and default branches only — excluded from `DEPARTED` because `RideService.updateDepartureTime()` rejects edits on departed rides at the service layer.
- `⏳ Pending (N)` now emits **`PENDING_REQUESTS:{rideId}`** (scoped to this ride), not the bare `PENDING_REQUESTS`. `DriverHandler.handlePendingRequests` branches on `ctx.entityId()`: non-null → `bookingService.getPendingRequestsForRide(rideId, carpoolUserId)`, null → the legacy all-rides `getPendingRequestsForDriver(carpoolUserId)`. The "◀️ Back to Pending" button on the request-detail screen now carries `b.rideId()` so it returns to the correctly-scoped list rather than the unscoped one.

**`🚗 Post a Ride` button:** shown in all three sub-branches whenever `activeRideCount < 2` (re-derived per card render via `rideService.getMyRides(carpoolUserId)`, counting `ACTIVE`/`FULL`/`DEPARTED`). Lets a driver with a single active ride post a second one (typically the return leg) without cancelling the first.

**`🔄 Repost {other direction}` button:** shown whenever `activeRideCount < 2` **and** the current ride's direction is `HOME_TO_WORK` or `WORK_TO_HOME` (never shown for `OTHER`). Routes to `DIRECTION:{otherDirection}`, pre-selecting the *opposite* direction so the driver lands one tap into posting the return leg. Label is direction-specific: `🔄 Repost 🏢 Work → Home` or `🔄 Repost 🏠 Home → Work`.

Both buttons sit in the row immediately before `🔍 Find a Ride` / `👤 My Profile` (or just before `👤 My Profile` in the `DEPARTED` branch, which has no Find-a-Ride row).

**Button naming convention — critical distinction:**
- `👥 My Passengers` → callback `RIDE_BOOKINGS:{rideId}` — driver views passengers booked **onto their ride**. Appears in the ride management card and on ride cards when the viewer is the driver.
- `📜 My Bookings (N)` → callback `MY_BOOKINGS` — user views their own bookings **as a passenger** on someone else's ride.
- `✏️ Edit Time` → callback `EDIT_RIDE_TIME:{rideId}` — driver updates the departure time of their active or FULL ride.
- `⏳ Pending (N)` → callback `PENDING_REQUESTS:{rideId}` — scoped to one ride; the legacy unscoped `PENDING_REQUESTS` (all rides) still works as a fallback when no `rideId` is present in the payload.

These buttons coexist in the ride management card. Never use "View Bookings" — it was renamed to "My Passengers" precisely to avoid ambiguity with "My Bookings".

## Edit Departure Time Flow

Drivers can change the departure time of an ACTIVE or FULL ride. Entry points: `✏️ Edit Time` button in the main menu active-ride block, and `✏️ Edit Time` button in `RideSearchHandler.handleViewRide()` when the viewer is the driver.

**BotFlow states:** `EDIT_RIDE_TIME_SELECT_DATE` (calendar shown) → `EDIT_RIDE_TIME_PICK` (time picker shown) → `EDIT_RIDE_TIME_CONFIRM` (confirmation screen shown, awaiting driver tap).

**Callbacks registered in `CallbackHandler`:**
- `EDIT_RIDE_TIME` → `DriverHandler.handleEditRideTime()` — validates ownership + status (ACTIVE/FULL), stores `selectedRideId` and `direction` in `UserState`, shows calendar.
- `CAL_NAV_EDIT_TIME` → `handleEditRideTimeCalendarNav()` — prev/next month navigation; updates `calendarMonth` in state.
- `CAL_DATE_EDIT_TIME` → `handleEditRideTimeDateSelected()` — stores chosen date as `editTimeSelectedDate` in `UserState`, transitions to `EDIT_RIDE_TIME_PICK`, shows time picker.
- `TIME_NAV_EDIT` → `handleEditRideTimePickerNav()` — earlier/later page navigation; updates `timeWindowStart`.
- `RIDE_TIME_EDIT` → `handleEditRideTimeSelected()` — parses hour/minute, validates `editTimeSelectedDate` and `selectedRideId` are non-null (stale-button guard), fetches the current ride to show current vs new time, stores `editTimePendingDateTime` in `UserState`, transitions to `EDIT_RIDE_TIME_CONFIRM`, and **edits the time picker message in-place** (via `EditMessageText` + `ctx.messageId()`) to show the confirmation screen. Does NOT call the service yet.
- `CONFIRM_EDIT_RIDE_TIME` → `handleConfirmEditRideTime()` — validates `editTimePendingDateTime` and `selectedRideId` are non-null (stale-button guard), calls `rideService.updateDepartureTime()`, resets state to IDLE, shows success message.

**`UserState` fields for edit-time flow:**
- `editTimeSelectedDate` (`LocalDate`) — date chosen on the calendar step.
- `editTimePendingDateTime` (`LocalDateTime`) — fully-composed new departure time stored after date+time are picked, held until the driver confirms. Cleared on `stateManager.reset()`.

Existing shared fields `selectedRideId`, `direction`, `calendarMonth`, and `timeWindowStart` are reused.

**Edit-in-place invariant:** `handleEditRideTimeSelected` uses `bot.edit(EditMessageText...)` with `ctx.messageId()` to replace the time picker message with the confirmation screen — the same pattern used by `showCalendar` and `showTimePicker`. This is critical: sending a new message instead would leave the time picker interactive, allowing a second slot tap to silently overwrite `editTimePendingDateTime` while an orphaned confirmation message still shows the old time.

**Calendar/time-picker prefix overloads:** `BotCalendarUtil.buildCalendar(calendarMonth, datePrefix, navPrefix)` and `BotTimePickerUtil.buildTimePicker(windowStart, selectedDate, slotPrefix, navPrefix)` accept custom callback prefixes, allowing the same UI components to be reused across the post-ride and edit-time flows without conflicts. `BotFlowHelper.showCalendar()` and `showTimePicker()` expose matching overloads.

**Confirmed-passenger notification with Keep/Cancel buttons:** After `updateDepartureTime()` succeeds, `NotificationService.onRideTimeChanged()` sends each confirmed passenger a DM with `✅ Keep Booking → KEEP_BOOKING:{bookingId}` and `❌ Cancel Booking → CANCEL_BOOKING:{bookingId}` inline buttons (keyboard overload of `sendAndRecord`). `KEEP_BOOKING` is registered in `CallbackHandler` and shows a confirmation message without changing booking status (the booking remains CONFIRMED). `CANCEL_BOOKING` follows the standard passenger cancellation path.

**Group post refresh:** `GroupNotificationService.onRideTimeChanged()` deletes the old group announcement and reposts a fresh one with the updated time. No 48-hour guard is applied — a time change is always high-signal. Returns early if `groupMessageId` is null or ride is not ACTIVE/FULL.

## Edit Route Flow

Drivers can change the origin and/or destination of an ACTIVE or FULL ride without cancelling it — added because cancelling sends accepted passengers a `RIDE_CANCELLED` DM even when the driver only meant to tweak the route. Entry point: `🔀 Change Route` button in the ride management card (`BotFlowHelper.showRideManagementCard`), placed directly after `✏️ Edit Time` in both the `pendingCount > 0` and default ACTIVE/FULL branches (not shown for `DEPARTED`, same restriction as Edit Time).

**Custom (unlisted) hub support:** when `hubMatcher.suggest(text)` returns no matches, `PostRideHandler.handleEditRouteOriginSearch()`/`handleEditRouteDestSearch()` show the same "isn't in our hub list yet" branch as the post-ride flow — recent hubs plus a `✅ Use "<text>"` button. That button fires `EDIT_CONFIRM_CUSTOM_ORIGIN`/`EDIT_CONFIRM_CUSTOM_DEST` → `PostRideHandler.handleEditConfirmCustomOrigin()`/`handleEditConfirmCustomDest()`, which both delegate to a private `applyCustomRouteHub()`: calls `hubService.suggestHub(new SuggestHubRequest(customName, "Unverified"), carpoolUserId)` to create/reuse a PENDING hub, then `rideService.updateRoute()` with that hub's id. This closed a real v1 gap found during prod smoke-testing — drivers typing an unlisted landmark previously hit a dead-end "No matching hub found" message with no way to proceed.

**Prerequisite fix:** `DriverHandler.handleEditRideRoute()` now also stores `.withCarpoolUserId(ctx.carpoolUserId())` in `UserState` — the custom-hub branch calls `hubService.getRecentHubsForUser(state.getCarpoolUserId())`, and unlike the post-ride flow (which sets `carpoolUserId` during direction selection), the edit-route entry point had no earlier step that set it.

One side (origin or destination) is still changed per pass; there is no combined "edit both at once" screen.

**BotFlow states:** `EDIT_ROUTE_ORIGIN` (awaiting text search for the new origin) and `EDIT_ROUTE_DEST` (awaiting text search for the new destination). No new `UserState` fields — reuses the existing `selectedRideId`.

**Callbacks registered in `CallbackHandler`:**
- `EDIT_RIDE_ROUTE` → `DriverHandler.handleEditRideRoute()` — validates ownership + status (ACTIVE/FULL), stores `selectedRideId` in `UserState`, shows a "Change Start / Change End" chooser.
- `EDIT_ROUTE_ORIGIN` → `handleEditRouteOriginStart()` — sets flow to `EDIT_ROUTE_ORIGIN`, prompts for a search term.
- `EDIT_ROUTE_DEST` → `handleEditRouteDestStart()` — sets flow to `EDIT_ROUTE_DEST`, prompts for a search term.
- Text input in either flow is routed by `MessageHandler` to `PostRideHandler.handleEditRouteOriginSearch()` / `handleEditRouteDestSearch()` — reuses the existing private `buildHubButtonRows()` helper with new callback prefixes `EDIT_HUB_ORIGIN` / `EDIT_HUB_DEST` and retype actions `RETYPE_EDIT_ORIGIN` / `RETYPE_EDIT_DEST`.
- `EDIT_HUB_ORIGIN` / `EDIT_HUB_DEST` → `DriverHandler.handleEditHubOriginSelected()` / `handleEditHubDestSelected()` — both delegate to a private `applyRouteChange()` that calls `rideService.updateRoute()`, resets state, and shows the new route on success. `InvalidRideStateException`/`SameHubException` and `NotRideOwnerException` are caught separately to show the exact validation message.
- `EDIT_CONFIRM_CUSTOM_ORIGIN` / `EDIT_CONFIRM_CUSTOM_DEST` → `PostRideHandler.handleEditConfirmCustomOrigin()` / `handleEditConfirmCustomDest()` — the custom-hub path described above.

**Session recovery:** `EDIT_ROUTE_ORIGIN`, `EDIT_ROUTE_DEST`, `EDIT_HUB_ORIGIN`, `EDIT_HUB_DEST`, `RETYPE_EDIT_ORIGIN`, `RETYPE_EDIT_DEST`, `EDIT_CONFIRM_CUSTOM_ORIGIN`, `EDIT_CONFIRM_CUSTOM_DEST` are flow-sensitive in `SessionRecoveryHandler` (mirrors the five edit-time callbacks). `EDIT_RIDE_ROUTE` itself is intentionally excluded — it reads `rideId` from the callback payload, not from `UserState`, so it works safely with a fresh session after a bot restart.

**Reused, unchanged:** `KEEP_BOOKING`/`CANCEL_BOOKING` callbacks and the passenger cancellation path (introduced by the time-change feature) — the route-change passenger DM (`NotificationService.onRideRouteChanged`) attaches the same two buttons.

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

**Stale-button guard:** After loading the original ride, `handleRepostRide()` calls `rideService.hasActiveRide(carpoolUserId, original.direction())`. If an active ride already exists in the same direction, the user receives "You already have an active ride post for this direction. Please cancel it first." and the flow is aborted. Prevents tapping old "Repost" buttons from Telegram chat history when the driver already has an active post.

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

The remaining count shown in the confirmation message and the `📢 Re-announce (N left)` button label both use `ProfileHandler.remainingAnnouncements(Integer)` / `remainingAnnouncementsText(Integer)` — two small private helpers (`Math.max(0, 10 - announceCount)` and its pluralized message form) shared by `handleReannounceRide`, `handleConfirmReannounce`, `handleReannounceEditSeatsText`, and `handleReannounceUpdateTotalSeatsText`. This block used to be copy-pasted separately in each of those four methods; add any new re-announcement-consuming flow through these helpers instead of re-inlining the formatting.

## Update Total Seats

A third button, **🚘 Update Total Seats**, sits alongside Re-announce and Edit Seats on the `handleReannounceRide` menu (`REANNOUNCE_UPDATE_TOTAL_SEATS:{rideId}`). Unlike Edit Seats — which only redistributes *available* seats within the ride's existing total — this changes the total itself, both up and down. Added for the case where a driver informally reserved a seat for someone outside the app, that arrangement falls through, and the app's own seat count has no record of it to release.

**BotFlow:** `REANNOUNCE_UPDATE_TOTAL_SEATS`, alongside `REANNOUNCE_EDIT_SEATS`.

- `REANNOUNCE_UPDATE_TOTAL_SEATS` (start) → `ProfileHandler.handleReannounceUpdateTotalSeatsStart` — ownership check, shows current total/reserved/available and prompts for a new total seat count, stores `selectedRideId` + flow in `UserState`. Not flow-sensitive in `SessionRecoveryHandler` — same as `REANNOUNCE_EDIT_SEATS`'s entry point, it reads `rideId` from the callback payload, not from state.
- The displayed "reserved" count and the "min" in the prompt both come from `rideService.getReservedSeatsCount(rideId)` (an extra read query), **not** from `totalSeats - availableSeats`. An earlier version derived it from `availableSeats` and shipped with two bugs: it overstated the floor whenever the driver had used Edit Seats to manually show fewer available seats than the total allowed, and it could tell the driver "min 0" when 0 is always rejected (total can never go below 1). `Math.max(1, reservedSeats)` is what's actually shown as the min.
- Text input routed by `MessageHandler` to `ProfileHandler.handleReannounceUpdateTotalSeatsText` — calls `rideService.updateTotalSeatsAndReannounce(rideId, newTotalSeats, carpoolUserId)`, a single atomic service call (see `carpool-service/CLAUDE.md`) rather than two separate calls. This means updating total seats **consumes a re-announcement slot**, same as editing available seats does today. An earlier version called `updateTotalSeats` then `reannounceRide` as two separate transactions — if the re-announce cap rejected the second call, the seat-count change from the first had already committed with no repost and no indication to the driver that anything had changed. The combined method closes that gap: both steps commit together or neither does.
- All validation errors (`InvalidRideStateException`) surface verbatim to the driver, same pattern as `handleReannounceEditSeatsText`.

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

## Ratings Wall

Paginated view of received ratings (stars + optional comment) for any user. Stateless — all state is encoded in callback data, no `UserState` needed.

**Handler:** `ViewRatingsHandler` — registered in `CallbackHandler` for three callbacks:
- `VIEW_RATINGS:{userId}` — loads page 0, sends a **new message**
- `RATINGS_PAGE:{userId}:{page}` — loads a specific page, **edits the existing message** in-place
- `CLOSE_RATINGS:{userId}` — deletes the ratings message

**Entry points:**
- "⭐ My Ratings" button in `BotFlowHelper.showMainMenu()` — **inactive-ride branch only** (not shown when the user has an active ride). Emits `VIEW_RATINGS:{currentUserId}`.
- "⭐ See Ratings" row in `RideSearchHandler.handleViewRide()` — **non-owner card only** (not shown to the driver viewing their own ride). Emits `VIEW_RATINGS:{driverUserId}`.

**Page clamping:** `handleRatingsPage` fetches the requested page directly (single DB call in the normal path). If `requestedPage >= page.getTotalPages()` (stale last-page button), it re-fetches the actual last page — two DB calls only in that edge case.

**Display name resolution:** For other-user views, the display name is read from `page.getContent().get(0).getRatee().getFullName()` on non-empty pages (no extra DB call). Falls back to `UserService.getUserById(targetUserId)` when the page is empty. Own-profile views pass `null` for display name and render "Your Ratings" as the header.

**Page size:** 5 ratings per page. Navigation row: `« Prev` (hidden on page 0) · `Next »` (hidden on last page) · `✕ Close`.

## Admin Stats Screen

`ProfileHandler.handleAdminStats` (`ADMIN_STATS` callback) renders `AdminStatsService.getStats()` — see `carpool-service/CLAUDE.md` for the full field list. Beyond the original Users/Rides/Bookings totals, the screen now shows a booking outcomes line (Declined / Cancelled by driver / by passenger / Timed out — closes the gap where `totalBookings` didn't reconcile with what was visible), a cancellation-rate and completion-rate percentage next to the Rides/Bookings totals, and a new "🏘️ Community" section (pending hub suggestions count, platform average rating). `s.avgPlatformRating()` is nullable — rendered as "No ratings yet" when null instead of a raw `null`/NaN. Gated the same as before by `botConfig.isAdmin(ctx.telegramId())`.

## Pending Hub Suggestions — Bulk Approval

`ProfileHandler.handlePendingHubs` shows an `✅ Approve All (N)` row below the per-page list (in addition to the existing per-row `✅`/`❌` approve/reject buttons scoped to that page). `N` is the total pending count, not just the current page.

- `APPROVE_ALL_HUBS` → `handleApproveAllHubs` — admin check, shows a confirm screen ("Approve all N pending hubs?") since bulk approval has no bulk-undo from the bot.
- `CONFIRM_APPROVE_ALL_HUBS` → `handleConfirmApproveAllHubs` — admin check, calls `hubService.approveAllPendingHubs()` (see `carpool-service/CLAUDE.md`), then lists every approved hub's name + generated code in the confirmation message.

Both callbacks are non-flow-sensitive, same as `PENDING_HUBS`/`APPROVE_HUB`/`REJECT_HUB` — no `SessionRecoveryHandler` change needed.

## Donate

`/donate` and the `DONATE_GCASH` callback let a member voluntarily support the community — deliberately kept separate from any ride, booking, or fare to stay outside the [PH LTFRB carpooling monetization constraint](project_monetization_constraint.md) (no per-trip/per-passenger fare collection).

- `DonateHandler.showDonate(chatId, bot)` — registered as `/donate` in `MessageHandler.handleCommand()` **and** as the `DONATE` callback in `CallbackHandler` (a `💙 Donate` button on every branch of `BotFlowHelper.showMainMenu()` and every status sub-branch of `showRideManagementCard()`, since a driver with exactly one active ride lands there instead of the generic menu). Sends a text message with the disclaimer ("optional, not tied to any ride or booking") and a single `💙 GCash` button (`DONATE_GCASH`).
- `DonateHandler.showGcash(chatId, carpoolUserId, bot)` — registered as `DONATE_GCASH` in `CallbackHandler`. Records a `DonateService.recordClick(carpoolUserId, "GCASH")` click (see `carpool-service/CLAUDE.md` "Donate Click Tracking" — curiosity/intent tracking, not the actual transfer) before loading `/images/gcash-qr.png` from the classpath and sending it via the `CarpoolBot.sendPhoto(SendPhoto)` helper (mirrors the `send(SendMessage)` pattern but takes a `String` chatId, per the Telegram Bots API `SendPhoto` type). Caption repeats the voluntary/not-a-fare disclaimer.
- Both `DONATE` and `DONATE_GCASH` are non-flow-sensitive — they read nothing from `UserState`, so no `SessionRecoveryHandler` change needed (same default-handling as `MY_PROFILE`).
- QR image lives at `carpool-bot/src/main/resources/images/gcash-qr.png` — this is the module's first `src/main/resources` directory. Additional donation channels (GoTyme, Maribank) should follow the same pattern: one QR resource + one button + one callback per channel.

**End-of-ride prompt:** `DonateHandler.shouldPromptOnRideEnd(rideId)` gates a `💙 Donate` row onto the ride-completion screens so the button doesn't nag on every single ride — it's platform-wide 1-in-3 (`rideId % 3 == 0`), a deliberately simple gate that needs no new DB column or per-user tracking state. Wired into:
- `DriverHandler.handleCompleteRide()` — the "Would you like to post another ride?" screen (driver side).
- `RatingHandler.submitRating()` — all three outcome branches (passenger-already-favorited, passenger-favorite-prompt, driver-rated-passenger), since the rating screen is the terminal step both driver and passenger see after a ride completes.
`DonateHandler.donateButton()` is a static helper returning the shared `InlineKeyboardButton` so the row doesn't get re-typed at each call site.

## Dead-End Message Prevention

Every terminal bot message (errors, confirmations, cancellations) must leave the user with at least a "🏠 Menu" button — `BotMessageBuilder.text(chatId, ...)` auto-attaches one via `menuButtonRow()` and is the default choice for any message that isn't immediately followed by another message with real inline buttons. `textNoMenu`/`textWithRemoveKeyboard` have no reply markup at all and should only be used as the *first* message of a two-message pair (e.g. `BotFlowHelper.showRideManagementCard` sends `textWithRemoveKeyboard` then immediately `sendWithInline` with the real menu).

Two dead ends were found and fixed by a full-module audit (2026-09-02):
- `MessageHandler.handleCancel()` (`/cancel`) previously used `textWithRemoveKeyboard` with no button, telling the user to type `/start` with nothing to tap. Now sends `BotMessageBuilder.text(chatId, "❌ Cancelled.")`, which has the Menu button.
- `CarpoolBot.handleParallelUpdate()`'s top-level `catch (Exception e)` previously only logged — any uncaught exception left the user with total silence (no response to their tap/message at all). Now also resolves `chatId` via the existing `resolveChatId(update)` helper and sends a generic "⚠️ Something went wrong. Please try again." (with Menu button) when resolvable.

The Telegram-username-required gate screens (`CarpoolBot.handleParallelUpdate`, both the message and callback paths) remain intentionally button-less — a Menu tap there would just re-trigger the same gate check before routing, since the gate runs ahead of `callbackHandler.handle()`/`messageHandler.handle()`.

## Schedulers

One scheduler in `carpool-bot/scheduler/`:
- `StaleAnnouncementRefreshScheduler` — every 4 hours (`initialDelay` 10 min): queries ACTIVE rides where `groupMessagePostedAt < now - 36h` via `RideRepository.findRidesWithStaleGroupAnnouncement`; calls `GroupNotificationService.refreshGroupAnnouncementForRide()` per ride (each dispatched `@Async`). The 36h threshold gives a 12-hour safety buffer before Telegram's 48h message-deletion limit.

Uses `fixedDelay` (not `fixedRate`) with staggered `initialDelay` to prevent startup overlap.
