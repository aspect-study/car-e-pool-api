# REST API Audit & Expansion — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose ratings, favorites, multi-vehicle management, and departure-time update to REST clients (web and mobile), fix two documentation bugs in BookingController, and correct underlying service-layer bugs found during review.

**Architecture:** New `VehicleController` and `RatingController` join existing controllers in `carpool-web`. Favorites endpoints added to `UserController`. Service bugs (wrong exception type, lazy init, N+1) fixed alongside the wiring. Two new typed exception classes added to `carpool-common` to replace raw `IllegalArgumentException`/`IllegalStateException` throws.

**Tech Stack:** Spring Boot 4, MapStruct, Spring Data JPA, OpenAPI/Swagger annotations, JUnit 5 + AssertJ integration tests against MySQL 3308.

---

## File Map

| Action | File |
|--------|------|
| NEW | `carpool-common/src/main/java/com/carpool/common/exception/RatingConflictException.java` |
| NEW | `carpool-common/src/main/java/com/carpool/common/exception/InvalidOperationException.java` |
| NEW | `carpool-service/src/main/java/com/carpool/service/dto/request/AddVehicleRequest.java` |
| NEW | `carpool-service/src/main/java/com/carpool/service/dto/request/SubmitRatingRequest.java` |
| NEW | `carpool-service/src/main/java/com/carpool/service/dto/request/UpdateDepartureTimeRequest.java` |
| NEW | `carpool-service/src/main/java/com/carpool/service/dto/response/UserSummaryResponse.java` |
| NEW | `carpool-service/src/main/java/com/carpool/service/dto/response/RatingResponse.java` |
| NEW | `carpool-service/src/main/java/com/carpool/service/dto/response/RatingEligibilityResponse.java` |
| EDIT | `carpool-repository/src/main/java/com/carpool/repository/RideRatingRepository.java` |
| EDIT | `carpool-service/src/main/java/com/carpool/service/mapper/EntityMapper.java` |
| EDIT | `carpool-service/src/main/java/com/carpool/service/rating/RatingService.java` |
| EDIT | `carpool-service/src/main/java/com/carpool/service/favorite/FavoriteService.java` |
| EDIT | `carpool-service/src/main/java/com/carpool/service/vehicle/VehicleService.java` |
| NEW | `carpool-web/src/main/java/com/carpool/web/controller/VehicleController.java` |
| NEW | `carpool-web/src/main/java/com/carpool/web/controller/RatingController.java` |
| EDIT | `carpool-web/src/main/java/com/carpool/web/controller/UserController.java` |
| EDIT | `carpool-web/src/main/java/com/carpool/web/controller/RideController.java` |
| EDIT | `carpool-web/src/main/java/com/carpool/web/controller/BookingController.java` |
| NEW | `carpool-web/src/test/java/com/carpool/web/integration/VehicleIntegrationTest.java` |
| NEW | `carpool-web/src/test/java/com/carpool/web/integration/RatingIntegrationTest.java` |
| NEW | `carpool-web/src/test/java/com/carpool/web/integration/FavoritesIntegrationTest.java` |

---

## Task 1: Typed Exception Classes

**Purpose:** Replace raw `IllegalArgumentException` and `IllegalStateException` throws in `RatingService` and `FavoriteService` with typed `CarpoolException` subclasses that `GlobalExceptionHandler` already knows how to handle — 400 and 409 respectively.

**Files:**
- Create: `carpool-common/src/main/java/com/carpool/common/exception/InvalidOperationException.java`
- Create: `carpool-common/src/main/java/com/carpool/common/exception/RatingConflictException.java`

- [ ] **Step 1: Create `InvalidOperationException`** (maps to 400 Bad Request)

```java
// carpool-common/src/main/java/com/carpool/common/exception/InvalidOperationException.java
package com.carpool.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidOperationException extends CarpoolException {
    public InvalidOperationException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_OPERATION", message);
    }
}
```

- [ ] **Step 2: Create `RatingConflictException`** (maps to 409 Conflict)

```java
// carpool-common/src/main/java/com/carpool/common/exception/RatingConflictException.java
package com.carpool.common.exception;

import org.springframework.http.HttpStatus;

public class RatingConflictException extends CarpoolException {
    public RatingConflictException(String message) {
        super(HttpStatus.CONFLICT, "RATING_CONFLICT", message);
    }
}
```

- [ ] **Step 3: Commit**

```
feat(common): add InvalidOperationException (400) and RatingConflictException (409)

Typed CarpoolException subclasses to replace raw IllegalArgumentException and
IllegalStateException in RatingService and FavoriteService. GlobalExceptionHandler
already handles CarpoolException — no handler changes needed.
```

---

## Task 2: Response DTOs

**Purpose:** Three new response records needed before any mapper or controller code can compile.

**Files:**
- Create: `carpool-service/src/main/java/com/carpool/service/dto/response/UserSummaryResponse.java`
- Create: `carpool-service/src/main/java/com/carpool/service/dto/response/RatingResponse.java`
- Create: `carpool-service/src/main/java/com/carpool/service/dto/response/RatingEligibilityResponse.java`

- [ ] **Step 1: Create `UserSummaryResponse`**

This DTO intentionally omits `plateNumber`, `carModel`, and `carColor`. It is used wherever user identity must be shown to third parties (e.g., rater/ratee in a public ratings list). The full `UserResponse` with vehicle fields must NOT appear there.

```java
// carpool-service/src/main/java/com/carpool/service/dto/response/UserSummaryResponse.java
package com.carpool.service.dto.response;

public record UserSummaryResponse(
        Long   id,
        String fullName,
        String telegramHandle,
        Double avgRating
) {}
```

- [ ] **Step 2: Create `RatingResponse`**

```java
// carpool-service/src/main/java/com/carpool/service/dto/response/RatingResponse.java
package com.carpool.service.dto.response;

import java.time.Instant;

public record RatingResponse(
        Long              id,
        Long              rideId,
        UserSummaryResponse rater,
        UserSummaryResponse ratee,
        int               stars,
        String            comment,
        String            raterRole,
        Instant           createdAt
) {}
```

- [ ] **Step 3: Create `RatingEligibilityResponse`**

```java
// carpool-service/src/main/java/com/carpool/service/dto/response/RatingEligibilityResponse.java
package com.carpool.service.dto.response;

import java.util.List;

public record RatingEligibilityResponse(
        boolean    canRate,
        List<Long> rateeIds
) {}
```

- [ ] **Step 4: Commit**

```
feat(service): add UserSummaryResponse, RatingResponse, RatingEligibilityResponse DTOs

UserSummaryResponse omits vehicle fields (plateNumber, carModel, carColor) for
plate-privacy compliance on the public ratings endpoint.
```

---

## Task 3: Request DTOs

