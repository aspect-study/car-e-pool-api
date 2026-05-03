# 🚗 Car-e-Pool PH — Session Context

> **Purpose:** Load this file at the start of every new Claude session for instant project context.
> **Last Updated:** 2026-05-03
> **Current Session:** 7

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
- **Single admin stats source** — `AdminStatsService.getStats()` used by both bot and REST API via `ProfileService.getAdminStats()`
- **Bot state machine** — Caffeine-backed `StateManager` with `UserState` per chatId
- **Rate limiting** — Caffeine cache (1hr TTL, 100k max) replacing old `ConcurrentHashMap`
- **Role system** — `UserRole` enum: `PASSENGER`, `DRIVER`, `BOTH`, `ADMIN`
- **Re-announce** — max 3 times per ride, `announce_count` column on rides table
- **Pending booking window** — 60 minutes, reminders at 15/30/45 min, auto-decline at 60
- **Command pattern** — `BotCommand` interface + `@PostConstruct` Map registry in `CallbackHandler` — adding a new callback action = one `commands.put()` line, router never changes
- **Facade pattern** — sub-handlers group domain logic (`PostRideHandler`, `BookingHandler`, `RideSearchHandler`, `DriverHandler`, `ProfileHandler`)
- **Value object** — `BotContext` record replaces scattered `(chatId, carpoolUserId, telegramId, state, parts, bot)` parameter lists
- **Shared helper** — `BotFlowHelper` is the single source of truth for `showMainMenu`, `askForTimeWindow`, `etdExample`, `sendWithInline`, `buildFilterSummary`, `buildTimeContext`, `handleDirectionSelected`

### Bot Handler Architecture (Session 7 Refactor)
```
CallbackHandler  — thin router only (Map registry dispatch)
MessageHandler   — thin router only (flow switch dispatch)
BotFlowHelper    — shared stateful flows (no handler dependencies)
PostRideHandler  — post ride sub-flow
BookingHandler   — passenger booking sub-flow
RideSearchHandler— search, filter, pagination
DriverHandler    — driver ride management
ProfileHandler   — profile, vehicle, terms, admin, onboarding
BotContext       — value object (replaces scattered params)
BotCommand       — @FunctionalInterface for Command pattern
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
- `@Async` → new thread, decoupled from caller
- `AFTER_COMMIT` → only fires after successful commit
- `REQUIRES_NEW` → fresh transaction for notification DB writes

---

## 3. Domain Model

### Key Entities
```
User        — telegram_id, full_name, telegram_handle, role, status, deleted
Ride        — driver, origin_hub, destination_hub, direction, departure_time,
              available_seats, total_seats, contribution_amount, status, announce_count
Booking     — ride, passenger, seats_reserved, status, expires_at, reminder_count
Hub         — name, area, type (PREDEFINED/USER_SUGGESTED), approved
RideWaypoint — ride, hub, type (PICKUP/DROPOFF)
Notification — user, type, message, sent_at
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

