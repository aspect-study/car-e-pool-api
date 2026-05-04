# 🚗 Car-e-Pool PH — Session Context

> **Purpose:** Load this file at the start of every new Claude session for instant project context.
> **Last Updated:** 2026-05-04
> **Current Session:** 8 (next)

---

## 1. Project Overview

### What It Is
A Telegram-based carpooling platform for South Metro Manila ↔ BGC/Makati commuters.
Community-driven, peer-to-peer, gas cost sharing model.

### Bot
- **Prod:** `@car_e_poolPH_bot`
- **Dev:** `@car_e_poolPH_dev_bot`
- **Admin Telegram ID:** `208038458`
- **Community Group:** `https://t.me/southispoolofcare`

### Tech Stack
| Layer | Technology |
|-------|-----------|
| Language | Java 25 |
| Framework | Spring Boot 4.x |
| Build | Maven multi-module |
| Database | MySQL 8 (AWS RDS in prod, local in dev) |
| Migrations | Flyway |
| Auth | JWT + Telegram Login Widget |
| Rate Limiting | Bucket4j + Caffeine |
| Caching | Caffeine |
| Mapping | MapStruct |
| HTTP Client | Apache HttpClient 5 (RestClient) |
| Bot Library | TelegramBots |

### Maven Modules
```
car-e-pool-api/
├── carpool-common       # Shared utilities (HtmlEscapeUtil, exceptions)
├── carpool-domain       # JPA entities, enums, domain logic
├── carpool-repository   # Spring Data repositories
├── carpool-service      # Business logic, schedulers, notifications
├── carpool-web          # REST controllers, security, filters, config
└── carpool-bot          # Telegram bot handlers, state machine
```

### Package
```
com.carpool
```

---

## 2. Architecture Decisions

### Key Decisions Made
- **Spring MVC over WebFlux** — blocking, traditional, preferred
- **Soft delete** — `deleted + deleted_at` on users, preserves history
- **Pessimistic locking** — `findByIdWithLock()` for ride booking (financial ops)
- **Event-driven notifications** — `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` + `@Transactional(REQUIRES_NEW)`
- **Single admin stats source** — `AdminStatsService.getStats()` used by both bot and REST API
- **Bot state machine** — Caffeine-backed `StateManager` with `UserState` per chatId
- **Rate limiting** — Caffeine cache (1hr TTL, 100k max)
- **Role system** — `UserRole` enum: `PASSENGER`, `DRIVER`, `BOTH`, `ADMIN`
- **Re-announce** — max 3 times per ride, `announce_count` column on rides table
- **Pending booking window** — 60 minutes, reminders at 15/30/45 min, auto-decline at 60
- **Command pattern** — `BotCommand` interface + `@PostConstruct` Map registry in `CallbackHandler` — adding a new callback = one `commands.put()` line
- **Facade pattern** — sub-handlers group domain logic
- **Value object** — `BotContext` record replaces scattered parameter lists
- **Shared helper** — `BotFlowHelper` is single source of truth for shared flows
- **TelegramClient** — proper Spring bean via `TelegramClientConfig` (eliminates lazy-init race condition)
- **Ratings** — mutual, both driver and passenger rate each other after completed ride. Driver rates each passenger independently (per-ratee check). Passenger rates driver once per ride.
- **Favorites** — passenger saves driver as favorite after rating. Alert fires when favorite driver posts a ride. Driver saving passenger as favorite is intentionally not supported.
- **Completion rate** — uses only terminal rides (completed + cancelled) as denominator. Excludes active/departed rides still in progress.
- **Scheduler events** — `expireStaleRides()` publishes `RideDepartedEvent`, `completeStaleRides()` publishes `RideCompletedEvent` — same notifications as manual driver actions.

### Bot Handler Architecture
```
CallbackHandler     — thin router only (Map registry dispatch)
MessageHandler      — thin router only (flow switch dispatch)
BotFlowHelper       — shared stateful flows (no handler dependencies)
PostRideHandler     — post ride sub-flow
BookingHandler      — passenger booking sub-flow
RideSearchHandler   — search, filter, pagination
DriverHandler       — driver ride management
ProfileHandler      — profile, vehicle, terms, admin, onboarding
RatingHandler       — rating + favorite flow
BotContext          — value object (replaces scattered params)
BotCommand          — @FunctionalInterface for Command pattern
```