**Purpose:** Three new request records required by the new endpoints.

**Files:**
- Create: `carpool-service/src/main/java/com/carpool/service/dto/request/AddVehicleRequest.java`
- Create: `carpool-service/src/main/java/com/carpool/service/dto/request/SubmitRatingRequest.java`
- Create: `carpool-service/src/main/java/com/carpool/service/dto/request/UpdateDepartureTimeRequest.java`

- [ ] **Step 1: Create `AddVehicleRequest`**

```java
// carpool-service/src/main/java/com/carpool/service/dto/request/AddVehicleRequest.java
package com.carpool.service.dto.request;

import jakarta.validation.constraints.*;

public record AddVehicleRequest(
        @NotBlank(message = "model is required")
        String model,

        String color,

        @NotBlank(message = "plateNumber is required")
        String plateNumber,

        @Min(value = 1, message = "seatCapacity must be at least 1")
        @Max(value = 8, message = "seatCapacity cannot exceed 8")
        Integer seatCapacity
) {}
```

- [ ] **Step 2: Create `SubmitRatingRequest`**

`stars` is `Integer` (not `int`) to prevent silent default of 0 when the field is missing from JSON — follows the same pattern as `totalSeats` in `CreateRideRequest`.

```java
// carpool-service/src/main/java/com/carpool/service/dto/request/SubmitRatingRequest.java
package com.carpool.service.dto.request;

import jakarta.validation.constraints.*;

public record SubmitRatingRequest(
        @NotNull(message = "rateeId is required")
        Long rateeId,

        @NotNull(message = "stars is required")
        @Min(value = 1, message = "stars must be at least 1")
        @Max(value = 5, message = "stars cannot exceed 5")
        Integer stars,

        @Size(max = 1000, message = "comment cannot exceed 1000 characters")
        String comment
) {}
```

- [ ] **Step 3: Create `UpdateDepartureTimeRequest`**

```java
// carpool-service/src/main/java/com/carpool/service/dto/request/UpdateDepartureTimeRequest.java
package com.carpool.service.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record UpdateDepartureTimeRequest(
        @NotNull(message = "newDepartureTime is required")
        @Future(message = "newDepartureTime must be in the future")
        LocalDateTime newDepartureTime
) {}
```

- [ ] **Step 4: Commit**

```
feat(service): add AddVehicleRequest, SubmitRatingRequest, UpdateDepartureTimeRequest DTOs

stars uses Integer (not int) to avoid silent 0 default when field is missing from JSON.
```

---

## Task 4: RideRatingRepository — JOIN FETCH Query

**Purpose:** The existing `findByRateeIdOrderByCreatedAtDesc(Pageable)` method returns bare entities with LAZY associations (`rater`, `ride`). After the transaction closes, the mapper will throw `LazyInitializationException`. Fix with a JOIN FETCH query that loads all needed associations in one SQL statement and eliminates the N+1 problem.

**Files:**
- Modify: `carpool-repository/src/main/java/com/carpool/repository/RideRatingRepository.java`

- [ ] **Step 1: Add JOIN FETCH method to `RideRatingRepository`**

Add the following method after the existing `findByRateeIdOrderByCreatedAtDesc(Long, Pageable)` declaration:

```java
@Query("SELECT r FROM RideRating r " +
       "JOIN FETCH r.rater " +
       "JOIN FETCH r.ratee " +
       "JOIN FETCH r.ride " +
       "WHERE r.ratee.id = :rateeId " +
       "ORDER BY r.createdAt DESC")
Page<RideRating> findByRateeIdWithAssociations(
        @Param("rateeId") Long rateeId, Pageable pageable);
```

The full file after this addition:

```java
package com.carpool.repository;

import com.carpool.domain.entity.RideRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface RideRatingRepository extends JpaRepository<RideRating, Long> {

    boolean existsByRideIdAndRaterId(Long rideId, Long raterId);

    Optional<RideRating> findByRideIdAndRaterId(Long rideId, Long raterId);

    List<RideRating> findByRateeIdOrderByCreatedAtDesc(Long rateeId);

    Page<RideRating> findByRateeIdOrderByCreatedAtDesc(Long rateeId, Pageable pageable);

    @Query("SELECT r FROM RideRating r " +
           "JOIN FETCH r.rater " +
           "JOIN FETCH r.ratee " +
           "JOIN FETCH r.ride " +
           "WHERE r.ratee.id = :rateeId " +
           "ORDER BY r.createdAt DESC")
    Page<RideRating> findByRateeIdWithAssociations(
            @Param("rateeId") Long rateeId, Pageable pageable);

    @Query("SELECT AVG(r.stars) FROM RideRating r WHERE r.ratee.id = :rateeId")
    Double findAverageRatingByRateeId(@Param("rateeId") Long rateeId);

    long countByRateeId(Long rateeId);

    @Query("SELECT AVG(r.stars) FROM RideRating r " +
            "WHERE r.ratee.id = :rateeId AND r.raterRole = 'PASSENGER'")
    Double findAverageDriverRatingByRateeId(@Param("rateeId") Long rateeId);

    @Query("SELECT AVG(r.stars) FROM RideRating r " +
            "WHERE r.ratee.id = :rateeId AND r.raterRole = 'DRIVER'")
    Double findAveragePassengerRatingByRateeId(@Param("rateeId") Long rateeId);

    @Query("SELECT COUNT(r) FROM RideRating r " +
            "WHERE r.ratee.id = :rateeId AND r.raterRole = 'PASSENGER'")
    long countDriverRatingsByRateeId(@Param("rateeId") Long rateeId);

    @Query("SELECT COUNT(r) FROM RideRating r " +
            "WHERE r.ratee.id = :rateeId AND r.raterRole = 'DRIVER'")
    long countPassengerRatingsByRateeId(@Param("rateeId") Long rateeId);

    List<RideRating> findByRaterIdOrderByCreatedAtDesc(Long raterId);

    boolean existsByRideIdAndRaterIdAndRateeId(Long rideId, Long raterId, Long rateeId);

    @Query("SELECT r.ratee.id FROM RideRating r WHERE r.ride.id = :rideId AND r.rater.id = :raterId")
    Set<Long> findRateeIdsByRideIdAndRaterId(@Param("rideId") Long rideId, @Param("raterId") Long raterId);

    @Query("SELECT r.ratee.id, AVG(r.stars) FROM RideRating r " +
            "WHERE r.ratee.id IN :driverIds AND r.raterRole = 'PASSENGER' " +
            "GROUP BY r.ratee.id")
    List<Object[]> findAverageRatingsByDriverIds(@Param("driverIds") List<Long> driverIds);
}
```

- [ ] **Step 2: Commit**

```
fix(repository): add JOIN FETCH query for ratings list to prevent LazyInitializationException

findByRateeIdWithAssociations eagerly loads rater, ratee, and ride in one query.
Required because open-in-view is disabled project-wide.
```

