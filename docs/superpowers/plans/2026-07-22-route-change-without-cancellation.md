# Route Change Without Cancellation — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a driver change an ACTIVE/FULL ride's origin and/or destination without cancelling; confirmed passengers get a distinct "Route Changed" DM with Keep/Cancel buttons, and the group post refreshes.

**Architecture:** Mirror the existing departure-time-change feature end to end. A new `RideService.updateRoute` publishes `RideRouteChangedEvent`; `NotificationService.onRideRouteChanged` DMs confirmed passengers (reusing the existing `KEEP_BOOKING` / `CANCEL_BOOKING` callbacks); `GroupNotificationService.onRideRouteChanged` refreshes the group announcement. Exposed via `PATCH /api/v1/rides/{id}/route` and a Telegram bot flow that reuses the post-ride hub picker.

**Tech Stack:** Java 25, Spring Boot 4, Spring Data JPA, MapStruct, Spring `ApplicationEventPublisher` + `@TransactionalEventListener`, JUnit 5 + Mockito + AssertJ, Telegram bot (custom handler registry).

**Reference spec:** `docs/superpowers/specs/2026-07-22-route-change-without-cancellation-design.md`

**Build/test note:** The user runs all `mvn` builds themselves. Do NOT run `mvn`. For each "run the test" step, hand the exact command to the user and wait for the result before proceeding.

---

## File Structure

**Phase 1 — Backend + REST**
- Modify `carpool-service/src/main/java/com/carpool/service/event/RideEvents.java` — add `RideRouteChangedEvent` record.
- Modify `carpool-domain/src/main/java/com/carpool/domain/enums/NotificationTypes.java` — add `RIDE_ROUTE_CHANGED`.
- Modify `carpool-service/src/main/java/com/carpool/service/ride/RideService.java` — add `updateRoute(...)`.
- Modify `carpool-service/src/main/java/com/carpool/service/notification/NotificationService.java` — add `onRideRouteChanged(...)`.
- Modify `carpool-bot/src/main/java/com/carpool/bot/service/GroupNotificationService.java` — add `onRideRouteChanged(...)`.
- Create `carpool-service/src/main/java/com/carpool/service/dto/request/UpdateRouteRequest.java`.
- Modify `carpool-web/src/main/java/com/carpool/web/controller/RideController.java` — add `PATCH /{id}/route`.
- Test: `carpool-service/src/test/java/com/carpool/service/ride/RideServiceTest.java` — new `UpdateRoute` nested class.
- Test: `carpool-web/src/test/java/com/carpool/web/integration/RideRouteChangeIntegrationTest.java` (new).

**Phase 2 — Bot flow**
- Modify `carpool-bot/src/main/java/com/carpool/bot/state/BotFlow.java` — add `EDIT_ROUTE_ORIGIN`, `EDIT_ROUTE_DEST`.
- Modify `carpool-bot/src/main/java/com/carpool/bot/handler/DriverHandler.java` — entry + selection handlers.
- Modify `carpool-bot/src/main/java/com/carpool/bot/handler/PostRideHandler.java` — reusable route-edit hub text search.
- Modify `carpool-bot/src/main/java/com/carpool/bot/handler/MessageHandler.java` — route text input for the two new flows.
- Modify `carpool-bot/src/main/java/com/carpool/bot/handler/CallbackHandler.java` — register new callbacks.
- Modify `carpool-bot/src/main/java/com/carpool/bot/handler/helper/BotFlowHelper.java` — add "Change Route" button to the ride management card.
- Modify `carpool-bot/src/main/java/com/carpool/bot/handler/SessionRecoveryHandler.java` — mark route-edit callbacks flow-sensitive.

**Docs (final task):** update `carpool-service/CLAUDE.md`, `carpool-bot/CLAUDE.md`.

---

# Phase 1 — Backend + REST

## Task 1: Add the `RideRouteChangedEvent` domain event

**Files:**
- Modify: `carpool-service/src/main/java/com/carpool/service/event/RideEvents.java` (append before the closing brace, after `RideTimeChangedEvent` at line 111)

- [ ] **Step 1: Add the event record**

Insert after the `RideTimeChangedEvent` record (currently the last record, line 111):

```java
    /**
     * Published when a driver changes the origin and/or destination of an active ride.
     * Carries the pre-change hub names so passenger DMs can show old → new.
     * Triggers: notify all confirmed passengers (route changed — keep or cancel booking),
     * refresh group announcement.
     */
    public record RideRouteChangedEvent(Ride ride, String oldOriginName, String oldDestinationName) {}
```

- [ ] **Step 2: Commit**

```bash
git add carpool-service/src/main/java/com/carpool/service/event/RideEvents.java
git commit -m "feat: add RideRouteChangedEvent domain event"
```

---

## Task 2: Add the `RIDE_ROUTE_CHANGED` notification type

**Files:**
- Modify: `carpool-domain/src/main/java/com/carpool/domain/enums/NotificationTypes.java:23`

- [ ] **Step 1: Add the constant**

Add directly below `RIDE_TIME_CHANGED` (line 23):

```java
    public static final String RIDE_ROUTE_CHANGED           = "RIDE_ROUTE_CHANGED";
```

- [ ] **Step 2: Commit**

