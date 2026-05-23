# ADR-006: Flyway Is the Only Allowed Schema Change Mechanism

**Status:** Accepted  
**Date:** 2024  
**Deciders:** Backend team

## Context

Spring Boot can manage the DB schema automatically via `spring.jpa.hibernate.ddl-auto=update`. This is convenient in development but dangerous in production: Hibernate may drop columns it thinks are no longer used, reorder columns on some DBs, and produce non-reviewable schema diffs.

## Decision

All schema changes must go through Flyway migrations. The `ddl-auto` setting is:
- `validate` in production and staging — Hibernate checks the schema matches entities, fails fast on mismatch
- `validate` in local development too (local dev uses docker-compose MySQL at port 3308)
- Never `create`, `update`, or `create-drop` in any committed properties file

Migration files live exclusively in:
```
carpool-web/src/main/resources/db/migration/V{N}__description.sql
```

Version numbers are sequential with no gaps. The current highest is **V44** (verify before adding the next one).

## Consequences

- Every entity field addition requires a migration file — no "just add the field and restart" shortcuts
- Schema changes are reviewable, auditable, and reversible (by writing a reverse migration)
- The `flyway-gate.sh` hook blocks writing files to `db/migration/` with invalid naming
- The `flyway-gate.sh` hook blocks editing existing migration files (Flyway rejects checksum changes)
- CI must run Flyway migrations before starting the app in integration tests

## Failure Mode

If a developer adds an entity field without a migration and runs the app locally with `ddl-auto=validate`, they get:
```
SchemaManagementException: Schema-validation: missing column [new_col] in table [ride]
```
This is intentional — it forces them to write the migration.