---

## Task 5: EntityMapper — toRatingResponse and toUserSummaryResponse

**Purpose:** MapStruct cannot auto-map nested `ride.id → rideId` without an explicit `@Mapping` annotation. Also adds mapping from `User` to `UserSummaryResponse`. `avgRating` on `UserSummaryResponse` is computed, not a direct field — must be ignored and populated by the controller/service.

**Files:**
- Modify: `carpool-service/src/main/java/com/carpool/service/mapper/EntityMapper.java`

- [ ] **Step 1: Add mappings to `EntityMapper`**

Replace the existing `EntityMapper.java` content with:

```java
package com.carpool.service.mapper;

import com.carpool.domain.entity.*;
import com.carpool.service.dto.response.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EntityMapper {

    UserResponse toUserResponse(User user);

    @Mapping(target = "avgRating", ignore = true)
    UserSummaryResponse toUserSummaryResponse(User user);

    HubResponse toHubResponse(Hub hub);

    WaypointResponse toWaypointResponse(RideWaypoint waypoint);

    VehicleResponse toVehicleResponse(Vehicle vehicle);

    @Mapping(target = "waypoints", source = "waypoints")
    @Mapping(target = "driverAvgRating", ignore = true)
    @Mapping(target = "vehicle", source = "vehicle")
    RideResponse toRideResponse(Ride ride);

    @Mapping(target = "rideId",           source = "ride.id")
    @Mapping(target = "ride",             source = "ride")
    @Mapping(target = "passengerMessage", source = "passengerMessage")
    @Mapping(target = "expiresAt",        source = "expiresAt")
    BookingResponse toBookingResponse(Booking booking);

    @Mapping(target = "rideId", source = "ride.id")
    RatingResponse toRatingResponse(RideRating rating);
}
```

Note: `avgRating` on `UserSummaryResponse` is `ignore = true` here. The `RatingController` will populate it separately using the existing `RatingService.getDriverRatingLabel()` or a direct repository query. Alternatively, map it to null and let the client treat null as "no ratings yet" — this is acceptable for MVP.

- [ ] **Step 2: Commit**

```
feat(service): add toRatingResponse and toUserSummaryResponse to EntityMapper

@Mapping(source="ride.id", target="rideId") is required — MapStruct cannot auto-map
nested id fields and will silently produce null without this annotation.
```

---

## Task 6: RatingService — Fix Lazy Init and Replace Raw Exceptions

**Purpose:** `getRatingsReceivedPaged` currently only initializes `ratee` in the forEach loop. `rater` and `ride` remain as uninitialized LAZY proxies — the mapper will throw `LazyInitializationException` when accessing them after the transaction closes. Also replace all raw `IllegalArgumentException`/`IllegalStateException` throws with the new typed exceptions from Task 1.

**Files:**
- Modify: `carpool-service/src/main/java/com/carpool/service/rating/RatingService.java`

- [ ] **Step 1: Update imports in `RatingService`**

Add to the imports block (replace the raw exceptions):

```java
import com.carpool.common.exception.InvalidOperationException;
import com.carpool.common.exception.RatingConflictException;
```

Remove any existing `import com.carpool.common.exception.InvalidRideStateException;` if not used elsewhere in the file.

- [ ] **Step 2: Replace raw exceptions in `submitRating`**

Find and replace all throws in `submitRating`:

| Old throw | New throw |
|-----------|-----------|
| `throw new IllegalArgumentException("Stars must be between 1 and 5.")` | `throw new InvalidOperationException("Stars must be between 1 and 5.")` |
| `throw new IllegalArgumentException("Comment is too long...")` | `throw new InvalidOperationException("Comment is too long. Maximum 1000 characters allowed.")` |
| `throw new IllegalArgumentException("Ride not found: " + rideId)` | `throw new InvalidOperationException("Ride not found: " + rideId)` |
| `throw new IllegalStateException("Ratings can only be submitted for completed rides.")` | `throw new RatingConflictException("Ratings can only be submitted for completed rides.")` |
| `throw new IllegalStateException("You have already rated this passenger.")` | `throw new RatingConflictException("You have already rated this passenger.")` |
| `throw new IllegalStateException("You have already rated this ride.")` | `throw new RatingConflictException("You have already rated this ride.")` |
| `throw new IllegalArgumentException("Rater not found: " + raterId)` | `throw new InvalidOperationException("Rater not found: " + raterId)` |
| `throw new IllegalArgumentException("Ratee not found: " + rateeId)` | `throw new InvalidOperationException("Ratee not found: " + rateeId)` |

- [ ] **Step 3: Replace raw exceptions in `getRateeIds`**

| Old throw | New throw |
|-----------|-----------|
| `throw new IllegalArgumentException("Ride not found: " + rideId)` | `throw new InvalidOperationException("Ride not found: " + rideId)` |
| `throw new IllegalStateException("No confirmed passengers found for ride: " + rideId)` | `throw new InvalidOperationException("No confirmed passengers found for ride: " + rideId)` |

- [ ] **Step 4: Fix `getRatingsReceivedPaged` — extend forEach and switch to JOIN FETCH**

Replace the current `getRatingsReceivedPaged` method:

```java
// BEFORE (only initializes ratee — rater and ride still lazy):
@Transactional(readOnly = true)
public Page<RideRating> getRatingsReceivedPaged(Long userId, int page, int pageSize) {
    Page<RideRating> result = ratingRepository.findByRateeIdOrderByCreatedAtDesc(
            userId, PageRequest.of(page, pageSize));
    result.getContent().forEach(r -> r.getRatee().getFullName());
    return result;
}
```

With:

```java
// AFTER: uses JOIN FETCH query — all associations loaded in one query, no N+1
@Transactional(readOnly = true)
public Page<RideRating> getRatingsReceivedPaged(Long userId, int page, int pageSize) {
    return ratingRepository.findByRateeIdWithAssociations(
            userId, PageRequest.of(page, pageSize));
}
```

The JOIN FETCH query added in Task 4 loads `rater`, `ratee`, and `ride` eagerly — the forEach proxy-init loop is no longer needed.

- [ ] **Step 5: Commit**

```
fix(service): fix LazyInitializationException in getRatingsReceivedPaged, replace raw exceptions

getRatingsReceivedPaged now uses JOIN FETCH query — loads rater, ratee, and ride
in one query instead of N+1. Raw IllegalArgumentException/IllegalStateException
replaced with typed InvalidOperationException/RatingConflictException.
```

---

## Task 7: FavoriteService — Add getMyFavoritesAsDtos and Replace Raw Exceptions

