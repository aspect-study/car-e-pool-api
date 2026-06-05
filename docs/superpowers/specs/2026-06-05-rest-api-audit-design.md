# REST API Audit & Expansion — Design Spec
**Date:** 2026-06-05  
**Status:** Approved (Engineering-Reviewed)

## Problem

The REST API has not been updated since the ratings, favorites, and multi-vehicle features were added to the bot. Five services (`RatingService`, `FavoriteService`, `VehicleService`, `RideService.updateDepartureTime`) have functional implementations with no REST exposure. Web and mobile clients cannot access these features. Two existing endpoint descriptions have documentation bugs.

## Scope

### New Controllers

**`VehicleController`** — `GET/POST/DELETE /api/v1/users/me/vehicles`  
Exposes the multi-vehicle management surface that already exists in `VehicleService`. The legacy `PATCH /me/vehicle` and `DELETE /me/vehicle` endpoints on `UserController` are kept for backward compatibility.

**`RatingController`** — under `/api/v1`  
Exposes rating submission, ratings received (paginated), and eligibility check. All logic already exists in `RatingService` — this is pure wiring.

### Additions to Existing Controllers

**`UserController`** — 4 new methods for favorites/followers  
`POST/DELETE /users/{userId}/favorite` and `GET /users/me/favorites`, `GET /users/me/followers`. Delegates to `FavoriteService` which already implements all four operations.

**`RideController`** — 1 new method  
`PATCH /rides/{id}/departure-time` delegates to `RideService.updateDepartureTime()`. This method already fires `RideTimeChangedEvent` which notifies passengers and refreshes the group post — no additional service work needed.

### Documentation Fixes

**`BookingController`**  
- `GET /bookings/mine` — description says "Driver's ride history", change to "Passenger's booking history"
- `PATCH /bookings/{id}/payment` — add the three missing `@ApiResponse` annotations (200, 403, 404)

## New Endpoints

### Vehicle

| Method | Path | Auth | Returns |
|--------|------|------|---------|
| `GET` | `/api/v1/users/me/vehicles` | Bearer | `List<VehicleResponse>` |
| `POST` | `/api/v1/users/me/vehicles` | Bearer | `VehicleResponse` |
| `DELETE` | `/api/v1/users/me/vehicles/{vehicleId}` | Bearer | `Void` |

- `GET` — ordered oldest-first (matches bot selection order); returns empty list if none registered
- `POST` — calls `VehicleService.addVehicle()`; enforces plate uniqueness, replace-oldest at 3 limit
- `DELETE` — calls `VehicleService.removeVehicle(vehicleId, userId)`; **403 if not the owner** (throws `NotRideOwnerException`, not `InvalidRideStateException`)

### Ratings

| Method | Path | Auth | Returns |
|--------|------|------|---------|
| `POST` | `/api/v1/rides/{rideId}/ratings` | Bearer | `RatingResponse` |
| `GET` | `/api/v1/users/{userId}/ratings` | Bearer | `PagedResponse<RatingResponse>` |
| `GET` | `/api/v1/rides/{rideId}/ratings/eligibility` | Bearer | `RatingEligibilityResponse` |

- `POST` — calls `RatingService.submitRating()`; **409** if already rated or ride not COMPLETED (throws `RatingConflictException`); **400** if rideId/rateeId not found (throws `InvalidOperationException`)
- `GET ratings` — paginated, ordered newest-first; `page` default 0, `size` default 10 max 50; backed by JOIN FETCH query
- `GET eligibility` — returns `canRate: boolean` and `rateeIds: List<Long>`; client uses this before showing rating UI

### Favorites

| Method | Path | Auth | Returns |
|--------|------|------|---------|
| `POST` | `/api/v1/users/{userId}/favorite` | Bearer | `Void` |
| `DELETE` | `/api/v1/users/{userId}/favorite` | Bearer | `Void` |
| `GET` | `/api/v1/users/me/favorites` | Bearer | `List<FollowerResponse>` |
| `GET` | `/api/v1/users/me/followers` | Bearer | `List<FollowerResponse>` |

- `POST` — idempotent (no error if already saved); **400 if favoriting yourself** (throws `InvalidOperationException`)
- `DELETE` — idempotent (no error if not saved)
- Both list endpoints reuse `FollowerResponse(userId, fullName, telegramHandle, followedAt)`
- **TODO (P2):** Add pagination (`page`/`size`) to `/me/favorites` and `/me/followers` once user counts grow; currently returns full list