```bash
git add carpool-domain/src/main/java/com/carpool/domain/enums/NotificationTypes.java
git commit -m "feat: add RIDE_ROUTE_CHANGED notification type"
```

---

## Task 3: `RideService.updateRoute` (TDD)

**Files:**
- Modify: `carpool-service/src/main/java/com/carpool/service/ride/RideService.java` (add method after `updateDepartureTime`, which ends at line 505)
- Test: `carpool-service/src/test/java/com/carpool/service/ride/RideServiceTest.java` (add nested class after the `UpdateDepartureTime` class, which ends at line 487)

Note the existing test fixtures (RideServiceTest lines 58–87): `activeRide` has id `100L`, driver id `1L`, `originHub` id `10L`, `destinationHub` id `11L`. `hubRepository` is a `@Mock`. `SameHubException` and `HubNotFoundException` already exist in `com.carpool.common.exception` (used by `createRide`).

- [ ] **Step 1: Write the failing tests**

Add this nested class to `RideServiceTest` after line 487 (the end of the `UpdateDepartureTime` class), inside the outer `RideServiceTest` class:

```java
// ── updateRoute ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateRoute()")
    class UpdateRoute {

        private Hub newDestination;

        @BeforeEach
        void routeSetup() {
            newDestination = Hub.builder()
                    .id(12L).code("ORTIGAS").name("Ortigas Center").area("Pasig")
                    .status(HubStatus.ACTIVE).build();
        }

        @Test
        @DisplayName("should throw NotRideOwnerException when caller is not the driver")
        void updateRoute_throwsWhenNotOwner() {
            when(rideRepository.findByIdWithLock(100L)).thenReturn(Optional.of(activeRide));

            assertThatThrownBy(() -> rideService.updateRoute(100L, null, 12L, 99L))
                    .isInstanceOf(NotRideOwnerException.class);
        }

        @ParameterizedTest
        @EnumSource(value = RideStatus.class, names = {"PENDING", "COMPLETED", "CANCELLED", "DEPARTED", "DRAFT"})
        @DisplayName("should throw InvalidRideStateException when ride is not ACTIVE or FULL")
        void updateRoute_throwsWhenRideNotActiveOrFull(RideStatus status) {
            activeRide.setStatus(status);
            when(rideRepository.findByIdWithLock(100L)).thenReturn(Optional.of(activeRide));

            assertThatThrownBy(() -> rideService.updateRoute(100L, null, 12L, 1L))
                    .isInstanceOf(InvalidRideStateException.class);
        }

        @Test
        @DisplayName("should throw InvalidRideStateException when both hub ids are null")
        void updateRoute_throwsWhenNothingProvided() {
            when(rideRepository.findByIdWithLock(100L)).thenReturn(Optional.of(activeRide));

            assertThatThrownBy(() -> rideService.updateRoute(100L, null, null, 1L))
                    .isInstanceOf(InvalidRideStateException.class);
        }

        @Test
        @DisplayName("should throw InvalidRideStateException when the resulting route is unchanged")
        void updateRoute_throwsWhenNoActualChange() {
            when(rideRepository.findByIdWithLock(100L)).thenReturn(Optional.of(activeRide));
            when(hubRepository.findById(10L)).thenReturn(Optional.of(originHub));
            when(hubRepository.findById(11L)).thenReturn(Optional.of(destinationHub));

            assertThatThrownBy(() -> rideService.updateRoute(100L, 10L, 11L, 1L))
                    .isInstanceOf(InvalidRideStateException.class)
                    .hasMessageContaining("No changes");
        }

        @Test
        @DisplayName("should throw SameHubException when resulting origin equals destination")
        void updateRoute_throwsWhenOriginEqualsDestination() {
            when(rideRepository.findByIdWithLock(100L)).thenReturn(Optional.of(activeRide));
            when(hubRepository.findById(11L)).thenReturn(Optional.of(destinationHub));

            // change origin to the current destination (11L) → origin == destination
            assertThatThrownBy(() -> rideService.updateRoute(100L, 11L, null, 1L))
                    .isInstanceOf(SameHubException.class);
        }

        @Test
        @DisplayName("should throw HubNotFoundException when a supplied hub id does not exist")
        void updateRoute_throwsWhenHubMissing() {
            when(rideRepository.findByIdWithLock(100L)).thenReturn(Optional.of(activeRide));
            when(hubRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> rideService.updateRoute(100L, null, 999L, 1L))
                    .isInstanceOf(HubNotFoundException.class);
        }

        @Test
        @DisplayName("should apply new destination only, keep origin, and publish event with old names")
        void updateRoute_changesDestinationOnly() {
            when(rideRepository.findByIdWithLock(100L)).thenReturn(Optional.of(activeRide));
            when(hubRepository.findById(12L)).thenReturn(Optional.of(newDestination));
            when(rideRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toRideResponse(any())).thenReturn(mock(RideResponse.class));

            rideService.updateRoute(100L, null, 12L, 1L);

            assertThat(activeRide.getOriginHub().getId()).isEqualTo(10L);
            assertThat(activeRide.getDestinationHub().getId()).isEqualTo(12L);

            ArgumentCaptor<RideEvents.RideRouteChangedEvent> captor =
                    ArgumentCaptor.forClass(RideEvents.RideRouteChangedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().oldOriginName()).isEqualTo("Ayala MRT");
            assertThat(captor.getValue().oldDestinationName()).isEqualTo("BGC High Street");
        }

        @Test
        @DisplayName("should return updated RideResponse after save")
        void updateRoute_returnsUpdatedRideResponse() {
            RideResponse expected = mock(RideResponse.class);
            when(rideRepository.findByIdWithLock(100L)).thenReturn(Optional.of(activeRide));
            when(hubRepository.findById(12L)).thenReturn(Optional.of(newDestination));
            when(rideRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toRideResponse(any())).thenReturn(expected);

            RideResponse result = rideService.updateRoute(100L, null, 12L, 1L);

            assertThat(result).isSameAs(expected);
        }
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Tell the user to run:
```bash
mvn test -pl carpool-service -Dtest=RideServiceTest
```
Expected: FAIL — compilation error, `rideService.updateRoute` does not exist yet.

- [ ] **Step 3: Implement `updateRoute`**

Add this method to `RideService` immediately after `updateDepartureTime` (after line 505). It reuses `findByIdWithLock`, the ownership/state guards from `updateDepartureTime` (lines 471–505), and the hub-loading + `SameHubException` pattern from `createRide` (lines 82–90):

```java
    /**
     * Updates the origin and/or destination hub of an active ride without cancelling it.
     * A null hub id means "keep the current one". Direction is intentionally NOT recomputed.
     */
    @Transactional
    public RideResponse updateRoute(Long rideId, Long newOriginHubId,
                                    Long newDestinationHubId, Long driverUserId) {
        Ride ride = rideRepository.findByIdWithLock(rideId)
                .orElseThrow(() -> new RideNotFoundException(rideId));

        if (!ride.getDriver().getId().equals(driverUserId)) {
            throw new NotRideOwnerException();
        }

        if (ride.getStatus() != RideStatus.ACTIVE && ride.getStatus() != RideStatus.FULL) {
            throw new InvalidRideStateException(
                    "Only ACTIVE or FULL rides can have their route updated.");
        }

        if (newOriginHubId == null && newDestinationHubId == null) {
            throw new InvalidRideStateException(
                    "No changes made — provide a new origin and/or destination.");
        }

        Hub newOrigin = newOriginHubId != null
                ? hubRepository.findById(newOriginHubId)
                        .orElseThrow(() -> new HubNotFoundException(newOriginHubId))
                : ride.getOriginHub();

        Hub newDestination = newDestinationHubId != null
                ? hubRepository.findById(newDestinationHubId)
                        .orElseThrow(() -> new HubNotFoundException(newDestinationHubId))
                : ride.getDestinationHub();

        if (newOrigin.getId().equals(newDestination.getId())) {
            throw new SameHubException();
        }

        boolean originChanged = !newOrigin.getId().equals(ride.getOriginHub().getId());
        boolean destChanged   = !newDestination.getId().equals(ride.getDestinationHub().getId());
        if (!originChanged && !destChanged) {
            throw new InvalidRideStateException(
                    "No changes made — the new route matches the current route.");
        }

        String oldOriginName      = ride.getOriginHub().getName();
        String oldDestinationName = ride.getDestinationHub().getName();

        ride.setOriginHub(newOrigin);
        ride.setDestinationHub(newDestination);
        Ride saved = rideRepository.save(ride);

        log.info("Route updated: rideId={} oldOrigin={} newOrigin={} oldDest={} newDest={} driverId={}",
                rideId, oldOriginName, newOrigin.getName(),
                oldDestinationName, newDestination.getName(), driverUserId);

        eventPublisher.publishEvent(
                new RideEvents.RideRouteChangedEvent(saved, oldOriginName, oldDestinationName));

        return mapper.toRideResponse(saved);
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Tell the user to run:
```bash
mvn test -pl carpool-service -Dtest=RideServiceTest
```
Expected: PASS (all `UpdateRoute` tests green, existing tests still green).

- [ ] **Step 5: Commit**

```bash
git add carpool-service/src/main/java/com/carpool/service/ride/RideService.java carpool-service/src/test/java/com/carpool/service/ride/RideServiceTest.java
git commit -m "feat: add RideService.updateRoute with ownership/state/route guards"
```

---

## Task 4: `NotificationService.onRideRouteChanged`

**Files:**
- Modify: `carpool-service/src/main/java/com/carpool/service/notification/NotificationService.java` (add listener after `onRideTimeChanged`, which ends at line 954)

This mirrors `onRideTimeChanged` (lines 908–954): same annotation stack, same confirmed-only booking filter, same `sendAndRecord` keyboard overload, reusing the existing `KEEP_BOOKING` / `CANCEL_BOOKING` callbacks. The DM shows old → new route.

- [ ] **Step 1: Add the listener**

Insert after `onRideTimeChanged` (after line 954), before the `directionLabel` helper (line 956):

```java
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRideRouteChanged(RideEvents.RideRouteChangedEvent event) {
        Long rideId = event.ride().getId();
        List<Booking> confirmedBookings = bookingRepository
                .findActiveBookingsForRide(rideId)
                .stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                .toList();

        if (confirmedBookings.isEmpty()) {
            log.info("Ride route changed with no confirmed passengers: rideId={}", rideId);
            return;
        }

        Ride ride = confirmedBookings.get(0).getRide();

        String msg = String.format(
                "📍 <b>Ride Route Updated</b>\n" +
                        directionLabel(ride.getDirection()) + "\n\n" +
                        "Your driver changed the route for your upcoming ride.\n\n" +
                        "Was: <s>%s → %s</s>\n" +
                        "➡️ Now: <b>%s → %s</b>\n\n" +
                        "Does this still work for you?",
                HtmlEscapeUtil.escape(event.oldOriginName()),
                HtmlEscapeUtil.escape(event.oldDestinationName()),
                HtmlEscapeUtil.escape(ride.getOriginHub().getName()),
                HtmlEscapeUtil.escape(ride.getDestinationHub().getName()));

        for (Booking booking : confirmedBookings) {
            sendAndRecord(booking.getPassenger(), NotificationTypes.RIDE_ROUTE_CHANGED, msg,
                    Map.of("rideId", rideId, "bookingId", booking.getId()),
                    List.of(
                            List.of(
                                    new TelegramNotificationPort.InlineButton(
                                            "✅ Keep Booking", "KEEP_BOOKING:" + booking.getId()),
                                    new TelegramNotificationPort.InlineButton(
                                            "❌ Cancel Booking", "CANCEL_BOOKING:" + booking.getId())
                            )
                    ));
        }

        log.info("Ride route change notifications sent: rideId={} passengersNotified={}",
                rideId, confirmedBookings.size());
    }
```

- [ ] **Step 2: Commit**

```bash
git add carpool-service/src/main/java/com/carpool/service/notification/NotificationService.java
git commit -m "feat: DM confirmed passengers on route change with Keep/Cancel buttons"
```

---

## Task 5: `GroupNotificationService.onRideRouteChanged`

**Files:**
- Modify: `carpool-bot/src/main/java/com/carpool/bot/service/GroupNotificationService.java` (add listener after `onRideTimeChanged`, which ends at line 370)

Mirrors `onRideTimeChanged` (lines 340–370) exactly: delete old post, repost fresh with the new route, no 48h guard, `persistGroupMessageId` on success.

- [ ] **Step 1: Add the listener**

Insert after `onRideTimeChanged` (after line 370):

```java
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRideRouteChanged(RideEvents.RideRouteChangedEvent event) {
        Long rideId = event.ride().getId();
        Ride ride = rideRepository.findById(rideId).orElse(null);
        if (ride == null || ride.getGroupMessageId() == null) return;
        if (ride.getStatus() != RideStatus.ACTIVE && ride.getStatus() != RideStatus.FULL) return;

        // No 48h guard — a route change is always high-signal and must be reflected immediately.
        try {
            try {
                carpoolBot.deleteMessage(botConfig.getGroupChatId(), ride.getGroupMessageId());
            } catch (Exception e) {
                log.warn("Could not delete old group post before route-change refresh: rideId={} error={}",
                        rideId, e.getMessage());
            }

            String message = buildRidePostedMessage(ride);
            Integer messageId = carpoolBot.sendToGroup(
                    message, ride.getId(), ride.getDriver().getId(), resolveTopicId(ride));
            log.info("Group announcement refreshed after route change: rideId={}", rideId);

            if (messageId != null) {
                persistGroupMessageId(rideId, messageId);
            }
        } catch (Exception e) {
            log.error("Failed to refresh group announcement after route change: rideId={} error={}",
                    rideId, e.getMessage(), e);
        }
    }
```

- [ ] **Step 2: Commit**

```bash
git add carpool-bot/src/main/java/com/carpool/bot/service/GroupNotificationService.java
git commit -m "feat: refresh group announcement on route change"
```

---

## Task 6: `UpdateRouteRequest` DTO

**Files:**
- Create: `carpool-service/src/main/java/com/carpool/service/dto/request/UpdateRouteRequest.java`

Both fields nullable (null = keep current); the "at least one present" and "not the same route" rules are enforced in `updateRoute` (Task 3), so the DTO only needs the class-level "at least one non-null" guard for a clean 400 before the service call.

- [ ] **Step 1: Create the DTO**

```java
package com.carpool.service.dto.request;

import jakarta.validation.constraints.AssertTrue;

/**
 * Request to change a ride's origin and/or destination hub.
 * A null field means "keep the current hub". At least one must be provided.
 */
public record UpdateRouteRequest(
        Long originHubId,
        Long destinationHubId
) {
    @AssertTrue(message = "Provide originHubId and/or destinationHubId")
    public boolean isAtLeastOneProvided() {
        return originHubId != null || destinationHubId != null;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add carpool-service/src/main/java/com/carpool/service/dto/request/UpdateRouteRequest.java
git commit -m "feat: add UpdateRouteRequest DTO"
```

---

## Task 7: REST endpoint `PATCH /api/v1/rides/{id}/route` (+ integration test)

**Files:**
- Modify: `carpool-web/src/main/java/com/carpool/web/controller/RideController.java` (add import + method after the departure-time endpoint, which ends at line 324; the class closes at line 325)
- Test: `carpool-web/src/test/java/com/carpool/web/integration/RideRouteChangeIntegrationTest.java` (new)

- [ ] **Step 1: Write the failing integration test**

Study `BookingIntegrationTest.java` and `RideSearchIntegrationTest.java` first for the exact `BaseIntegrationTest` helpers (JWT header creation, seeding a driver/ride/hub, MockMvc usage) and copy those idioms — helper names below are illustrative and MUST be replaced with the real ones from `BaseIntegrationTest`. Create:

```java
package com.carpool.web.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("PATCH /api/v1/rides/{id}/route")
class RideRouteChangeIntegrationTest extends BaseIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("owner can change the destination and gets 200 with the new hub")
    void ownerChangesDestination() throws Exception {
        // Arrange: seed driver + active ride (origin hub A → destination hub B) + a third hub C.
        // Use the BaseIntegrationTest seeding helpers (see BookingIntegrationTest for the real names).
        Long rideId = seedActiveRideForDriver();     // replace with real helper
        Long newDestHubId = seedHub("Ortigas");      // replace with real helper
        String jwt = jwtForDriver();                  // replace with real helper

        mockMvc.perform(patch("/api/v1/rides/{id}/route", rideId)
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinationHubId\":" + newDestHubId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.destinationHub.id").value(newDestHubId));
    }

    @Test
    @DisplayName("non-owner gets 403")
    void nonOwnerForbidden() throws Exception {
        Long rideId = seedActiveRideForDriver();
        Long newDestHubId = seedHub("Ortigas");
        String otherJwt = jwtForOtherUser();          // replace with real helper

        mockMvc.perform(patch("/api/v1/rides/{id}/route", rideId)
                        .header("Authorization", "Bearer " + otherJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinationHubId\":" + newDestHubId + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("empty body (both null) gets 400")
    void emptyBodyRejected() throws Exception {
        Long rideId = seedActiveRideForDriver();
        String jwt = jwtForDriver();

        mockMvc.perform(patch("/api/v1/rides/{id}/route", rideId)
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Tell the user to run (requires MySQL on 3308):
```bash
mvn test -pl carpool-web -Dtest=RideRouteChangeIntegrationTest
```
Expected: FAIL — endpoint returns 404/405 (route not mapped yet) or the test does not compile until helpers are wired.

- [ ] **Step 3: Add the endpoint**

Add the import near the other request DTO imports (after line 6):
```java
import com.carpool.service.dto.request.UpdateRouteRequest;
```

Add this method after the `updateDepartureTime` endpoint (after line 324), before the class-closing brace at line 325:

```java
    @Operation(summary = "Change a ride's origin and/or destination",
            description = """
                    Driver changes the route of an **ACTIVE** or **FULL** ride without cancelling it.
                    A null field keeps the current hub. Confirmed passengers are notified with
                    Keep/Cancel options and the group announcement is refreshed.

                    Requires ride ownership.
                    """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/{id}/route")
    public ResponseEntity<ApiResponse<RideResponse>> updateRoute(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRouteRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        RideResponse ride = rideService.updateRoute(
                id, request.originHubId(), request.destinationHubId(), currentUser.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(ride));
    }
```

- [ ] **Step 4: Run the integration test to verify it passes**

Tell the user to run:
```bash
mvn test -pl carpool-web -Dtest=RideRouteChangeIntegrationTest
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add carpool-web/src/main/java/com/carpool/web/controller/RideController.java carpool-web/src/test/java/com/carpool/web/integration/RideRouteChangeIntegrationTest.java
git commit -m "feat: add PATCH /rides/{id}/route endpoint with integration tests"
```

**Phase 1 is now a complete, working, testable deliverable** — web/mobile clients can change routes via REST, passengers are notified, and the group post refreshes.

---

# Phase 2 — Bot flow

**v1 scope decision:** the bot route-edit flow reuses the post-ride hub *search* (`hubMatcher.suggest`) and picks from existing matched hubs only. The custom "add a brand-new location" path (`CONFIRM_CUSTOM_ORIGIN`/`_DEST`, PENDING hub creation) from the post-ride flow is **out of scope for v1** to keep the change focused — a driver needing a brand-new hub can still use it at ride-post time. This is recorded as a follow-up in the spec.

## Task 8: Bot `BotFlow` states + `UserState` reuse

**Files:**
- Modify: `carpool-bot/src/main/java/com/carpool/bot/state/BotFlow.java`

The flow reuses the existing `UserState.selectedRideId` and `direction` fields (set by the edit-time flow). No new `UserState` fields are needed — the "which end" is encoded in the flow state itself.

- [ ] **Step 1: Add the two flow states**

Add to the `BotFlow` enum (place them near the edit-time flow states for readability):

```java
    EDIT_ROUTE_ORIGIN,   // awaiting text search for the new origin hub
    EDIT_ROUTE_DEST,     // awaiting text search for the new destination hub
```

- [ ] **Step 2: Commit**

```bash
git add carpool-bot/src/main/java/com/carpool/bot/state/BotFlow.java
git commit -m "feat: add EDIT_ROUTE_ORIGIN and EDIT_ROUTE_DEST bot flow states"
```

---

## Task 9: Reusable route-edit hub search in `PostRideHandler`

**Files:**
- Modify: `carpool-bot/src/main/java/com/carpool/bot/handler/PostRideHandler.java`

Reuses the existing private `buildHubButtonRows(...)` (line 263) and the `hubMatcher` field. Adds two public entry points that render the hub search results with route-edit callback prefixes (`EDIT_HUB_ORIGIN` / `EDIT_HUB_DEST`).

- [ ] **Step 1: Add the two search handlers**

Add these public methods to `PostRideHandler` (near the existing `handlePostRideOrigin` / `handlePostRideDestination`, lines ~145–257):

```java
    /** Route-edit: driver typed a search term for the new origin hub. */
    public void handleEditRouteOriginSearch(Long chatId, String text,
                                            UserState state, CarpoolBot bot) {
        if (state.getSelectedRideId() == null) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Session expired. Please reopen the ride from the main menu."));
            stateManager.reset(chatId);
            return;
        }
        if (text.trim().length() < 3) {
            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "⚠️ Please type at least 3 characters to search."));
            return;
        }
        List<HubResponse> suggestions = hubMatcher.suggest(text);
        if (suggestions.isEmpty()) {
            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "⚠️ No matching hub found. Try a nearby landmark:"));
            return;
        }
        bot.send(flowHelper.sendWithInline(chatId,
                "📍 <b>Select the new start point:</b>\n\nResults for \"" +
                        HtmlEscapeUtil.escape(text) + "\":",
                buildHubButtonRows(suggestions, "EDIT_HUB_ORIGIN", "RETYPE_EDIT_ORIGIN")));
    }

    /** Route-edit: driver typed a search term for the new destination hub. */
    public void handleEditRouteDestSearch(Long chatId, String text,
                                          UserState state, CarpoolBot bot) {
        if (state.getSelectedRideId() == null) {
            bot.send(BotMessageBuilder.text(chatId,
                    "⚠️ Session expired. Please reopen the ride from the main menu."));
            stateManager.reset(chatId);
            return;
        }
        if (text.trim().length() < 3) {
            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "⚠️ Please type at least 3 characters to search."));
            return;
        }
        List<HubResponse> suggestions = hubMatcher.suggest(text);
        if (suggestions.isEmpty()) {
            bot.send(BotMessageBuilder.textWithCancel(chatId,
                    "⚠️ No matching hub found. Try a nearby landmark:"));
            return;
        }
        bot.send(flowHelper.sendWithInline(chatId,
                "🏁 <b>Select the new end point:</b>\n\nResults for \"" +
                        HtmlEscapeUtil.escape(text) + "\":",
                buildHubButtonRows(suggestions, "EDIT_HUB_DEST", "RETYPE_EDIT_DEST")));
    }