**Purpose:** `getMyFavorites()` returns `List<UserFavorite>` (domain entities) — controllers cannot and must not depend on domain entities. Add `getMyFavoritesAsDtos()` returning `List<FollowerResponse>` (mirrors existing `getFollowers()`). Also replace raw `IllegalArgumentException` throws with `InvalidOperationException`.

**Files:**
- Modify: `carpool-service/src/main/java/com/carpool/service/favorite/FavoriteService.java`

- [ ] **Step 1: Add import**

Add to imports:
```java
import com.carpool.common.exception.InvalidOperationException;
```

- [ ] **Step 2: Replace raw exceptions in `saveFavorite`**

| Old | New |
|-----|-----|
| `throw new IllegalArgumentException("You cannot save yourself as a favorite.")` | `throw new InvalidOperationException("You cannot save yourself as a favorite.")` |
| `throw new IllegalArgumentException("Follower not found: " + followerId)` | `throw new InvalidOperationException("Follower not found: " + followerId)` |
| `throw new IllegalArgumentException("Favorite user not found: " + favoriteId)` | `throw new InvalidOperationException("Favorite user not found: " + favoriteId)` |

- [ ] **Step 3: Add `getMyFavoritesAsDtos` method**

Add after the existing `getMyFavorites()` method:

```java
@Transactional(readOnly = true)
public List<FollowerResponse> getMyFavoritesAsDtos(Long followerId) {
    return favoriteRepository.findByFollowerIdOrderByCreatedAtDesc(followerId)
            .stream()
            .map(uf -> new FollowerResponse(
                    uf.getFavorite().getId(),
                    uf.getFavorite().getFullName(),
                    uf.getFavorite().getTelegramHandle(),
                    uf.getCreatedAt()))
            .toList();
}
```

- [ ] **Step 4: Commit**

```
fix(service): add getMyFavoritesAsDtos to FavoriteService, replace raw exceptions

Controllers cannot use domain entities — getMyFavoritesAsDtos returns List<FollowerResponse>
matching the existing getFollowers() pattern. Raw IllegalArgumentException replaced with
typed InvalidOperationException.
```

---

## Task 8: VehicleService — Fix Ownership Exception

**Purpose:** `removeVehicle` throws `InvalidRideStateException` (→ 400) for ownership failure. Authorization failure should be 403. Fix to throw `NotRideOwnerException`.

**Files:**
- Modify: `carpool-service/src/main/java/com/carpool/service/vehicle/VehicleService.java`

- [ ] **Step 1: Add import**

Add to imports:
```java
import com.carpool.common.exception.NotRideOwnerException;
```

- [ ] **Step 2: Fix the ownership check in `removeVehicle`**

Find this block in `removeVehicle`:
```java
if (!vehicle.getUser().getId().equals(userId)) {
    throw new InvalidRideStateException("You can only remove your own vehicles.");
}
```

Replace with:
```java
if (!vehicle.getUser().getId().equals(userId)) {
    throw new NotRideOwnerException();
}
```

Also fix the "Vehicle not found" throw to use `ResourceNotFoundException` instead of `InvalidRideStateException`:

Find:
```java
Vehicle vehicle = vehicleRepository.findById(vehicleId)
        .orElseThrow(() -> new InvalidRideStateException("Vehicle not found."));
```

Replace with:
```java
Vehicle vehicle = vehicleRepository.findById(vehicleId)
        .orElseThrow(() -> new com.carpool.common.exception.ResourceNotFoundException(
                "Vehicle not found: " + vehicleId));
```

(Import `ResourceNotFoundException` if not already imported.)

- [ ] **Step 3: Commit**

```
fix(service): VehicleService.removeVehicle throws 403 NotRideOwnerException for ownership failure

Was throwing InvalidRideStateException (400). Authorization failure must be 403.
Also changed vehicle-not-found to throw ResourceNotFoundException (404).
```

---

## Task 9: VehicleController

**Purpose:** Expose multi-vehicle management via `GET/POST/DELETE /api/v1/users/me/vehicles`.

**Files:**
- Create: `carpool-web/src/main/java/com/carpool/web/controller/VehicleController.java`

- [ ] **Step 1: Create `VehicleController`**

```java
package com.carpool.web.controller;

import com.carpool.common.response.ApiResponse;
import com.carpool.service.dto.request.AddVehicleRequest;
import com.carpool.service.dto.response.VehicleResponse;
import com.carpool.service.vehicle.VehicleService;
import com.carpool.web.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users/me/vehicles")
@RequiredArgsConstructor
@Tag(name = "Vehicles", description = "Multi-vehicle management for drivers")
public class VehicleController {

    private final VehicleService vehicleService;

    @Operation(summary = "List my vehicles",
            description = """
                Returns all active vehicles registered by the authenticated driver.
                Ordered oldest-first (matches bot vehicle selection order).
                Returns empty list if no vehicles registered.
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "List of registered vehicles")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "Not authenticated")
    @GetMapping
    public ResponseEntity<ApiResponse<List<VehicleResponse>>> listMyVehicles(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        return ResponseEntity.ok(ApiResponse.ok(
                vehicleService.getActiveVehiclesForUser(currentUser.getUserId())));
    }

    @Operation(summary = "Add a vehicle",
            description = """
                Registers a new vehicle for the authenticated driver.
                
                - Maximum **3 active vehicles** — adding a 4th soft-deletes the oldest (replace-oldest policy)
                - `plateNumber` is normalized to uppercase
                - `seatCapacity` defaults to **4** if omitted
                
                Returns 409 if the plate number is already registered by another user.
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "Vehicle added")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "Plate number already registered by another user")
    @PostMapping
    public ResponseEntity<ApiResponse<VehicleResponse>> addVehicle(
            @Valid @RequestBody AddVehicleRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        VehicleResponse vehicle = vehicleService.addVehicle(
                currentUser.getUserId(),
                request.model(),
                request.color(),
                request.plateNumber(),
                request.seatCapacity());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(vehicle));
    }

    @Operation(summary = "Remove a vehicle",
            description = """
                Soft-deletes a specific vehicle owned by the authenticated driver.
                
                Returns 403 if the vehicle belongs to a different user.
                Returns 404 if the vehicle does not exist.
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Vehicle removed")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "Vehicle belongs to a different user")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "Vehicle not found")
    @DeleteMapping("/{vehicleId}")
    public ResponseEntity<ApiResponse<Void>> removeVehicle(
            @PathVariable Long vehicleId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        vehicleService.removeVehicle(vehicleId, currentUser.getUserId());
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
```

- [ ] **Step 2: Commit**

```
feat(web): add VehicleController — GET/POST/DELETE /api/v1/users/me/vehicles
```

---

## Task 10: RatingController

**Purpose:** Expose rating submission, ratings list (paginated), and eligibility check.

**Files:**
- Create: `carpool-web/src/main/java/com/carpool/web/controller/RatingController.java`

