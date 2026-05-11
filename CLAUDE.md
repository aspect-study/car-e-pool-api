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
       ↓
carpool-bot      — Telegram bot (LongPolling, handlers, state machine)
       ↓
carpool-web      — Spring Boot entry point, REST controllers, security, Flyway migrations
```

Only `carpool-web` produces an executable JAR. All application config lives in `carpool-web/src/main/resources/`. Schema migrations are in `carpool-web/src/main/resources/db/migration/` (Flyway `V#__description.sql`).

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

`VEHICLE_SELECT` and `ADD_VEHICLE` are listed in `SessionRecoveryHandler.POST_RIDE_ACTIONS` so stale buttons after a bot restart show a context-aware "session expired" message.

### Bot: Repost Edit Screen
`PostRideHandler.handleRepostRide()` pre-fills origin, destination, direction, seats, contribution, and notes from the original ride into `UserState`, sets `repostEditMode = true`, and shows an inline edit screen via `showRepostEditScreen()`. The driver can tap any field button (📍 Edit Start, 🏁 Edit End, 🪑 Edit Seats, ⛽ Edit Share, 📝 Edit Note) to edit it; each edit returns to the same edit screen. Tapping "✅ Continue" calls `handleRepostProceed()` which shows the calendar picker. After date selection, the flow goes to vehicle selection (`showVehicleSelectStep`), then confirmation — the same path as a new ride.

### Bot: Group Announcement Lifecycle
`GroupNotificationService.onRidePosted()` posts a ride announcement to the configured Telegram group topic and stores the returned Telegram message ID in `Ride.groupMessageId` (added by V37 migration, column `group_message_id`). The DB save for the message ID is isolated in a separate try/catch so a failure there never masks a successful group post or corrupts the ride transaction.

**Re-announce:** `RideService.reannounceRide()` increments `Ride.announceCount` (max 3 total) and re-fires `RidePostedEvent`. `onRidePosted` detects a non-null `groupMessageId` and deletes the old message first (isolated in its own try/catch — a Telegram failure logs a warning but does not abort the new post). Follower alerts are suppressed on re-announces: the loop is guarded by `announceCount <= 1` so followers receive only one DM per ride regardless of how many times the driver re-announces.

When a ride is departed, completed, or cancelled, `GroupNotificationService` listens for `RideDepartedEvent`, `RideCompletedEvent`, and `RideCancelledEvent` (all `@Async + @TransactionalEventListener(AFTER_COMMIT)`) and calls `CarpoolBot.deleteMessage()` to remove the announcement. Deletion is skipped if `groupMessageId` is null or if the ride was created more than 48 hours ago (Telegram API limitation).

**Favorite driver alerts:** `CarpoolBot.sendToUser(telegramId, text, rideId, driverId)` sends the alert DM with three inline buttons: `VIEW_RIDE:{rideId}`, `BOOK_RIDE:{rideId}`, and `UNFOLLOW_DRIVER:{driverId}`. Tapping Unfollow calls `RatingHandler.handleUnfollowDriver()`, which removes the `UserFavorite` record and edits the alert message in-place to confirm — no menu navigation needed.

### Booking: Pessimistic Locking
`BookingService.createBooking()` acquires `SELECT FOR UPDATE` on the ride row (`RideRepository.findByIdWithLock()`) to prevent double-booking the last seat. The lock is held for the full transaction duration.

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

### Ride Card: Member Verification Badge
`BotMessageBuilder.formatRideCard(ride, ratingLabel, memberBadge)` renders a badge line between the driver name and "Posted X ago". The badge is built by `BotMessageBuilder.buildMemberBadge(ProfileStatsResponse)` and shows role, completed ride count, and member-since date. Applied in both `RideSearchHandler.handleViewRide()` (bot search flow) and `MessageHandler.handleStart()` (group deep-link flow).

## Configuration

Active profile is set via `SPRING_PROFILES_ACTIVE`. Local dev uses `application-local.properties`. Key env vars for prod (see `docker-compose.yml`): `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_USERNAME`, `TELEGRAM_GROUP_CHAT_ID`, `TELEGRAM_GROUP_HOME_TO_WORK_TOPIC_ID`, `TELEGRAM_GROUP_WORK_TO_HOME_TOPIC_ID`.

The bot only operates in private chats — group messages are ignored. Group membership is used only to post ride announcements to topic threads.

## Schedulers

Three background schedulers in `carpool-service/scheduler/`:
- `RideExpiryScheduler` — every 30 min: auto-expires rides past departure; auto-completes DEPARTED rides 2h+ old
- `PendingBookingScheduler` — sends up to 3 reminder notifications to drivers with unresponded booking requests, then auto-declines
- `RideDepartureReminderScheduler` — notifies driver + confirmed passengers 30 min before departure

All use `fixedDelay` (not `fixedRate`) with staggered `initialDelay` to prevent startup overlap.