### Departure Time

| Method | Path | Auth | Returns |
|--------|------|------|---------|
| `PATCH` | `/api/v1/rides/{id}/departure-time` | Bearer | `RideResponse` |

- 400 if new time equals current time, or is less than 15 minutes from now
- 400 if ride is not ACTIVE or FULL (`InvalidRideStateException` → `BadRequestException` → 400)
- 403 if not the ride owner

## New DTOs

### Request Records

**`AddVehicleRequest`** (`carpool-service/.../dto/request/`)
```
model        — @NotBlank
color        — optional
plateNumber  — @NotBlank (normalized to uppercase by service)
seatCapacity — @Min(1) @Max(8), optional (defaults to 4)
```

**`SubmitRatingRequest`** (`carpool-service/.../dto/request/`)
```
rateeId  — @NotNull Long
stars    — @NotNull @Min(1) @Max(5) Integer  (Integer not int — avoids silent 0 default)
comment  — @Size(max=1000) String, optional
```

**`UpdateDepartureTimeRequest`** (`carpool-service/.../dto/request/`)
```
newDepartureTime — @NotNull @Future LocalDateTime
```

### Response Records

**`UserSummaryResponse`** (`carpool-service/.../dto/response/`) — *new, plate-privacy DTO*
```
id             — Long
fullName       — String
telegramHandle — String
avgRating      — Double (nullable)
```
Used wherever user identity must be shown to third parties. Does NOT include `plateNumber`, `carModel`, or `carColor`.

**`RatingResponse`** (`carpool-service/.../dto/response/`)
```
id         — Long
rideId     — Long
rater      — UserSummaryResponse  (NOT UserResponse — plate privacy)
ratee      — UserSummaryResponse  (NOT UserResponse — plate privacy)
stars      — int
comment    — String (nullable)
raterRole  — String ("DRIVER" or "PASSENGER")
createdAt  — Instant
```

**`RatingEligibilityResponse`** (`carpool-service/.../dto/response/`)
```
canRate   — boolean
rateeIds  — List<Long>
```

### Mapper Addition

`EntityMapper` gets one new method:
```java
@Mapping(source = "ride.id", target = "rideId")
@Mapping(source = "rater", target = "rater")
@Mapping(source = "ratee", target = "ratee")
RatingResponse toRatingResponse(RideRating rating);
UserSummaryResponse toUserSummaryResponse(User user);
```
The `@Mapping(source = "ride.id", target = "rideId")` annotation is **required** — MapStruct cannot auto-map nested `.id` fields without it; rideId will silently be null without this.

## Architecture Notes

### Exception Strategy

`RatingService` and `FavoriteService` currently throw raw `IllegalArgumentException` (bad input) and `IllegalStateException` (duplicate/invalid state) that fall through to the 500 catch-all in `GlobalExceptionHandler`. **We do NOT broaden `GlobalExceptionHandler`** with raw Java exception handlers — `IllegalStateException` is thrown by Spring internals and a global handler would mask framework errors.

Instead, introduce two new typed `CarpoolException` subclasses in `carpool-common`:

| Class | Extends | HTTP Status |
|-------|---------|-------------|
| `RatingConflictException` | `CarpoolException(HttpStatus.CONFLICT)` | 409 |
| `InvalidOperationException` | `CarpoolException(HttpStatus.BAD_REQUEST)` | 400 |

Replace raw exception throws in `RatingService` and `FavoriteService` with these typed subclasses. `GlobalExceptionHandler` already handles `CarpoolException` — no handler changes needed.

Existing domain exceptions remain unchanged:

| Exception | HTTP |
|-----------|------|
| `NotRideOwnerException` | 403 |
| `ResourceNotFoundException` | 404 |
| `InvalidRideStateException` | 400 |
| `DuplicateBookingException` | 409 |

### Lazy Loading Fix (RatingService)

The project has `spring.jpa.open-in-view=false`. `RatingService.getRatingsReceivedPaged()` currently initializes only `ratee` in a post-load forEach, leaving `rater` and `ride` as uninitialized LAZY proxies. The mapper will throw `LazyInitializationException` after the transaction closes.

