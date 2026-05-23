---
name: new-scheduler
description: Use when adding a new scheduled background job to carpool-service or carpool-bot. Covers fixedDelay vs fixedRate, staggered initialDelay, module placement, and ARCHITECTURE.md update.
---

# Adding a New Scheduler

## Step 1 — Choose the module

- **`carpool-service/scheduler/`** — jobs that query the DB or call services (expiry, reminders, departure alerts)
- **`carpool-bot/scheduler/`** — jobs that need bot-specific logic like Telegram group operations (`StaleAnnouncementRefreshScheduler`)

If the job only needs service-layer calls, put it in `carpool-service`. Only move it to `carpool-bot` if it must call `GroupNotificationService` or `CarpoolBot` directly.

## Step 2 — Pick a staggered initialDelay

Every scheduler must have a unique `initialDelay` so they don't all fire simultaneously at startup. Existing delays in use:

| Scheduler | initialDelay |
|-----------|-------------|
| `RideExpiryScheduler.expireStaleRides` | 60 000 ms (1 min) |
| `RideExpiryScheduler.completeStaleRides` | 300 000 ms (5 min) |
| `PendingBookingScheduler` | 120 000 ms (2 min) |
| `RideDepartureReminderScheduler` | 180 000 ms (3 min) |
| `StaleAnnouncementRefreshScheduler` | 600 000 ms (10 min) |

Pick the next available slot (e.g. 240 000 ms / 4 min, or 420 000 ms / 7 min). Verify by checking the actual scheduler files before committing — this list may be out of date.

## Step 3 — Create the scheduler class

```java
package com.carpool.service.scheduler; // or carpool-bot/scheduler

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MyThingScheduler {

    private final MyThingService myThingService;

    /**
     * Runs every N minutes.
     * fixedDelay = wait N min AFTER last execution completes — prevents overlap.
     * initialDelay = starts X min after boot — staggered from other schedulers.
     */
    @Scheduled(fixedDelay = N * 60 * 1000, initialDelay = X_000)
    public void doThing() {
        log.debug("Running my-thing check...");
        myThingService.doThing();
    }
}
```

**Always use `fixedDelay`, never `fixedRate`.** `fixedDelay` waits for the previous execution to complete before starting the next countdown. `fixedRate` fires on a wall-clock interval regardless of whether the previous run finished — if the job takes longer than the interval, executions pile up.

## Step 4 — Implement the service method

Put the actual logic in the service layer, not in the scheduler. The scheduler is a thin trigger only. Example:

```java
// In RideService (or whichever service owns the domain)
@Transactional
public void doThing() {
    List<MyEntity> targets = myRepository.findCandidates();
    if (targets.isEmpty()) {
        return;
    }
    log.info("my-thing scheduler: processing {} items", targets.size());
    for (MyEntity item : targets) {
        try {
            processItem(item);
        } catch (Exception e) {
            log.error("Failed to process item {}", item.getId(), e);
        }
    }
}
```

Wrap per-item logic in try/catch so one failure doesn't abort the whole batch.

## Step 5 — If the job dispatches async work per item

Follow the pattern in `StaleAnnouncementRefreshScheduler` — dispatch each item `@Async` and let the scheduler return immediately:

```java
for (MyEntity item : targets) {
    myService.processItemAsync(item.getId()); // @Async method
}
```

Mark the service method `@Async` so the scheduler thread is not blocked by Telegram calls or slow DB work.

## Step 6 — Update ARCHITECTURE.md

Add a row to the schedulers table in `ARCHITECTURE.md`:

```
| `MyThingScheduler` | `carpool-service` | Every N min | One-line description of what it does |
```

Also add a bullet to the **Schedulers** section of the relevant module CLAUDE.md (`carpool-service/CLAUDE.md` or `carpool-bot/CLAUDE.md`).

## Checklist

- [ ] Class is in `carpool-service/scheduler/` or `carpool-bot/scheduler/` (not in handler or service package)
- [ ] Uses `fixedDelay`, not `fixedRate`
- [ ] `initialDelay` is unique — does not collide with any existing scheduler
- [ ] Scheduler class is a thin trigger only — logic lives in the service layer
- [ ] Per-item processing is wrapped in try/catch so one failure doesn't abort the batch
- [ ] If the job does Telegram calls or slow work per item, the service method is `@Async`
- [ ] ARCHITECTURE.md schedulers table updated
- [ ] Module CLAUDE.md schedulers section updated