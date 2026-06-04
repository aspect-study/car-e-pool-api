# Ratings Wall — Design Spec

**Date:** 2026-06-04  
**Status:** Approved  
**Surface:** Telegram bot  

---

## Overview

Show received ratings (stars + comment) for any user in the Telegram bot. Accessible from the main menu (own profile) and from driver cards during ride search (other users). Paginated to keep the chat clean.

---

## Entry Points

| Trigger | Who is shown | Callback emitted |
|---|---|---|
| "My Ratings ⭐" button on main menu | Current user | `VIEW_RATINGS:{currentUserId}` |
| "See Ratings" button on driver info card | Driver being viewed | `VIEW_RATINGS:{driverUserId}` |

---

## Callbacks

| Callback | Action |
|---|---|
| `VIEW_RATINGS:{userId}` | Load page 0 for userId |
| `RATINGS_PAGE:{userId}:{page}` | Load the given page for userId |

Page size: **5 ratings per page**.  
Both callbacks **edit the existing message** — no new message sent.

---

## Message Format

```
Your Ratings (12 total)          ← own profile
Ratings for Ahmed (12 total)     ← viewing another user

⭐⭐⭐⭐⭐
Great driver, very smooth ride!

⭐⭐⭐
Arrived a bit late but friendly.

...

— Page 1 of 3 —
```

- Ratings with no comment: show stars only, do not skip the entry.
- Navigation inline keyboard row: `« Prev` (hidden on page 0) · `Next »` (hidden on last page) · `✕ Close`
- `✕ Close` deletes the message entirely.

---

## Service Layer

**New method in `RatingService`:**

```java
Page<RideRating> getRatingsReceivedPaged(Long userId, int page, int pageSize);
```

Delegates to the repository with a `PageRequest.of(page, pageSize)`.

**New method in `RideRatingRepository`:**

```java
Page<RideRating> findByRateeIdOrderByCreatedAtDesc(Long rateeId, Pageable pageable);
```

Spring Data derives this — no custom JPQL required. No DB migration needed; `RideRating.comment` already exists.

---

## Bot Handler

**New class:** `ViewRatingsHandler` in `carpool-bot`  
**Registered in:** `CallbackHandler`  
**Dependencies:** `RatingService`, `UserService`  
**State:** None — fully stateless, driven by callback data.

Responsibilities:
- Parse `userId` and `page` from callback data
- Fetch paged ratings via `RatingService.getRatingsReceivedPaged`
- Fetch display name via `UserService` (for "Ratings for [Name]" header when viewing others)
- Build and edit the message with formatted entries and navigation keyboard

---

## Wiring Changes

- `MainMenuHandler` — add "My Ratings ⭐" button emitting `VIEW_RATINGS:{currentUserId}`
- Driver info card builder — add "See Ratings" button emitting `VIEW_RATINGS:{driverUserId}`

---

## Out of Scope

- REST API exposure (bot-only for now)
- Filtering by role (driver vs. passenger comments shown together)
- Pagination on web or mobile frontends
