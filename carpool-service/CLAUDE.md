# carpool-service — Module Guide

Business logic, DTOs, MapStruct mapper, event system, schedulers, and AI service. Depended on by `carpool-bot` and `carpool-admin`.

## Event-Driven Notifications

Services publish `RideEvents.*` records via `ApplicationEventPublisher`. `NotificationService` listens with `@Async + @TransactionalEventListener(AFTER_COMMIT) + @Transactional(REQUIRES_NEW)` — notifications only fire after the outer transaction commits, run in a virtual thread, and write to the `notifications` table with PENDING → SENT/FAILED status.

**`sendAndRecord` overloads:** The private `sendAndRecord(User, String, String, Map)` delegates to `sendAndRecord(User, String, String, Map, List<List<InlineButton>>)`. The overload with keyboard calls `telegramPort.sendMessageWithKeyboard()` when keyboard is non-null/non-empty, and `telegramPort.sendMessage()` otherwise. Both persist the notification record with PENDING → SENT/FAILED status. Use the keyboard overload when a notification needs actionable inline buttons without splitting into two separate DMs.

**`onBookingConfirmed`** uses the keyboard overload to attach a `📋 View My Booking → VIEW_BOOKING:{bookingId}` inline button to the passenger's confirmation DM.

**`onBookingReminder`** uses the keyboard overload to send the driver Accept/Decline/Menu buttons with each reminder DM. Failure is tracked as `NotificationStatus.FAILED` via `sendAndRecord` (previously the status was always recorded as SENT even on Telegram failure).

**`onRideTimeChanged`** listens for `RideTimeChangedEvent` (`@Async + @TransactionalEventListener(AFTER_COMMIT) + @Transactional(REQUIRES_NEW)`). Fetches all confirmed bookings for the ride; returns early if none. Formats new departure time as `"EEE, MMM d 'at' h:mm a"` (e.g. "Thu, May 23 at 7:30 AM"). Sends each confirmed passenger a DM with `✅ Keep Booking → KEEP_BOOKING:{bookingId}` and `❌ Cancel Booking → CANCEL_BOOKING:{bookingId}` inline buttons via the keyboard overload of `sendAndRecord`. Persists each notification with `NotificationTypes.RIDE_TIME_CHANGED` and PENDING → SENT/FAILED status.

**Direction label in all notifications:** All 18 DM notifications (9 driver + 9 passenger) include a direction line (`🏠 Home → Work` or `🏢 Work → Home`) on the line immediately after the notification title. The private static helper `directionLabel(RideDirection direction)` in `NotificationService` is the single source of truth — returns `"🏠 Home → Work"`, `"🏢 Work → Home"`, or `"📍 Other"` (also null-safe). Never use `RideDirection.label()` in notification messages — it returns machine-readable lowercase strings, not display text.

## Multi-Vehicle Management

`Vehicle` is a domain entity (soft-delete via `deletedAt`) with fields `user (FK LAZY)`, `plateNumber`, `model`, `color`, `seatCapacity (Integer)`. `VehicleRepository` exposes `findByUserIdAndDeletedAtIsNullOrderByCreatedAtAsc`, `findActiveByPlateForOtherUser`, and `existsByUserIdAndDeletedAtIsNull`.

`VehicleService.addVehicle()` enforces three rules: (1) plate uniqueness across all active vehicles belonging to other users — throws if occupied; (2) replace-oldest policy — if the user already has 3 active vehicles, the oldest (by `createdAt`) is soft-deleted before saving the new one; (3) after saving, the User entity's legacy `carModel`, `plateNumber`, `carColor`, and `carSeatCapacity` fields are synced to the newest active vehicle for backward compatibility with any code still reading those fields.

`removeVehicle(vehicleId, userId)` verifies ownership then sets `deletedAt`. Throws `NotRideOwnerException` (403) — not `InvalidRideStateException` — when the caller does not own the vehicle. Throws `ResourceNotFoundException` (404) when the vehicle ID does not exist. `getActiveVehiclesForUser(userId)` returns `List<VehicleResponse>` ordered oldest-first (matches selection order in bot UX).