- [ ] **Step 1: Create `RatingController`**

```java
package com.carpool.web.controller;

import com.carpool.common.response.ApiResponse;
import com.carpool.common.response.PagedResponse;
import com.carpool.service.dto.request.SubmitRatingRequest;
import com.carpool.service.dto.response.RatingEligibilityResponse;
import com.carpool.service.dto.response.RatingResponse;
import com.carpool.service.mapper.EntityMapper;
import com.carpool.service.rating.RatingService;
import com.carpool.web.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Ratings", description = "Driver and passenger ratings")
public class RatingController {

    private final RatingService ratingService;
    private final EntityMapper  mapper;

    @Operation(summary = "Submit a rating",
            description = """
                Submit a star rating (1–5) for a completed ride.
                
                - Passengers rate the driver (one rating per ride)
                - Drivers rate each confirmed passenger individually
                - Returns **409** if already rated, or if the ride is not COMPLETED
                - Returns **400** if `rideId` or `rateeId` is not found
                
                Check `/rides/{rideId}/ratings/eligibility` first to get valid `rateeId` values.
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "Rating submitted")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", description = "Ride or user not found")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "Already rated, or ride not COMPLETED")
    @PostMapping("/rides/{rideId}/ratings")
    public ResponseEntity<ApiResponse<RatingResponse>> submitRating(
            @PathVariable Long rideId,
            @Valid @RequestBody SubmitRatingRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        RatingResponse rating = mapper.toRatingResponse(
                ratingService.submitRating(
                        rideId,
                        currentUser.getUserId(),
                        request.rateeId(),
                        request.stars(),
                        request.comment()));

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(rating));
    }

    @Operation(summary = "Get ratings received by a user",
            description = """
                Returns a paginated list of all ratings received by the specified user.
                Ordered newest-first.
                
                **Pagination:** `page` (default 0), `size` (default 10, max 50)
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Paginated list of ratings")
    @GetMapping("/users/{userId}/ratings")
    public ResponseEntity<ApiResponse<PagedResponse<RatingResponse>>> getRatings(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        Page<RatingResponse> ratings = ratingService
                .getRatingsReceivedPaged(userId, page, Math.min(size, 50))
                .map(mapper::toRatingResponse);

        return ResponseEntity.ok(ApiResponse.ok(PagedResponse.of(ratings)));
    }

    @Operation(summary = "Check rating eligibility for a ride",
            description = """
                Returns whether the authenticated user can rate a specific ride,
                and the list of user IDs they are eligible to rate.
                
                Clients should call this before showing the rating UI.
                
                - `canRate: false` — ride not COMPLETED, or user already rated everyone
                - `rateeIds` — for passengers: `[driverId]`; for drivers: list of passenger IDs not yet rated
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Eligibility result")
    @GetMapping("/rides/{rideId}/ratings/eligibility")
    public ResponseEntity<ApiResponse<RatingEligibilityResponse>> checkEligibility(
            @PathVariable Long rideId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        Long raterId = currentUser.getUserId();
        boolean canRate = ratingService.canRate(rideId, raterId);
        java.util.List<Long> rateeIds = canRate
                ? ratingService.getRateeIds(rideId, raterId)
                : java.util.List.of();

        return ResponseEntity.ok(ApiResponse.ok(
                new RatingEligibilityResponse(canRate, rateeIds)));
    }
}
```

- [ ] **Step 2: Commit**

```
feat(web): add RatingController — POST ratings, GET ratings, GET eligibility
```

---

## Task 11: UserController — Favorites and Followers Endpoints

**Purpose:** Add 4 endpoints for favorites/followers management to the existing `UserController`.

**Files:**
- Modify: `carpool-web/src/main/java/com/carpool/web/controller/UserController.java`

- [ ] **Step 1: Add `FavoriteService` import and field**

Add to the imports in `UserController.java`:
```java
import com.carpool.service.dto.response.FollowerResponse;
import com.carpool.service.favorite.FavoriteService;
import java.util.List;
```

Add `FavoriteService` to the constructor fields (Lombok `@RequiredArgsConstructor` handles injection):
```java
private final FavoriteService favoriteService;
```

- [ ] **Step 2: Add 4 favorites/followers methods to `UserController`**

Add these 4 methods at the end of `UserController` (before the closing `}`):

```java
@Operation(summary = "Save a user as favorite",
        description = """
            Save another user as a favorite driver/passenger.
            Idempotent — no error if already saved.
            Returns 400 if trying to save yourself.
            """,
        security = @SecurityRequirement(name = "bearerAuth"))
@io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200", description = "Saved (or already saved)")
@io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400", description = "Cannot favorite yourself")
@PostMapping("/{userId}/favorite")
public ResponseEntity<ApiResponse<Void>> saveFavorite(
        @PathVariable Long userId,
        @AuthenticationPrincipal AuthenticatedUser currentUser) {

    favoriteService.saveFavorite(currentUser.getUserId(), userId);
    return ResponseEntity.ok(ApiResponse.ok());
}

@Operation(summary = "Remove a user from favorites",
        description = """
            Remove a saved favorite. Idempotent — no error if not found.
            """,
        security = @SecurityRequirement(name = "bearerAuth"))
@io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200", description = "Removed (or was not saved)")
@DeleteMapping("/{userId}/favorite")
public ResponseEntity<ApiResponse<Void>> removeFavorite(
        @PathVariable Long userId,
        @AuthenticationPrincipal AuthenticatedUser currentUser) {

    favoriteService.removeFavorite(currentUser.getUserId(), userId);
    return ResponseEntity.ok(ApiResponse.ok());
}

@Operation(summary = "Get my favorites",
        description = """
            Returns all users saved as favorites by the authenticated user.
            Ordered newest-first.
            """,
        security = @SecurityRequirement(name = "bearerAuth"))
@io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200", description = "List of saved favorites")
@GetMapping("/me/favorites")
public ResponseEntity<ApiResponse<List<FollowerResponse>>> getMyFavorites(
        @AuthenticationPrincipal AuthenticatedUser currentUser) {

    return ResponseEntity.ok(ApiResponse.ok(
            favoriteService.getMyFavoritesAsDtos(currentUser.getUserId())));
}

@Operation(summary = "Get my followers",
        description = """
            Returns all users who have saved the authenticated user as a favorite.
            Ordered newest-first.
            """,
        security = @SecurityRequirement(name = "bearerAuth"))
@io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200", description = "List of followers")
@GetMapping("/me/followers")
public ResponseEntity<ApiResponse<List<FollowerResponse>>> getMyFollowers(
        @AuthenticationPrincipal AuthenticatedUser currentUser) {

    return ResponseEntity.ok(ApiResponse.ok(
            favoriteService.getFollowers(currentUser.getUserId())));
}
```

- [ ] **Step 3: Commit**

