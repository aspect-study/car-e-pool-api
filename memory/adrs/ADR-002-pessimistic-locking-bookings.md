# ADR-002: Pessimistic Locking for Booking Writes

**Status:** Accepted  
**Date:** 2024  
**Deciders:** Backend team

## Context

Multiple users can simultaneously attempt to book the last available seat on a ride. Without locking, both requests read `availableSeats = 1`, both decrement it to 0, and two `Booking` records are created for zero seats — overbooking the ride.

## Decision

Use `@Lock(LockModeType.PESSIMISTIC_WRITE)` on the ride repository query that precedes any seat-decrement operation. The lock is held for the duration of the booking transaction.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT r FROM Ride r WHERE r.id = :id")
Optional<Ride> findByIdForUpdate(@Param("id") Long id);
```

Optimistic locking (version field + retry) was considered and rejected because:
1. Under high contention on popular rides, optimistic locking leads to frequent retries and poor UX
2. The booking transaction is short (read + check + insert) — pessimistic lock hold time is minimal
3. The semantic is clearer: "I am about to modify this ride's seat count, no one else should touch it"

## Consequences

- Booking requests for the same ride are serialised at the DB row level
- Throughput is bounded by the number of distinct rides, not the total booking volume — acceptable for a carpool service where ride counts are small
- Any future code that modifies `availableSeats` **must** use `findByIdForUpdate`, not `findById`
- Integration tests for concurrent booking **must** use a real DB (`@Tag("integration")`) — the lock has no effect with an in-memory H2 database

## Review Trigger

The `/concurrency-review` skill checks for missing `findByIdForUpdate` calls before any seat-decrement merge.