**Fix (two-part):**
1. Extend the forEach to also call `r.getRater().getFullName()` and `r.getRide().getId()` to initialize all proxies within the transaction
2. Add a `findRatingsReceivedPaged` method to `RideRatingRepository` with a JOIN FETCH on `rater`, `ratee`, and `ride` — this eliminates the N+1 and makes the forEach initialization unnecessary

### Plate Number Privacy

`VehicleResponse` includes `plateNumber`. This is correct for `/me/vehicles` (owner viewing own data). However, `RatingResponse` uses `rater` and `ratee` user fields — since `GET /users/{userId}/ratings` is accessible by any authenticated user, these must NOT include plate numbers.

Solution: `RatingResponse.rater` and `RatingResponse.ratee` use `UserSummaryResponse` instead of `UserResponse`. `UserSummaryResponse` contains only `id`, `fullName`, `telegramHandle`, and `avgRating`.

### Vehicle Ownership Exception

`VehicleService.removeVehicle()` currently throws `InvalidRideStateException("You can only remove your own vehicles.")` which maps to **400**. The correct status for authorization failure is **403**. Fix: throw `NotRideOwnerException()` instead.

### FavoriteService Mapping Gap

`FavoriteService.getMyFavorites()` returns `List<UserFavorite>` (domain entities). Controllers cannot depend on domain entities. Add a new method `getMyFavoritesAsDtos(Long followerId)` that mirrors the existing `getFollowers()` pattern and returns `List<FollowerResponse>`.

### Controller Pattern

All new controllers follow the existing pattern: `@RestController`, `@RequiredArgsConstructor`, `@Tag` for Swagger grouping, `@SecurityRequirement(name = "bearerAuth")` on each protected operation.

## Out of Scope

- `DriverNoteService` — notes are a bot-internal UX concept (saved ride descriptions); not useful to web/mobile clients
- Rating analytics queries (star distribution, leaderboard) — documented as P3 TODO in `carpool-service/CLAUDE.md`
- Passenger-initiated ride request flow — deferred per project backlog
- Pagination for `/me/favorites` and `/me/followers` — deferred as P2 TODO (full list acceptable at current scale)

## Files Changed

| Action | File |
|--------|------|
| NEW | `carpool-web/.../controller/VehicleController.java` |
| NEW | `carpool-web/.../controller/RatingController.java` |
| NEW | `carpool-service/.../dto/request/AddVehicleRequest.java` |
| NEW | `carpool-service/.../dto/request/SubmitRatingRequest.java` |
| NEW | `carpool-service/.../dto/request/UpdateDepartureTimeRequest.java` |
| NEW | `carpool-service/.../dto/response/RatingResponse.java` |
| NEW | `carpool-service/.../dto/response/RatingEligibilityResponse.java` |
| NEW | `carpool-service/.../dto/response/UserSummaryResponse.java` — plate-privacy DTO for third-party user fields |
| NEW | `carpool-common/.../exception/RatingConflictException.java` — extends CarpoolException(CONFLICT) → 409 |
| NEW | `carpool-common/.../exception/InvalidOperationException.java` — extends CarpoolException(BAD_REQUEST) → 400 |
| NEW | `carpool-web/.../integration/RatingIntegrationTest.java` |
| NEW | `carpool-web/.../integration/VehicleIntegrationTest.java` |
| NEW | `carpool-web/.../integration/FavoritesIntegrationTest.java` |
| EDIT | `carpool-web/.../controller/UserController.java` — add 4 favorites methods |
| EDIT | `carpool-web/.../controller/RideController.java` — add departure-time PATCH |
| EDIT | `carpool-web/.../controller/BookingController.java` — fix 2 doc bugs |
| EDIT | `carpool-service/.../mapper/EntityMapper.java` — add toRatingResponse + toUserSummaryResponse with @Mapping(source="ride.id", target="rideId") |
| EDIT | `carpool-service/.../service/rating/RatingService.java` — forEach init fix for rater+ride, typed exceptions (RatingConflictException, InvalidOperationException) |
| EDIT | `carpool-service/.../service/favorite/FavoriteService.java` — add getMyFavoritesAsDtos(), typed exceptions |
| EDIT | `carpool-service/.../service/vehicle/VehicleService.java` — NotRideOwnerException instead of InvalidRideStateException for ownership check |
| EDIT | `carpool-repository/.../RideRatingRepository.java` — add JOIN FETCH query for ratings list (eliminates N+1) |