```
feat(web): add favorites/followers endpoints to UserController

POST/DELETE /users/{userId}/favorite and GET /users/me/favorites, /users/me/followers
```

---

## Task 12: RideController — Departure Time Endpoint

**Purpose:** Expose `PATCH /api/v1/rides/{id}/departure-time` — delegates to existing `RideService.updateDepartureTime()` which already fires `RideTimeChangedEvent` to notify passengers.

**Files:**
- Modify: `carpool-web/src/main/java/com/carpool/web/controller/RideController.java`

- [ ] **Step 1: Add import for `UpdateDepartureTimeRequest`**

Add to imports in `RideController.java`:
```java
import com.carpool.service.dto.request.UpdateDepartureTimeRequest;
```

- [ ] **Step 2: Add `updateDepartureTime` method to `RideController`**

Add after the `reannounceRide` method (before the closing `}`):

```java
@Operation(summary = "Update ride departure time",
        description = """
            Driver updates the departure time of an ACTIVE or FULL ride.
            
            - Notifies all confirmed passengers via Telegram automatically
            - Updates the community group post with the new time
            
            **Constraints:**
            - New time must be at least 15 minutes from now
            - New time cannot equal the current departure time
            - Ride must be ACTIVE or FULL
            - Only the ride owner can update the time
            """,
        security = @SecurityRequirement(name = "bearerAuth"))
@io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200", description = "Departure time updated — passengers notified")
@io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400", description = "Invalid time or ride not in updatable state")
@io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403", description = "Not the ride owner")
@io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404", description = "Ride not found")
@PatchMapping("/{id}/departure-time")
public ResponseEntity<ApiResponse<RideResponse>> updateDepartureTime(
        @PathVariable Long id,
        @Valid @RequestBody UpdateDepartureTimeRequest request,
        @AuthenticationPrincipal AuthenticatedUser currentUser) {

    RideResponse ride = rideService.updateDepartureTime(
            id, request.newDepartureTime(), currentUser.getUserId());
    return ResponseEntity.ok(ApiResponse.ok(ride));
}
```

- [ ] **Step 3: Commit**

```
feat(web): add PATCH /rides/{id}/departure-time to RideController

Delegates to RideService.updateDepartureTime() which fires RideTimeChangedEvent
to notify passengers and refresh the community group post.
```

---

## Task 13: BookingController — Documentation Fixes

**Purpose:** Fix two documentation bugs: wrong description on `GET /bookings/mine`, and missing `@ApiResponse` annotations on `PATCH /bookings/{id}/payment`.

**Files:**
- Modify: `carpool-web/src/main/java/com/carpool/web/controller/BookingController.java`

- [ ] **Step 1: Fix `GET /bookings/mine` description**

Find this line inside the `getMyBookings` method's `@Operation`:
```java
@io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200", description = "Driver's ride history")
```

Replace with:
```java
@io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200", description = "Passenger's booking history")
```

- [ ] **Step 2: Add missing `@ApiResponse` annotations on `PATCH /bookings/{id}/payment`**

Find the `updatePayment` method. It currently has no `@ApiResponse` annotations. Add them between `@Operation(...)` and `@PatchMapping`:

```java
@io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200", description = "Payment recorded")
@io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403", description = "Not the ride owner")
@io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404", description = "Booking not found")
```

- [ ] **Step 3: Commit**

```
fix(web): correct BookingController documentation bugs

GET /bookings/mine description was "Driver's ride history" — fixed to "Passenger's booking history".
PATCH /bookings/{id}/payment was missing all @ApiResponse annotations — added 200, 403, 404.
```

---

## Task 14: VehicleIntegrationTest

**Files:**
- Create: `carpool-web/src/test/java/com/carpool/web/integration/VehicleIntegrationTest.java`

- [ ] **Step 1: Create `VehicleIntegrationTest`**

```java
package com.carpool.web.integration;

import com.carpool.domain.entity.Hub;
import com.carpool.domain.entity.User;
import com.carpool.domain.enums.UserRole;
import com.carpool.domain.enums.UserStatus;
import com.carpool.repository.HubRepository;
import com.carpool.repository.UserRepository;
import com.carpool.repository.VehicleRepository;
import com.carpool.service.dto.response.VehicleResponse;
import com.carpool.service.vehicle.VehicleService;
import com.carpool.common.exception.NotRideOwnerException;
import com.carpool.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Vehicle Integration")
class VehicleIntegrationTest extends BaseIntegrationTest {

    @Autowired private VehicleService    vehicleService;
    @Autowired private UserRepository    userRepository;
    @Autowired private VehicleRepository vehicleRepository;

    private User driver;
    private User otherDriver;

    @BeforeEach
    void setUp() {
        long seed = System.currentTimeMillis();

        driver = userRepository.save(User.builder()
                .telegramId(seed + 10).fullName("Driver A")
                .role(UserRole.DRIVER).status(UserStatus.ACTIVE).build());

        otherDriver = userRepository.save(User.builder()
                .telegramId(seed + 11).fullName("Driver B")
                .role(UserRole.DRIVER).status(UserStatus.ACTIVE).build());
    }

    @Test
    @DisplayName("addVehicle — happy path returns VehicleResponse with plateNumber")
    void addVehicle_returnsResponse() {
        VehicleResponse response = vehicleService.addVehicle(
                driver.getId(), "Toyota Vios", "Silver", "ABC 1234", 4);

        assertThat(response.id()).isNotNull();
        assertThat(response.plateNumber()).isEqualTo("ABC 1234");
        assertThat(response.model()).isEqualTo("Toyota Vios");
        assertThat(response.seatCapacity()).isEqualTo(4);
    }

    @Test
    @DisplayName("addVehicle — 4th vehicle soft-deletes oldest (replace-oldest policy)")
    void addVehicle_replaceOldestAtLimit() {
        vehicleService.addVehicle(driver.getId(), "Model A", null, "PLATE1", 4);
        vehicleService.addVehicle(driver.getId(), "Model B", null, "PLATE2", 4);
        vehicleService.addVehicle(driver.getId(), "Model C", null, "PLATE3", 4);
        vehicleService.addVehicle(driver.getId(), "Model D", null, "PLATE4", 4);

        List<VehicleResponse> active = vehicleService.getActiveVehiclesForUser(driver.getId());

        assertThat(active).hasSize(3);
        assertThat(active).noneMatch(v -> v.plateNumber().equals("PLATE1"));
        assertThat(active).anyMatch(v -> v.plateNumber().equals("PLATE4"));
    }

    @Test
    @DisplayName("addVehicle — duplicate plate for another user throws 400")
    void addVehicle_duplicatePlate_throwsBadRequest() {
        vehicleService.addVehicle(otherDriver.getId(), "Model X", null, "DUPE123", 4);

        assertThatThrownBy(() ->
                vehicleService.addVehicle(driver.getId(), "Model Y", null, "DUPE123", 4))
                .isInstanceOf(com.carpool.common.exception.CarpoolException.class);
    }

    @Test
    @DisplayName("getActiveVehiclesForUser — returns ordered list oldest-first")
    void getActiveVehiclesForUser_orderedOldestFirst() {
        vehicleService.addVehicle(driver.getId(), "First",  null, "FIRST1", 4);
        vehicleService.addVehicle(driver.getId(), "Second", null, "SECOND2", 4);

        List<VehicleResponse> list = vehicleService.getActiveVehiclesForUser(driver.getId());

        assertThat(list).hasSize(2);
        assertThat(list.get(0).plateNumber()).isEqualTo("FIRST1");
        assertThat(list.get(1).plateNumber()).isEqualTo("SECOND2");
    }

    @Test
    @DisplayName("removeVehicle — own vehicle returns cleanly")
    void removeVehicle_ownVehicle_succeeds() {
        VehicleResponse v = vehicleService.addVehicle(
                driver.getId(), "Model Z", null, "OWN999", 4);

        assertThatCode(() -> vehicleService.removeVehicle(v.id(), driver.getId()))
                .doesNotThrowAnyException();

        assertThat(vehicleService.getActiveVehiclesForUser(driver.getId())).isEmpty();
    }

    @Test
    @DisplayName("removeVehicle — other user's vehicle throws 403 NotRideOwnerException")
    void removeVehicle_otherUsersVehicle_throws403() {
        VehicleResponse v = vehicleService.addVehicle(
                otherDriver.getId(), "Other Model", null, "OTHER1", 4);

        assertThatThrownBy(() -> vehicleService.removeVehicle(v.id(), driver.getId()))
                .isInstanceOf(NotRideOwnerException.class);
    }

    @Test
    @DisplayName("removeVehicle — non-existent vehicle throws 404")
    void removeVehicle_notFound_throws404() {
        assertThatThrownBy(() -> vehicleService.removeVehicle(99999L, driver.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
```

