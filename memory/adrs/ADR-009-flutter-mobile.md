# ADR-009: Flutter for the Mobile App

**Status:** Accepted
**Date:** 2026-05
**Deciders:** Project architect

## Context

The carpool platform needs a mobile app for passengers and drivers on iOS and Android. The choices evaluated were:

- **Flutter** — Dart, single codebase for iOS + Android + Web, compiled to native ARM
- **React Native** — JavaScript/TypeScript, single codebase, shares language with a Next.js web frontend
- **Native (Swift + Kotlin)** — best performance and platform integration, but two separate codebases

## Decision

Use **Flutter**.

Key reasons:

1. **Native feel for a transport app**: The core user interaction — browsing rides, booking, tracking departure time — benefits from smooth 60/120fps animations. Flutter compiles to native ARM code and draws its own UI. React Native bridges to native components, which introduces frame drops on complex screens.

2. **Single codebase with true parity**: Flutter's widget tree is identical on iOS and Android — no platform-specific workarounds for layout or navigation. React Native frequently requires `Platform.select()` patches and platform-specific dependencies.

3. **Dart is approachable from Java**: The project team already writes Java. Dart's type system, class structure, and async/await patterns are closer to Java than JavaScript. The ramp-up is faster.

4. **Ecosystem fit**: `go_router` (navigation), `Riverpod` (state), `Dio` (HTTP), `freezed` (immutable models), `flutter_secure_storage` (encrypted storage) — all mature, well-maintained libraries that cover every requirement cleanly.

5. **Code sharing with web is not a priority**: The Next.js web app and Flutter mobile app both consume the same Spring Boot REST API. Business logic lives on the backend. The UI layers are inherently different — sharing component code between web and mobile would add complexity with little gain.

## Consequences

- **Separate codebase** from the Next.js web app — but shared API contract via the Spring Boot REST endpoints
- Dart models must mirror the Spring Boot DTOs exactly — use `freezed` + `json_serializable` for type-safe deserialization
- Auth: Telegram Login Widget cannot run natively in Flutter — must open a WebView to the Next.js `/login` page and catch the deep link redirect (see `flutter-mobile.md` agent)
- `flutter_secure_storage` is used for JWT — never SharedPreferences (see ADR-011)
- `--dart-define-from-file=env.json` for environment config — `API_BASE_URL` is never hardcoded
- Android emulator localhost is `10.0.2.2:8080`, not `localhost:8080`

## Related

- ADR-008 — Next.js web app (the companion frontend)
- ADR-011 — flutter_secure_storage decision
- `flutter-mobile.md` agent — full implementation patterns
