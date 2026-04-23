# CLAUDE.md
This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands
```bash
# Build (skip tests)
mvn clean package -DskipTests
# Run all tests
mvn test
# Run tests for a specific module
mvn test -pl carpool-service
# Run a single test class
mvn test -Dtest=JwtServiceTest -pl carpool-service
# Run a single test method
mvn test -Dtest=JwtServiceTest#testGenerateToken -pl carpool-service
# Skip integration tests (require MySQL)
mvn test -Dgroups="!integration"
# Run only integration tests
mvn test -pl carpool-web -Dgroups="integration"
# Start MySQL for local dev
docker-compose up mysql_db -d
# Start full stack
docker-compose up --build
```
Integration tests require MySQL running on port 3308. Start it with `docker-compose up mysql_db -d` before running them.

## Module Structure
Six-module Maven project with strict dependency direction:
```
carpool-web        <- REST controllers, security config, Flyway migrations
    |
carpool-service    <- Business logic, DTOs, mappers, event publishing
    |
carpool-repository <- Spring Data JPA repositories
    |
carpool-domain     <- JPA entities, enums
    |
carpool-common     <- Exceptions, API response wrappers, constants
carpool-bot        <- Telegram bot (long polling, command handlers, keyboards)
```
`carpool-web` contains the main class (`CarpoolApiApplication`), Spring Security config, JWT filter, rate-limit filter, and Flyway migration scripts under `src/main/resources/db/migration/`.

## Architecture
**Authentication:** Telegram Login Widget only. `POST /api/v1/auth/telegram` verifies the Telegram payload with HMAC-SHA256, creates or updates the user, and returns a JWT. The JWT carries `userId`, `telegramId`, and `role` claims. `JwtAuthFilter` validates tokens on every request.

**Ride/Booking flow:** Drivers post rides (`Ride`). Passengers create bookings (`Booking`). Seat reservation uses pessimistic locking in `BookingRepository` to prevent double-booking. Status transitions live in `BookingService` and `RideService`.

**Notifications:** Domain events (`RideEvents.java` in carpool-service) are published via `ApplicationEventPublisher`. `@Async` listeners in `NotificationService` send Telegram messages via the Bot API. The `Notification` entity records each sent message.

**Telegram Bot:** `carpool-bot` runs as a separate Spring component using `telegrambots-springboot-longpolling-starter`. It manages per-user conversation state (stored in-memory), handles slash commands and inline keyboard callbacks, and delegates to services in carpool-service.

**Caching:** Caffeine caches `CACHE_HUBS` (60-min TTL), `CACHE_USERS` (10-min TTL), `CACHE_HUB_SEARCH` (5-min TTL). Always evict affected caches when mutating hub or user entities.

**Scheduling:** `@EnableScheduling` drives auto-decline of timed-out bookings, booking reminders, and ride expiry checks. Schedulers live in `carpool-service/src/main/java/com/carpool/service/scheduler/`.

## Key Domain Enums
- `RideStatus`: DRAFT -> ACTIVE -> FULL / DEPARTED -> COMPLETED / CANCELLED
- `BookingStatus`: PENDING -> CONFIRMED -> COMPLETED / CANCELLED_BY_PASSENGER / CANCELLED_BY_DRIVER / DECLINED / TIMED_OUT
- `UserRole`: PASSENGER, DRIVER, BOTH
- `RideDirection`: HOME_TO_WORK, WORK_TO_HOME, OTHER

## Configuration
Active profile is set via `spring.profiles.active`. Use `local` for development (maps to `application-local.properties`).

Required environment variables in production: `JWT_SECRET`, `TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_USERNAME`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.

Swagger UI is available at `/swagger-ui.html` in `local` profile only; disabled in `prod`.

## Database
MySQL 8.0. Schema managed by Flyway -- never edit existing migration scripts, always add a new `V{n}__description.sql` file. Migrations live in `carpool-web/src/main/resources/db/migration/`.

Local connection: `localhost:3308`, database `car_e_pool_db`, user/password `carpool/carpool`.

---

## Business Context & Session Memory

> Added manually -- covers domain decisions, flows, and rules built across development sessions.
> Last updated: April 22, 2026

### Project Identity

| Field | Value |
|---|---|
| **GitHub** | `https://github.com/aspect-study/car-e-pool-api` |
| **Local path (Windows)** | `C:\Users\ADMIN\IdeaProjects\car-e-pool-api` |
| **Community** | ~5,000 Telegram members, South to Taguig/Makati corridor |