```

- [ ] **Step 2: Commit**

```bash
git add carpool-bot/src/main/java/com/carpool/bot/handler/PostRideHandler.java
git commit -m "feat: add reusable route-edit hub search handlers"
```

---

## Task 10: Entry + selection handlers in `DriverHandler`

**Files:**
- Modify: `carpool-bot/src/main/java/com/carpool/bot/handler/DriverHandler.java`

Follows the edit-time pattern (`handleEditRideTime`, `handleConfirmEditRideTime`). `DriverHandler` already injects `rideService` and `stateManager` (used by the edit-time handlers) and has access to `postRideHandler` and `bot`/`BotMessageBuilder` idioms — mirror the exact field names already present in the class.

- [ ] **Step 1: Add the entry + prompt + selection handlers**

Add these methods to `DriverHandler` (near the edit-time handlers):

```java
    /** Entry: driver tapped "Change Route" on the ride management card. */
    public void handleEditRideRoute(BotContext ctx) {
        Long rideId = ctx.entityId();
        try {
            var ride = rideService.getRideById(rideId);
            if (!ride.driver().id().equals(ctx.carpoolUserId())) {
                ctx.bot().send(BotMessageBuilder.text(ctx.chatId(), "⚠️ This is not your ride."));
                return;
            }
            if (!"ACTIVE".equals(ride.status()) && !"FULL".equals(ride.status())) {
                ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                        "⚠️ Only active rides can have their route changed."));
                return;
            }
            stateManager.save(ctx.chatId(), ctx.state()
                    .withSelectedRideId(rideId)
                    .withFlow(BotFlow.IDLE));

            var rows = List.of(
                    List.of(BotMessageBuilder.button(
                            "📍 Change Start", "EDIT_ROUTE_ORIGIN:" + rideId, null)),
                    List.of(BotMessageBuilder.button(
                            "🏁 Change End", "EDIT_ROUTE_DEST:" + rideId, null)));
            ctx.bot().send(flowHelper.sendWithInline(ctx.chatId(),
                    "🔀 <b>Change Route</b>\n\n" +
                            "Current: <b>" + HtmlEscapeUtil.escape(ride.originHub().name()) +
                            " → " + HtmlEscapeUtil.escape(ride.destinationHub().name()) + "</b>\n\n" +
                            "What do you want to change?",
                    rows));
        } catch (Exception e) {
            log.warn("Could not load ride for route edit id={}: {}", rideId, e.getMessage(), e);
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ This ride is no longer available."));
        }
    }

    /** Driver chose to change the origin — prompt for a search term. */
    public void handleEditRouteOriginStart(BotContext ctx) {
        stateManager.save(ctx.chatId(), ctx.state()
                .withSelectedRideId(ctx.entityId())
                .withFlow(BotFlow.EDIT_ROUTE_ORIGIN));
        ctx.bot().send(BotMessageBuilder.textWithCancel(ctx.chatId(),
                "📍 <b>New start point?</b>\n\nType a nearby landmark. Example: <code>SM Southmall</code>"));
    }

    /** Driver chose to change the destination — prompt for a search term. */
    public void handleEditRouteDestStart(BotContext ctx) {
        stateManager.save(ctx.chatId(), ctx.state()
                .withSelectedRideId(ctx.entityId())
                .withFlow(BotFlow.EDIT_ROUTE_DEST));
        ctx.bot().send(BotMessageBuilder.textWithCancel(ctx.chatId(),
                "🏁 <b>New end point?</b>\n\nType a nearby landmark. Example: <code>BGC</code>"));
    }

    /** Driver picked the new origin hub from search results. */
    public void handleEditHubOriginSelected(BotContext ctx) {
        applyRouteChange(ctx, ctx.entityId(), null);
    }

    /** Driver picked the new destination hub from search results. */
    public void handleEditHubDestSelected(BotContext ctx) {
        applyRouteChange(ctx, null, ctx.entityId());
    }

    private void applyRouteChange(BotContext ctx, Long newOriginHubId, Long newDestHubId) {
        Long rideId = ctx.state().getSelectedRideId();
        if (rideId == null) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Session expired. Please reopen the ride from the main menu."));
            stateManager.reset(ctx.chatId());
            return;
        }
        try {
            var updated = rideService.updateRoute(
                    rideId, newOriginHubId, newDestHubId, ctx.carpoolUserId());
            stateManager.reset(ctx.chatId());
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "✅ <b>Route updated</b>\n\n" +
                            "📍 " + HtmlEscapeUtil.escape(updated.originHub().name()) +
                            " → " + HtmlEscapeUtil.escape(updated.destinationHub().name()) + "\n\n" +
                            "Confirmed passengers have been notified."));
        } catch (Exception e) {
            log.warn("Route update failed rideId={}: {}", rideId, e.getMessage(), e);
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(),
                    "⚠️ Could not update the route: " + e.getMessage()));
        }
    }
