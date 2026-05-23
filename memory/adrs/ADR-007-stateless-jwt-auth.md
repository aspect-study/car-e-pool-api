# ADR-007: Stateless JWT Authentication with Telegram Login Widget

**Status:** Accepted  
**Date:** 2024  
**Deciders:** Backend + Security

## Context

The carpool API needs to authenticate users who are identified by their Telegram account. Traditional username/password authentication is not suitable since users are already authenticated via Telegram. The authentication must work for both the REST API (used by a web frontend) and must integrate with the Telegram bot's user identity.

## Decision

Use a two-step authentication flow:

1. **Telegram Login Widget** — the web frontend redirects the user through Telegram's OAuth-like widget, which calls back to our server with signed user data (`id`, `first_name`, `hash`, `auth_date`)
2. **HMAC validation** — the server validates the Telegram-provided data using `HMAC_SHA256(sorted_data_string, SHA256(BOT_TOKEN))`
3. **JWT issuance** — on valid login, the server issues a short-lived JWT containing `carpoolUserId` (our internal ID), signed with `JWT_SECRET`
4. **Stateless request auth** — every subsequent request presents `Authorization: Bearer <token>`; the `JwtAuthFilter` validates and extracts `carpoolUserId` into the `SecurityContext`

No server-side sessions are maintained. The JWT is self-contained.

## Telegram Validation Implementation Invariants

- Data string: sort all params except `hash` alphabetically, join as `key=value\n`, HMAC with `SHA256(BOT_TOKEN)` as secret key
- Always check `auth_date` is within 86400 seconds of now — prevents replay attacks
- Reject if `hash` param doesn't match the computed HMAC

## Consequences

- The server is horizontally scalable — no session store needed
- JWT expiry must be short enough to limit the window if a token is stolen (configured via `jwt.expiration-ms`)
- Token revocation requires either a blocklist (not yet implemented) or short expiry
- `JWT_SECRET` must be ≥ 256 bits, stored only in the `JWT_SECRET` env var — never in committed properties files
- The `carpoolUserId` (Long) is the canonical user identity in the service layer — `chatId` (Telegram's identifier) is used only for sending Telegram messages
- Bot users are identified by `chatId` when they interact via Telegram; the bot resolves `carpoolUserId` by looking up the user record with `userRepository.findByChatId(chatId)`

## Related

- `carpool-web/CLAUDE.md` — filter chain details
- `security.md` agent — reviews any change to the auth flow
- ADR-004 — bot private-chat-only policy (complements this auth architecture)