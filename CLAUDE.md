# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build all modules (skips tests)
mvn clean install -DskipTests

# Run unit tests only (excludes integration tests — no DB required)
mvn clean verify -Dgroups="!integration"

# Run a single test class
mvn test -pl carpool-service -Dtest=BookingServiceTest

# Run integration tests (requires MySQL on port 3308)
mvn test -pl carpool-web

# Start MySQL for local dev
docker-compose up mysql_db -d

# Run the app locally (from carpool-web)
mvn spring-boot:run -pl carpool-web -Dspring-boot.run.profiles=local
```

Integration tests are tagged `@Tag("integration")` and inherit from `BaseIntegrationTest`. They require MySQL at `localhost:3308` with DB `car_e_pool_db`, user/pass `carpool/carpool`. The executable JAR is produced only by `carpool-web`.

## Module Architecture

This is a Maven multi-module project with a strict one-way dependency chain:

```
carpool-common   — shared exceptions, ApiResponse, PagedResponse
       ↓
carpool-domain   — JPA entities, enums (no Spring, no repositories)
       ↓
carpool-repository — Spring Data JPA repositories
       ↓
carpool-service  — business logic, DTOs, MapStruct mapper, schedulers, event system
       ↓                              ↓
carpool-bot                      carpool-admin — webforJ admin panel (port 8082)
       ↓
carpool-web      — Spring Boot entry point, REST controllers, security, Flyway migrations
```

Only `carpool-web` and `carpool-admin` produce executable JARs. All application config for the main app lives in `carpool-web/src/main/resources/`. Schema migrations are in `carpool-web/src/main/resources/db/migration/` (Flyway `V#__description.sql`).

`carpool-admin` is a second Spring Boot module (port 8082) built with the **webforJ** UI framework. It depends on `carpool-service` only — it does not pull in `carpool-bot` or `carpool-web`. `AdminBeanConfig` provides a no-op `TelegramNotificationPort` bean to satisfy the interface without the Telegram adapter. Security is delegated entirely to `WebforjSecurityConfigurer.webforj().loginPage("/login", "/login").logout("/logout", "/login")` — do not add manual `.authorizeHttpRequests()` blocks after it (the configurer internally adds `anyRequest()`, causing a startup conflict). CSRF is currently disabled (`csrf.disable()`) — a known gap.

## Key Architectural Patterns

### Bot: Command Registry + BotContext
`CallbackHandler` maintains a `Map<String, BotCommand>` registered via `@PostConstruct`. Adding a new callback = one new `commands.put("ACTION", ctx -> handler.method(ctx))` entry — `CallbackHandler` itself never changes.

`BotContext` is an immutable record `(chatId, carpoolUserId, telegramId, state, payload, parts, bot, messageId)` passed to every command. `ctx.entityId()` parses `payload` as `Long`.

`MessageHandler` routes text input based on `UserState.flow` (a `BotFlow` enum).

### Bot: Conversation State
`StateManager` holds per-chatId `UserState` in a Caffeine cache (30-minute write-TTL, max 10k entries). `UserState` is an immutable record with `with*()` builder methods. `BotFlow` enum defines all possible conversation steps. `BotFlowHelper.showMainMenu()` calls `stateManager.reset()`, which wipes all flow state including `direction`.

**Stale button hazard:** Telegram messages stay interactive forever. A user can tap a button from an old message after their state has been reset (e.g., by returning to the main menu). All hub-selection and confirmation callbacks in `PostRideHandler` guard against this by checking that required state fields (`direction`, `originHubId`) are non-null before proceeding — missing fields mean the button is stale and a session-expired message is shown instead of crashing. `handleVehicleSelect` additionally checks that `departureTime`, `originHubId`, `seats`, and `contribution` are all non-null before calling `showConfirmation()` (which auto-unboxes `seats` and `contribution` and would NPE on null).

### Service: Event-Driven Notifications
Services publish `RideEvents.*` records via `ApplicationEventPublisher`. `NotificationService` listens with `@Async + @TransactionalEventListener(AFTER_COMMIT) + @Transactional(REQUIRES_NEW)` — notifications only fire after the outer transaction commits, run in a virtual thread, and write to the `notifications` table with PENDING → SENT/FAILED status.

