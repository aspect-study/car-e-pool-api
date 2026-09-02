# Car-E-Pool — Architecture Reference

---

## What It Is

Car-E-Pool is a **Telegram bot-based carpooling system** for daily commuters. There is no web UI for end users — everything happens through a Telegram private chat. The backend is a Spring Boot application that also runs a Telegram LongPolling bot.

---

## Module Structure

```
carpool-common        ← shared utilities, exceptions, API response wrappers
carpool-domain        ← JPA entities and enums only — no Spring beans
carpool-repository    ← Spring Data JPA repositories
carpool-service       ← all business logic, DTOs, event system, schedulers
carpool-bot           ← Telegram bot: handlers, state machine, notifications
carpool-admin         ← webforJ admin panel (port 8082); Login, Dashboard, Hub Approval
carpool-web           ← Spring Boot entry point, REST controllers, security, Flyway
```

The dependency flows strictly downward — `carpool-bot` can use `carpool-service` but never the reverse. `carpool-admin` also depends only on `carpool-service` — it does not import `carpool-bot` or `carpool-web`. Both `carpool-web` and `carpool-admin` produce executable JARs (ports 8080 and 8082 respectively).

---

## Domain Layer (`carpool-domain`)

The core entities:

| Entity | Key Fields | Notes |
|--------|-----------|-------|
| `User` | `telegramId`, `fullName`, `role` (PASSENGER/DRIVER/BOTH/ADMIN), `status` | Legacy vehicle fields kept for backward compat |
| `Ride` | `driver`, `originHub`, `destinationHub`, `departureTime`, `status`, `availableSeats`, `groupMessageId`, `groupMessagePostedAt`, `announceCount` | Status: DRAFT→ACTIVE/FULL→DEPARTED→COMPLETED. `groupMessagePostedAt` records when the Telegram group message was last posted/refreshed — used by the stale-refresh scheduler |
| `Booking` | `ride`, `passenger`, `status`, `seats` | Status: PENDING→CONFIRMED/DECLINED/TIMED_OUT→COMPLETED |
| `Hub` | `name`, `area`, `code`, `status` (ACTIVE/PENDING/REJECTED) | Shared pickup/dropoff landmarks |
| `Vehicle` | `user`, `plateNumber`, `model`, `color`, `seatCapacity`, `deletedAt` | Soft-delete; up to 3 per user |
| `Rating` | `rater`, `ratee`, `ride`, `stars`, `comment` | One per rater-ratee-ride combination |
| `UserFavorite` | `follower`, `favorite` | Follow relationship between users |
| `Notification` | `user`, `type`, `status` (PENDING→SENT/FAILED) | Written by NotificationService |

---

## Service Layer (`carpool-service`)

All business logic lives here. Key services:

### `RideService`
- `createRide()` — creates ride as DRAFT; validates driver has no active same-direction ride or passenger booking conflict (direction-scoped)
- `updateRideStatus()` — handles all status transitions; publishes `RidePostedEvent` on DRAFT→ACTIVE
- `reannounceRide()` — increments `announceCount` (max 10) and re-fires `RidePostedEvent`
- `updateAvailableSeats()` — updates seat count *within* the existing total and transitions ride status (0 → FULL, ≥1 → ACTIVE)
- `updateTotalSeats(rideId, newTotalSeats, driverId)` — corrects the total seat ceiling itself (up or down), bounded below by reserved (in-app) seats; also flips FULL↔ACTIVE
- `updateDepartureTime(rideId, newTime, driverId)` — validates ownership, ACTIVE/FULL status, ≥15 min from now; publishes `RideTimeChangedEvent`

### `BookingService`
- `createBooking()` — acquires `SELECT FOR UPDATE` pessimistic lock on the ride row to prevent double-booking the last seat
- `respondToBooking()` — ACCEPT/DECLINE with reason; decrements available seats on accept; on accept, if ride becomes FULL, auto-cancels all remaining PENDING bookings and publishes `BookingAutoSyncedEvent` for each
- `removePassenger(bookingId, driverId)` — driver-initiated removal of a confirmed passenger; cancels booking as `CANCELLED_BY_DRIVER`, increments `availableSeats`, transitions ride FULL→ACTIVE if needed, notifies passenger, publishes `BookingCancelledByDriverEvent`

### `FavoriteService`
- `saveFavorite()` — idempotent (silently ignores duplicates); throws `IllegalArgumentException` for self-follow
- `removeFavorite()` — idempotent; uses `@Modifying @Query` DELETE (returns row count) instead of derived delete so re-delivered Telegram callbacks after a restart do not throw
- `isFavorite()` — single `EXISTS` query
- `getFollowers()` — `JOIN FETCH` to avoid N+1