---

### Flyway Migrations (Applied)

| Version | Description |
|---|---|
| V1-V13 | Core schema, entities, seed data, hub data |
| V14 | Hub aliases (500+ aliases for 143 hubs) |
| V15 | Driver notes table |
| V16 | Booking pending approval columns (passenger_message, decline_reason, reminder_count, expires_at) |
| V17 | Fix reminder_count type to INT |
| V18 | Expand booking status column to VARCHAR(30) |
| V19 | Add vehicle info to users (car_model, car_color, plate_number) with unique index on plate_number |

---

### Booking Approval Flow

```
Passenger books ride
    -> Booking saved as PENDING (seats decremented immediately)
    -> Driver notified with Accept / Decline buttons
    -> Reminder 1 at 5 min
    -> Reminder 2 at 10 min
    -> Reminder 3 at 15 min
    -> Auto-TIMED_OUT at 20 min (seats restored)

Driver accepts  -> CONFIRMED -> passenger notified
Driver declines (with reason) -> DECLINED -> seats restored -> passenger notified
Passenger cancels (with reason) -> CANCELLED_BY_PASSENGER -> seats restored -> driver notified
```

Key booking columns:
- `passenger_message VARCHAR(500)` -- optional note from passenger to driver
- `decline_reason VARCHAR(255)` -- reused as cancellationReason for ALL cancel/decline reasons
- `reminder_count INT DEFAULT 0` -- reminders sent (0-3)
- `expires_at TIMESTAMP` -- auto-decline deadline (20 min from creation)

---

### Schedulers

| Scheduler | Interval | Description |
|---|---|---|
| `PendingBookingScheduler` | Every 60s | Sends reminders at 5/10/15 min, auto-declines expired bookings |
| `RideExpiryScheduler` | Every 30 min | Auto-departs stale rides (15 min buffer), auto-completes DEPARTED rides (2 hr grace) |

---

### Bot State Machine

All conversation state lives in `StateManager` (Caffeine cache, keyed by Telegram chatId).
`UserState` is an immutable record using Lombok `@With` for safe state transitions.

Key BotFlows:
```
POST_RIDE_DIRECTION -> POST_RIDE_DEPARTURE_TIME -> POST_RIDE_ORIGIN
-> POST_RIDE_DESTINATION -> POST_RIDE_SEATS -> POST_RIDE_CONTRIBUTION
-> POST_RIDE_NOTES -> POST_RIDE_VEHICLE_CONFIRM -> POST_RIDE_CONFIRM

SET_VEHICLE_COLOR -> SET_VEHICLE_MODEL -> SET_VEHICLE_PLATE -> POST_RIDE_VEHICLE_CONFIRM

SEARCH_SELECT_DIRECTION -> SEARCH_SELECT_TIME -> SEARCH_RESULTS -> SEARCH_FILTER

BOOKING_MESSAGE       -- passenger typing optional message before booking
POST_RIDE_NOTES_WRITE -- driver typing custom note
```

Handler routing:
```
CarpoolBot.onUpdateReceived()
    |-- Rate limiter check (BotRateLimiter -- 30 req/min per chatId)
    |-- MessageHandler.handle()   -- text messages
    +-- CallbackHandler.handle()  -- inline button taps
```

---

### Hub Matching (HubMatcher)

Five-layer fuzzy matching (in order):
1. Alias match (HubAliasRepository)
2. Exact name match
3. Contains match
4. Word-score match (most matching words wins)
5. Levenshtein <= 2 (typo tolerance)

Rules:
- Minimum 3 characters required -- shorter input returns empty, shows error
- Suggestion-only mode -- never auto-assigns a hub, always shows buttons for user to confirm
- Recent hubs fallback -- shows user's last 5 used hubs when no match found (derived from rides + bookings tables, no separate table)

---

### Search & Filter

```
Direction -> Time Window -> Paginated Results (5 per page, bot)
                                  | optional
                           [Filter & Sort]
                                  |
             Sort:      Earliest / Cheapest / Most Seats
             Min Seats: 1+ / 2+ / 3+ / Any
             Max Share: 50 / 100 / 150 / Any
```

Default sort: Earliest. Filters are stored in UserState and reapplied on page navigation.
Bot uses in-memory pagination (5/page). REST API uses Spring Pageable (10/page default, max 50).

---

