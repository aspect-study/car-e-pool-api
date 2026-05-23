---
name: new-repository-method
description: Use when adding a new query method to a Spring Data JPA repository in carpool-repository. Covers derived query naming, @Query for complex cases, @Lock for pessimistic reads, and integration test requirements.
---

# Adding a New Repository Method

## Step 1 — Find the right repository

Repositories live in `carpool-repository/src/main/java/com/carpool/repository/`:

| Repository | Owns queries for |
|-----------|-----------------|
| `RideRepository` | `Ride` entity |
| `BookingRepository` | `Booking` entity |
| `UserRepository` | `User` entity |
| `HubRepository` | `Hub` entity |
| `VehicleRepository` | `Vehicle` entity |
| `NotificationRepository` | `Notification` entity |
| `RideRatingRepository` | `RideRating` entity |

## Step 2 — Choose the method type

**Derived query** — Spring Data generates the SQL from the method name. Use when the condition is a simple field equality, `In`, `And`, `Or`, `OrderBy`, or `exists`/`count` check:

```java
// Examples of derived queries already in the project:
boolean existsByDriverIdAndDirectionAndStatusIn(Long driverId, RideDirection direction, List<RideStatus> statuses);
List<Ride> findByDriverIdAndStatusInOrderByDepartureTimeDesc(Long driverId, List<RideStatus> statuses);
Optional<User> findByTelegramId(Long telegramId);
```

**`@Query` with JPQL** — use when derived naming becomes unreadable (3+ joined conditions), you need a `JOIN FETCH` to avoid N+1, or you need a custom projection:

```java
@Query("SELECT r FROM Ride r JOIN FETCH r.driver WHERE r.id = :id")
Optional<Ride> findByIdWithDriver(@Param("id") Long id);
```

**Native `@Query`** — use only for analytics/aggregates that can't be expressed in JPQL (GROUP BY with HAVING, window functions):

```java
@Query(value = "SELECT AVG(stars) FROM ride_ratings WHERE ratee_id = :userId", nativeQuery = true)
Double findAverageRatingByUserId(@Param("userId") Long userId);
```

## Step 3 — Add pessimistic locking (SELECT FOR UPDATE)

Only needed when the caller must prevent concurrent modification of the same row (e.g., booking the last seat, updating departure time):

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT r FROM Ride r WHERE r.id = :id")
Optional<Ride> findByIdWithLock(@Param("id") Long id);
```

`@Lock` requires an explicit `@Query` — it cannot be combined with a derived method name. The caller must be inside a `@Transactional` method or the lock is released immediately.

## Step 4 — Mark read-only queries

Any method that only reads (no insert/update/delete) should signal this to the JPA provider:

```java
@Transactional(readOnly = true)
List<Ride> findByDriverIdAndStatusInOrderByDepartureTimeDesc(Long driverId, List<RideStatus> statuses);
```

Spring Data repositories are `@Transactional(readOnly = true)` by default on read methods, but add it explicitly on `@Query` methods to be safe.

## Step 5 — Return type conventions

| Use case | Return type |
|----------|------------|
| Single result, may not exist | `Optional<T>` |
| Multiple results | `List<T>` |
| Existence check | `boolean` (derived: `existsBy…`) |
| Count | `long` (derived: `countBy…`) |
| Paginated results | `Page<T>` (add `Pageable pageable` param) |
| Projection (subset of fields) | Projection interface or record |

Never return `null` from a repository method — use `Optional` or an empty `List`.

## Step 6 — Write an integration test

Unit tests cannot verify derived query names — Spring Data generates the SQL at startup and a typo causes a startup failure, not a test failure. Integration tests catch this early.

Add to `carpool-web/src/test/java/com/carpool/web/integration/` (create a new file or add to an existing relevant test class):

```java
@DisplayName("MyRepository integration")
class MyRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired private MyRepository myRepository;
    @Autowired private UserRepository userRepository; // for fixture data

    @BeforeEach
    void setUp() {
        // Create the minimum fixture data the query needs
        long seed = System.currentTimeMillis();
        // ...
    }

    @Test
    @Transactional
    void findByXAndY_returnsMatchingRows() {
        // Arrange: data is from setUp
        // Act
        List<MyEntity> result = myRepository.findByXAndY(...);
        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getField()).isEqualTo(expectedValue);
    }

    @Test
    @Transactional
    void findByXAndY_returnsEmpty_whenNoMatch() {
        List<MyEntity> result = myRepository.findByXAndY(nonExistentValue);
        assertThat(result).isEmpty();
    }
}
```

For pessimistic-lock methods, write a concurrent test (see `BookingIntegrationTest` for the pattern — it uses `ExecutorService` + `CountDownLatch` to simulate simultaneous requests).

**Run:** `mvn test -pl carpool-web`

## Checklist

- [ ] Method is in the correct repository for its entity
- [ ] Derived query name compiles (test with `mvn test -pl carpool-web -Dgroups="!integration"` — Spring Data validates names at startup)
- [ ] `@Query` used when derived naming would be unreadable or requires JOIN FETCH
- [ ] `@Lock(PESSIMISTIC_WRITE)` + `@Query` added for any row that needs SELECT FOR UPDATE
- [ ] `@Transactional(readOnly = true)` present on `@Query` read methods
- [ ] Return type uses `Optional` for single-result, `List` for multi-result, `boolean` for existence
- [ ] Integration test covers the happy path and the empty/not-found case