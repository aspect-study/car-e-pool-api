# TODOS

Technical debt and deferred product work. Each item includes enough context
to pick it up in 3 months without re-reading old conversations.

---

## P1 — High priority

### Passenger-initiated ride requests (REQUEST_RIDE flow)
**What:** New bot flow where a passenger posts "I need a ride to [hub] by [time]."
The system sends the request to active drivers on the same corridor.
Drivers see pending passenger requests on their main menu and can accept with one tap.
**Why:** Currently passengers are entirely reactive — they can only book existing driver-posted rides.
This closes the biggest product gap: days with no driver posts leave passengers with nothing.
**Context:** Requires a new `RideRequest` entity (not to be confused with `Booking`),
a new bot flow (direction → hub → time → publish), a new `RideRequestPostedEvent`,
and a new "Pending Passenger Requests" section on the driver main menu.
The existing `BookingService` and `RideService` patterns apply directly.
`SessionRecoveryHandler` will need new flow-sensitive actions for the request flow.
**Effort:** Human ~3 days / CC ~45 min.
**Priority:** P1 — highest product value unbuilt.

---

## P2 — Medium priority

### Micrometer metrics instrumentation
**What:** Add Spring Boot Actuator + Micrometer. Instrument: ride posting rate,
booking acceptance/decline rate, Telegram API error rate, notification failure rate,
rate limit hit frequency per chatId.
**Why:** Production visibility is log-only today. The first sign of a problem is a user complaint.
**Context:** Spring Boot Actuator is a one-dependency add. `@Counted` / `@Timed` on
`RideService.createRide()`, `BookingService.createBooking()`, `BookingService.respondToBooking()`,
and `TelegramNotificationAdapter.sendMessage()`. Needs a Prometheus scrape target or push gateway.
**Effort:** Human ~1 day / CC ~15 min.
**Priority:** P2.

### Handler decomposition — PostRideHandler + ProfileHandler
**What:** Extract `PostRideHandler` (994 LOC) into `PostRideHandler` (direction/date/hub only)
and `PostRideConfirmationHandler` (vehicle select, confirmation, repost).
Extract `ProfileHandler` (898 LOC) into `ProfileHandler` (display),
`VehicleManagementHandler`, and `AdminPanelHandler`.
**Why:** Each handler is a God class — adding a new conversation step requires reading the whole file.
**Context:** The only real risk is missing a `commands.put()` registration in `CallbackHandler`
after the move. Write the extraction test-first: grep all callback keys in the old handler,
verify all are re-registered in `CallbackHandler` after extraction.
`SessionRecoveryHandler.isFlowSensitive()` may also need updating.
**Effort:** Human ~1 day / CC ~15 min. Pure move, no behavior change.
**Trigger:** Build before adding the next major new flow to either handler.
**Priority:** P2.

### Multi-corridor support
**What:** Generalize `RideDirection` from a two-value enum to a DB-backed `Corridor` entity
(name, home-to-work topic ID, work-to-home topic ID, departure window).
Admins add corridors via the bot. Rides reference a `Corridor` instead of a hardcoded direction.
**Why:** Every feature built against the hardcoded enum makes this refactor more expensive later.
**Context:** This is a significant schema migration. All queries referencing `RideDirection`
(RideRepository, RideService, GroupNotificationService topic selection) need updating.
BotConfig's `GROUP_HOME_TO_WORK_TOPIC_ID` / `GROUP_WORK_TO_HOME_TOPIC_ID` become
per-corridor DB fields. The `PostRideHandler` direction-select step becomes a corridor-select step.
**Effort:** Human ~4 days / CC ~1.5 hours.
**Trigger:** Build before adding the second corridor.
**Priority:** P2.

### Driver weekly earnings summary
**What:** A Monday 6am Asia/Manila scheduler that queries completed bookings by driver
for the past 7 days, computes total contribution received, and sends a summary DM:
"You completed 4 rides, carried 9 passengers, earned ₱1,800 in gas share."
**Why:** Drivers have no visibility into cumulative earnings. Closing this loop
makes driving feel rewarding and improves driver retention.
**Context:** No new DB tables — pure query on `bookings` (status=COMPLETED)
joined to `rides` (driver=userId). Use `@Scheduled(cron = "0 0 6 * * MON",
zone = "Asia/Manila")`. Use `fixedDelay` pattern with an `initialDelay` staggered
from existing schedulers. Add to `carpool-service` scheduler package.
Send via `TelegramNotificationPort` (once the TelegramPort refactor ships).
**Effort:** Human ~3 hours / CC ~10 min.
**Priority:** P2.

---

## P3 — Lower priority

### Web admin dashboard
**What:** A simple read-only + moderation web UI at `/admin` —
hub approvals with context, ride volume by corridor, top drivers, flagged users.
**Why:** Bot-only admin is a ceiling. At ~500+ active users, admin work needs
bulk operations, audit logs, and multi-person access.
**Context:** REST controllers already exist in `carpool-web`. The admin UI
consumes existing endpoints. Spring MVC with Thymeleaf templates is the path
of least resistance (no separate frontend build). Add `ADMIN` role gate on all
`/admin/**` paths in `SecurityConfig`.
**Effort:** Human ~3-4 days / CC ~1 hour.
**Trigger:** Build when bot admin panel becomes a bottleneck (~500 active users).
**Priority:** P3.

### Extract TIME_FMT formatter constant
**What:** `DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a").withZone(ZoneId.of("Asia/Manila"))`
is defined identically in `NotificationService`, `GroupNotificationService`, and `BotMessageBuilder`.
Extract to a `CarpoolTimeFormat` constant in `carpool-common`.
**Why:** When the format changes, all three files need updating — the third will inevitably be missed.
**Context:** After the TelegramPort refactor ships, `NotificationService` will be cleaner.
This is a safe 1-file-in carpool-common + 3-file import update.
**Effort:** Human ~15 min / CC ~2 min.
**Priority:** P3.

### Notification follower DM batching
**What:** `GroupNotificationService.onRidePosted()` sends one Telegram API call per follower
sequentially on the async event thread. For a driver with 100 followers, this is 100 sequential
HTTP calls before the thread completes.
**Why:** No functional bug today at low follower counts, but the latency scales linearly.
**Context:** Telegram does not support bulk sends. Mitigation: parallel virtual thread dispatch
per follower (each send in its own `CompletableFuture`), with a semaphore cap (e.g., 10
concurrent) to avoid exhausting the connection pool.
**Effort:** Human ~2 hours / CC ~10 min.
**Trigger:** Build when a driver accumulates 50+ followers.
**Priority:** P3.
