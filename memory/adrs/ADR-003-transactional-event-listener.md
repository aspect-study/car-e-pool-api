# ADR-003: TransactionalEventListener(AFTER_COMMIT) for Notifications

**Status:** Accepted  
**Date:** 2024  
**Deciders:** Backend team

## Context

When a booking is created (or cancelled, or a ride is posted), we need to notify the relevant users via Telegram. The naive approach is to call `TelegramBot.sendMessage()` directly inside the service method. This has two failure modes:

1. **Premature notification:** The DB transaction rolls back after the Telegram message was sent — the user gets a "booking confirmed" message for a booking that doesn't exist
2. **Notification blocking the transaction:** A slow Telegram API call holds the DB transaction open, consuming a connection pool slot

## Decision

Use the Spring event system with `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` + `@Async`:

```java
// In service method (inside @Transactional)
eventPublisher.publishEvent(new RideEvents.RideBooked(ride, passenger, driver));

// In NotificationService
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Async
public void onRideBooked(RideEvents.RideBooked event) {
    telegramNotificationPort.send(event.getDriver().getChatId(), "New booking: ...");
    telegramNotificationPort.send(event.getPassenger().getChatId(), "Booking confirmed!");
}
```

If the listener also writes to the DB (e.g., saves a `Notification` audit record), it must use `@Transactional(propagation = Propagation.REQUIRES_NEW)` — there is no active transaction in an `AFTER_COMMIT` listener.

## Consequences

- Notifications are guaranteed to fire only after a successful commit
- Telegram API latency does not affect DB transaction duration
- All domain event classes live in `carpool-service/event/`
- The `NotificationService` depends on `TelegramNotificationPort` (interface), which is implemented in `carpool-bot` — this respects the module dependency chain
- Listener write-backs require `REQUIRES_NEW` propagation — forgetting this causes `IllegalTransactionStateException` at runtime

## Anti-patterns Caught By

The `/concurrency-review` skill audits for `@EventListener` (wrong — fires on rollback) instead of `@TransactionalEventListener(AFTER_COMMIT)`.