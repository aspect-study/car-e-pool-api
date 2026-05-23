---
name: concurrency-review
description: Use when reviewing any code that writes to the ride or booking tables, decrements seats, or publishes domain events. Audits for missing pessimistic locks, wrong TransactionPhase, and unsafe listener write-backs.
---

# Concurrency Review

Run this review on any service method that touches `Ride.availableSeats`, creates or cancels a `Booking`, or publishes a `RideEvent`.

## Step 1 — Seat Decrement Audit

Find every call site that reads `availableSeats` and decrements it. For each one, verify:

```java
// Required pattern — must hold a write lock before reading seats
Ride ride = rideRepository.findByIdForUpdate(rideId)   // @Lock(PESSIMISTIC_WRITE)
    .orElseThrow(...);
if (ride.getAvailableSeats() < 1) throw new NoSeatsAvailableException();
ride.setAvailableSeats(ride.getAvailableSeats() - 1);
```

**Red flags:**
- `rideRepository.findById(rideId)` used instead of `findByIdForUpdate` before seat modification
- `availableSeats` read and written in separate transactions
- Seat count checked in a service method without a lock, then decremented later in the same call

## Step 2 — Event Listener Phase Audit

Find all `@EventListener` and `@TransactionalEventListener` annotations in `carpool-service/notification/`:

```java
// Correct
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Async

// Wrong — fires even on rollback
@EventListener

// Wrong — notification before commit is premature
@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
```

For each listener that sends a Telegram message, it must be `AFTER_COMMIT` + `@Async`.

## Step 3 — Listener Write-Back Audit

If a listener calls any `@Transactional` method (e.g., saves a `Notification` record), verify the propagation:

```java
// Correct — new transaction for DB write inside AFTER_COMMIT listener
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void onEvent(SomeEvent event) {
    notificationRepository.save(...);  // safe — new TX
}

// Wrong — no transaction is active at AFTER_COMMIT; this will fail
@Transactional   // no propagation — inherits, but there's nothing to inherit
public void onEvent(SomeEvent event) {
    notificationRepository.save(...);  // IllegalTransactionStateException
}
```

## Step 4 — Scheduler Bulk Update Audit

Find all `@Scheduled` methods in `carpool-service/scheduler/`. For each that updates ride/booking status:

```java
// Correct — single SQL, no row-level lock held per entity
@Modifying
@Query("UPDATE Ride r SET r.status = 'EXPIRED' WHERE r.departureTime < :cutoff AND r.status = 'ACTIVE'")
int bulkExpireOlderThan(@Param("cutoff") Instant cutoff);

// Wrong — locks every row individually, causes contention under load
for (Ride r : rideRepository.findAllExpired()) {
    r.setStatus(RideStatus.EXPIRED);
    rideRepository.save(r);
}
```

## Step 5 — Direction Conflict Check Audit

The direction conflict check (driver already has an active ride in the same direction on the same day) must be part of the same transaction as the ride-creation write. Verify it's not a separate service call that could race:

```java
@Transactional
public Ride postRide(PostRideRequest req, Long driverId) {
    // Conflict check happens here, inside the transaction
    if (rideRepository.existsActiveRideForDriverAndDirection(...)) {
        throw new ConflictingRideException();
    }
    // Create ride — safe because conflict check and creation are atomic
    return rideRepository.save(new Ride(...));
}
```

## Output Format

For each issue found:
```
[LOCK MISSING] BookingService:82 — findById used before seat decrement; replace with findByIdForUpdate
[WRONG PHASE]  NotificationService:114 — @EventListener fires on rollback; change to @TransactionalEventListener(AFTER_COMMIT)
[TX WRITEBACK] NotificationService:134 — @Transactional without REQUIRES_NEW inside AFTER_COMMIT listener; will throw
```

If no issues found: `CONCURRENCY REVIEW PASSED — no locking gaps found.`