DB migrations: V39 creates `vehicles` with soft-delete and FK to `users (ON DELETE CASCADE)`; V40 adds `vehicle_id` FK (nullable, `ON DELETE SET NULL`) to `rides`; V41 migrates existing user vehicle data from `users` columns into `vehicles`; V42 widens `seat_capacity` from `TINYINT` to `INT` (Hibernate schema validation requires `Types#INTEGER` for Java `Integer`).

`EntityMapper` has `VehicleResponse toVehicleResponse(Vehicle vehicle)`. `RideResponse` includes a `VehicleResponse vehicle` field (may be null for rides posted before V39). `GroupNotificationService` and `BotMessageBuilder` both fall back to `ride.getDriver()` legacy fields when `ride.getVehicle()` is null.

## Group Announcement Lifecycle

`GroupNotificationService.onRidePosted()` posts a ride announcement to the configured Telegram group topic and stores the returned Telegram message ID in `Ride.groupMessageId` (added by V37 migration, column `group_message_id`). V44 adds a companion column `group_message_posted_at TIMESTAMP NULL` (indexed) that records exactly when the message was posted to Telegram — distinct from `createdAt` (when the ride was created).

**`persistGroupMessageId(Long rideId, Integer messageId)`** is a private helper that sets both `Ride.groupMessageId` and `Ride.groupMessagePostedAt(Instant.now())` atomically in a single isolated try/catch save. All three previous inline save blocks in `onRidePosted`, `refreshGroupPostAfterSeatFreed`, and `onBookingConfirmed` now delegate to this helper — ensures the two fields are always written together.

**Re-announce:** `RideService.reannounceRide()` increments `Ride.announceCount` (max 10 total) and re-fires `RidePostedEvent`. `onRidePosted` detects a non-null `groupMessageId` and deletes the old message first (isolated in its own try/catch — a Telegram failure logs a warning but does not abort the new post). Follower alerts are suppressed on re-announces: the loop is guarded by `announceCount <= 1` so followers receive only one DM per ride regardless of how many times the driver re-announces.

**0-seats FULL handling:** When `onRidePosted` fires for a re-announce and `ride.getAvailableSeats() == 0`, the old group post has already been deleted. The method then detects the zero seat count, clears `Ride.groupMessageId` to `null` in DB (isolated try/catch), logs `"Ride is FULL — group announcement removed, not reposted"`, and returns early — no new announcement is posted.

**Time-change refresh:** `GroupNotificationService.onRideTimeChanged()` (`@Async + @TransactionalEventListener(AFTER_COMMIT) + @Transactional(REQUIRES_NEW)`) deletes the old group post and reposts a fresh announcement with the updated departure time. No 48-hour guard is applied (time changes are always high-signal). Returns early if `groupMessageId` is null or ride is not ACTIVE/FULL. Calls `persistGroupMessageId` on success.

**Seat-freed auto-refresh:** `GroupNotificationService` listens for `BookingCancelledByDriverEvent`, `BookingCancelledByPassengerEvent`, `BookingAutoSyncedEvent`, and `BookingDeclinedEvent`. All four call `refreshGroupPostAfterSeatFreed(rideId, reason)`. That private method: (1) loads the ride; (2) skips if ride is not ACTIVE/FULL or the ride is older than 48h; (3) if `groupMessageId` is non-null, deletes the old group post (isolated try/catch — a FULL→ACTIVE re-open may have already cleared it to null); (4) reposts a fresh announcement via `sendToGroup`; (5) calls `persistGroupMessageId` to save the new message ID and timestamp. The null-groupMessageId path handles the case where a ride was FULL (announcement deleted) and then a seat is freed — the method posts a new announcement instead of refreshing an existing one.

**Proactive stale refresh:** `refreshGroupAnnouncementForRide(Long rideId)` is a `public @Async @Transactional(REQUIRES_NEW)` method called by `StaleAnnouncementRefreshScheduler`. It performs the same delete-then-repost logic as `refreshGroupPostAfterSeatFreed` but without the 48h guard — it is exclusively called by the scheduler which has already filtered rides by `groupMessagePostedAt`. On success it calls `persistGroupMessageId`, resetting the 48h clock.

