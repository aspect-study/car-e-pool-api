# Route Change Without Cancellation — Design

**Date:** 2026-07-22
**Status:** Approved (design)
**Origin:** Telegram user request — a driver wanted to change a ride's destination without cancelling, because cancelling fires a cancellation DM to accepted passengers and forces the driver to separately explain it was not a real cancellation.

## Problem

Today the only mutable fields on an existing ride are status, departure time, and available seats
([`RideController`](../../../carpool-web/src/main/java/com/carpool/web/controller/RideController.java)).
`originHubId` / `destinationHubId` are set only at creation
([`CreateRideRequest`](../../../carpool-service/src/main/java/com/carpool/service/dto/request/CreateRideRequest.java)).
To change a route a driver must cancel and repost, which:

- Fires `RIDE_CANCELLED` DMs to accepted passengers (reads as a real cancellation).
- Loses all existing bookings and ride state.

At the same time, a route change is not a free edit: passengers booked based on the *original* route,
so silently rewriting the destination could strand them on a route they never agreed to.

## Solution overview

Let a driver change a ride's origin and/or destination on an ACTIVE or FULL ride. Instead of a
cancellation, confirmed passengers receive a distinct **"Route Changed"** DM with **✅ Keep Booking /
❌ Cancel Booking** buttons, and the group announcement refreshes to the new route.

This mirrors the existing **departure-time change** feature almost 1:1
([`RideService.updateDepartureTime`](../../../carpool-service/src/main/java/com/carpool/service/ride/RideService.java),
`RideTimeChangedEvent`, `NotificationService.onRideTimeChanged`,
`GroupNotificationService.onRideTimeChanged`), reusing its proven event/notification/keyboard patterns
rather than inventing new ones.

### Decisions locked during brainstorming

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Editable scope | **Origin + destination** | Origin has the identical "booked on the original route" concern; symmetric and funnels through one event. |
| Confirmed-booking behavior | **Passive (mirror time-change)** | Booking stays `CONFIRMED`; passenger gets Keep/Cancel DM. If ignored, they stay booked. No new booking state, no migration. |
| Delivery surfaces | **Bot flow + REST** | Serves the Telegram user from the conversation directly and web/mobile via REST. |
| Passenger scope for DMs | **Confirmed bookings only** | Matches time-change. PENDING passengers see the new route on acceptance and in the refreshed group post. |
| Request field shape | **Nullable fields** (`null` = keep current) | "Change destination only" needs just one field; no re-picking origin. |

### Explicitly out of scope

- Ride `direction` (`HOME_TO_WORK` / `WORK_TO_HOME`) is **not** recomputed or changed. A driver reversing
  direction should post a new ride. Route change stays within the existing direction.
- No new booking state / re-confirmation gate (rejected in favor of the passive model).
- Waypoints are not restructured. If a booking's pickup waypoint becomes less relevant after a route
  change, the passenger decides via Keep/Cancel; the feature does not mutate waypoints.

## Detailed design

### 1. Service layer — `RideService.updateRoute`

New method mirroring `updateDepartureTime`:

```
RideResponse updateRoute(Long rideId, Long newOriginHubId, Long newDestinationHubId, Long driverUserId)
```

- Acquire `rideRepository.findByIdWithLock(rideId)` (`SELECT FOR UPDATE`) — same concurrency guard as
  `updateDepartureTime`.
- Validate:
  - caller is ride owner → `NotRideOwnerException`;
  - status is `ACTIVE` or `FULL` → `InvalidRideStateException`;
  - `null` for either hub id means "keep current";
  - resulting origin id ≠ resulting destination id → `InvalidRideStateException`;
  - at least one hub actually changes vs. current → `InvalidRideStateException`
    (mirrors the "no changes made" guard in `updateDepartureTime`);
  - each supplied hub id resolves to an existing hub → `ResourceNotFoundException`.
- Capture old origin/destination hub **names** before mutation.
- Apply new origin/destination hub references, `rideRepository.save(...)`.
- Log `"Route updated: rideId=… oldOrigin=… newOrigin=… oldDest=… newDest=… driverId=…"`.
- Publish `RideEvents.RideRouteChangedEvent(saved, oldOriginName, oldDestinationName)`.
- Return `mapper.toRideResponse(saved)`.

