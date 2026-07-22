# Design System — Carpool (public marketplace web app)

## Product Context
- **What this is:** A web app for a public carpool ride-sharing marketplace — drivers post rides, passengers book seats, matching happens between strangers rather than coworkers.
- **Who it's for:** General public riders and drivers, not gated to one company's employees.
- **Space/industry:** Ride-sharing / carpool marketplace — peers are BlaBlaCar, Waze Carpool, Liftshare.
- **Project type:** Web app (Next.js App Router) — authenticated booking/search flows plus a public-facing marketing/trust surface.
- **Memorable thing:** Feels like a trustworthy platform for strangers to share a car — safety and verification earn the trust that a shared employer used to provide for free.

## Aesthetic Direction
- **Direction:** Refined/Grounded — calm, precise, "handled with care." Deliberately not playful-bubbly like every competitor in the category (BlaBlaCar, Waze Carpool, Liftshare all use rounded friendly-illustration styles).
- **Decoration level:** Intentional — a recurring route-line motif (thin traced journey lines, animated draw-in on the hero) instead of blob shapes or character illustrations. No literal maps, no pins.
- **Mood:** The product should feel the way a well-run insurance or fintech app feels — established, careful, unglamorous in the right way — rather than "fun community app." Warmth comes from the coral accent and Fraunces serif, not from cartoon people.
- **Reference sites:** [Liftshare](https://liftshare.com) (category convention: teal, rounded, community-illustration-heavy — used as the "don't do this" baseline), BlaBlaCar/Waze Carpool (researched via web search — two-way ratings, verified-profile badges, real-time trip status are category table stakes).

## Typography
- **Display/Hero:** Fraunces — a serif, deliberately departs from the rounded geometric sans every competitor uses for display type. Signals "established/trustworthy" rather than "toy-like."
- **Body:** Instrument Sans — clean, humanist, does the actual talking.
- **UI/Labels:** Instrument Sans (same as body).
- **Data/Tables:** Geist, `font-variant-numeric: tabular-nums` — prices, departure times, ratings counts, seat counts must align in columns.
- **Code:** Not a primary surface for this product; use Geist Mono or JetBrains Mono only if an internal/admin surface needs it.
- **Loading:** Self-hosted — inline `@font-face` via base64 data URIs (required in any Artifact/CSP-restricted context; in the real Next.js app, use `next/font/google` for Fraunces, Instrument Sans, and Geist instead of a Google Fonts `<link>`).
- **Scale:** Hero 68px / 40px (clamp), section heading 30px, sub-heading 24px, body 16px, small/meta 13px, data 16px (tabular).

## Color
- **Approach:** Balanced — pine + coral carry meaning, everything else is neutral.
- **Primary — Pine:** `#1F4E3D` — brand, verification badges, primary accents on trust surfaces. Chosen over the near-universal bright teal/blue of every competitor.
- **Accent — Coral:** `#E8623D` — the one color that means "tap this." Used only for primary CTAs (Book, Request to book, Search).
- **Neutrals:** Cream background `#FAF6F0` (not stark white), surface `#FFFFFF`, ink text `#1A2420`, soft ink `#4B5751`, line/border `#E1D9CB`, muted/mist `#8A8375`.
- **Semantic:** success `#2F7D5C`, warning `#A8721F`, error `#B23A2E`, info `#2F5F81` — each with a soft background tint for alert/badge fills.
- **Dark mode:** Not a naive invert — surfaces move to a warm near-black (`#14120D` bg / `#1C2620` card), pine and coral both lighten for contrast (`#6FBE9C` / `#F0855F`), soft tints darken rather than lighten. See the CSS custom-property tokens in the approved preview for exact values.

## Spacing
- **Base unit:** 8px.
- **Density:** Comfortable on marketing/trust surfaces, slightly cozier on ride-list rows (they get scanned, not read).
- **Scale:** 2xs(2) xs(4) sm(8) md(16) lg(24) xl(32) 2xl(48) 3xl(64).
- **Border radius:** sm 6px (buttons, inputs, ride-card list rows), md 10px (cards, swatches), lg 18px (chrome-framed screen mockups, modals), full 999px (pills, badges, avatars).

## Layout
- **Approach:** Hybrid — grid-disciplined for app screens (ride search, booking, profile: strict columns, predictable alignment, built to be scanned), editorial/asymmetric for marketing and trust-story surfaces (hero, safety explainer).
- **Grid:** Ride-card rows use a 4-column grid (driver block / route / spacer / price) collapsing to single-column under ~780px. Marketing hero uses an asymmetric 1.15fr/0.85fr split collapsing under ~860px.
- **Max content width:** 1180px.

## Motion
- **Approach:** Intentional, not expressive — no bounce, no playful character animation (a deliberate departure from category convention).
- **Signature moment:** The route-line motif draws itself in via `stroke-dasharray`/`stroke-dashoffset` on the hero, once, on load. Respects `prefers-reduced-motion: reduce` (renders fully drawn, no animation).
- **Easing:** enter `cubic-bezier(.4,0,.2,1)`, exit `ease-in`, move `ease-in-out`.
- **Duration:** micro 50–100ms (hover states), short 150–250ms (UI transitions), medium 250–400ms, long 2.4s (the one-time hero route-line draw only).

## Trust & Safety Surfaces (product-specific, not just visual)
These are first-class UI elements, not afterthoughts, because the product's core risk is matching strangers:
- **Verified-identity badge** — a small pine-tinted pill (`✓ Verified`) next to every driver/passenger name, everywhere a name appears (ride card, booking panel, profile).
- **Two-way ratings** — always visible next to a name as `★ rating (count)`, never hidden behind a profile tap.
- **Rating distribution + review list** — profile screens show the 1–5 star breakdown as bars, plus individual reviews tagged `As driver` / `As passenger` (role matters — a driver review and a passenger review say different things).
- **Safety checklist on the booking panel** — identity verification, trip-sharing, in-app SOS listed explicitly before the user confirms a booking, using the success-green dot, not the coral accent (coral is reserved for the one CTA action).

## Decisions Log
| Date | Decision | Rationale |
|------|----------|-----------|
| 2026-07-10 | Initial design system created | `/design-consultation` run against the pivot to a public-rider marketplace (see project decision to move from single-company internal tool to general-public scope). Researched Liftshare (visual baseline), BlaBlaCar/Waze Carpool (trust-mechanic conventions) via web search + browse. |
| 2026-07-10 | Serif display (Fraunces) chosen over category-standard rounded sans | Deliberate risk: differentiates from every competitor's "friendly community app" look, reinforces "handled with care" positioning given the product now matches strangers, not coworkers. |
| 2026-07-10 | Pine/coral/cream palette chosen over near-universal carpool-category teal/blue | Deliberate risk: avoids reading as a BlaBlaCar/Liftshare clone at a glance; costs some instant category recognition, gains a calmer/more premium feel. |