### Flyway Migrations (Latest: V30)
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
V30      — add additional hub aliases (ups5, upsv, valley 1, jaka plaza, yp mall, etc.)
```

### Important Indexes
- `rides`: composite index on `(driver_id, status)`
- `bookings`: composite index on `(passenger_id, status)`, `(ride_id, status)`

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
- Fix: use `X-Real-IP` set by nginx, fall back to `getRemoteAddr()`

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
- Flow-sensitive actions (need state): `POST_RIDE_ACTIONS`, `BOOKING_ACTIONS`, `APPROVAL_ACTIONS`, `CANCEL_ACTIONS`
- `CONFIRM_CANCEL_RIDE` — **NOT** flow-sensitive (carries rideId+reason in payload)
- `ACCEPT_BOOKING` / `DECLINE_BOOKING` — **NOT** flow-sensitive (carry bookingId in payload)
- `CANCEL_BOOKING_REASON` — flow-sensitive

### Hub Search (HubMatcher)
- 5-layer matching: alias exact → alias fuzzy → name exact → name fuzzy → area
- `suggest()` alias check as Layer 1 via cached `HubService.findByAlias()`
- 2-column button layout for hub names ≤20 chars, 1-column for longer
- MAX_SUGGESTIONS = 20

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
- [ ] Bot responds to `/start`
- [ ] Ride search works — all time slots return results
- [ ] Admin stats visible in profile
- [ ] Notifications sending
- [ ] Flyway V1–V30 all applied
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

---

## 11. Feature Backlog

| Priority | Feature |
|----------|---------|
| 🔴 High | **Ratings system** — trust is the product, 87% trust correlation |
| 🟠 Medium | **Hub suggestion flow in bot** — TDD document complete (Session 7), implementation pending |
| 🟠 Medium | **Auto-accept toggle** — driver profile setting |
| 🟡 Low | **Emergency contact** — one field on user profile |
| 🟡 Low | **Carbon savings tracker** — based on completed rides |
| 🟡 Low | **Recurring ride / regular riders** |
| 🟡 Low | **Group/Event carpools** |
| 🟡 Low | **Corporate/workplace carpool codes** |

---

## 12. Session History

### Session 7 — 2026-05-03

#### What We Did
1. **Group invite link feature** — community group link prominently shown on terms screen and as dedicated join prompt after acceptance. `groupInviteLink` added to `BotConfig`, `urlButton()` added to `BotMessageBuilder`.
2. **Hub suggestion TDD** — full Technical Design Document produced for the hub suggestion bot flow. Implementation deferred — document saved for study.
3. **Bot handler refactor** — major architectural overhaul of `CallbackHandler` and `MessageHandler`:
   - Command pattern via `@PostConstruct` Map registry — router never changes again
   - Facade pattern via 5 focused sub-handlers
   - `BotContext` value object eliminates scattered parameter lists
   - `BotFlowHelper` as single source of truth for shared flows
   - Eliminated 7 duplicated methods between the two handlers
   - `CallbackHandler` reduced from 2,328 lines to 167 lines
4. **Search time buffer fix** — custom time input now searches `from - 1hr` to `from + 2hr` — fixes bug where rides departing slightly before typed time were missed.
5. **Time window overhaul** — 6 slots with full 24-hour coverage, no gaps:
   - Early Morning (4-6 AM), Morning Rush (6-9 AM), Late Morning (9 AM-12 PM)
   - Noon (12-3 PM), Afternoon (3-7 PM), Evening (7-11 PM)
6. **Notes prompt UX** — updated driver notes prompt with realistic pickup/stop/drop-off example. Updated `NOTE_WRITE` prompt for consistency.
7. **Booking message UX** — updated passenger booking message prompt to emphasize importance and give general example.
8. **Deployed to production** — all changes live on EC2, Flyway V1–V30 applied.

#### Files Changed
```
application-local.properties
application-prod.properties
carpool-bot/config/BotConfig.java
carpool-bot/util/BotMessageBuilder.java
carpool-bot/handler/BotContext.java           (NEW)
carpool-bot/handler/BotCommand.java           (NEW)
carpool-bot/handler/BotFlowHelper.java        (NEW)
carpool-bot/handler/PostRideHandler.java      (NEW)
carpool-bot/handler/BookingHandler.java       (NEW)
carpool-bot/handler/RideSearchHandler.java    (NEW)
carpool-bot/handler/DriverHandler.java        (NEW)
carpool-bot/handler/ProfileHandler.java       (NEW)
carpool-bot/handler/CallbackHandler.java      (REPLACED)
carpool-bot/handler/MessageHandler.java       (REPLACED)
carpool-bot/handler/PostRideHelper.java       (UPDATED)
```

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

### Sessions 1–5 (earlier work — pre-context-doc)
- Hub alias search improvements (V28–V30 migrations, 12 new Parañaque Sucat hubs)
- Direction-based group topics (`groupHomeToWorkTopicId` / `groupWorkToHomeTopicId`)
- Gas share hidden from group announcement
- Dynamic date/time examples in bot prompts
- `ACCEPT_BOOKING` / `DECLINE_BOOKING` removed from `APPROVAL_ACTIONS` in `SessionRecoveryHandler`
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