### Cancellation Reasons

Driver cancelling a ride:
- Vehicle issue
- Route change
- Personal reason
- Other reason

Driver declining a booking:
- Already fully booked
- Route change
- Vehicle issue
- Other reason

Passenger cancelling a booking:
- Found another ride
- Change of plans
- Running late
- Other reason

All reasons stored in `bookings.decline_reason` column (reused as cancellationReason).

---

### Context-Aware Main Menu

No active ride:
- Always show: [Home to Work] [Work to Home]
- If has active/pending bookings: [My Bookings (N)]
- If has completed/cancelled rides: [My Rides]
- Always show: [My Profile]

Prompt:
- Has bookings AND has past rides -> "What would you like to do?"
- Otherwise -> "Where are you headed today?"

Active ride (ACTIVE/FULL) -- no pending:
- [View Bookings] [Start Ride]
- [Cancel Ride]
- [Find a Ride]
- [My Profile]

Active ride (ACTIVE/FULL) -- with pending requests:
- [View Bookings] [Start Ride]
- [Pending (N)] [Cancel Ride]
- [Find a Ride]
- [My Profile]

Active ride (DEPARTED):
- [View Bookings] [Complete Ride]
- [My Profile]

My Profile button is ALWAYS shown in ALL menu states -- no exceptions.

---

### Repost Ride Flow

```
My Rides -> last 10 COMPLETED/CANCELLED rides only
    -> [Use This Route] per ride
    -> Shows full ride details for review
    -> Asks departure time only (all other fields pre-filled from original)
    -> Direction auto-detected from original ride (no need to ask)
    -> Skips hub selection (origin/destination set in UserState)
    -> Goes straight to notes -> vehicle confirm -> confirm -> post
```

Key implementation: In `handlePostRideEtd`, if `state.getOriginHubId() != null && state.getDestinationHubId() != null` then skip to confirmation. Normal post ride flow always has null hubs at ETD step.

---

### Profile View

Role-aware stats computed from rides and bookings tables (no separate stats table).

Driver stats:
- Rides posted = COUNT all rides by driver
- Completed = COUNT WHERE status = COMPLETED
- Cancelled = COUNT WHERE status = CANCELLED (driver-initiated only)
- Passengers served = SUM(seats_reserved) on COMPLETED bookings on driver's rides
- Completion rate = (completed / total) * 100

Passenger stats:
- Bookings made = COUNT all bookings by passenger
- Completed = COUNT WHERE status = COMPLETED
- Cancelled by me = COUNT WHERE status = CANCELLED_BY_PASSENGER ONLY
  (DECLINED, TIMED_OUT, CANCELLED_BY_DRIVER are NOT counted against the passenger)
- Completion rate = (completed / total) * 100

Vehicle info (drivers):
- Shows car_color + car_model + plate_number if set
- Shows "No vehicle info yet" if not set
- [My Vehicle] button always shown for drivers

Entry points: /profile command + My Profile menu button (always visible in ALL menu states).
REST API: GET /api/v1/users/me/stats
No caching -- 7 COUNT/SUM queries on indexed columns, fast enough at current scale.

---

### Vehicle Info Flow

Single vehicle per driver (Path 1). Designed for easy upgrade to multi-vehicle post-launch.

DB columns on users table (V19):
- `car_model VARCHAR(100) NULL`
- `car_color VARCHAR(50) NULL`
- `plate_number VARCHAR(20) NULL` -- unique index, stored uppercase

Post Ride flow -- vehicle confirmation step (after NOTES, before CONFIRM):
```
Has saved vehicle?
  YES -> Show vehicle confirmation
         [Yes, Proceed] [Change Vehicle]
         If Yes -> load vehicle to state -> go to CONFIRM
         If Change -> start vehicle input flow

  NO  -> Start vehicle input flow directly
         Color -> Model -> Plate -> Save & Continue -> go to CONFIRM
```

Vehicle input flow:
```
SET_VEHICLE_COLOR -> SET_VEHICLE_PLATE -> SET_VEHICLE_PLATE
    -> shows vehicle summary
    -> [Save & Continue] [Change] [Cancel]
```

Bot commands:
- /vehicle -- view/edit/remove vehicle info
- Callbacks: VEHICLE_CONFIRM_YES, VEHICLE_CONFIRM_SAVE, VEHICLE_CHANGE, VEHICLE_REMOVE

REST API: PATCH /api/v1/users/me/vehicle

