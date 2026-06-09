# Plan: Show All Active Ride Cards in the Bot Main Menu

## Context

`BotFlowHelper.showMainMenu()` currently uses `.findFirst()` on the driver's active rides — so when a driver has both a HOME_TO_WORK and a WORK_TO_HOME ride active simultaneously (permitted by the domain), only one card is shown. The user wants both cards visible, each with their own action buttons. This also resolves the `PENDING_REQUESTS` ambiguity where the badge was ride-specific but the button was scoped to all rides.

**Complexity: LOW.** The data layer already returns all rides. Every action button already encodes `{rideId}`. The change is mostly UI assembly in `showMainMenu()`, plus a small fix to scope pending requests per ride.

---

## Affected Files

| File | Change |
|------|--------|
| `carpool-bot/.../helper/BotFlowHelper.java` | Replace `findFirst()` with `toList()`, loop over active rides, fix pending button |
| `carpool-bot/.../handler/DriverHandler.java` | Branch `handlePendingRequests` on rideId; fix "Back to Pending" button |
| `carpool-service/.../booking/BookingService.java` | Add `countPendingRequestsForRide(Long rideId)` |
| `carpool-repository/.../BookingRepository.java` | Add `countPendingByRideId(Long rideId)` JPQL query |

**No changes needed:** `CallbackHandler` (`:` split already routes `PENDING_REQUESTS:N`), `BotMessageBuilder.formatRideCard()`, service/repo ride layer, DB schema.

---

## Step 1 — `BookingRepository`: Add per-ride pending count

Add after `countPendingByDriverId` (line 166):

```java
@Query("""
    SELECT COUNT(b) FROM Booking b
    WHERE b.ride.id = :rideId
      AND b.status = 'PENDING'
    """)
long countPendingByRideId(@Param("rideId") Long rideId);
```

`findPendingByRideId(rideId)` already exists at line 126 — reuse it in the service layer.

---

## Step 2 — `BookingService`: Add `countPendingRequestsForRide`

Add alongside `countPendingRequestsForDriver` (line 367):

```java
@Transactional(readOnly = true)
public long countPendingRequestsForRide(Long rideId) {
    return bookingRepository.countPendingByRideId(rideId);
}

@Transactional(readOnly = true)
public List<BookingResponse> getPendingRequestsForRide(Long rideId, Long driverUserId) {
    return bookingRepository.findPendingByRideId(rideId)
            .stream()
            .filter(b -> b.getRide().getDriver().getId().equals(driverUserId))
            .map(mapper::toBookingResponse)
            .toList();
}
```

---

## Step 3 — `BotFlowHelper.showMainMenu()`: Show all active rides

Replace the `if (hasActiveRide)` block (lines 65–143).

**New structure:**

```java
List<RideResponse> activeRides = myRides.stream()
        .filter(r -> r.status() == RideStatus.ACTIVE
                  || r.status() == RideStatus.FULL
                  || r.status() == RideStatus.DEPARTED)
        .toList();  // at most 2 by domain constraint

boolean hasActiveRide = !activeRides.isEmpty();

if (hasActiveRide) {
    List<BookingResponse> myBookings = bookingService.getMyBookings(carpoolUserId);

    for (int i = 0; i < activeRides.size(); i++) {
        RideResponse active = activeRides.get(i);
        String dirLabel = active.direction() == RideDirection.HOME_TO_WORK
                ? "Home → Work" : "Work → Home";

        String header = activeRides.size() > 1
                ? "🚗 <b>Active Ride (" + dirLabel + ")</b>\n\n"
                : "🚗 <b>Your Active Ride</b>\n\n";

        String msg = header + BotMessageBuilder.formatRideCard(active)
                + "\n\nWhat would you like to do?";

        long pendingCount = bookingService.countPendingRequestsForRide(active.id());
        boolean canReannounce = active.announceCount() != null && active.announceCount() < 10;

        List<List<InlineKeyboardButton>> rows = buildRideActionRows(
                active, pendingCount, canReannounce, myBookings);

        if (i == 0) {
            bot.send(BotMessageBuilder.textWithRemoveKeyboard(chatId, msg));
        } else {
            bot.send(BotMessageBuilder.text(chatId, msg));  // keyboard already removed
        }
        bot.send(sendWithInline(chatId, "Choose an action:", rows));
    }
```

Extract the 3-branch button logic into a private helper to keep `showMainMenu` readable:

```java
private List<List<InlineKeyboardButton>> buildRideActionRows(
        RideResponse active, long pendingCount,
        boolean canReannounce, List<BookingResponse> myBookings) { ... }
```

Inside `buildRideActionRows`, change the pending button from:
```java
"PENDING_REQUESTS"
```
to:
```java
"PENDING_REQUESTS:" + active.id()
```

The `countPendingRequestsForDriver(carpoolUserId)` call is removed — replaced by per-ride counts.

---

## Step 4 — `DriverHandler`: Scope `handlePendingRequests` by rideId

`handlePendingRequests` (line 368) — branch on `ctx.entityId()`:

```java
public void handlePendingRequests(BotContext ctx) {
    Long rideId = ctx.entityId();
    List<BookingResponse> pending = (rideId != null)
            ? bookingService.getPendingRequestsForRide(rideId, ctx.carpoolUserId())
            : bookingService.getPendingRequestsForDriver(ctx.carpoolUserId()); // legacy fallback

    // ... rest unchanged
}
```

Fix the "Back to Pending" button in `handleViewPendingRequest` (line 450).
`BookingResponse` exposes a flat `rideId` field — use `b.rideId()`:

```java
// Before:
"◀️ Back to Pending", "PENDING_REQUESTS", ButtonStyle.PRIMARY.toString()

// After:
"◀️ Back to Pending", "PENDING_REQUESTS:" + b.rideId(), ButtonStyle.PRIMARY.toString()
```

`b` here is the `BookingResponse` being viewed. `ctx.entityId()` gives the bookingId; the booking is fetched from `bookingService` within the method.

---

## Cases: No regression for single-ride drivers

| Scenario | Behavior |
|----------|----------|
| 0 active rides | Unchanged — direction-selector screen |
| 1 active ride | Identical to today — single card, single action block; header stays "Your Active Ride" |
| 2 active rides | Two cards sent sequentially, each with own action buttons; headers show direction |

---

## Verification

1. Driver with 1 active ride: main menu looks identical to current behavior.
2. Driver with 2 active rides: both cards appear, each card's buttons carry the correct `rideId`.
3. Tap `⏳ Pending` on card 1 → shows only pending requests for ride 1.
4. Inside a pending request detail, tap `◀️ Back to Pending` → returns to the correct ride's pending list.
5. Tap stale bare `PENDING_REQUESTS` from old cached Telegram message → fallback shows all pending, no crash.
6. Existing single-ride unit tests in `RideServiceTest` pass without changes.
