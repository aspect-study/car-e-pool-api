# ADR-008: Next.js (App Router) for the Web Frontend

**Status:** Accepted
**Date:** 2026-05
**Deciders:** Project architect

## Context

The carpool platform needs a web interface for passengers and drivers who prefer a browser over the Telegram bot. The Spring Boot REST API already exists. The choices evaluated were:

- **Next.js (App Router)** — React-based, SSR + RSC, strong TypeScript support, official Telegram Login Widget React component
- **Vue 3 / Nuxt** — good framework, but smaller ecosystem for Telegram-specific integrations
- **SvelteKit** — lighter, excellent performance, but smaller community and fewer mature libraries for auth patterns
- **Plain React (Vite + SPA)** — no SSR, SEO irrelevant for authenticated routes, but auth callback handling is clunkier without a server layer

## Decision

Use **Next.js 14+ with the App Router** (not the legacy Pages Router).

Key reasons:

1. **Telegram Login Widget integration**: The widget redirects to a callback URL with query params. A Next.js API route (`app/api/auth/telegram/route.ts`) handles the callback server-side, calls the Spring Boot `POST /api/v1/auth/telegram` endpoint, and sets an HTTP-only cookie — all without the JWT ever touching client JavaScript. This pattern is natural in Next.js and awkward in a pure SPA.

2. **Server Components by default**: Data fetching for authenticated routes (ride listings, booking detail) happens in Server Components — no `useEffect` + `fetch` patterns that would expose the JWT to client-side code.

3. **App Router over Pages Router**: App Router is the current standard. Pages Router is in maintenance mode. All new Next.js patterns (Server Actions, `next/headers`, Suspense boundaries) are App Router only.

4. **TypeScript + ecosystem**: Strong ecosystem for the auth pattern (cookies via `next/headers`), form handling (Zod + Server Actions), and data fetching (TanStack Query for client-side mutations).

## Consequences

- The `app/api/auth/telegram/route.ts` API route is the only place that calls the Spring Boot auth endpoint — the JWT is set as an HTTP-only cookie server-side
- `API_BASE_URL` must never have the `NEXT_PUBLIC_` prefix — it would leak into the client bundle
- `middleware.ts` handles route protection for all `/app/(protected)/*` paths — no per-page auth checks needed
- Server Components are the default; Client Components (`'use client'`) are only for interactive widgets (hub autocomplete, booking form)
- `NEXT_PUBLIC_BOT_USERNAME` is the only frontend-accessible env var — it's the public Telegram bot handle needed by the Login Widget script

## Related

- ADR-007 — JWT auth on the backend (the token this app receives and stores)
- ADR-010 — HTTP-only cookie decision (how the JWT is stored on the web client)
- `frontend-web.md` agent — implementation patterns
