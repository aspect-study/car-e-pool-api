---
name: spring-boot-review
description: Use when reviewing Spring Boot 4 patterns, the JWT filter chain, security configuration, REST controller structure, or Spring profiles. Catches misconfigured filters, wrong bean scopes, and security misconfigurations before merge.
---

# Spring Boot Review

Run this review on changes to `carpool-web` (controllers, security, filters) or any Spring configuration class.

## Step 1 — Filter Chain Order

Open `carpool-web/.../security/` and verify filter registration order:

```
RateLimitFilter (order 1)  → JwtAuthFilter (order 2) → Spring Security filter chain
```

Correct:
```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
        ...
        .build();
}
```

Red flags:
- `JwtAuthFilter` added `addFilterAfter` Spring Security — auth never runs
- `RateLimitFilter` added after `JwtAuthFilter` — rate limiting happens after auth cost

## Step 2 — SecurityFilterChain Endpoint Rules

Every new REST endpoint must have an explicit permission rule in `SecurityFilterChain`:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/auth/**").permitAll()
    .requestMatchers("/api/admin/**").hasRole("ADMIN")
    .requestMatchers("/api/**").authenticated()
    .anyRequest().denyAll()                    // deny anything not listed
)
```

Red flags:
- New endpoint under `/api/**` but no explicit rule — relies on the catch-all `authenticated()`, which may or may not be correct
- `anyRequest().permitAll()` — opens all unmatched routes
- `anyRequest().authenticated()` without a `denyAll` fallback — insecure default

## Step 3 — JWT Filter Correctness

In `JwtAuthFilter`, check:

```java
// 1. Extract token from header
String token = request.getHeader("Authorization");
if (token == null || !token.startsWith("Bearer ")) {
    filterChain.doFilter(request, response);  // let Spring Security decide
    return;
}
token = token.substring(7);

// 2. Validate and extract claims
// 3. Set SecurityContext — must use the internal carpoolUserId, not Telegram chatId
UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
    carpoolUserId, null, authorities);
SecurityContextHolder.getContext().setAuthentication(auth);
```

Red flags:
- `SecurityContext` set with `chatId` instead of `carpoolUserId` — wrong identity in downstream services
- Token validation skips signature check
- `auth_date` staleness check missing on Telegram login endpoint

## Step 4 — Controller Layer Rules

```java
@RestController
@RequestMapping("/api/rides")
@RequiredArgsConstructor
public class RideController {

    // Correct: extract authenticated user from SecurityContext, not request body
    @GetMapping("/my")
    public ResponseEntity<PagedResponse<RideResponse>> myRides(
            Authentication auth,
            @PageableDefault(size = 20) Pageable pageable) {
        Long carpoolUserId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(rideService.findByDriver(carpoolUserId, pageable));
    }
}
```

Red flags:
- `userId` taken from `@RequestBody` or `@RequestParam` instead of `Authentication` — user can spoof identity
- No `Pageable` on list endpoints — DoS risk via large result set
- Returning `List<>` instead of `PagedResponse<>` — no pagination metadata for the client

## Step 5 — Spring Profile Safety

Check `application-local.properties` is NOT committed with real secrets. Check that all production-facing properties are in `application.properties` with env-var placeholders:

```properties
# Correct
spring.datasource.url=${DB_URL}
jwt.secret=${JWT_SECRET}
telegram.bot.token=${TELEGRAM_BOT_TOKEN}

# Wrong — hardcoded value that ends up in the image
spring.datasource.password=carpool
```

Red flags:
- Any secret hardcoded in a committed properties file
- `spring.jpa.hibernate.ddl-auto=create` or `update` in any committed properties file (should be `validate`)
- `logging.level.root=DEBUG` committed — logs sensitive data

## Step 6 — Bean Scope & State

```java
// Correct — controllers and services are stateless singletons
@RestController  // singleton by default

// Wrong — storing per-request state in a singleton
@Service
public class RideService {
    private Ride currentRide;  // shared state — race condition
}
```

Red flags:
- Instance fields on `@Service` or `@Component` that hold request-scoped data
- `@Scope("prototype")` on a class injected into a singleton — scope mismatch, prototype becomes effectively singleton

## Output Format

For each issue found:
```
[FILTER ORDER]    SecurityConfig:44 — JwtAuthFilter registered after rate limiter; swap order
[IDENTITY SPOOF]  RideController:67 — userId from request body; use Authentication.getPrincipal()
[SECRET EXPOSED]  application-local.properties:12 — hardcoded password; use ${DB_PASSWORD}
```

If no issues found: `SPRING BOOT REVIEW PASSED — filter chain and config are correct.`