### `HubService`
- `suggestHub()` — deduplicates: returns existing if ACTIVE/PENDING, re-queues to PENDING if REJECTED
- `approveHub()` — auto-generates hub code from name if none provided

Other services: `RatingService`, `ProfileService`, `VehicleService`, `NotificationService`

### Event System

Services publish `RideEvents.*` records via Spring's `ApplicationEventPublisher`:

| Event | Trigger |
|-------|---------|
| `RidePostedEvent` | DRAFT → ACTIVE status transition, or re-announce |
| `RideDepartedEvent` | ACTIVE/FULL → DEPARTED |
| `RideCompletedEvent` | DEPARTED → COMPLETED |
| `RideCancelledEvent` | Any → CANCELLED |
| `BookingConfirmedEvent` | Driver accepts a booking |
| `BookingCancelledByPassengerEvent` | Passenger cancels their booking |
| `BookingCancelledByDriverEvent` | Driver removes a confirmed passenger |
| `BookingAutoSyncedEvent` | Pending booking auto-cancelled when ride becomes FULL |
| `RideTimeChangedEvent` | Driver updates departure time of an active ride |

Listeners use `@Async + @TransactionalEventListener(AFTER_COMMIT) + @Transactional(REQUIRES_NEW)` — guaranteed to fire only after the DB transaction commits, on a virtual thread.

### Schedulers

All use `fixedDelay` with staggered `initialDelay` to prevent startup overlap:

| Scheduler | Module | Frequency | What It Does |
|-----------|--------|-----------|-------------|
| `RideExpiryScheduler` | `carpool-service` | Every 30 min | Auto-cancels expired ACTIVE/FULL rides; auto-completes DEPARTED rides 2h+ old |
| `PendingBookingScheduler` | `carpool-service` | Every 60 sec | Sends driver reminders at 15, 30, and 45 min; no auto-expiry — bookings remain PENDING indefinitely until the driver responds |
| `RideDepartureReminderScheduler` | `carpool-service` | Every 5 min | One-shot 30-min-before reminder to driver + confirmed passengers |
| `StaleAnnouncementRefreshScheduler` | `carpool-bot` | Every 4 hours | Queries ACTIVE rides where `groupMessagePostedAt < now − 36h`; re-posts each announcement via `refreshGroupAnnouncementForRide()` (dispatched `@Async`). Keeps posts within Telegram's 48h deletion window without driver action |

---

## Bot Layer (`carpool-bot`)

### Entry Points — `CarpoolBot`

The bot extends Telegram's `LongPollingBot`. Two types of incoming updates:
- **Text messages** → `MessageHandler.handle()`
- **Callback queries** (button taps) → `CallbackHandler.handle()`

Group messages are ignored except for new member join events (welcome message).

### Routing — `CallbackHandler`

Maintains a `Map<String, BotCommand>` registered via `@PostConstruct`. Every registered action maps to one handler method:

```java
commands.put("POST_RIDE",       postRideHandler::handleStartPostRide);
commands.put("BOOK_RIDE",       bookingHandler::handleBookRide);
commands.put("COMPLETE_RIDE",   driverHandler::handleCompleteRide);
commands.put("UNFOLLOW_DRIVER", ratingHandler::handleUnfollowDriver);
// ~50 more entries
```

Adding a new callback = one `commands.put(...)` line. `CallbackHandler` itself never changes.

Callback data format: `ACTION` or `ACTION:payload` (e.g., `BOOK_RIDE:42`). `ctx.entityId()` parses the payload as `Long`.

### Routing — `MessageHandler`

Routes text input based on `UserState.flow`. The `/start` command also handles two deep link formats:

| Deep Link | Handler | Behaviour |
|-----------|---------|-----------|
| `?start=RIDE_{rideId}` | `handleStart()` | Shows ride card directly |
| `?start=FOLLOW_RIDE_{driverId}_{rideId}` | `handleFollowAndViewRide()` | Follows driver (if new) + shows ride card |

`handleFollowAndViewRide()` fetches the ride from DB using `rideId` only — the `driverId` in the URL is not trusted. `ride.driver().id()` is used for all operations (`saveFavorite`, Unfollow button callback). Calls `isFavorite()` before `saveFavorite()` so existing followers don't see a false "now following" message.

### State Machine — `StateManager` + `UserState` + `BotFlow`

- **`StateManager`** holds per-`chatId` `UserState` in a Caffeine cache (30-min write TTL, max 10k entries)
- **`UserState`** is an immutable record with `with*()` builder methods — every state change returns a new instance
- **`BotFlow`** is an enum of all conversation steps: `POST_RIDE_DIRECTION`, `POST_RIDE_SELECT_DATE`, `POST_RIDE_ORIGIN`, etc.