### Multi-Vehicle Management
`Vehicle` is a domain entity (soft-delete via `deletedAt`) with fields `user (FK LAZY)`, `plateNumber`, `model`, `color`, `seatCapacity (Integer)`. `VehicleRepository` exposes `findByUserIdAndDeletedAtIsNullOrderByCreatedAtAsc`, `findActiveByPlateForOtherUser`, and `existsByUserIdAndDeletedAtIsNull`.

`VehicleService.addVehicle()` enforces three rules: (1) plate uniqueness across all active vehicles belonging to other users — throws if occupied; (2) replace-oldest policy — if the user already has 3 active vehicles, the oldest (by `createdAt`) is soft-deleted before saving the new one; (3) after saving, the User entity's legacy `carModel`, `plateNumber`, `carColor`, and `carSeatCapacity` fields are synced to the newest active vehicle for backward compatibility with any code still reading those fields.

`removeVehicle(vehicleId, userId)` verifies ownership then sets `deletedAt`. `getActiveVehiclesForUser(userId)` returns `List<VehicleResponse>` ordered oldest-first (matches selection order in bot UX).

DB migrations: V39 creates `vehicles` with soft-delete and FK to `users (ON DELETE CASCADE)`; V40 adds `vehicle_id` FK (nullable, `ON DELETE SET NULL`) to `rides`; V41 migrates existing user vehicle data from `users` columns into `vehicles`; V42 widens `seat_capacity` from `TINYINT` to `INT` (Hibernate schema validation requires `Types#INTEGER` for Java `Integer`).

`EntityMapper` has `VehicleResponse toVehicleResponse(Vehicle vehicle)`. `RideResponse` includes a `VehicleResponse vehicle` field (may be null for rides posted before V39). `GroupNotificationService` and `BotMessageBuilder` both fall back to `ride.getDriver()` legacy fields when `ride.getVehicle()` is null.

### Bot: Vehicle Selection in the Post-Ride Flow
After the notes step, the post-ride flow calls `ProfileHandler.showVehicleSelectStep()` instead of going directly to confirmation. This shows the driver's saved vehicles as inline buttons (`VEHICLE_SELECT:{id}`), plus "➕ Add New Vehicle" if fewer than 3 exist. If no vehicles are saved yet, the flow jumps directly to `SET_VEHICLE_COLOR` (the add-vehicle input flow). The Cancel button on these screens uses `CANCEL_POST_RIDE`.

`handleVehicleSelect(ctx)` resolves the chosen vehicle, builds a display label (`color + model | plate`), stores it as `selectedVehicleId` and `selectedVehicleLabel` in `UserState`, then calls `postRideHelper.showConfirmation()`. **Stale button guard:** if `departureTime`, `originHubId`, `seats`, or `contribution` is null (stale button after session reset), a session-expired message is shown and the state is reset — avoids NPE when `showConfirmation` auto-unboxes `state.getSeats()`.

`handleAddVehicle(ctx)` starts the `SET_VEHICLE_COLOR → SET_VEHICLE_MODEL → SET_VEHICLE_PLATE → SET_VEHICLE_CAPACITY → VEHICLE_CONFIRM_SAVE` input flow. It checks `ctx.state().getDepartureTime() != null` to choose the cancel button: `CANCEL_POST_RIDE` (post-ride context) or `VEHICLE_CHANGE` (standalone vehicle management). The same context check applies in `showVehicleConfirmation()` for its Cancel button.

`VEHICLE_SELECT` is listed in `SessionRecoveryHandler.POST_RIDE_ACTIONS` so stale buttons after a bot restart show a context-aware "session expired" message. `ADD_VEHICLE` is intentionally excluded — `handleAddVehicle` handles both post-ride and standalone contexts via the `departureTime` null check, so it works safely with a fresh `UserState`.

### Bot: Time Picker (BotTimePickerUtil)
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

