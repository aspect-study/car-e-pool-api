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
carpool-web           ← Spring Boot entry point, REST controllers, security, Flyway
```

The dependency flows strictly downward — `carpool-bot` can use `carpool-service` but never the reverse. Only `carpool-web` produces an executable JAR.

---

## Domain Layer (`carpool-domain`)

The core entities:

| Entity | Key Fields | Notes |
|--------|-----------|-------|
| `User` | `telegramId`, `fullName`, `role` (PASSENGER/DRIVER/BOTH/ADMIN), `status` | Legacy vehicle fields kept for backward compat |
| `Ride` | `driver`, `originHub`, `destinationHub`, `departureTime`, `status`, `availableSeats`, `groupMessageId`, `announceCount` | Status: DRAFT→ACTIVE/FULL→DEPARTED→COMPLETED |
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
- `createRide()` — creates ride as DRAFT; validates driver has no active ride or booking conflict
- `updateRideStatus()` — handles all status transitions; publishes `RidePostedEvent` on DRAFT→ACTIVE
- `reannounceRide()` — increments `announceCount` (max 3) and re-fires `RidePostedEvent`

### `BookingService`
- `createBooking()` — acquires `SELECT FOR UPDATE` pessimistic lock on the ride row to prevent double-booking the last seat
- `respondToBooking()` — ACCEPT/DECLINE with reason; decrements available seats on accept

### `FavoriteService`
- `saveFavorite()` — idempotent (silently ignores duplicates); throws `IllegalArgumentException` for self-follow
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
| `RidePostedEvent` | DRAFT → ACTIVE status transition |
| `RideDepartedEvent` | ACTIVE/FULL → DEPARTED |
| `RideCompletedEvent` | DEPARTED → COMPLETED |
| `RideCancelledEvent` | Any → CANCELLED |

Listeners use `@Async + @TransactionalEventListener(AFTER_COMMIT) + @Transactional(REQUIRES_NEW)` — guaranteed to fire only after the DB transaction commits, on a virtual thread.

### Schedulers

All use `fixedDelay` with staggered `initialDelay` to prevent startup overlap:

| Scheduler | Frequency | What It Does |
|-----------|-----------|-------------|
| `RideExpiryScheduler` | Every 30 min | Auto-cancels expired ACTIVE/FULL rides; auto-completes DEPARTED rides 2h+ old |
| `PendingBookingScheduler` | Every 60 sec | Sends driver reminders at 15/30/45 min; auto-declines at 60 min |
| `RideDepartureReminderScheduler` | Every 5 min | One-shot 30-min-before reminder to driver + confirmed passengers |

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

### Helpers

| Helper | Responsibility |
|--------|---------------|
| `BotFlowHelper` | Resets state; builds and sends the context-aware main menu |
| `PostRideHelper` | Shared confirmation screen and notes prompt (used by new post and repost flows) |
| `BotMessageBuilder` | All message formatting: `formatRideCard()`, `buildMemberBadge()`, `button()`, `inlineButtons()` |

`BotMessageBuilder` is a pure static utility — no Spring bean.

### Group Notifications — `GroupNotificationService`

Listens for all `RideEvents.*` via `@Async + @TransactionalEventListener(AFTER_COMMIT)`:

**`onRidePosted`**
1. If re-announce: deletes old group message first (isolated try/catch)
2. Posts announcement to the correct group topic (HOME→WORK vs WORK→HOME)
3. Stores returned Telegram message ID in `Ride.groupMessageId`
4. Sends follower DM alerts — suppressed when `announceCount > 1` (re-announces don't re-notify)

Group post includes two URL button rows:
- `🚘#N ❯❯❯❯ | View | Request a Seat` → `?start=RIDE_{rideId}`
- `⭐ Follow Driver | View Ride` → `?start=FOLLOW_RIDE_{driverId}_{rideId}`

**`onRideDeparted` / `onRideCompleted` / `onRideCancelled`**
- Deletes the group announcement via `CarpoolBot.deleteMessage()`
- Skipped if `groupMessageId` is null or the ride was created more than 48 hours ago (Telegram API limitation)

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