`@Transactional`, same as sibling update methods.

### 2. Event — `RideEvents.RideRouteChangedEvent`

```
public record RideRouteChangedEvent(Ride ride, String oldOriginName, String oldDestinationName) {}
```

Old names are carried on the event because the saved `Ride` already holds the new values; the DM needs
`old → new` for clarity.

### 3. Notification type

Add `NotificationTypes.RIDE_ROUTE_CHANGED = "RIDE_ROUTE_CHANGED"`.

### 4. Passenger DMs — `NotificationService.onRideRouteChanged`

- Annotation stack identical to `onRideTimeChanged`:
  `@Async` + `@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)`.
- Fetch **confirmed** bookings for the ride; return early if none.
- Include the direction line via the existing `directionLabel(...)` helper (per module convention).
- Message shows the change explicitly, e.g.:
  `📍 Route changed: <old origin> → <old destination>` then
  `➡️ New route: <new origin> → <new destination>`, plus the departure time.
- Attach **✅ Keep Booking → `KEEP_BOOKING:{bookingId}`** and
  **❌ Cancel Booking → `CANCEL_BOOKING:{bookingId}`** inline buttons via the keyboard overload of
  `sendAndRecord`. These callbacks already exist from the time-change feature — **no new callback
  handlers required**.
- Persist each notification with type `RIDE_ROUTE_CHANGED`, PENDING → SENT/FAILED.

### 5. Group announcement refresh — `GroupNotificationService.onRideRouteChanged`

- Same annotation stack as `onRideTimeChanged`.
- Delete the old group post and repost a fresh announcement reflecting the new route.
- No 48h guard (route change is high-signal, matching the time-change refresh).
- Return early if `groupMessageId` is null or ride is not ACTIVE/FULL.
- Call `persistGroupMessageId(...)` on success.

### 6. REST endpoint — `RideController`

- `PATCH /api/v1/rides/{id}/route`
- Body: `UpdateRouteRequest(Long originHubId, Long destinationHubId)` — both nullable; at least one must
  be non-null (bean validation: a class-level `@AssertTrue` check or explicit service guard).
- Owner enforced via the service-layer ownership guard (consistent with `updateDepartureTime`).
- Returns `ResponseEntity<ApiResponse<RideResponse>>`.
- Integration test added to the `BaseIntegrationTest` suite (owner happy path; non-owner 403;
  invalid state; same origin==destination; no-op change).

### 7. Bot flow

Mirrors the departure-time change flow (`DriverHandler`, `BotFlow`, `MessageHandler`, `CallbackHandler`):

- Entry: **My Rides → select ride → ✏️ Change Route → [ Change Origin | Change Destination ]**.
- Reuse the existing post-ride **hub picker** UI for selecting the new hub.
- New `BotFlow` enum value(s) and `UserState` field(s) to hold the in-progress selection.
- Route the selection through `MessageHandler` / `CallbackHandler`; on confirm call
  `rideService.updateRoute(...)`.
- `stateManager.reset()` at the terminal step (per `/new-bot-flow` convention).

## Reused, unchanged

- `KEEP_BOOKING:{id}` / `CANCEL_BOOKING:{id}` callback handlers and the passenger cancellation path
  (introduced by the time-change feature).
- `findByIdWithLock`, `sendAndRecord` keyboard overload, `persistGroupMessageId`, `directionLabel`.

## Testing

- **Unit (`carpool-service`):** `updateRoute` — owner check, state check, origin==destination guard,
  no-op guard, null-means-keep behavior, event published with correct old names.
- **Integration (`carpool-web`):** `PATCH /rides/{id}/route` happy path and each failure mode.
- **Notification:** confirmed-only fetch; Keep/Cancel keyboard present; type `RIDE_ROUTE_CHANGED`.

## Migration impact

**None.** No schema change — origin/destination FKs already exist on `rides`. The passive model adds no
booking state. (Highest current Flyway version remains V44.)

## Follow-ups / open items

- Consider whether PENDING passengers should also be notified of a route change (currently deferred to
  keep parity with time-change).
- Consider a combined "Change Origin and Destination in one flow" bot step if drivers frequently change
  both at once (v1 changes one at a time).
