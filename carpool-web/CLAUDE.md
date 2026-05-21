# carpool-web — Module Guide

Spring Boot entry point for the main application. Owns REST controllers, security configuration, and Flyway migrations.

## Security

Stateless JWT auth. `POST /api/v1/auth/telegram` is public — validates Telegram Login Widget hash (HMAC-SHA256 of `SHA256(bot_token)`). All other endpoints require Bearer token. `@PreAuthorize` is enabled for method-level role checks.

Filters chain: `RateLimitFilter` (Bucket4j, per-IP) → `JwtAuthFilter` → Spring Security.

## Flyway Migrations

Migrations live in `carpool-web/src/main/resources/db/migration/`. Naming: `V{N}__description_in_snake_case.sql` (two underscores). Current highest version: **V44**. Never modify an existing migration file — Flyway validates checksums on startup.

Use the `/flyway-migration` skill when adding a new migration.

## Integration Tests

Integration tests are tagged `@Tag("integration")` and inherit from `BaseIntegrationTest`. They require MySQL at `localhost:3308` with DB `car_e_pool_db`, user/pass `carpool/carpool`.

Run them with:
```bash
mvn test -pl carpool-web
```

Unit tests (no DB) run with:
```bash
mvn clean verify -Dgroups="!integration"
```