plate_number has unique index -- duplicate plates throw DataIntegrityViolationException.
carColor is optional -- carModel and plateNumber are required.

---

### Driver View Bookings

Driver's booking detail view shows ONLY what the driver needs to contact the passenger:
- Passenger name + Telegram handle
- Seats reserved
- Gas share amount
- Settlement status
- Passenger's note (labeled "Passenger's note:" not "Message:")
- Booking status

Does NOT show: Route, Departure time, Pickup, Dropoff -- driver already knows these from their own ride.

---

### Bot Rate Limiting

Per-chatId rate limiting using Bucket4j token bucket algorithm.
Consistent with REST API RateLimitFilter approach.

Config (application-local.properties):
```
carpool.bot.rate-limit.capacity=30
carpool.bot.rate-limit.refill-tokens=30
carpool.bot.rate-limit.refill-seconds=60
carpool.bot.rate-limit.warn-interval-seconds=10
```

Behavior:
- 30 requests per minute per chatId
- On exceed: warn user ("You're sending too fast. Please wait a moment.")
- Warning throttled to once per 10 seconds -- no message pile-up
- Applied in CarpoolBot.consume() before routing to handlers
- In-memory (ConcurrentHashMap) -- single instance assumption
- For multi-instance: replace with Redis-backed Bucket4j

---

### REST API Pagination

All list endpoints paginated using Spring Pageable.
Response wrapped in PagedResponse<T>:
```json
{
  "success": true,
  "data": {
    "content": [...],
    "page": 0,
    "size": 10,
    "totalElements": 42,
    "totalPages": 5,
    "last": false
  }
}
```

Paginated endpoints:
- GET /api/v1/rides -- page, size (default 10, max 50), sort by departureTime ASC
- GET /api/v1/rides/mine -- page, size (default 10, max 50), sort by departureTime DESC
- GET /api/v1/rides/direction -- page, size (default 10, max 50)
- GET /api/v1/bookings/mine -- page, size (default 10, max 50), sort by createdAt DESC
- GET /api/v1/rides/{id}/bookings -- page, size (default 10, max 50), sort by createdAt ASC

Bot pagination is separate (in-memory, 5/page) -- NOT affected by REST pagination changes.
Unpaged service methods preserved for bot use.

---

### REST API Controllers

```
AuthController       POST /api/v1/auth/telegram
HubController        GET  /api/v1/hubs
                     GET  /api/v1/hubs/search
                     POST /api/v1/hubs/suggest
                     GET  /api/v1/hubs/pending (admin)
                     PATCH /api/v1/hubs/{id}/approve (admin)
                     PATCH /api/v1/hubs/{id}/reject (admin)
RideController       POST  /api/v1/rides
                     GET   /api/v1/rides (paginated)
                     GET   /api/v1/rides/direction (paginated)
                     GET   /api/v1/rides/mine (paginated)
                     GET   /api/v1/rides/{id}
                     PATCH /api/v1/rides/{id}/status
BookingController    POST  /api/v1/rides/{rideId}/bookings
                     GET   /api/v1/rides/{rideId}/bookings (paginated)
                     GET   /api/v1/bookings/mine (paginated)
                     POST  /api/v1/bookings/{id}/accept
                     POST  /api/v1/bookings/{id}/decline
                     DELETE /api/v1/bookings/{id}
                     PATCH /api/v1/bookings/{id}/payment
UserController       GET   /api/v1/users/me
                     PATCH /api/v1/users/me/role
                     PATCH /api/v1/users/me/vehicle
ProfileController    GET   /api/v1/users/me/stats
```

---

### Key Business Rules

1. One active ride at a time -- driver cannot post while having ACTIVE/FULL/DEPARTED ride
2. No booking own ride -- driver cannot book their own ride
3. Active booking blocks posting -- passenger with CONFIRMED/PENDING booking cannot post ride
4. Active ride blocks finding -- driver with active ride cannot search as passenger
5. Seat soft-hold on PENDING -- seats decremented immediately, restored on DECLINED/TIMED_OUT/cancel
6. FULL transition -- when availableSeats = 0, ride transitions to FULL
7. FULL -> ACTIVE -- when booking cancelled/declined, ride reopens if it was FULL
8. @Transactional on ALL updateRideStatus overloads -- required to prevent lazy loading proxy errors on event publishing
9. My Profile button always visible in ALL menu states including active ride menus

---