- [ ] **Step 2: Run the test to confirm all pass**

```
mvn test -pl carpool-web -Dtest=VehicleIntegrationTest
```

Expected: All 6 tests PASS.

- [ ] **Step 3: Commit**

```
test(web): add VehicleIntegrationTest — addVehicle, replace-oldest, duplicate plate, removeVehicle 403/404
```

---

## Task 15: RatingIntegrationTest

**Files:**
- Create: `carpool-web/src/test/java/com/carpool/web/integration/RatingIntegrationTest.java`

- [ ] **Step 1: Create `RatingIntegrationTest`**

```java
package com.carpool.web.integration;

import com.carpool.common.exception.RatingConflictException;
import com.carpool.domain.entity.*;
import com.carpool.domain.enums.*;
import com.carpool.repository.*;
import com.carpool.service.dto.response.RatingEligibilityResponse;
import com.carpool.service.mapper.EntityMapper;
import com.carpool.service.rating.RatingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Rating Integration")
class RatingIntegrationTest extends BaseIntegrationTest {

    @Autowired private RatingService     ratingService;
    @Autowired private EntityMapper      mapper;
    @Autowired private UserRepository    userRepository;
    @Autowired private HubRepository     hubRepository;
    @Autowired private RideRepository    rideRepository;
    @Autowired private BookingRepository bookingRepository;

    private User driver;
    private User passenger;
    private Ride completedRide;
    private Booking confirmedBooking;

    @BeforeEach
    void setUp() {
        long seed = System.currentTimeMillis();

        driver = userRepository.save(User.builder()
                .telegramId(seed + 20).fullName("Rating Driver")
                .role(UserRole.DRIVER).status(UserStatus.ACTIVE).build());

        passenger = userRepository.save(User.builder()
                .telegramId(seed + 21).fullName("Rating Passenger")
                .role(UserRole.PASSENGER).status(UserStatus.ACTIVE).build());

        Hub origin = hubRepository.findByCode("AYALA_MRT").orElseThrow();
        Hub destination = hubRepository.findByCode("BGC_HIGH_STREET").orElseThrow();

        completedRide = rideRepository.save(Ride.builder()
                .driver(driver)
                .originHub(origin)
                .destinationHub(destination)
                .direction(RideDirection.HOME_TO_WORK)
                .departureTime(LocalDateTime.now().minusHours(2))
                .totalSeats(4)
                .availableSeats(3)
                .status(RideStatus.COMPLETED)
                .contributionAmount(BigDecimal.valueOf(100))
                .build());

        confirmedBooking = bookingRepository.save(Booking.builder()
                .ride(completedRide)
                .passenger(passenger)
                .status(BookingStatus.COMPLETED)
                .build());
    }

    @Test
    @DisplayName("submitRating — passenger rates driver, returns saved RideRating")
    void submitRating_passengerRatesDriver_succeeds() {
        var rating = ratingService.submitRating(
                completedRide.getId(), passenger.getId(), driver.getId(), 5, "Great driver!");

        assertThat(rating.getId()).isNotNull();
        assertThat(rating.getStars()).isEqualTo(5);
        assertThat(rating.getRaterRole()).isEqualTo("PASSENGER");
    }

    @Test
    @DisplayName("submitRating — duplicate rating throws 409 RatingConflictException")
    void submitRating_duplicate_throws409() {
        ratingService.submitRating(
                completedRide.getId(), passenger.getId(), driver.getId(), 4, null);

        assertThatThrownBy(() ->
                ratingService.submitRating(
                        completedRide.getId(), passenger.getId(), driver.getId(), 3, null))
                .isInstanceOf(RatingConflictException.class);
    }

    @Test
    @DisplayName("submitRating — non-completed ride throws 409 RatingConflictException")
    void submitRating_nonCompletedRide_throws409() {
        Hub origin = hubRepository.findByCode("AYALA_MRT").orElseThrow();
        Hub destination = hubRepository.findByCode("BGC_HIGH_STREET").orElseThrow();

        Ride activeRide = rideRepository.save(Ride.builder()
                .driver(driver)
                .originHub(origin)
                .destinationHub(destination)
                .direction(RideDirection.HOME_TO_WORK)
                .departureTime(LocalDateTime.now().plusHours(2))
                .totalSeats(4)
                .availableSeats(4)
                .status(RideStatus.ACTIVE)
                .contributionAmount(BigDecimal.valueOf(100))
                .build());

        assertThatThrownBy(() ->
                ratingService.submitRating(
                        activeRide.getId(), passenger.getId(), driver.getId(), 5, null))
                .isInstanceOf(RatingConflictException.class);
    }

    @Test
    @DisplayName("getRatingsReceivedPaged — no LazyInitializationException (regression test)")
    void getRatingsReceivedPaged_noLazyInitException() {
        ratingService.submitRating(
                completedRide.getId(), passenger.getId(), driver.getId(), 4, "Good");

        // This must not throw LazyInitializationException.
        // The mapper accesses rater.fullName, ratee.fullName, and ride.id —
        // all LAZY associations that would fail if not initialized by the JOIN FETCH query.
        assertThatCode(() -> {
            Page<com.carpool.domain.entity.RideRating> page =
                    ratingService.getRatingsReceivedPaged(driver.getId(), 0, 10);
            page.getContent().forEach(r -> mapper.toRatingResponse(r));
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("getRatingsReceivedPaged — pagination returns correct page size")
    void getRatingsReceivedPaged_pagination() {
        ratingService.submitRating(
                completedRide.getId(), passenger.getId(), driver.getId(), 5, null);

        var page = ratingService.getRatingsReceivedPaged(driver.getId(), 0, 10);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("canRate — passenger eligible before rating, ineligible after")
    void canRate_eligibilityToggle() {
        assertThat(ratingService.canRate(completedRide.getId(), passenger.getId())).isTrue();

        ratingService.submitRating(
                completedRide.getId(), passenger.getId(), driver.getId(), 5, null);

        assertThat(ratingService.canRate(completedRide.getId(), passenger.getId())).isFalse();
    }

    @Test
    @DisplayName("getRateeIds — passenger gets driver ID as single ratee")
    void getRateeIds_passengerGetsDriverId() {
        var rateeIds = ratingService.getRateeIds(completedRide.getId(), passenger.getId());

        assertThat(rateeIds).containsExactly(driver.getId());
    }
}
```