Every handler reads the current state, creates a modified copy, saves it back, and sends a message. `BotFlowHelper.showMainMenu()` calls `stateManager.reset()`, which wipes all state including `direction`.

### Context — `BotContext`

An immutable record passed to every command:

```
(chatId, carpoolUserId, telegramId, state, payload, parts, bot, messageId)
```

### Stale Button Hazard

Telegram messages stay interactive forever. A user can tap a button from an old message after their session has reset. Guards are in place across all critical handlers:
- Post-ride flow checks `direction`, `originHubId`, `departureTime`, `seats`, `contribution` before proceeding
- `SessionRecoveryHandler.isFlowSensitive(action)` routes stale callbacks after a bot restart to a "session expired" message instead of crashing
- Non-flow-sensitive actions (`MY_PROFILE`, `MY_FOLLOWERS`, etc.) get a fresh `UserState.initial()` and proceed normally

### Handlers

| Handler | Responsibility |
|---------|---------------|
| `PostRideHandler` | Full post-ride flow (direction → date → origin → destination → seats → contribution → notes → vehicle → confirm) |
| `DriverHandler` | Ride lifecycle: depart, complete, cancel, my-rides list |
| `BookingHandler` | Passenger booking flow; driver accept/decline |
| `RideSearchHandler` | Find-ride flow: direction → date → time window → results → view |
| `ProfileHandler` | My profile, my vehicles, my followers, vehicle add/remove |
| `RatingHandler` | Rating flow, save/skip favorite, unfollow |
| `SessionRecoveryHandler` | Guards stale buttons after bot restart |
| `HelpHandler` | `/help` command |
| `DonateHandler` | `/donate` command — voluntary community donations (GCash QR), decoupled from rides/fares |

### Helpers

| Helper | Responsibility |
|--------|---------------|
| `BotFlowHelper` | Resets state; builds and sends the context-aware main menu |
| `PostRideHelper` | Shared confirmation screen and notes prompt (used by new post and repost flows) |
| `BotMessageBuilder` | All message formatting: `formatRideCard()`, `buildMemberBadge()`, `button()`, `inlineButtons()` |

`BotMessageBuilder` is a pure static utility — no Spring bean.

`ProfileBadgeBuilder` (`carpool-service/util`) is a pure static utility that formats member verification badge strings (role, completed rides, member-since date). It is the single source of truth for badge formatting — used by `BotMessageBuilder.buildMemberBadge()` for ride card driver badges and by `BookingHandler` for passenger mini-profile badges on incoming booking requests.

### Group Notifications — `GroupNotificationService`

Listens for all `RideEvents.*` via `@Async + @TransactionalEventListener(AFTER_COMMIT)`:

**`persistGroupMessageId(rideId, messageId)` — private helper**
- Sets `Ride.groupMessageId` and `Ride.groupMessagePostedAt` (to `Instant.now()`) atomically in one isolated try/catch save
- Called by `onRidePosted`, `refreshGroupPostAfterSeatFreed`, `onBookingConfirmed`, and `refreshGroupAnnouncementForRide` — the single write path for both fields

