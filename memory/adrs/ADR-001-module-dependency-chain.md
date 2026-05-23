# ADR-001: Strict One-Way Module Dependency Chain

**Status:** Accepted  
**Date:** 2024  
**Deciders:** Project architect

## Context

The project is structured as a Maven multi-module build. Without explicit rules, modules can develop circular imports over time, making it impossible to build individual modules in isolation and leading to compilation order problems.

## Decision

Enforce a strict one-way dependency chain:

```
carpool-common → carpool-domain → carpool-repository → carpool-service
                                                              ↓           ↓
                                                        carpool-bot  carpool-admin
                                                              ↓
                                                        carpool-web
```

- `carpool-domain` has **no Spring beans** — pure JPA entities and enums
- `carpool-repository` has **only** Spring Data repositories — no business logic
- `carpool-service` owns all business logic and defines port interfaces for outbound adapters
- `carpool-bot` and `carpool-admin` implement service ports (e.g., `GroupAnnouncementPort`)
- `carpool-web` is the only executable JAR entry point for the main app (port 8080)
- `carpool-admin` is a separate executable JAR (port 8082, webforJ framework)

## Consequences

- New shared abstractions (exceptions, response wrappers) go into `carpool-common`
- Cross-cutting concerns that feel like they belong in `carpool-service` but need `carpool-bot` capabilities → use a port interface in service, implement in bot
- Adding a field to a domain entity always requires syncing: entity → repository query (if needed) → service DTO → MapStruct mapper
- Flyway migrations are exclusively in `carpool-web` (it's the entry point with the DB connection)

## Enforcement

The `module-boundary-check.sh` hook blocks `Write` and `Edit` operations that would introduce an illegal import at save time.