When a ride is departed, completed, or cancelled, `GroupNotificationService` listens for `RideDepartedEvent`, `RideCompletedEvent`, and `RideCancelledEvent` (all `@Async + @TransactionalEventListener(AFTER_COMMIT)`) and calls `CarpoolBot.deleteMessage()` to remove the announcement. Deletion is skipped if `groupMessageId` is null or if the ride was created more than 48 hours ago. When the guard triggers, it logs `"48h guard triggered — stale announcement scheduler may have been down: rideId={}"`.

**Follower DM dispatch:** The follower alert loop in `onRidePosted()` uses parallel virtual thread dispatch — each follower DM runs in its own virtual thread via `Executors.newVirtualThreadPerTaskExecutor()` (try-with-resources). A `Semaphore(10)` caps concurrent Telegram API calls to respect rate limits. `CompletableFuture.allOf(...).join()` waits for all sends before the method returns.

**Booking-acceptance auto-refresh:** `GroupNotificationService.onBookingConfirmed(BookingConfirmedEvent)` listens `@Async + @TransactionalEventListener(AFTER_COMMIT) + @Transactional(REQUIRES_NEW)`. When a driver accepts a booking it deletes the old group post and reposts a fresh announcement reflecting the updated available seat count. If `ride.getAvailableSeats() == 0` the old post is simply deleted. This listener does **not** increment `announceCount` and does **not** trigger follower DMs.

For the bot-side CarpoolBot methods (sendToGroup, sendToUser, group buttons, handleFollowAndViewRide), see `carpool-bot/CLAUDE.md`.

## Booking: Remove Passenger & Pending Auto-sync

**Remove Passenger:** `BookingService` cancels the booking with status `CANCELLED_BY_DRIVER`, restores `ride.availableSeats`, transitions the ride from FULL back to ACTIVE if needed, notifies the removed passenger, and publishes `BookingCancelledByDriverEvent`. `GroupNotificationService.onBookingCancelledByDriver()` picks this up and calls `refreshGroupPostAfterSeatFreed`.

