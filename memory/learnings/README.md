# Learnings

Patterns and hard-won knowledge worth preserving across sessions. Unlike ADRs (which capture architectural decisions), learnings capture the "gotchas" and unexpected behaviors discovered during implementation.

## How to Add

Create a file named `YYYY-MM-DD-short-description.md`. Keep it tight: what happened, why it was surprising, and what the fix is.

## Index

### Backend (Spring Boot / Telegram bot)
- [2025-05-23-tinyint-integer-type-rule.md](2025-05-23-tinyint-integer-type-rule.md) — TINYINT must map to Integer, not byte
- [2025-05-23-stale-button-session-recovery.md](2025-05-23-stale-button-session-recovery.md) — NPE on old bot buttons after restart

### Web (Next.js)
<!-- Add learnings here as they are discovered during development -->
<!-- Examples of what belongs here:
  - Unexpected Telegram Login Widget behaviour in a specific browser
  - Next.js cookies() behaving differently in middleware vs Server Components
  - LocalDateTime timezone rendering bug in the browser
-->

### Mobile (Flutter)
<!-- Add learnings here as they are discovered during development -->
<!-- Examples of what belongs here:
  - flutter_secure_storage first-launch behaviour on iOS after reinstall
  - Dio interceptor ordering issue with 401 retry
  - go_router redirect loop on auth state change
-->