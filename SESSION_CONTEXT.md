# 🚗 Car-e-Pool PH — Session Context

> **Purpose:** Load this file at the start of every new Claude session for instant project context.
> **Last Updated:** 2026-05-02
> **Current Session:** 6

---

## 1. Project Overview

### What It Is
A Telegram-based carpooling platform for South Metro Manila ↔ BGC/Makati commuters.
Community-driven, peer-to-peer, gas cost sharing model.

### Bot
- **Prod:** `@car_e_poolPH_bot`
- **Dev:** `@car_e_poolPH_dev_bot`
- **Admin Telegram ID:** `208038458`

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

### Flyway Migrations (Latest: V27)
```
V1–V20   — initial schema, hubs, waypoints, notifications
V21      — add notes to rides
V22      — add reminder_count to bookings
V23      — add pending booking expiry
V24      — add announce_count to rides
V25      — fix announce_count type to INT
V26      — add deleted + deleted_at to users
V27      — ALTER users.role ENUM to include ADMIN
```

### Important Indexes
- `rides`: composite index on `(driver_id, status)`
- `bookings`: composite index on `(passenger_id, status)`, `(ride_id, status)`
- `tb_history_*`: `INSERT IGNORE` / `ON DUPLICATE KEY UPDATE` with composite unique indexes

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
POST_RIDE  → direction → origin hub → dest hub → ETD → seats →
             contribution → notes → vehicle confirm → confirm post

FIND_RIDE  → direction → time window → filter → view ride → book

BOOKING    → driver receives notification → accept/decline →
             passenger notified → ride departs → completed
```

### Session Recovery
- `SessionRecoveryHandler` handles expired `UserState`
- Flow-sensitive actions (need state): `POST_RIDE_ACTIONS`, `BOOKING_ACTIONS`, `APPROVAL_ACTIONS`, `CANCEL_ACTIONS`
- `CONFIRM_CANCEL_RIDE` — **NOT** flow-sensitive (carries rideId+reason in payload)
- `CANCEL_BOOKING_REASON` — flow-sensitive

---

## 8. Deployment

### Server
- **Provider:** AWS (EC2 + RDS)
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
- [ ] Ride search works
- [ ] Admin stats visible in profile
- [ ] Notifications sending
- [ ] Flyway ran V27 successfully
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

## 11. Feature Backlog (Not Yet Implemented)

| Priority | Feature |
|----------|---------|
| 🔴 High | **Ratings system** — trust is the product, 87% trust correlation |
| 🟠 Medium | **Hub suggestion flow in bot** — API exists, bot UI missing |
| 🟠 Medium | **Auto-accept toggle** — driver profile setting |
| 🟡 Low | **Emergency contact** — one field on user profile |
| 🟡 Low | **Carbon savings tracker** — based on completed rides |
| 🟡 Low | **Recurring ride / regular riders** |
| 🟡 Low | **Group/Event carpools** |
| 🟡 Low | **Corporate/workplace carpool codes** |

---

## 12. Last Session Summary (Session 6 — 2026-05-02)

### What We Did
1. Full project review — 15 bugs/security/perf/maintainability issues found
2. Fixed all 15 issues across 3 priority rounds
3. Added `ADMIN` role to `UserRole` enum + V27 Flyway migration
4. Configured production-ready `RestClient` with Apache HttpClient 5
5. Added `profileStats` cache and `adminStats` cache
6. Fixed `SessionRecoveryHandler` — removed `CONFIRM_CANCEL_RIDE` from flow-sensitive
7. Fixed `User.canDrive()` — added `ADMIN` role check
8. Fixed `BookingService` — block booking if passenger has active ride as driver
9. Set up local dev environment with separate dev bot
10. Deployed to production successfully

### Files Changed This Session
```
carpool-domain/entity/User.java
carpool-domain/enums/UserRole.java
carpool-repository/BookingRepository.java
carpool-service/booking/BookingService.java
carpool-service/ride/RideService.java
carpool-service/user/UserService.java
carpool-service/notification/NotificationService.java
carpool-service/profile/ProfileService.java
carpool-service/admin/AdminStatsService.java
carpool-service/auth/TelegramAuthService.java
carpool-service/scheduler/PendingBookingScheduler.java
carpool-service/scheduler/RideExpiryScheduler.java
carpool-web/config/CacheConfig.java
carpool-web/config/WebClientConfig.java
carpool-web/config/SecurityConfig.java
carpool-web/controller/RideController.java
carpool-web/controller/UserController.java
carpool-web/filter/RateLimitFilter.java
carpool-web/filter/JwtAuthFilter.java
carpool-web/security/AuthenticatedUser.java
carpool-bot/handler/CallbackHandler.java
carpool-bot/handler/MessageHandler.java
carpool-bot/handler/SessionRecoveryHandler.java
carpool-bot/util/BotMessageBuilder.java
carpool-common/util/HtmlEscapeUtil.java
db/migration/V27__add_admin_role.sql
application-prod.properties
```

---

## 13. How to Use This File

At the start of a new Claude session:
1. Upload this `SESSION_CONTEXT.md` file
2. Say: _"Continue Car-e-Pool development. Here is my context file."_
3. Claude will be instantly up to speed

Update **Section 12** at the end of each session with what was done.
Update **Section 11** when features are completed or added.
Update **Section 4** when new Flyway migrations are added.