**`onRidePosted`**
1. If re-announce: deletes old group message first (isolated try/catch)
2. Posts announcement to the correct group topic (HOME→WORK vs WORK→HOME)
3. Calls `persistGroupMessageId` to store the Telegram message ID and post timestamp
4. Sends follower DM alerts — suppressed when `announceCount > 1` (re-announces don't re-notify)

**`refreshGroupAnnouncementForRide(rideId)` — public, called by scheduler**
- `@Async @Transactional(REQUIRES_NEW)` — runs on the Spring async executor
- Same delete-then-repost logic as `refreshGroupPostAfterSeatFreed` but **without** the 48h guard and without seat-count semantics
- Called exclusively by `StaleAnnouncementRefreshScheduler`; on success resets `groupMessagePostedAt` via `persistGroupMessageId`

**Vehicle plate privacy:** Group posts show vehicle color and model only — the plate number is intentionally omitted to protect driver privacy. The plate is revealed exclusively in the booking confirmation DM sent to the confirmed passenger, and in the passenger's booking detail view (CONFIRMED/COMPLETED statuses only).

Group post includes two URL button rows:
- `🚘#N ❯❯❯❯ | View | Request a Seat` → `?start=RIDE_{rideId}`
- `⭐ Follow Driver | View Ride` → `?start=FOLLOW_RIDE_{driverId}_{rideId}`

**`onRideDeparted` / `onRideCompleted` / `onRideCancelled`**
- Deletes the group announcement via `CarpoolBot.deleteMessage()`
- Skipped if `groupMessageId` is null or the ride was created more than 48 hours ago (Telegram API limitation — the proactive stale-refresh scheduler is the mechanism intended to prevent this guard from triggering)

**`onBookingConfirmed`**
- Refreshes group post to show updated seat count after a driver accepts a booking
- If `availableSeats == 0` (ride FULL), deletes the post instead

**`onBookingCancelledByDriver` / `onBookingAutoSynced` / `onBookingCancelledByPassenger`**
- All call `refreshGroupPostAfterSeatFreed(rideId, reason)` — deletes old group post and reposts with updated seat count
- Guards: `groupMessageId` non-null, ride status ACTIVE or FULL, ride not older than 48h

**0-seats guard in `onRidePosted`**
- When re-announcing a ride with 0 available seats (entered via the seat-count-edit flow), the old post is deleted, `Ride.groupMessageId` is cleared, and the method returns early without posting a new announcement

---

## Web Layer (`carpool-web`)

### Security

Stateless JWT auth:
- `POST /api/v1/auth/telegram` — public; validates Telegram Login Widget HMAC-SHA256 hash (`SHA256(bot_token)`)
- All other endpoints require `Authorization: Bearer <token>`
- `@PreAuthorize` for method-level role checks

**Filter chain:** `RateLimitFilter` (Bucket4j, 60 req/min per IP) → `JwtAuthFilter` → Spring Security

### REST Controllers

Standard CRUD for rides, bookings, hubs, and users. Primarily used by admin tooling — end users interact through the bot only.

### Flyway Migrations

Located in `carpool-web/src/main/resources/db/migration/`. Notable migrations:

| Migration | What It Does |
|-----------|-------------|
| V37 | Adds `group_message_id` column to `rides` |
| V39 | Creates `vehicles` table with soft-delete and FK to `users` |
| V40 | Adds nullable `vehicle_id` FK to `rides` |
| V41 | Migrates existing vehicle data from `users` columns into `vehicles` |
| V42 | Widens `seat_capacity` from `TINYINT` to `INT` |
| V43 | Widens unique constraint on `ride_ratings` from `(ride_id, rater_id)` to `(ride_id, rater_id, ratee_id)` — supports one rating per passenger per ride for driver raters |
| V44 | Adds `group_message_posted_at TIMESTAMP NULL` (+ index) to `rides` — tracks when the Telegram group message was last posted; used by the stale-refresh scheduler to proactively re-post before the 48h Telegram deletion limit |

---

## Key Flows End-to-End

### Driver Posts a Ride

```
POST_RIDE callback
  → PostRideHandler (9 steps: direction → date → origin → destination
                               → seats → contribution → notes → vehicle → confirm)
  → handleConfirmPostRide()
      → rideService.createRide()         [DRAFT]
      → rideService.updateRideStatus()   [ACTIVE]
      → RidePostedEvent published
  → GroupNotificationService
      → posts group announcement (2 buttons)
      → sends follower DMs
```

### Passenger Follows Driver and Books from Group

```
Taps "⭐ Follow Driver | View Ride" in group
  → Opens bot in private chat
  → MessageHandler.handleFollowAndViewRide()
      → fetches ride from DB
      → isFavorite() check → saveFavorite() if new follower
      → shows ride card ("⭐ You're now following [driver]!" for new follows)
  → Taps "✅ Book This Ride"
  → BookingHandler sends booking request
  → Driver notified → accepts/declines
```

### Ride Lifecycle

```
ACTIVE
  → Driver taps "🚀 Start Ride"   → DEPARTED  → group announcement deleted
  → Driver taps "✅ Complete Ride" → COMPLETED → group announcement deleted
                                               → rating prompt sent to all
                                               → bot asks driver "Post another ride?"
                                                   [🚗 Yes] → POST_RIDE → fresh flow
                                                   [❌ No]  → MAIN_MENU
```

---

## Configuration

All config lives in `carpool-web/src/main/resources/`. Local dev uses `application-local.properties`.

| Env Var | Purpose |
|---------|---------|
| `SPRING_PROFILES_ACTIVE` | Active Spring profile |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | MySQL connection |
| `JWT_SECRET` | JWT signing key |
| `TELEGRAM_BOT_TOKEN` | Bot API token |
| `TELEGRAM_BOT_USERNAME` | Bot username (used in deep link URLs) |
| `TELEGRAM_GROUP_CHAT_ID` | Community group chat ID |
| `TELEGRAM_GROUP_HOME_TO_WORK_TOPIC_ID` | Morning topic thread ID |
| `TELEGRAM_GROUP_WORK_TO_HOME_TOPIC_ID` | Evening topic thread ID |
