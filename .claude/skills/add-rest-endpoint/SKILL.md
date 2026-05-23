---
name: add-rest-endpoint
description: Use when adding a new REST endpoint to carpool-web. Covers DTO placement, controller method, @PreAuthorize role check, ApiResponse wrapper, and integration test in BaseIntegrationTest.
---

# Adding a New REST Endpoint

## Step 1 — Define request and response DTOs

DTOs are Java records. Place them in `carpool-service/src/main/java/com/carpool/service/dto/`:

```
dto/
  request/   ← inbound payloads (@Valid annotations go here)
  response/  ← outbound data shapes
```

Request DTO — use Jakarta Validation annotations for input constraints:

```java
package com.carpool.service.dto.request;

public record CreateMyThingRequest(
        @NotNull Long rideId,
        @NotBlank String description,
        @Min(1) @Max(10) int quantity
) {}
```

Response DTO — plain record, no annotations needed:

```java
package com.carpool.service.dto.response;

public record MyThingResponse(
        Long id,
        Long rideId,
        String description,
        int quantity,
        LocalDateTime createdAt
) {}
```

If the new endpoint reuses an existing response type (e.g., `RideResponse`), skip this step.

## Step 2 — Add the service method

Business logic lives in `carpool-service`, never in the controller. The controller is a thin HTTP adapter.

```java
// In the relevant service class (e.g., RideService, BookingService)
@Transactional
public MyThingResponse createMyThing(CreateMyThingRequest request, Long callerId) {
    // validation, entity creation, event publishing
    MyThing saved = myThingRepository.save(...);
    return mapper.toMyThingResponse(saved);
}
```

If the method needs to map entities to DTOs, add a method to `EntityMapper` in `carpool-service/`:

```java
MyThingResponse toMyThingResponse(MyThing myThing);
```

## Step 3 — Add the controller method

Controllers live in `carpool-web/src/main/java/com/carpool/web/controller/`. Add to an existing controller if the resource belongs to it, or create a new `@RestController` class.

```java
/**
 * POST /api/v1/things
 * Creates a new thing — requires DRIVER or BOTH role.
 */
@Operation(summary = "Create a thing",
        description = "One-paragraph description of what this does and allowed transitions.",
        security = @SecurityRequirement(name = "bearerAuth"))
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Thing created")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not authorized")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Ride not found")
@PostMapping
public ResponseEntity<ApiResponse<MyThingResponse>> createMyThing(
        @Valid @RequestBody CreateMyThingRequest request,
        @AuthenticationPrincipal AuthenticatedUser currentUser) {

    MyThingResponse result = myThingService.createMyThing(request, currentUser.getUserId());
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result));
}
```

**HTTP status conventions used in this project:**
- `201 CREATED` — new resource created (`POST`)
- `200 OK` — read, update, or action (`GET`, `PATCH`, `POST` for actions)
- `204 NO_CONTENT` — delete with no body

**`@PreAuthorize` role checks** — add at class level (applies to all methods) or method level:

```java
// At class level — all methods in this controller require the role
@PreAuthorize("hasAnyRole('DRIVER', 'BOTH')")

// At method level — only this method requires the role  
@PreAuthorize("hasAnyRole('DRIVER', 'BOTH')")
@PostMapping
public ResponseEntity<...> createMyThing(...) { ... }
```

Available roles: `DRIVER`, `PASSENGER`, `BOTH`, `ADMIN`.

**`ApiResponse` wrapper** — always wrap the response:
```java
ResponseEntity.ok(ApiResponse.ok(result))           // 200 with body
ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result))  // 201 with body
```

**Paginated responses** — use `PagedResponse<T>` from `carpool-common`:
```java
Pageable pageable = PageRequest.of(page, Math.min(size, 50), Sort.by("createdAt").descending());
return ResponseEntity.ok(ApiResponse.ok(myService.getThings(pageable)));
```

Always cap `size` at 50 to prevent abuse: `Math.min(size, 50)`.

## Step 4 — Write an integration test

Integration tests live in `carpool-web/src/test/java/com/carpool/web/integration/`. They extend `BaseIntegrationTest` (which sets up `@SpringBootTest`, MySQL at port 3308, and the `local` profile).

```java
@DisplayName("MyThing integration")
class MyThingIntegrationTest extends BaseIntegrationTest {

    @Autowired private MyThingService myThingService;
    @Autowired private UserRepository  userRepository;
    @Autowired private RideRepository  rideRepository;
    @Autowired private HubRepository   hubRepository;

    private User driver;
    private Ride ride;

    @BeforeEach
    void setUp() {
        long seed = System.currentTimeMillis();

        driver = userRepository.save(User.builder()
                .telegramId(seed + 1).fullName("Driver")
                .role(UserRole.DRIVER).status(UserStatus.ACTIVE).build());

        // create minimum fixture data for the test
    }

    @Test
    @Transactional
    @DisplayName("createMyThing — happy path")
    void createMyThing_succeeds() {
        CreateMyThingRequest request = new CreateMyThingRequest(ride.getId(), "desc", 2);

        MyThingResponse result = myThingService.createMyThing(request, driver.getId());

        assertThat(result.id()).isNotNull();
        assertThat(result.description()).isEqualTo("desc");
    }

    @Test
    @Transactional
    @DisplayName("createMyThing — throws when ride not found")
    void createMyThing_throwsWhenRideNotFound() {
        CreateMyThingRequest request = new CreateMyThingRequest(9999L, "desc", 2);

        assertThatThrownBy(() -> myThingService.createMyThing(request, driver.getId()))
                .isInstanceOf(RideNotFoundException.class);
    }
}
```

Use `seed = System.currentTimeMillis()` for unique `telegramId` values so parallel test runs don't collide on unique constraints.

**Run:** `mvn test -pl carpool-web`

## Checklist

- [ ] Request DTO is a record with Jakarta Validation annotations on constrained fields
- [ ] Response DTO is a plain record in `carpool-service/dto/response/`
- [ ] Business logic is in the service layer — controller is a thin adapter only
- [ ] `EntityMapper` updated if the new response DTO needs entity mapping
- [ ] Controller wraps the return in `ApiResponse.ok(...)` 
- [ ] HTTP status is correct: `201` for resource creation, `200` for updates/reads
- [ ] `@PreAuthorize` role check is present (method or class level)
- [ ] Paginated endpoints cap `size` at 50
- [ ] `@Operation` and `@io.swagger.v3.oas.annotations.responses.ApiResponse` annotations document the endpoint
- [ ] Integration test covers happy path and at least one error case
- [ ] Integration test uses `System.currentTimeMillis()` seed for unique telegram IDs