## Testing

Integration tests in `carpool-web` should cover:

**RatingIntegrationTest**
- `POST /rides/{rideId}/ratings` — happy path (driver rates passenger, returns 201 RatingResponse)
- `POST /rides/{rideId}/ratings` — duplicate rating returns 409
- `POST /rides/{rideId}/ratings` — ride not COMPLETED returns 409
- `GET /users/{userId}/ratings` — pagination (page, size params)
- `GET /users/{userId}/ratings` — **LazyInit regression**: response must not throw 500; verifies rater, ratee, and ride all initialized
- `GET /rides/{rideId}/ratings/eligibility` — driver eligible returns canRate=true
- `GET /rides/{rideId}/ratings/eligibility` — after rating, canRate=false

**VehicleIntegrationTest**
- `POST /users/me/vehicles` — adds vehicle, returns VehicleResponse with plateNumber (own data)
- `POST /users/me/vehicles` — replace-oldest when at 3-vehicle limit
- `POST /users/me/vehicles` — duplicate plate returns 409
- `GET /users/me/vehicles` — returns ordered list
- `DELETE /users/me/vehicles/{vehicleId}` — own vehicle returns 204
- `DELETE /users/me/vehicles/{vehicleId}` — other user's vehicle returns 403

**FavoritesIntegrationTest**
- `POST /users/{userId}/favorite` — happy path, returns 200
- `POST /users/{userId}/favorite` — idempotent (second call also 200)
- `POST /users/{userId}/favorite` — self-favorite returns 400
- `DELETE /users/{userId}/favorite` — idempotent (works even if not saved)
- `GET /users/me/favorites` — returns list of saved favorites as FollowerResponse
- `GET /users/me/followers` — returns list of followers as FollowerResponse

**RideController (existing test class)**
- `PATCH /rides/{id}/departure-time` — valid update, returns RideResponse
- `PATCH /rides/{id}/departure-time` — non-owner returns 403
- `PATCH /rides/{id}/departure-time` — past/too-soon time returns 400

---

## Engineering Review Notes

*Review conducted: 2026-06-05*

**D2 — Lazy init fix:** `getRatingsReceivedPaged` forEach must initialize both `rater` (`.getFullName()`) and `ride` (`.getId()`) in addition to existing `ratee` initialization. Backed by JOIN FETCH query to eliminate N+1.

**D3 — Plate privacy:** `RatingResponse.rater/ratee` use `UserSummaryResponse` (no plate/car fields) not `UserResponse`. `GET /users/{userId}/ratings` is public to any authenticated user.

**D4 — FavoriteService gap:** `getMyFavoritesAsDtos(Long followerId)` added to `FavoriteService` — returns `List<FollowerResponse>` matching the existing `getFollowers()` pattern.

**D5 — MapStruct annotation:** `@Mapping(source = "ride.id", target = "rideId")` is mandatory; MapStruct will silently produce null rideId without it.

**D6 — LazyInit regression test:** Explicit test in `RatingIntegrationTest` that verifies GET /users/{userId}/ratings returns 200 (not 500) — guards against lazy proxy regression.

**D7 — N+1 fix:** JOIN FETCH query in `RideRatingRepository` fetches rater, ratee, and ride in one query. Proxy initialization in forEach becomes defense-in-depth.

**D8 — Favorites pagination deferred:** Full list acceptable at current user counts. Tracked as P2 TODO.

**D9 — Vehicle 403:** `VehicleService.removeVehicle` throws `NotRideOwnerException` (403) not `InvalidRideStateException` (400) for ownership failure.

**D11 — Exception strategy:** No changes to `GlobalExceptionHandler`. Two new typed exceptions (`RatingConflictException`, `InvalidOperationException`) replace raw Java throws in `RatingService` and `FavoriteService`. Existing `CarpoolException` handler already covers them.

**D12 — Eligibility endpoint kept:** `GET /rides/{rideId}/ratings/eligibility` with `rateeIds: List<Long>` field retained — clients need the ratee list to show per-person rating UI, not just `canRate`.