```

**Note:** confirm the exact accessor names on `RideResponse` (`originHub()`, `destinationHub()`, `status()`, `driver().id()`) and on the hub summary (`.name()`, `.id()`) against `RideResponse.java` before running — adjust if they differ. `getRideById` returns `RideResponse` (RideService line 220).

- [ ] **Step 2: Commit**

```bash
git add carpool-bot/src/main/java/com/carpool/bot/handler/DriverHandler.java
git commit -m "feat: add route-edit entry and hub-selection handlers"
```

---

## Task 11: Register callbacks + route text input

**Files:**
- Modify: `carpool-bot/src/main/java/com/carpool/bot/handler/CallbackHandler.java` (register near the edit-time entries, lines 129–135)
- Modify: `carpool-bot/src/main/java/com/carpool/bot/handler/MessageHandler.java` (route the two new flows to the search handlers)

- [ ] **Step 1: Register the callbacks**

Add to the `@PostConstruct` command map in `CallbackHandler` (near line 129):

```java
        commands.put("EDIT_RIDE_ROUTE",   driverHandler::handleEditRideRoute);
        commands.put("EDIT_ROUTE_ORIGIN", driverHandler::handleEditRouteOriginStart);
        commands.put("EDIT_ROUTE_DEST",   driverHandler::handleEditRouteDestStart);
        commands.put("EDIT_HUB_ORIGIN",   driverHandler::handleEditHubOriginSelected);
        commands.put("EDIT_HUB_DEST",     driverHandler::handleEditHubDestSelected);
        commands.put("RETYPE_EDIT_ORIGIN", driverHandler::handleEditRouteOriginStart);
        commands.put("RETYPE_EDIT_DEST",   driverHandler::handleEditRouteDestStart);
