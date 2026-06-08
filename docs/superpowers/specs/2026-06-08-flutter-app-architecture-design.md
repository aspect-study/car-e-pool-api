# Flutter Mobile App — Architecture Design

**Date:** 2026-06-08
**Status:** Approved
**Scope:** Car-E-Pool Flutter app — full feature parity with the Telegram bot, Android-first, both Driver and Passenger roles

---

## 1. Context

The project currently delivers its entire UX through a Telegram bot. This spec defines the architecture for a Flutter mobile app that provides the same functionality through a native Android interface. The Telegram bot will continue to operate in parallel — both channels share the same Spring Boot REST API backend.

---

## 2. Goals

- Full feature parity with the Telegram bot (all driver and passenger flows)
- Android-first; iOS added later with minimal incremental work
- Both Driver and Passenger roles supported from launch
- Native push notifications via FCM (phased: Telegram fallback in Phase 1, native push in Phase 2)
- Passenger flows shipped first, driver flows second

---

## 3. Non-Goals

- Replacing or modifying the Telegram bot
- Building a new backend (all data comes from the existing REST API at `/api/v1/`)
- iOS distribution in the initial release
- Web or desktop Flutter targets

---

## 4. Flutter Stack

| Concern | Library | Version |
|---------|---------|---------|
| State management | `flutter_riverpod` + `riverpod_annotation` | 2.x |
| Navigation | `go_router` | 14.x |
| HTTP client | `dio` | 5.x |
| Auth token storage | `flutter_secure_storage` | 9.x |
| Telegram Login WebView | `flutter_inappwebview` | 6.x |
| Data models | `freezed` + `json_serializable` | 2.x / 4.x |
| Push notifications | `firebase_messaging` + `flutter_local_notifications` | latest |
| UI system | Material Design 3 (Flutter built-in) | — |
| Profile photos | `cached_network_image` | 3.x |

**Code generation:** `build_runner`, `freezed`, `json_serializable`, `riverpod_generator`

---

## 5. Project Structure

Feature-first layout. Each feature owns its screens, providers, and repository. Shared models and widgets live in `core/` and `shared/`.

```
lib/
├── main.dart
├── app.dart                          ← MaterialApp.router + ProviderScope
├── core/
│   ├── api/
│   │   ├── dio_client.dart           ← Dio instance, JWT interceptor, 401 redirect
│   │   └── api_exception.dart        ← typed API error model
│   ├── auth/
│   │   ├── auth_repository.dart      ← login, logout, token storage
│   │   ├── auth_provider.dart        ← Riverpod AsyncNotifier, watches JWT
│   │   └── token_storage.dart        ← flutter_secure_storage wrapper
│   ├── router/
│   │   └── router.dart               ← go_router, auth redirect guard, role guards
│   ├── push/
│   │   ├── push_service.dart         ← FCM token registration, foreground handler
│   │   └── notification_router.dart  ← tap handler → deep-link to correct screen
│   └── models/                       ← all freezed DTOs mirroring backend responses
│       ├── ride.dart
│       ├── booking.dart
│       ├── user.dart
│       ├── hub.dart
│       ├── rating.dart
│       ├── vehicle.dart
│       └── paged_response.dart
├── features/
│   ├── auth/
│   │   └── login_screen.dart         ← WebView + Telegram Login Widget
│   ├── home/
│   │   └── home_screen.dart          ← context-aware shell (role + active ride)
│   ├── rides/
│   │   ├── ride_search_screen.dart
│   │   ├── ride_detail_screen.dart
│   │   ├── rides_provider.dart
│   │   └── rides_repository.dart
│   ├── post_ride/
│   │   ├── step1_when_screen.dart    ← direction + date + time
│   │   ├── step2_route_screen.dart   ← origin + destination hub search
│   │   ├── step3_details_screen.dart ← seats, gas, note, vehicle
│   │   ├── step4_confirm_screen.dart ← review + post
│   │   └── post_ride_provider.dart   ← wizard state (Riverpod StateNotifier)
│   ├── my_rides/
│   │   ├── active_ride_screen.dart   ← driver dashboard (passengers, start, cancel)
│   │   ├── my_rides_screen.dart      ← past rides + repost
│   │   └── my_rides_provider.dart
│   ├── bookings/
│   │   ├── bookings_screen.dart
│   │   ├── booking_detail_screen.dart
│   │   └── bookings_provider.dart
│   ├── passengers/
│   │   ├── passengers_screen.dart    ← driver: list + accept/decline
│   │   └── passengers_provider.dart
│   ├── profile/
│   │   ├── profile_screen.dart
│   │   ├── vehicles_screen.dart
│   │   ├── favorites_screen.dart
│   │   ├── followers_screen.dart
│   │   └── profile_provider.dart
│   └── ratings/
│       ├── ratings_wall_screen.dart
│       ├── rate_user_screen.dart
│       └── ratings_provider.dart
└── shared/
    └── widgets/
        ├── ride_card.dart
        ├── booking_card.dart
        ├── hub_search_field.dart     ← autocomplete, debounced search
        ├── rating_stars.dart
        ├── direction_toggle.dart
        └── empty_state.dart
```