### LTFRB Legal Compliance -- CRITICAL

Never use commercial fare/payment language. LTFRB may classify the app as TNVS (requiring franchise) if commercial terms are used. This is a community cost-sharing tool, not a transport service.

Never use -> use instead:
- Fare -> Gas Contribution or Suggested Share
- Price -> Suggested Share
- Payment -> Contribution or Share
- "per seat" price framing -> "share/seat"
- Contribution due -> Suggested share
- Max price -> Max share
- Pay -> Settle share
- Contribution paid -> Share settled
- Unpaid -> Not yet settled
- Partially paid -> Partially settled
- Paid -> Settled

Emoji convention: use ⛽ for all gas share amounts (e.g. ⛽ ₱150 share/seat)

"Suggested" framing is intentional -- implies voluntary cost-sharing, not commercial transaction.
"Gas share" framing is intentional -- communicates specific cost-sharing purpose, not a fare.
"Settle" vs "Pay" -- informal peer-to-peer language, not commercial transaction language.

Status: COMPLETED (Apr 22, 2026) -- All commercial terms replaced across:
  BotMessageBuilder, MessageHandler, PostRideHelper, CallbackHandler, NotificationService.

Files affected (full list):
- BotMessageBuilder.java -- ride card, ride list, paginated list
- MessageHandler.java -- post ride prompts, my rides, my bookings
- PostRideHelper.java -- confirmation screen
- CallbackHandler.java -- booking details, driver views, filter screen, pending requests, my rides
- NotificationService.java -- all notification message strings

---

### Threading Decision

Uses LongPollingSingleThreadUpdateConsumer -- intentional, not a bug.
Single-thread guarantees state consistency without synchronization overhead.
Do NOT switch to multi-thread without measured performance data justifying it.
Current scale < 500 users. Revisit at 500+ daily active users with measured p95 latency > 2s.

---

### Event Publishing Rules

- All events published AFTER transaction commits (TransactionPhase.AFTER_COMMIT)
- Event listeners run @Async + Propagation.REQUIRES_NEW
- This isolates notification failures from business logic
- Never move event publishing inside the main transaction

---

### Exception Messages (User-Facing)

All exceptions use clear, non-technical messages:
- DuplicateBookingException: "You already have an active booking request on this ride..."
- RideFullException: "Sorry, this ride is already fully booked..."
- RideNotFoundException: "This ride is no longer available..."
- BookingNotFoundException: "This booking no longer exists..."
- DeparturePastException: "Departure time must be in the future..."
- HubNotFoundException: "The selected location could not be found..."
- SameHubException: "Pickup and drop-off locations cannot be the same..."
- UserNotFoundException: "User account not found. Please register first via /start."
- NotRideOwnerException: "You do not have permission to modify this ride."
- NotBookingOwnerException: "You do not have permission to modify this booking."
- InvalidRideStateException: dynamic message (context-dependent, always user-friendly)

---

### Pending Work

Priority 1 (Tech Debt):
- BookingService is large -- consider splitting approval logic into BookingApprovalService
- CallbackHandler is large -- consider splitting into feature-specific handlers

Priority 2 (Quality):
- Integration tests for REST API endpoints (defer until after first deployment)

Priority 3 (Features -- post-launch based on user feedback):
- Multi-vehicle support (Path 2) -- organic accumulation via plate uniqueness
- Full ride history / reporting
- Hub search suggestions in Find a Ride flow

---

### Session History

| Date | Key Work |
|---|---|
| Apr 14, 2026 | REST API scaffold, auth, DB schema, bot UX planning |
| Apr 15, 2026 | Spring Boot 4.0.5 + Java 25 upgrade, hub seed migration |
| Apr 16, 2026 | Bot implementation, booking flows, notifications |
| Apr 19, 2026 | Hub aliases migration, HubMatcher fuzzy search, UX improvements |
| Apr 20, 2026 | Booking approval flow, scheduler, filter + pagination, profile view |
| Apr 21, 2026 | Cancel with reason, repost ride, context-aware menu, LTFRB compliance, CLAUDE.md |
| Apr 22, 2026 | RideService unit tests, REST API alignment, ProfileController, bot rate limiting, REST pagination, vehicle info flow, driver view bookings fix, My Profile always visible, LTFRB compliance completed |
| Apr 23, 2026 | Repost ride (3 max DB limit), booking history (7 max DB limit), passenger bookings shows driver name/handle, vehicle update fix |