### Bot: Repost Edit Screen
`PostRideHandler.handleRepostRide()` pre-fills origin, destination, direction, seats, contribution, and notes from the original ride into `UserState`, sets `repostEditMode = true`, and shows an inline edit screen via `showRepostEditScreen()`. The driver can tap any field button (📍 Edit Start, 🏁 Edit End, 🪑 Edit Seats, ⛽ Edit Share, 📝 Edit Note) to edit it; each edit returns to the same edit screen. Tapping "✅ Continue" calls `handleRepostProceed()` which shows the calendar picker. After date selection, the flow goes to vehicle selection (`showVehicleSelectStep`), then confirmation — the same path as a new ride.

### Bot: AI Natural Language Ride Posting
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

### Bot: Group Announcement Lifecycle
`GroupNotificationService.onRidePosted()` posts a ride announcement to the configured Telegram group topic and stores the returned Telegram message ID in `Ride.groupMessageId` (added by V37 migration, column `group_message_id`). The DB save for the message ID is isolated in a separate try/catch so a failure there never masks a successful group post or corrupts the ride transaction.

**Re-announce:** `RideService.reannounceRide()` increments `Ride.announceCount` (max 10 total) and re-fires `RidePostedEvent`. `onRidePosted` detects a non-null `groupMessageId` and deletes the old message first (isolated in its own try/catch — a Telegram failure logs a warning but does not abort the new post). Follower alerts are suppressed on re-announces: the loop is guarded by `announceCount <= 1` so followers receive only one DM per ride regardless of how many times the driver re-announces.

**0-seats FULL handling:** When `onRidePosted` fires for a re-announce and `ride.getAvailableSeats() == 0`, the old group post has already been deleted (see above). The method then detects the zero seat count, clears `Ride.groupMessageId` to `null` in DB (isolated try/catch), logs `"Ride is FULL — group announcement removed, not reposted"`, and returns early — no new announcement is posted. The ride remains in the DB with its current status; the group channel simply has no active post for it. This path is triggered by the seat-count-edit re-announce flow (see §Bot: Re-announce with Seat Count Edit).

**Seat-freed auto-refresh:** `GroupNotificationService` also listens for `BookingCancelledByDriverEvent` (driver explicitly removes a confirmed passenger) and `BookingAutoSyncedEvent` (pending bookings auto-cancelled when a ride became FULL after a booking acceptance). Both listeners call `refreshGroupPostAfterSeatFreed(rideId, reason)`. That private method: (1) loads the ride; (2) skips if `groupMessageId` is null, ride is not ACTIVE/FULL, or the ride is older than 48h; (3) deletes the old group post (isolated try/catch); (4) reposts a fresh announcement via `sendToGroup`; (5) stores the new message ID. This keeps the group post seat count in sync whenever a seat is freed without a manual re-announce by the driver.

When a ride is departed, completed, or cancelled, `GroupNotificationService` listens for `RideDepartedEvent`, `RideCompletedEvent`, and `RideCancelledEvent` (all `@Async + @TransactionalEventListener(AFTER_COMMIT)`) and calls `CarpoolBot.deleteMessage()` to remove the announcement. Deletion is skipped if `groupMessageId` is null or if the ride was created more than 48 hours ago (Telegram API limitation).

**Favorite driver alerts:** `CarpoolBot.sendToUser(telegramId, text, rideId, driverId)` sends the alert DM with three inline buttons: `VIEW_RIDE:{rideId}`, `BOOK_RIDE:{rideId}`, and `UNFOLLOW_DRIVER:{driverId}`. Tapping Unfollow calls `RatingHandler.handleUnfollowDriver()`, which removes the `UserFavorite` record and edits the alert message in-place to confirm — no menu navigation needed.

**Follower DM dispatch:** The follower alert loop in `onRidePosted()` uses parallel virtual thread dispatch — each follower DM runs in its own virtual thread via `Executors.newVirtualThreadPerTaskExecutor()` (try-with-resources). A `Semaphore(10)` caps concurrent Telegram API calls to respect rate limits. `CompletableFuture.allOf(...).join()` waits for all sends before the method returns. 100 followers completes in ~4s instead of ~35s. The sequential `Thread.sleep(50)` loop was removed.