### Module Dependency Rules
- `carpool-bot` depends on `carpool-service`
- `carpool-service` depends on `carpool-repository`
- `carpool-repository` depends on `carpool-domain`
- `carpool-common` has no dependencies — shared by all modules
- **Never** create circular dependencies between modules

### Notification Threading Model
```
@Async + @TransactionalEventListener(AFTER_COMMIT) + @Transactional(REQUIRES_NEW)
```

---

## 3. Domain Model

### Key Entities
```
User         — telegram_id, full_name, telegram_handle, role, status, deleted
Ride         — driver, origin_hub, destination_hub, direction, departure_time,
               available_seats, total_seats, contribution_amount, status, announce_count
Booking      — ride, passenger, seats_reserved, status, expires_at, reminder_count
Hub          — name, area, type (PREDEFINED/USER_SUGGESTED), approved
RideWaypoint — ride, hub, type (PICKUP/DROPOFF)
Notification — user, type, message, sent_at
RideRating   — ride, rater, ratee, stars (TINYINT), comment (VARCHAR 1000),
               rater_role (DRIVER/PASSENGER), created_at
               UNIQUE: (ride_id, rater_id, ratee_id)
UserFavorite — follower, favorite, created_at
               UNIQUE: (follower_id, favorite_id)
```

### Enums
```
UserRole:      PASSENGER, DRIVER, BOTH, ADMIN
UserStatus:    ACTIVE, SUSPENDED, BANNED
RideStatus:    DRAFT, ACTIVE, FULL, DEPARTED, COMPLETED, CANCELLED
RideDirection: HOME_TO_WORK, WORK_TO_HOME, OTHER
BookingStatus: PENDING, CONFIRMED, DECLINED, TIMED_OUT,
               CANCELLED_BY_PASSENGER, CANCELLED_BY_DRIVER, COMPLETED
```

### Ride Status Transitions
```
DRAFT → ACTIVE    (publish)
ACTIVE → DEPARTED (start ride)
FULL → DEPARTED   (start full ride)
ACTIVE → CANCELLED
FULL → CANCELLED
DEPARTED → COMPLETED
```

---

## 4. Database

### Flyway Migrations (Latest: V33)
```
V1–V20   — initial schema, hubs, waypoints, notifications
V21      — add notes to rides
V22      — add reminder_count to bookings
V23      — add pending booking expiry
V24      — add announce_count to rides
V25      — fix announce_count type to INT
V26      — add deleted + deleted_at to users
V27      — ALTER users.role ENUM to include ADMIN
V28      — add Parañaque Sucat Road hubs
V29      — add hub aliases (V28 hubs)
V30      — add additional hub aliases (ups5, upsv, valley 1, jaka plaza, yp mall etc.)
V31      — expand driver_notes.content VARCHAR(500) → VARCHAR(1000)
V32      — add ride_ratings table (mutual rating, per-ratee unique constraint)
V33      — add user_favorites table (follower/favorite unique constraint)
```

### Important Indexes
- `rides`: composite index on `(driver_id, status)`
- `bookings`: composite index on `(passenger_id, status)`, `(ride_id, status)`
- `ride_ratings`: indexes on `ratee_id`, `ride_id`, `rater_id`
- `user_favorites`: indexes on `follower_id`, `favorite_id`

### Prod DB
- AWS RDS MySQL 8
- Credentials in `application-prod.properties` (env vars)
- **Never** run migrations manually — Flyway handles on startup

---

## 5. Caches (Caffeine)

| Cache Name | TTL | Max Size | Eviction |
|------------|-----|----------|---------| 
| `hubs` | 60 min | 200 | On admin hub approval |
| `users` | 10 min | 1000 | `@CacheEvict` on role/status/delete change |
| `hub-search` | 5 min | 100 | — |
| `hub-aliases` | 60 min | 2000 | — |
| `adminStats` | 30 sec | 1 | — |
| `profileStats` | 5 min | 500 | — |

---

## 6. Security

### JWT
- Role embedded in JWT claim
- Stale role after upgrade — user must re-login (documented in `JwtAuthFilter`)
- `@PreAuthorize("hasRole('ADMIN')")` on `/api/v1/users/admin/stats`