```

- [ ] **Step 2: Route the text input**

In `MessageHandler`, where it switches on `UserState.flow` (the same place `POST_RIDE_DESTINATION` etc. are handled), add cases for the two new flows. Match the existing routing idiom in the file (it passes `chatId, text, state, bot` to `PostRideHandler` methods):

```java
            case EDIT_ROUTE_ORIGIN ->
                    postRideHandler.handleEditRouteOriginSearch(chatId, text, state, bot);
            case EDIT_ROUTE_DEST ->
                    postRideHandler.handleEditRouteDestSearch(chatId, text, state, bot);
```

- [ ] **Step 3: Commit**

```bash
git add carpool-bot/src/main/java/com/carpool/bot/handler/CallbackHandler.java carpool-bot/src/main/java/com/carpool/bot/handler/MessageHandler.java
git commit -m "feat: wire route-edit callbacks and text routing"
```

---

## Task 12: "Change Route" button + session recovery

**Files:**
- Modify: `carpool-bot/src/main/java/com/carpool/bot/handler/helper/BotFlowHelper.java` (`showRideManagementCard`)
- Modify: `carpool-bot/src/main/java/com/carpool/bot/handler/SessionRecoveryHandler.java`

- [ ] **Step 1: Add the button to the ride management card**

In `BotFlowHelper.showRideManagementCard(...)`, in the same rows where `✏️ Edit Time → EDIT_RIDE_TIME:{rideId}` is emitted (the `pendingCount > 0` and default `ACTIVE`/`FULL` branches — NOT the `DEPARTED` branch, matching the Edit-Time placement), add alongside it:

```java
        rows.add(List.of(BotMessageBuilder.button(
                "🔀 Change Route", "EDIT_RIDE_ROUTE:" + rideId, null)));