**Group announcement buttons:** `CarpoolBot.sendToGroup(text, rideId, driverId, topicId)` attaches two URL button rows to every group post: `🚘#N ❯❯❯❯ | View | Request a Seat` deep-links to `?start=RIDE_{rideId}`; `⭐ Follow Driver | View Ride` deep-links to `?start=FOLLOW_RIDE_{driverId}_{rideId}`. The `FOLLOW_RIDE_` parameter is handled in `MessageHandler.handleStart()` by the private `handleFollowAndViewRide()` method. It fetches the ride from DB using only `rideId` (the `driverId` in the URL is not trusted for any operation — `ride.driver().id()` is used throughout). It calls `favoriteService.isFavorite()` before `saveFavorite()` so existing followers do not see a false "now following" confirmation. New followers see "⭐ You're now following [driver]!" + ride card + Unfollow button. Existing followers see the ride card normally. The driver tapping their own link sees the driver ride card (View Bookings). Malformed deep links throw `NumberFormatException` caught at WARN level, and the user is sent to the main menu.

**Booking-acceptance auto-refresh:** `GroupNotificationService.onBookingConfirmed(BookingConfirmedEvent)` listens `@Async + @TransactionalEventListener(AFTER_COMMIT) + @Transactional(REQUIRES_NEW)`. When a driver accepts a booking it deletes the old group post and reposts a fresh announcement reflecting the updated available seat count. If `ride.getAvailableSeats() == 0` (ride is now FULL) the old post is simply deleted instead — consistent with departed/completed/cancelled behaviour. This listener does **not** increment `announceCount` (the driver's manual re-announce quota is unaffected) and does **not** trigger follower DMs.

**Post-completion prompt:** `DriverHandler.handleCompleteRide()` resets state then shows "Would you like to post another ride?" with two buttons: `🚗 Yes, Post New Ride` → `POST_RIDE` (starts the normal post-ride direction-select flow with a clean state) and `❌ No, Thanks` → `MAIN_MENU`.

### Bot: My Followers Screen
`ProfileHandler.handleMyFollowers(ctx)` is registered as the `MY_FOLLOWERS` callback. It is accessible from the driver profile screen — `handleMyProfile` appends a "👥 My Followers (N)" button whenever `stats.driverRidesPosted() != null` (i.e., the user has a driver role). The count `N` comes from `FavoriteService.getFollowerCount(driverId)`, a separate `COUNT` query called inside the `handleMyProfile` try-catch.

`FavoriteService.getFollowers(Long driverId)` is annotated `@Transactional(readOnly = true)` and returns `List<FollowerResponse>`. It calls `UserFavoriteRepository.findByFavoriteIdWithFollowerOrderByCreatedAtDesc`, which uses a `@Query` with `JOIN FETCH uf.follower` to load all follower `User` entities in a single query — avoiding the N+1 that a plain derived method would cause since `follower` is `FetchType.LAZY`.

`FollowerResponse(Long userId, String fullName, String telegramHandle, LocalDateTime followedAt)` is a DTO record in `carpool-service/src/main/java/com/carpool/service/dto/response/`.

The handler paginates at 8 followers per page. Pagination is encoded in callback data: `MY_FOLLOWERS:0`, `MY_FOLLOWERS:1`, etc. — `ctx.payload()` gives the page number. A static `FOLLOWED_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy")` constant formats the `followedAt` date. The screen is read-only; there are no per-follower actions. `MY_FOLLOWERS` is non-flow-sensitive — `SessionRecoveryHandler` treats it like `MY_PROFILE` (fresh `UserState`, proceeds normally after a bot restart).

### Bot: Re-announce with Seat Count Edit
When a driver taps **📢 Re-announce** from the main menu, the flow enters `BotFlow.REANNOUNCE_EDIT_SEATS`. The bot prompts: *"How many available seats do you want to show?"* The driver types a number.

`ProfileHandler.handleReannounceEditSeatsText()` parses the input, calls `rideService.updateAvailableSeats(rideId, newSeats, carpoolUserId)` to persist the new count (this also transitions the ride status: 0 seats → FULL, ≥1 seat → ACTIVE), then branches. State is reset to IDLE only **after** `reannounceRide()` returns successfully in each branch — if the call throws, the user remains in `REANNOUNCE_EDIT_SEATS` and can retry cleanly.

- **`newSeats == 0`:** Calls `rideService.reannounceRide(rideId, ...)` which fires `RidePostedEvent`. `onRidePosted` detects 0 available seats, deletes the old group post, clears `groupMessageId`, and returns without posting a new announcement. Bot confirms: "🚫 Ride Marked as Full — group announcement has been removed."
- **`newSeats > 0`:** Calls `rideService.reannounceRide(rideId, ...)` which fires `RidePostedEvent`. `onRidePosted` deletes old post and reposts with updated seat count. Bot confirms: "📢 Ride Re-announced! Seat count updated to N and ride posted to group. X re-announcements remaining."

The remaining count shown in the confirmation message and the `📢 Re-announce (N left)` button label both use `Math.max(0, 10 - ride.announceCount())`.

### Booking: Remove Passenger & Pending Auto-sync
**Remove Passenger:** Drivers can remove a confirmed passenger from their active ride (via the bookings list). `BookingService` cancels the booking with status `CANCELLED_BY_DRIVER`, decrements `ride.availableSeats`, transitions the ride from FULL back to ACTIVE if needed, notifies the removed passenger, and publishes `BookingCancelledByDriverEvent`. `GroupNotificationService.onBookingCancelledByDriver()` picks this up and calls `refreshGroupPostAfterSeatFreed` — the group announcement is refreshed to reflect the newly available seat.

**Auto-sync on acceptance (ride goes FULL):** When a driver accepts a booking that fills the last available seat, `BookingService` auto-cancels all remaining PENDING bookings on the same ride. Each auto-cancelled booking fires `BookingAutoSyncedEvent` and the affected passenger receives a notification. `GroupNotificationService.onBookingAutoSynced()` calls `refreshGroupPostAfterSeatFreed` — however, since the ride is now FULL (0 seats), `onBookingConfirmed` has already deleted the group post, so the refresh guard (`groupMessageId == null`) typically short-circuits this call.

### Booking: Pessimistic Locking
`BookingService.createBooking()` acquires `SELECT FOR UPDATE` on the ride row (`RideRepository.findByIdWithLock()`) to prevent double-booking the last seat. The lock is held for the full transaction duration.

### Bot: Departure Time Guard
`DriverHandler.handleStartRide()` enforces a 1-hour early-start window. If the current time is more than 60 minutes before `ride.getDepartureTime()`, the handler rejects the tap and sends a countdown message ("You can start the ride in X hours Y minutes") instead of transitioning the ride to DEPARTED. This prevents drivers from accidentally marking a ride as departed hours before the scheduled time.

### Security
Stateless JWT auth. `POST /api/v1/auth/telegram` is public — validates Telegram Login Widget hash (HMAC-SHA256 of `SHA256(bot_token)`). All other endpoints require Bearer token. `@PreAuthorize` is enabled for method-level role checks.

Filters chain: `RateLimitFilter` (Bucket4j, per-IP) → `JwtAuthFilter` → Spring Security.

### DTOs & Mapping
All entity→DTO mapping uses a single `EntityMapper` (MapStruct, compile-time generated, Spring bean). DTOs are Java records in `carpool-service/src/main/java/com/carpool/service/dto/`.

### Bot: Session Recovery
`SessionRecoveryHandler.isFlowSensitive(action)` guards against stale buttons after a bot restart. Flow-sensitive actions (post-ride steps, rating steps, custom hub confirmation) show a context-aware "session expired" message instead of crashing. Non-flow-sensitive actions (`MY_PROFILE`, `PENDING_HUBS`, etc.) get a fresh `UserState.initial()` and proceed normally.

### Hubs
`HubStatus` has three values: `ACTIVE`, `PENDING`, `REJECTED`. PENDING hubs are immediately usable as ride origins/destinations — `RideService.createRide()` does not validate hub status.

`HubService.suggestHub()` prevents duplicate rows: if a hub with the same name+area already exists as ACTIVE or PENDING it is returned as-is; if REJECTED it is re-queued back to PENDING. Custom locations typed by drivers in the post-ride flow are saved with area `"Unverified"` and status `PENDING` for admin review.

`HubService.approveHub(id, null)` auto-generates a unique code from the hub name (uppercase + underscores, suffix `_2`/`_3`/… for collisions) when no explicit code is provided. Approval evicts both `hubs` and `hub-search` caches.

Admin hub management (list pending, approve, reject) is available in the bot under `MY_PROFILE → 🏘️ Pending Hubs` — gated by `BotConfig.isAdmin()`. No admin web UI exists yet.

### Rating System
Ratings are **per-ride**, not per user-pair. The same driver and passenger can rate each other again on every new completed ride — there is no "already rated this person" global block.

**Duplicate check scope:**
- Passenger rater: `existsByRideIdAndRaterId` — one rating per ride total (one driver per ride).
- Driver rater: `existsByRideIdAndRaterIdAndRateeId` — one rating per passenger per ride. A driver with 3 passengers submits 3 independent ratings and is only blocked per-ratee, not per-ride.

V43 Flyway migration widens the DB unique constraint on `ride_ratings` from `(ride_id, rater_id)` to `(ride_id, rater_id, ratee_id)`, matching the repository query above. The JPA `@Table(uniqueConstraints = …)` annotation was updated in step.

**Role stored on save:** `raterRole` is `"DRIVER"` or `"PASSENGER"` (set from `ride.driver.id.equals(raterId)`). The repository queries `raterRole` to separate driver-received ratings from passenger-received ratings — `findAverageDriverRatingByRateeId` filters `raterRole = 'PASSENGER'` (passenger rated the driver), `findAveragePassengerRatingByRateeId` filters `raterRole = 'DRIVER'`.

**Analytics queries (P3 TODO):** Five MySQL aggregate queries are documented for future `RideRatingRepository` addition: star distribution, top-rated drivers leaderboard (min 3 ratings), monthly trend per driver, completion rate (% of completed rides that got rated), and rating drop detection (last-30d avg vs all-time avg, drop > 0.5).

### Ride Card: Member Verification Badge
`BotMessageBuilder.formatRideCard(ride, ratingLabel, memberBadge)` renders a badge line between the driver name and "Posted X ago". The badge is built by `BotMessageBuilder.buildMemberBadge(ProfileStatsResponse)` and shows role, completed ride count, and member-since date. Applied in both `RideSearchHandler.handleViewRide()` (bot search flow) and `MessageHandler.handleStart()` (group deep-link flow).

## Configuration

Active profile is set via `SPRING_PROFILES_ACTIVE`. Local dev uses `application-local.properties`. Key env vars for prod (see `docker-compose.yml`): `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_USERNAME`, `TELEGRAM_GROUP_CHAT_ID`, `TELEGRAM_GROUP_HOME_TO_WORK_TOPIC_ID`, `TELEGRAM_GROUP_WORK_TO_HOME_TOPIC_ID`.

The bot only operates in private chats — group messages are ignored. Group membership is used only to post ride announcements to topic threads.

## Schedulers

Three background schedulers in `carpool-service/scheduler/`:
- `RideExpiryScheduler` — every 30 min: auto-expires rides past departure; auto-completes DEPARTED rides 2h+ old
- `PendingBookingScheduler` — sends up to 3 reminder notifications to drivers with unresponded booking requests (at 15, 30, and 45 minutes); requests that remain unanswered are marked `TIMED_OUT` by the scheduler — there is no automatic decline with a reason
- `RideDepartureReminderScheduler` — notifies driver + confirmed passengers 30 min before departure

All use `fixedDelay` (not `fixedRate`) with staggered `initialDelay` to prevent startup overlap.

## Skill routing

When the user's request matches an available skill, invoke it via the Skill tool. When in doubt, invoke the skill.

Key routing rules:
- Product ideas/brainstorming → invoke /office-hours
- Strategy/scope → invoke /plan-ceo-review
- Architecture → invoke /plan-eng-review
- Design system/plan review → invoke /design-consultation or /plan-design-review
- Full review pipeline → invoke /autoplan
- Bugs/errors → invoke /investigate
- QA/testing site behavior → invoke /qa or /qa-only
- Code review/diff check → invoke /review
- Visual polish → invoke /design-review
- Ship/deploy/PR → invoke /ship or /land-and-deploy
- Save progress → invoke /context-save
- Resume context → invoke /context-restore