### Rate Limiting
- `RateLimitFilter` — Caffeine cache, 1hr TTL, 100k max IPs
- `X-Forwarded-For` still uses spoofable header — **fix when nginx is deployed**

### Bot Gates (in order)
```
1. Terms acceptance
2. Telegram @username required (hard block)
3. Deleted account check (silent ignore)
```

### ADMIN Access
- Set via: `UPDATE users SET role = 'ADMIN' WHERE telegram_id = 208038458;`
- Must run this on prod DB after every fresh deploy if role resets

---

## 7. Bot State Machine

### Key Flows
```
POST_RIDE  → direction → ETD → origin hub → dest hub → seats →
             contribution → notes → vehicle confirm → confirm post

FIND_RIDE  → direction → time window → filter → view ride → book →
             booking message → sent

BOOKING    → driver receives notification → accept/decline →
             passenger notified → ride departs → completed

RATING     → ride completed → rate now button →
             star tap → optional comment → favorite prompt (passenger only)

MULTI-PAX  → driver has 2+ passengers → passenger selection screen →
             rate each passenger independently
```

### Time Window Slots (Find Ride)
```
🌙 Early Morning  — 3:30 AM to 6:00 AM
🌅 Morning Rush   — 6:00 AM to 9:00 AM
☀️ Late Morning   — 9:00 AM to 12:00 PM
🌤️ Noon          — 12:00 PM to 3:00 PM
🌇 Afternoon      — 2:30 PM to 7:00 PM
🌆 Evening        — 6:30 PM to 11:00 PM
🔍 Show All Today — now to 23:59
📅 Custom         — user input (searches from -1hr to +2hr of typed time)
```

### Session Recovery
- `SessionRecoveryHandler` handles expired `UserState`
- **Flow-sensitive** (need state): `POST_RIDE_ACTIONS`, `RATING_ACTIONS`
- **NOT flow-sensitive** (carry payload): `VIEW_RIDE`, `BOOK_RIDE`, `BOOK_NOW`,
  `CANCEL_BOOKING`, `DECLINE_BOOKING_REASON`, `ACCEPT_BOOKING`, `DECLINE_BOOKING`,
  `CONFIRM_CANCEL_RIDE`, `RATE_RIDE`, `RATE_PASSENGER`, `SAVE_FAVORITE`, `SKIP_FAVORITE`

### Hub Search (HubMatcher)
- 5-layer matching: alias exact → alias fuzzy → name exact → name fuzzy → area
- `suggest()` alias check as Layer 1 via cached `HubService.findByAlias()`
- 2-column button layout for hub names ≤20 chars, 1-column for longer
- MAX_SUGGESTIONS = 20

### Rating System
- Triggered after `RideCompletedEvent` via `onRideCompleted()` in `NotificationService`
- Rating prompt sent via `sendTelegramMessageWithButtons()` with `⭐ Rate Now` button
- Driver — rates each passenger independently (per-ratee unique check)
- Passenger — rates driver once per ride (per-ride unique check)
- Stars: 1–5 (TINYINT in DB, Integer in Java with `columnDefinition = "TINYINT"`)
- Comment: optional, max 1000 characters
- Favorite prompt: shown to passenger only after rating driver
- Average rating shown on ride card and profile

### Schedulers
```
RideExpiryScheduler      — every 30 min, initialDelay 1 min
  expireStaleRides()     — ACTIVE/FULL rides 15+ min past departure → DEPARTED
                           publishes RideDepartedEvent (passengers notified)

  completeStaleRides()   — DEPARTED rides 2+ hours past departure → COMPLETED
                           publishes RideCompletedEvent (passengers notified + rating prompts)

RideDepartureReminderScheduler — every 5 min
  sendDepartureReminders() — sends reminder 25-35 min before departure
                             duplicate check via notifications table
```

### Notification Events
```
RidePostedEvent            → group announcement + favorite follower alerts
RideDepartedEvent          → confirmed passengers notified (driver on the way)
                             fires on manual Start Ride AND auto-depart scheduler
RideCompletedEvent         → passengers notified + rating prompts sent
                             fires on manual Complete Ride AND auto-complete scheduler
RideCancelledEvent         → all booked passengers notified
RideExpiredEvent           → booked passengers notified
BookingConfirmedEvent      → passenger notified
BookingDeclinedEvent       → passenger notified
BookingTimedOutEvent       → passenger + driver notified
BookingReminderEvent       → driver reminded of pending request
RideDepartureReminderEvent → driver + passengers reminded 30min before
```