---

## 6. Authentication Flow

The Telegram Login Widget is a web component — it cannot run natively in Flutter. The approach:

1. App launches → check JWT in `flutter_secure_storage`
2. JWT present → make a lightweight API call (e.g. `GET /users/me/stats`); if 401, clear token and go to step 3
3. No JWT (or expired) → open `flutter_inappwebview` pointing at the Next.js `/login` page
4. Telegram widget fires → Next.js calls `POST /api/v1/auth/telegram` → returns JWT in response or sets it in a redirect URL parameter
5. WebView intercepts the redirect → extracts JWT → stores in `SecureStorage`
6. *(Phase 2 only)* Register FCM device token: `POST /api/v1/device-tokens` with the token from `firebase_messaging`
7. Navigate to Home

**go_router redirect guard:** `authProvider` exposes the current auth state as a stream. The router's `redirect` callback sends unauthenticated users to `/login` on any navigation attempt. Role-based guards block driver-only routes for Passenger-role users.

**JWT refresh:** the Dio interceptor catches 401 responses, clears the stored token, and redirects the user to `/login`.

---

## 7. Navigation Structure

Bottom navigation bar — tab count depends on role:

**Passenger role (3 tabs):**
```
Tab 1: Find Ride
Tab 2: My Bookings
Tab 3: Profile
```

**Driver or Both role (4 tabs):**
```
Tab 1: Find Ride
Tab 2: My Rides / Active Ride  ← driver dashboard or ride history
Tab 3: My Bookings
Tab 4: Profile
```

The Home shell reads the user's role from the `authProvider` on mount. Tab 2 (My Rides) is injected only for Driver/Both. When a driver has an active ride, Tab 2 routes to the Active Ride Dashboard instead of the rides history list. This mirrors the bot's context-aware main menu.

---

## 8. Screen Map

### Phase 1 — Passenger flows

| Screen | Route | Description |
|--------|-------|-------------|
| Find Ride | `/rides` | Direction, date, time window selector + paginated results + filter sheet |
| Ride Detail | `/rides/:id` | Full ride card + Book button + See Ratings button |
| Book Ride | `/rides/:id/book` | Optional message + confirm |
| My Bookings | `/bookings` | List of active bookings (PENDING / CONFIRMED) |
| Booking Detail | `/bookings/:id` | Ride info, driver details, vehicle + plate (if CONFIRMED), cancel |
| Rate Driver | `/rate/:rideId` | Star picker + optional comment |
| Ratings Wall | `/ratings/:userId` | Paginated received ratings, 5 per page |
| Profile | `/profile` | Stats, role badge, member since |
| My Vehicles | `/profile/vehicles` | List + add + remove (max 3) |
| My Favorites | `/profile/favorites` | Followed drivers list + unfollow |
| My Followers | `/profile/followers` | Driver-only: who follows you |
| Terms | `/terms` | Accept/decline prompt (first login) |
| Login | `/login` | WebView: Telegram Login Widget |

### Phase 2 — Driver flows

| Screen | Route | Description |
|--------|-------|-------------|
| Post Ride Step 1 | `/post/when` | Direction toggle, calendar, time picker |
| Post Ride Step 2 | `/post/route` | Origin hub search, destination hub search |
| Post Ride Step 3 | `/post/details` | Seats stepper, gas input, note, vehicle picker |
| Post Ride Step 4 | `/post/confirm` | Full summary + Post button |
| Active Ride Dashboard | `/my-ride` | Ride card, passenger count, action buttons |
| My Passengers | `/my-ride/passengers` | Confirmed + pending list |
| Accept/Decline | `/my-ride/passengers/:bookingId` | Bottom sheet: passenger info + accept/decline |
| Edit Departure Time | `/my-ride/edit-time` | Calendar + time picker, 15-min-minimum rule |
| Re-announce | `/my-ride/reannounce` | Seat count input + confirm |
| Repost a Ride | `/my-rides/:id/repost` | Pre-filled edit form |
| My Past Rides | `/my-rides` | Last 3 rides + repost buttons |

### Shared (always available)

| Screen | Route |
|--------|-------|
| Home shell | `/home` |
| Notification inbox | `/notifications` |
| Hub suggest | (inline in hub search) |

---

## 9. Post Ride — Grouped Screen Flow

The 9-step bot wizard becomes 4 grouped screens:

```
Step 1 — When
  Direction toggle (Home→Work / Work→Home)
  Date (Material DatePicker)
  Time (Material TimePicker)

Step 2 — Route
  Origin hub: text field + debounced autocomplete list (GET /hubs/search?q=)
  Destination hub: same, origin excluded from results
  Full-screen viewport needed for keyboard + live list

Step 3 — Ride Details
  Seats: stepper widget (1–7)
  Gas contribution: number field with ₱ prefix
  Note: optional TextField, 300-char limit, counter
  Vehicle: card list from GET /users/me/vehicles

Step 4 — Confirm
  Read-only summary of all fields
  [Post Ride] button → POST /rides
  [Back] to edit any step
```

Wizard state is held in a single `PostRideNotifier` (Riverpod `StateNotifier`) so navigating back preserves all inputs.

---

## 10. Push Notifications (FCM)

### Phase 1 — Telegram fallback

No backend changes. App users receive notifications via Telegram (they still need the bot). The app polls `GET /bookings/mine` and `GET /rides/mine/active` on foreground resume to refresh state.

### Phase 2 — Native FCM push

**Backend additions:**

1. `device_tokens` table: `(id, user_id FK, token VARCHAR(255), platform ENUM('ANDROID','IOS'), created_at)`
2. Flyway migration: `V45__add_device_tokens.sql`
3. `POST /api/v1/device-tokens` — register token on login
4. `DELETE /api/v1/device-tokens/{token}` — deregister on logout
5. `PushNotificationService` — Firebase Admin SDK, sends FCM messages
6. Wire into existing `@TransactionalEventListener` stack — each notification event gets a parallel push send alongside the existing Telegram send

**Events wired for push:**

| Event | Push message |
|-------|-------------|
| `BookingConfirmedEvent` | "Your booking is confirmed!" |
| `BookingDeclinedEvent` | "Booking declined: [reason]" |
| `BookingCancelledByDriverEvent` | "Your booking was cancelled" |
| `RideTimeUpdatedEvent` | "Departure time changed — keep or cancel?" |
| `RideCancelledEvent` | "Ride cancelled by driver" |
| `DepartureReminderEvent` | "Your ride departs in 30 minutes" |
| `RideCompletedEvent` | "Ride complete — rate your [driver/passenger]" |
| Pending booking reminder | "You have a pending booking request" |

**Flutter side:**

- `firebase_messaging` handles foreground, background, and terminated-state messages
- `flutter_local_notifications` shows heads-up banners when app is in foreground
- Notification tap payload contains `type` + `entityId` → `NotificationRouter` deep-links to the correct screen (e.g., `type=BOOKING_CONFIRMED, bookingId=42` → `/bookings/42`)

---

## 11. Shared Widgets

| Widget | Used in |
|--------|---------|
| `RideCard` | Search results, active ride dashboard, past rides |
| `BookingCard` | My Bookings list |
| `HubSearchField` | Post Ride Step 2, ride search filters |
| `RatingStars` | Rate screen, ride detail, profile |
| `DirectionToggle` | Home, post ride, search |
| `EmptyState` | All list screens (no rides, no bookings, etc.) |

---

## 12. Implementation Order

### Foundation (prerequisite for everything)
- Flutter project scaffold, `pubspec.yaml` with all dependencies
- `DioClient` with JWT `Authorization: Bearer` interceptor + 401 logout handler
- `TokenStorage` (SecureStorage wrapper)
- `AuthProvider` (Riverpod `AsyncNotifier`) + `LoginScreen` (WebView)
- `go_router` with auth redirect guard and role-based guards
- All `freezed` DTO models mirroring backend response shapes
- `ProviderScope` in `app.dart`

### Passenger flows
Ride search → Ride detail → Book → My Bookings → Booking Detail → Rate Driver → Ratings Wall → Profile → Vehicles → Favorites / Followers

### Driver flows
Post Ride wizard (4 screens) → Active Ride Dashboard → My Passengers → Accept/Decline → Start/Complete → Edit Time → Re-announce → Repost

### Push (FCM) — Phase 2
Backend: Flyway migration, `PushNotificationService`, `DeviceTokenController`, wire into event listeners
Flutter: Firebase project setup, `firebase_messaging` init, token registration on login, `NotificationRouter`, deep-link handlers

---

## 13. Key Constraints from Backend

- `plate_number` is **not** returned in public endpoints — only in `/me/vehicles` (own data) and booking confirmation DMs. The app must not display plate numbers anywhere except in a confirmed passenger's own booking detail.
- `availableSeats` is the source of truth for whether a ride can be booked (`status == ACTIVE`).
- Direction conflict rules are enforced server-side — the app should surface the 400 error message directly to the user rather than duplicating the guard logic.
- The Telegram Login Widget hash validation is entirely server-side. The app just passes the raw widget response payload to `POST /auth/telegram`.
- Rate limiting: 60 req/min per IP. Avoid polling loops; use pull-on-resume instead.

---

## 14. Out of Scope for This Spec

- Admin flows (hub approval, role management) — admin panel is the webforJ app
- Pre-scheduled return ride feature (in backlog)
- Passenger-initiated ride request flow (in backlog)
- iOS distribution (deferred)
- Web or desktop Flutter targets