**Auto-sync on acceptance (passenger's other rides):** When a driver accepts a booking, `BookingService.acceptBooking()` auto-cancels the same passenger's PENDING bookings on **other rides** (not on the same ride). Each auto-cancelled booking fires `BookingAutoSyncedEvent`. `GroupNotificationService.onBookingAutoSynced()` calls `refreshGroupPostAfterSeatFreed` on the other ride, refreshing its group announcement to reflect the freed seat. If that other ride was FULL and its announcement was already deleted, `refreshGroupPostAfterSeatFreed` posts a fresh announcement.

**Ride-scoped pending requests (for drivers with multiple active rides):** `getPendingRequestsForRide(rideId, driverUserId)` and `countPendingRequestsForRide(rideId)` complement the existing driver-wide `getPendingRequestsForDriver` / `countPendingRequestsForDriver`. `BookingRepository.findPendingByRideId` does `JOIN FETCH b.passenger`, `JOIN FETCH b.ride r`, and `JOIN FETCH r.driver` — the latter two are required so the service-layer `.filter(b -> b.getRide().getDriver().getId().equals(driverUserId))` ownership check doesn't throw `LazyInitializationException` outside the transaction. `countPendingByRideId` is a plain `COUNT` query, no fetch joins needed.

## Direction-Scoped Conflict Checks

Conflict checks are direction-scoped — a user can drive HOME_TO_WORK and hold a WORK_TO_HOME passenger booking at the same time. `RideDirection.label()` returns a human-readable string (`"home-to-work"`, `"work-to-home"`, `"other"`) used in all error messages.

**`RideService.hasActiveRide(Long driverId, RideDirection direction)` — public helper:**
Single source of truth for the active-ride guard. Calls `rideRepository.existsByDriverIdAndDirectionAndStatusIn(driverId, direction, [ACTIVE, FULL])`. Used by `createRide()`, `updateRideStatus()`, and bot-side handlers — never duplicated inline.

**`RideService.createRide()` guards (before inserting the ride):**
- `hasActiveRide(driverId, direction)` — throws `InvalidRideStateException` if driver already has an active same-direction ride.
- `bookingRepository.existsByPassengerIdAndRide_DirectionAndStatusIn(driverId, direction, [CONFIRMED, PENDING])` — throws `InvalidRideStateException` if the same user has an active same-direction passenger booking.

**`RideService.updateRideStatus()` guard (DRAFT → ACTIVE transition):**
Before `rideRepository.save()`, checks `hasActiveRide(requestingUserId, ride.getDirection())`. The guard runs while the ride is still DRAFT so the query does not find the current ride. Throws `InvalidRideStateException` if another ACTIVE/FULL ride exists in the same direction. Closes the gap where two DRAFT rides created via the API (or a double-tap race) could both be published.

**`BookingService.createBooking()` step 5b:**
- `rideRepository.existsByDriverIdAndDirectionAndStatusIn(passengerId, direction, [ACTIVE, FULL, DEPARTED])` — throws `InvalidRideStateException` if the passenger has a same-direction driver ride in progress. DEPARTED is included here (not in `createRide`) because a departed ride is actively running.

The bot-side checks in `PostRideHandler`, `RideSearchHandler`, and `MessageHandler` are early-warning UX that fire before the user fills a form. The service layer is the authoritative gate and must remain consistent with it.

## Update Departure Time

`RideService.updateDepartureTime(rideId, newDepartureTime, callerId)` allows a driver to reschedule an active ride:
- Acquires `SELECT FOR UPDATE` via `rideRepository.findByIdWithLock(rideId)` to prevent concurrent conflicts.
- Validates: caller is ride owner (`NotRideOwnerException`); ride status is ACTIVE or FULL (`InvalidRideStateException`); new time ≠ current time (`InvalidRideStateException`); new time is ≥ 15 minutes from now (`InvalidRideStateException`).
- Updates `ride.departureTime`, saves, then publishes `RideEvents.RideTimeChangedEvent(saved)`.
- `RideTimeChangedEvent` is handled by `NotificationService.onRideTimeChanged` (passenger DMs with Keep/Cancel buttons) and `GroupNotificationService.onRideTimeChanged` (group post refresh).
- `NotificationTypes.RIDE_TIME_CHANGED` constant used for the notification type field.

## Booking: Pessimistic Locking

`BookingService.createBooking()` acquires `SELECT FOR UPDATE` on the ride row (`RideRepository.findByIdWithLock()`) to prevent double-booking the last seat. The lock is held for the full transaction duration.

## Passenger Mini-Profile Badge

Badge is built by `ProfileBadgeBuilder.buildPassengerBadge(ProfileStatsResponse stats, String ratingLabel)` in `carpool-service/src/main/java/com/carpool/service/util/ProfileBadgeBuilder.java`. This is the single source of truth — accessible from both `NotificationService` (same module) and `BotMessageBuilder` (which exposes a static delegate method that carpool-bot callers use).

**Badge format:**
- New member (roleLabel starts with "👋"): `👋 New Member | Since Apr 2025`
- Passenger with rides: `✅ 🧳 Passenger | 12 rides done | Since Jan 2025`
- Passenger no rides yet: `✅ 🧳 Passenger | Since Jan 2025`
- With rating: badge + `\n⭐ 4.8 (9 ratings)`

Badge is sent in **Booking request notification** (`NotificationService.onBookingRequested`) — built before sending the DM and appended after the passenger name line.

**Best-effort pattern:** Profile lookup is wrapped in a separate inner try-catch. A failure logs a warning and defaults `badge` to `""` — never blocks the notification.

**No circular dependency risk:** `ProfileService` and `RatingService` do not inject `NotificationService` (verified by grep). Both are safe to add as `final` fields via `@RequiredArgsConstructor`.

## Vehicle Plate Privacy — Service Side

Plate numbers are withheld from public-facing surfaces. They are only disclosed to passengers with a confirmed booking.

**Hidden:**
- `GroupNotificationService.buildRidePostedMessage()` — group announcement shows `🚘 White Toyota Vios` without plate

**Revealed to confirmed passengers only:**
- `NotificationService.buildPassengerConfirmationMessage()` — booking confirmation DM includes `🚘 White Toyota Vios | 🔢 ABC 1234`
- `NotificationService.onRideDeparted()` and `onRideDepartureReminder()` — departure notification and 30-min reminder already reach only confirmed passengers

Both Entity and legacy fallback handled: each location checks `ride.getVehicle()` first, then falls back to `ride.getDriver().getCarModel()` legacy fields.

**REST-side rule:** `RatingResponse` uses `UserSummaryResponse` (no vehicle fields) for `rater` and `ratee`. `VehicleResponse` (with `plateNumber`) is only returned from `/me/vehicles` — the owner's own data.

For bot-side enforcement (BotMessageBuilder, BookingHandler), see `carpool-bot/CLAUDE.md`.

## DTOs & Mapping

All entity→DTO mapping uses a single `EntityMapper` (MapStruct, compile-time generated, Spring bean). DTOs are Java records in `carpool-service/src/main/java/com/carpool/service/dto/`.

**Response DTOs:**
- `UserResponse` — full user profile including `plateNumber`, `carModel`, `carColor`. Only for own-data endpoints (`/me`).
- `UserSummaryResponse(Long id, String fullName, String telegramHandle, Double avgRating)` — plate-privacy DTO for third-party user fields. Used in `RatingResponse.rater` and `RatingResponse.ratee`. Never includes vehicle fields.
- `VehicleResponse(Long id, String model, String color, String plateNumber, Integer seatCapacity)` — only returned from own-data endpoints (`/me/vehicles`).
- `RatingResponse(Long id, Long rideId, UserSummaryResponse rater, UserSummaryResponse ratee, int stars, String comment, String raterRole, Instant createdAt)` — public endpoint; uses `UserSummaryResponse` not `UserResponse`.
- `RatingEligibilityResponse(boolean canRate, List<Long> rateeIds)` — returned from `GET /rides/{rideId}/ratings/eligibility`.
- `FollowerResponse(Long userId, String fullName, String telegramHandle, LocalDateTime followedAt)` — used by both `FavoriteService.getFollowers()` and `FavoriteService.getMyFavoritesAsDtos()`.

**`FavoriteService.getMyFavoritesAsDtos(Long followerId)`** — returns `List<FollowerResponse>` for the REST `/me/favorites` endpoint. Mirrors `getFollowers()` pattern. The original `getMyFavorites()` still exists (returns `List<UserFavorite>` entities) and is used by bot-side code that needs the full entity.

**Typed exceptions in FavoriteService:** `IllegalArgumentException` throws in `saveFavorite` replaced with `InvalidOperationException` (400). `GlobalExceptionHandler` handles via the existing `CarpoolException` handler.

## Hubs

`HubStatus` has three values: `ACTIVE`, `PENDING`, `REJECTED`. PENDING hubs are immediately usable as ride origins/destinations — `RideService.createRide()` does not validate hub status.

`HubService.suggestHub()` prevents duplicate rows: if a hub with the same name+area already exists as ACTIVE or PENDING it is returned as-is; if REJECTED it is re-queued back to PENDING. Custom locations typed by drivers in the post-ride flow are saved with area `"Unverified"` and status `PENDING` for admin review.

`HubService.approveHub(id, null)` auto-generates a unique code from the hub name (uppercase + underscores, suffix `_2`/`_3`/… for collisions) when no explicit code is provided. Approval evicts both `hubs` and `hub-search` caches.

Admin hub management (list pending, approve, reject) is available in the bot under `MY_PROFILE → 🏘️ Pending Hubs` — gated by `BotConfig.isAdmin()`. No admin web UI exists yet.

`HubService.approveAllPendingHubs()` bulk-approves every pending hub in one pass — loops `hubRepository.findAllPending()`, generating a unique code per hub via the same `generateUniqueCode` helper `approveHub` uses, saving each hub individually (not `saveAll`) so each subsequent `generateUniqueCode` call sees prior codes from the same batch via JPA auto-flush before `findByCode`. Same cache eviction (`hubs`, `hub-search`) as `approveHub`. Returns the list of approved `HubResponse`. See `carpool-bot/CLAUDE.md` for the bot-side confirm flow.

## Rating System

Ratings are **per-ride**, not per user-pair. The same driver and passenger can rate each other again on every new completed ride — there is no "already rated this person" global block.

**Duplicate check scope:**
- Passenger rater: `existsByRideIdAndRaterId` — one rating per ride total (one driver per ride).
- Driver rater: `existsByRideIdAndRaterIdAndRateeId` — one rating per passenger per ride. A driver with 3 passengers submits 3 independent ratings and is only blocked per-ratee, not per-ride.

V43 Flyway migration widens the DB unique constraint on `ride_ratings` from `(ride_id, rater_id)` to `(ride_id, rater_id, ratee_id)`, matching the repository query above. The JPA `@Table(uniqueConstraints = …)` annotation was updated in step.

**Role stored on save:** `raterRole` is `"DRIVER"` or `"PASSENGER"` (set from `ride.driver.id.equals(raterId)`). The repository queries `raterRole` to separate driver-received ratings from passenger-received ratings — `findAverageDriverRatingByRateeId` filters `raterRole = 'PASSENGER'` (passenger rated the driver), `findAveragePassengerRatingByRateeId` filters `raterRole = 'DRIVER'`.

**Analytics queries (P3 TODO):** Five MySQL aggregate queries are documented for future `RideRatingRepository` addition: star distribution, top-rated drivers leaderboard (min 3 ratings), monthly trend per driver, completion rate (% of completed rides that got rated), and rating drop detection (last-30d avg vs all-time avg, drop > 0.5).

**Ratings wall (paginated):** `RatingService.getRatingsReceivedPaged(Long userId, int page, int pageSize)` returns a `Page<RideRating>`. Uses `RideRatingRepository.findByRateeIdWithAssociations(rateeId, pageable)` — a `@Query` with `JOIN FETCH r.rater JOIN FETCH r.ratee JOIN FETCH r.ride`. This is required because `open-in-view=false` is set project-wide: without JOIN FETCH the mapper throws `LazyInitializationException` when accessing `rater`, `ratee`, or `ride` after the transaction closes. The non-paged `findByRateeIdOrderByCreatedAtDesc` list overload is still used by bot profile screens that don't need the mapper.

**Typed exceptions in RatingService:** Raw `IllegalArgumentException` and `IllegalStateException` throws have been replaced with typed `CarpoolException` subclasses: `InvalidOperationException` (400, error code `INVALID_OPERATION`) for bad-input cases (ride/user not found, invalid stars), and `RatingConflictException` (409, error code `RATING_CONFLICT`) for state-conflict cases (duplicate rating, ride not COMPLETED). Both classes live in `carpool-common`. `GlobalExceptionHandler` handles them via the existing `CarpoolException` handler — no handler changes needed.

**REST-facing RatingResponse:** `RatingResponse` uses `UserSummaryResponse` (not `UserResponse`) for the `rater` and `ratee` fields. `UserSummaryResponse` intentionally omits `plateNumber`, `carModel`, and `carColor` — `GET /users/{userId}/ratings` is accessible by any authenticated user, so vehicle fields must not appear. `EntityMapper.toRatingResponse` requires `@Mapping(source = "ride.id", target = "rideId")` — MapStruct cannot auto-map nested `.id` fields and will silently produce null without this annotation.

## Schedulers

Three schedulers in `carpool-service/scheduler/`:
- `RideExpiryScheduler` — every 30 min: auto-expires rides past departure; auto-completes DEPARTED rides 2h+ old
- `PendingBookingScheduler` — sends up to 3 reminder notifications to drivers with unresponded booking requests (at 15, 30, and 45 minutes); bookings that receive no response stay `PENDING` indefinitely — there is no auto-expiry or auto-decline. The `TIMED_OUT` status and `BookingTimedOutEvent` infrastructure exist in the domain but are not currently triggered.
- `RideDepartureReminderScheduler` — notifies driver + confirmed passengers 30 min before departure

All use `fixedDelay` (not `fixedRate`) with staggered `initialDelay` to prevent startup overlap.