---

## 8. Deployment

### Server
- **Provider:** AWS (EC2 + RDS) — Amazon Linux AMI 2018.03
- **Path:** `/opt/systems88/sep/car-e-pool-api/`
- **Deploy tool:** MobaXterm (SFTP) + `deploy.sh`

### Deploy Process
```bash
# 1. Local — build
mvn clean install -DskipTests

# 2. MobaXterm — transfer entire project to:
#    /opt/systems88/sep/car-e-pool-api/

# 3. Server — run deploy script
./deploy.sh

# 4. deploy.sh does:
docker-compose build carpool-api
docker-compose up -d --no-deps carpool-api
docker logs car-e-pool-api --tail=20

# 5. Watch full startup logs
docker logs car-e-pool-api -f

# 6. After successful startup — grant admin
UPDATE users SET role = 'ADMIN' WHERE telegram_id = 208038458;
```

### Verify After Deploy
- [ ] Flyway V1–V33 all applied successfully
- [ ] Bot responds to `/start`
- [ ] Ride search works — all 6 time slots return results
- [ ] Admin stats visible in profile
- [ ] Notifications sending
- [ ] Complete test ride → rating prompts appear with Rate Now button
- [ ] Favorite alert fires when driver posts new ride
- [ ] View Ride from favorite alert opens correctly
- [ ] Driver departed notification fires on Start Ride
- [ ] No ERROR lines in startup logs

---

## 9. Test Accounts

| Role | Telegram | Notes |
|------|----------|-------|
| Driver/Admin | `@AspectJump` (ID: 208038458) | Main test account |
| Passenger | Second account | Use separate Telegram account |

### Local Dev Setup
```properties
# application-local.properties
carpool.telegram.bot-token=<dev_bot_token>
carpool.telegram.bot-username=car_e_poolPH_dev_bot
carpool.telegram.group-chat-id=<dev_group_chat_id>
carpool.community.group-invite-link=https://t.me/southispoolofcare
springdoc.swagger-ui.enabled=true
springdoc.api-docs.enabled=true
```

---

## 10. Known Skipped Items (Fix Later)

| # | Issue | Where | Condition |
|---|-------|--------|-----------| 
| SEC-01 | `X-Forwarded-For` spoofable | `RateLimitFilter` | Fix when nginx deployed |
| PERF-01 | 8 separate DB queries for profile stats | `ProfileService` | Revisit at 1k+ daily users |
| ARCH-01 | `NotificationService` makes raw Telegram REST calls | `sendTelegramMessageWithButtons()` | Refactor to `TelegramNotificationPort` interface — bot module implements, service module depends on interface only |

---

## 11. Feature Backlog

| Priority | Feature |
|----------|---------| 
| 🟠 Medium | **Hub suggestion flow in bot** — TDD document complete (Session 7), implementation pending |
| 🟠 Medium | **Auto-accept toggle** — driver profile setting. High value for regular commuter pairs who prefer bot over PM. |
| 🟠 Medium | **Date picker UX** — replace MM/DD HH:MM free-text with quick-select day buttons (Today, Tomorrow, Next Monday, etc.) + separate time input. Reported by user on weekend posting confusion. |
| 🟠 Medium | **Rating comments display** — show saved comments on ratee's profile (driver and passenger reviews section) |
| 🟡 Low | **Emergency contact** — one field on user profile |
| 🟡 Low | **Carbon savings tracker** — based on completed rides |
| 🟡 Low | **Recurring ride / regular riders** |
| 🟡 Low | **Group/Event carpools** |
| 🟡 Low | **Corporate/workplace carpool codes** |

---

## 12. Session History

### Session 7 — 2026-05-03 to 2026-05-04 ✅ DEPLOYED