```

Place it immediately after the Edit-Time button row so the two edit actions sit together. Use the exact `rows`/button idiom already in that method.

- [ ] **Step 2: Mark route-edit callbacks flow-sensitive**

In `SessionRecoveryHandler.isFlowSensitive(...)`, add the mid-flow route-edit callbacks to the flow-sensitive set (mirroring the five edit-time callbacks). The entry point `EDIT_RIDE_ROUTE` is intentionally excluded — it reads `rideId` from the payload, so it works with a fresh session:

```java
        // route-edit mid-flow actions (need live UserState.selectedRideId)
        "EDIT_ROUTE_ORIGIN", "EDIT_ROUTE_DEST",
        "EDIT_HUB_ORIGIN", "EDIT_HUB_DEST",
        "RETYPE_EDIT_ORIGIN", "RETYPE_EDIT_DEST"
```

Match the existing collection/switch structure in `isFlowSensitive` — add these entries to it rather than inventing a new mechanism.

- [ ] **Step 3: Commit**

```bash
git add carpool-bot/src/main/java/com/carpool/bot/handler/helper/BotFlowHelper.java carpool-bot/src/main/java/com/carpool/bot/handler/SessionRecoveryHandler.java
git commit -m "feat: add Change Route button and route-edit session recovery"
```

---

## Task 13: Update module docs

**Files:**
- Modify: `carpool-service/CLAUDE.md`
- Modify: `carpool-bot/CLAUDE.md`

- [ ] **Step 1: Document the service + notification behavior**

In `carpool-service/CLAUDE.md`, add an "Update Route" subsection modeled on the existing "Update Departure Time" section: describe `RideService.updateRoute` (lock, guards, null-means-keep, `SameHubException`, event publish), `RideRouteChangedEvent`, `NotificationService.onRideRouteChanged` (confirmed-only, Keep/Cancel reuse, `RIDE_ROUTE_CHANGED` type), and `GroupNotificationService.onRideRouteChanged` (refresh, no 48h guard).

- [ ] **Step 2: Document the bot flow**

In `carpool-bot/CLAUDE.md`, add an "Edit Route Flow" subsection modeled on "Edit Departure Time Flow": the `EDIT_ROUTE_ORIGIN`/`EDIT_ROUTE_DEST` states, the callbacks, the `🔀 Change Route` button on the ride management card, the v1 "existing hubs only" simplification, and the reuse of `KEEP_BOOKING`/`CANCEL_BOOKING`.

- [ ] **Step 3: Commit**

```bash
git add carpool-service/CLAUDE.md carpool-bot/CLAUDE.md
git commit -m "docs: document route-change service, notifications, and bot flow"
```

---

## Self-Review (completed during authoring)

**Spec coverage:**
- Service `updateRoute` (spec §2) → Task 3.
- `RideRouteChangedEvent` (§3) → Task 1.
- `RIDE_ROUTE_CHANGED` type (§3) → Task 2.
- Passenger DMs, confirmed-only, Keep/Cancel reuse (§4) → Task 4.
- Group refresh, no 48h guard (§5) → Task 5.
- REST `PATCH /rides/{id}/route`, nullable fields, at-least-one guard, integration test (§6) → Tasks 6–7.
- Bot flow: entry from ride card, hub picker reuse, one-side-at-a-time (§7) → Tasks 8–12.
- Out-of-scope items (direction unchanged, no new booking state, no migration) → honored: `updateRoute` never touches `direction`; no schema change anywhere.

**Type consistency:** `updateRoute(Long, Long, Long, Long)` signature is identical across Task 3 (service + tests), Task 7 (controller), and Task 10 (bot). `RideRouteChangedEvent(Ride, String, String)` is consistent across Tasks 1, 4, 5. Callback strings (`EDIT_RIDE_ROUTE`, `EDIT_ROUTE_ORIGIN`, `EDIT_ROUTE_DEST`, `EDIT_HUB_ORIGIN`, `EDIT_HUB_DEST`, `RETYPE_EDIT_ORIGIN`, `RETYPE_EDIT_DEST`) match between Task 9 (button prefixes), Task 10 (handlers), Task 11 (registration), and Task 12 (session recovery).

**Known verification points flagged inline for the implementer:**
- `RideResponse` accessor names (`originHub()`, `destinationHub()`, `status()`, `driver().id()`) — Task 10.
- `BaseIntegrationTest` seeding/JWT helper names — Task 7.
- `Ride` entity has `setOriginHub` / `setDestinationHub` setters — Task 3 (consistent with existing `setDepartureTime`/`setStatus` usage).
- `MessageHandler` flow-switch idiom and `SessionRecoveryHandler` flow-sensitive collection shape — Tasks 11–12.
