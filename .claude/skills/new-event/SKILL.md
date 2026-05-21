---
name: new-event
description: Use when adding a new domain event and async notification listener in the carpool service. Covers RideEvents record, ApplicationEventPublisher, and the exact listener annotation stack required.
---

# Adding a New Domain Event

## Step 1 — Define the event record

Open `carpool-service/.../event/RideEvents.java`. Add a new nested record:

```java
public record MyThingHappenedEvent(Long rideId, Long userId, /* other fields */) {}
```

Keep it a plain data carrier — no logic, no Spring references.

## Step 2 — Publish the event

In the service method where the thing happens, inject `ApplicationEventPublisher` and publish after the DB write succeeds:

```java
@Autowired
private ApplicationEventPublisher eventPublisher;

// Inside the @Transactional service method, after save:
eventPublisher.publishEvent(new RideEvents.MyThingHappenedEvent(rideId, userId));
```

The event is published inside the transaction. The listener (step 3) will only fire after the transaction commits — this is intentional.

## Step 3 — Create the listener

The annotation stack is exact and non-negotiable. Wrong order or missing annotation causes silent failures or duplicate execution:

```java
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void onMyThingHappened(RideEvents.MyThingHappenedEvent event) {
    // runs in a virtual thread, after outer tx commits
    // runs in its own new transaction
}
```

**Why each annotation:**
- `@Async` — runs in a virtual thread so it doesn't block the caller
- `@TransactionalEventListener(AFTER_COMMIT)` — only fires after the outer transaction commits; prevents notifications on rolled-back writes
- `@Transactional(REQUIRES_NEW)` — wraps the listener body in its own transaction so DB writes here (e.g., `notifications` table) commit independently

Put this method in `NotificationService` if it sends Telegram messages. Put it in `GroupNotificationService` if it touches the group announcement.

## Step 4 — Write to notifications table (if sending a DM)

Follow the existing PENDING → SENT/FAILED pattern:

```java
Notification notification = notificationRepository.save(
    Notification.builder()
        .userId(recipientId)
        .message(text)
        .status(NotificationStatus.PENDING)
        .build()
);
try {
    bot.sendToUser(telegramId, text);
    notification.setStatus(NotificationStatus.SENT);
} catch (Exception e) {
    notification.setStatus(NotificationStatus.FAILED);
    log.warn("Failed to send notification for event: {}", e.getMessage());
} finally {
    notificationRepository.save(notification);
}
```

## Checklist

- [ ] Event record added to `RideEvents.java` as a nested record
- [ ] `eventPublisher.publishEvent(...)` called inside the `@Transactional` service method
- [ ] Listener has all three annotations in order: `@Async`, `@TransactionalEventListener(AFTER_COMMIT)`, `@Transactional(REQUIRES_NEW)`
- [ ] Notification written to DB with PENDING → SENT/FAILED lifecycle
- [ ] Listener placed in `NotificationService` (DMs) or `GroupNotificationService` (group posts)