#### What We Did
1. **Group invite link** — shown prominently on terms screen and as dedicated join prompt after acceptance
2. **Hub suggestion TDD** — full Technical Design Document produced. Implementation deferred to future session.
3. **Bot handler refactor** — Command + Facade pattern:
   - `BotContext` value object, `BotCommand` interface
   - `BotFlowHelper` — single source of truth for shared flows
   - 5 focused sub-handlers: `PostRideHandler`, `BookingHandler`, `RideSearchHandler`, `DriverHandler`, `ProfileHandler`
   - `CallbackHandler` reduced from 2,328 lines to ~170 lines
   - `TelegramClientConfig` — proper Spring bean, eliminates lazy-init race condition
4. **Search time buffer fix** — custom time input searches `from - 1hr` to `from + 2hr`
5. **Time window overhaul** — 6 slots with full 24-hour coverage, no gaps
6. **Notes prompt UX** — realistic pickup/stop/drop-off example, max 1000 chars (V31)
7. **Booking message UX** — emphasizes importance, general example
8. **Group announcement** — notes moved to end of card
9. **Driver departed notification** — `RideDepartedEvent` fires when driver taps Start Ride
10. **Rating system** — mutual rating after completed rides (V32):
    - Stars (1–5) + optional comment (max 1000 chars)
    - Driver rates each passenger independently
    - Passenger rates driver once per ride
    - Average rating shown on ride card and profile
    - Rating prompt sent with Rate Now button
    - Favorite prompt shown to passenger only
11. **Favorite driver system** — save driver as favorite after rating, alert on ride post (V33)
12. **Multi-passenger rating fix** — per-ratee duplicate check, passenger selection screen
13. **Session recovery fix** — removed over-blocking for VIEW_RIDE, BOOK_RIDE, BOOK_NOW, CANCEL_BOOKING, DECLINE_BOOKING_REASON, SAVE_FAVORITE, SKIP_FAVORITE
14. **Schema fix** — `RideRating.stars` uses `columnDefinition = "TINYINT"`
15. **Scheduler event fix** — `expireStaleRides()` and `completeStaleRides()` now publish events — passengers notified on auto-depart and auto-complete, rating prompts triggered on auto-complete
16. **Timezone fix** — all `LocalDateTime.now()` in `RideService` use explicit `ZoneId.of("Asia/Manila")`
17. **Completion rate fix** — uses terminal rides only (completed + cancelled) as denominator
18. **Dead code cleanup** — removed unused `BOOKING_ACTIONS`, `APPROVAL_ACTIONS`, `CANCEL_ACTIONS` sets from `SessionRecoveryHandler`

#### Community Feedback Received
- User reported session expired on decline booking reason — fixed
- User reported 67% completion rate after auto-completed ride — fixed
- User reported auto-start/complete not notifying — fixed
- User feedback: regular commuters prefer PM over bot for established relationships — noted, auto-accept toggle added to backlog
- User requested date picker for easier ETD input — added to backlog

---

### Session 6 — 2026-05-02
1. Full project review — 15 bugs/security/perf/maintainability issues found and fixed
2. Added `ADMIN` role to `UserRole` enum + V27 Flyway migration
3. Configured production-ready `RestClient` with Apache HttpClient 5
4. Added `profileStats` cache and `adminStats` cache
5. Fixed `SessionRecoveryHandler` — removed `CONFIRM_CANCEL_RIDE` from flow-sensitive
6. Fixed `User.canDrive()` — added `ADMIN` role check
7. Fixed `BookingService` — block booking if passenger has active ride as driver
8. Set up local dev environment with separate dev bot
9. Deployed to production

### Sessions 1–5
- Hub alias search improvements (V28–V30 migrations, 12 new Parañaque Sucat hubs)
- Direction-based group topics
- Gas share hidden from group announcement
- Dynamic date/time examples in bot prompts
- `ACCEPT_BOOKING` / `DECLINE_BOOKING` removed from `APPROVAL_ACTIONS`
- Initial scaffold, auth, ride posting, booking, notifications, schedulers

---

## 13. How to Use This File

At the start of a new Claude session:
1. Upload this `SESSION_CONTEXT.md` file
2. Say: _"Continue Car-e-Pool development. Here is my context file."_
3. Claude will be instantly up to speed

Update **Section 12** at the end of each session with what was done.
Update **Section 11** when features are completed or added.
Update **Section 4** when new Flyway migrations are added.
Update **Section 7** when new bot flows or state changes are made.