- [ ] **Step 2: Run the test**

```
mvn test -pl carpool-web -Dtest=RatingIntegrationTest
```

Expected: All 6 tests PASS.

- [ ] **Step 3: Commit**

```
test(web): add RatingIntegrationTest — submit, duplicate 409, non-completed 409, lazy-init regression, pagination, eligibility
```

---

## Task 16: FavoritesIntegrationTest

**Files:**
- Create: `carpool-web/src/test/java/com/carpool/web/integration/FavoritesIntegrationTest.java`

- [ ] **Step 1: Create `FavoritesIntegrationTest`**

```java
package com.carpool.web.integration;

import com.carpool.common.exception.InvalidOperationException;
import com.carpool.domain.entity.User;
import com.carpool.domain.enums.UserRole;
import com.carpool.domain.enums.UserStatus;
import com.carpool.repository.UserRepository;
import com.carpool.service.dto.response.FollowerResponse;
import com.carpool.service.favorite.FavoriteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Favorites Integration")
class FavoritesIntegrationTest extends BaseIntegrationTest {

    @Autowired private FavoriteService favoriteService;
    @Autowired private UserRepository  userRepository;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        long seed = System.currentTimeMillis();

        userA = userRepository.save(User.builder()
                .telegramId(seed + 30).fullName("User A")
                .role(UserRole.PASSENGER).status(UserStatus.ACTIVE).build());

        userB = userRepository.save(User.builder()
                .telegramId(seed + 31).fullName("User B")
                .role(UserRole.DRIVER).status(UserStatus.ACTIVE).build());
    }

    @Test
    @DisplayName("saveFavorite — happy path")
    void saveFavorite_happyPath() {
        assertThatCode(() -> favoriteService.saveFavorite(userA.getId(), userB.getId()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("saveFavorite — idempotent (second call does not throw)")
    void saveFavorite_idempotent() {
        favoriteService.saveFavorite(userA.getId(), userB.getId());

        assertThatCode(() -> favoriteService.saveFavorite(userA.getId(), userB.getId()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("saveFavorite — self-favorite throws 400 InvalidOperationException")
    void saveFavorite_selfFavorite_throws400() {
        assertThatThrownBy(() -> favoriteService.saveFavorite(userA.getId(), userA.getId()))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    @DisplayName("removeFavorite — idempotent (works even when not saved)")
    void removeFavorite_idempotent() {
        assertThatCode(() -> favoriteService.removeFavorite(userA.getId(), userB.getId()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("getMyFavoritesAsDtos — returns saved favorites as FollowerResponse")
    void getMyFavoritesAsDtos_returnsList() {
        favoriteService.saveFavorite(userA.getId(), userB.getId());

        List<FollowerResponse> favorites = favoriteService.getMyFavoritesAsDtos(userA.getId());

        assertThat(favorites).hasSize(1);
        assertThat(favorites.get(0).userId()).isEqualTo(userB.getId());
        assertThat(favorites.get(0).fullName()).isEqualTo("User B");
    }

    @Test
    @DisplayName("getFollowers — returns followers of userB as FollowerResponse")
    void getFollowers_returnsList() {
        favoriteService.saveFavorite(userA.getId(), userB.getId());

        List<FollowerResponse> followers = favoriteService.getFollowers(userB.getId());

        assertThat(followers).hasSize(1);
        assertThat(followers.get(0).userId()).isEqualTo(userA.getId());
    }
}
```

- [ ] **Step 2: Run the test**

```
mvn test -pl carpool-web -Dtest=FavoritesIntegrationTest
```

Expected: All 6 tests PASS.

- [ ] **Step 3: Commit**

```
test(web): add FavoritesIntegrationTest — save, idempotency, self-favorite 400, getMyFavoritesAsDtos, getFollowers
```

---

## Task 17: Run Full Integration Test Suite

- [ ] **Step 1: Run all integration tests**

```
mvn test -pl carpool-web
```

Expected: All tests in `BookingIntegrationTest`, `RideSearchIntegrationTest`, `FlywayMigrationTest`, `VehicleIntegrationTest`, `RatingIntegrationTest`, `FavoritesIntegrationTest` PASS.

- [ ] **Step 2: Verify Swagger UI**

Start the app (`mvn spring-boot:run -pl carpool-web -Dspring-boot.run.profiles=local`) and open `http://localhost:8080/swagger-ui.html`. Confirm:

- **Vehicles** tag shows `GET`, `POST`, `DELETE /api/v1/users/me/vehicles`
- **Ratings** tag shows `POST /api/v1/rides/{rideId}/ratings`, `GET /api/v1/users/{userId}/ratings`, `GET /api/v1/rides/{rideId}/ratings/eligibility`
- **Users** tag shows `POST/DELETE /api/v1/users/{userId}/favorite`, `GET /api/v1/users/me/favorites`, `GET /api/v1/users/me/followers`
- **Rides** tag shows `PATCH /api/v1/rides/{id}/departure-time`
- **Bookings** tag → `GET /bookings/mine` description reads "Passenger's booking history" (not "Driver's ride history")
- **Bookings** tag → `PATCH /bookings/{id}/payment` shows 200/403/404 responses
