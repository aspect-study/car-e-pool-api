# ADR-010: JWT Stored in HTTP-only Cookie on the Web Frontend

**Status:** Accepted
**Date:** 2026-05
**Deciders:** Project architect + Security

## Context

After the Spring Boot backend issues a JWT (see ADR-007), the Next.js web app must store it somewhere for subsequent requests. The two common options:

- **`localStorage`** — easy to implement, survives page refresh, but readable by any JavaScript on the page
- **HTTP-only cookie** — not readable by JavaScript, set by the server, sent automatically on every request

## Decision

Store the JWT in an **HTTP-only cookie**, set server-side by the Next.js API route.

```typescript
// app/api/auth/telegram/route.ts
cookieStore.set('token', data.accessToken, {
  httpOnly: true,       // inaccessible to JavaScript
  secure: true,         // HTTPS only in production
  sameSite: 'lax',      // CSRF protection
  maxAge: Math.floor(data.expiresInMs / 1000),
  path: '/',
});
```

Key reasons:

1. **XSS immunity**: An HTTP-only cookie cannot be read by `document.cookie` or any JavaScript. If an XSS vulnerability exists anywhere on the page (e.g., in ride `notes` or `passengerMessage` rendered unsanitized), `localStorage` tokens are immediately exfiltrated. HTTP-only cookies are not.

2. **Server-side control**: The Next.js API route receives the JWT from Spring Boot and sets the cookie — the token never touches client-side JavaScript at all. Client Components have no way to read or forward the token accidentally.

3. **SameSite=Lax CSRF mitigation**: With `SameSite=Lax`, the cookie is not sent on cross-site POST requests initiated by third-party pages. This prevents CSRF attacks without requiring a CSRF token on read endpoints.

4. **Server Components read it cleanly**: Next.js Server Components use `cookies()` from `next/headers` to read the token and attach it to outbound API calls — no client-side state management needed for auth.

## Consequences

- The JWT is never accessible from any client-side JavaScript — `useEffect` + `fetch` patterns that try to read the token and call the Spring Boot API directly will fail silently (the cookie exists but JS can't read it)
- All authenticated calls to the Spring Boot API must go through Server Components or Next.js API routes — they use `next/headers` to read and forward the cookie
- `NEXT_PUBLIC_` env vars must not include anything sensitive — they are bundled into client JavaScript
- Cookie expiry matches `jwt.expiration-ms` on the Spring Boot side — they rotate together
- `Secure: true` means the cookie only works over HTTPS — local dev must either use HTTP (dev mode) or a local TLS proxy

## Failure mode if violated

If JWT is stored in `localStorage` instead: one reflected XSS anywhere on any page can exfiltrate all user tokens. Given that the app renders user-supplied content (`ride.notes`, `booking.passengerMessage`), this is a realistic attack surface.

## Related

- ADR-007 — JWT issuance on the backend
- ADR-008 — Next.js web app
- `security.md` agent — cross-layer auth rules
- `frontend-web.md` agent — implementation details
