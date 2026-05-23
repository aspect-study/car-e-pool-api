# 🚗 Car-e-pool PH

A full-stack carpooling platform for employees to share rides to and from work. Drivers post rides via a Telegram bot, passengers browse and book, and the system handles the full lifecycle — confirmations, group announcements, notifications, ratings, and an admin panel for hub management.

---

## Features

### For Drivers
- Post a ride in seconds via Telegram bot — direction, departure time, seats, gas share, vehicle, and notes
- Natural language ride posting — just type "magpost BGC bukas 7am" and the AI fills in the details
- Re-announce rides to the group (up to 10 times) with updated seat counts
- Edit departure time on an active ride — confirmed passengers are notified immediately and can keep or cancel their booking
- Manage bookings — accept, decline, or remove passengers
- View follower count and who's following you
- Rate passengers after each completed ride

### For Passengers
- Browse available rides with filters (direction, date, price, rating)
- Book a seat with a custom message to the driver
- View booking status and full trip details — vehicle and plate number revealed only after the driver accepts
- Rate drivers after each completed ride
- Follow favorite drivers and get notified when they post a new ride

### Group Announcements
- Rides are automatically posted to the designated Telegram group topic
- Announcement is updated in real time when seats change (booking accepted, passenger removed)
- Announcement is removed when the ride is full, departed, completed, or cancelled
- Vehicle plate number is intentionally hidden from public posts — only disclosed to confirmed passengers

### Notifications
- Driver notified instantly when a passenger requests a seat (includes passenger mini-profile)
- Passenger notified when booking is confirmed, declined, or cancelled
- 30-minute departure reminder for driver and all confirmed passengers
- Departure notification with full vehicle details sent to confirmed passengers
- All confirmed passengers notified when a driver changes departure time, with Keep / Cancel booking buttons inline

### Admin Panel
- Separate web UI (port 8082) for hub management
- Approve or reject community-suggested pickup/dropoff hubs
- Dashboard with platform activity overview

---

## Architecture

Multi-module Maven project with a strict one-way dependency chain:

```
carpool-common
      ↓
carpool-domain
      ↓
carpool-repository
      ↓
carpool-service
      ↓              ↓
carpool-bot      carpool-admin
      ↓
carpool-web
```

| Module | Responsibility |
|--------|---------------|
| `carpool-common` | Shared exceptions, `ApiResponse`, `PagedResponse` |
| `carpool-domain` | JPA entities, enums — no Spring, no repositories |
| `carpool-repository` | Spring Data JPA repositories |
| `carpool-service` | Business logic, DTOs, MapStruct mapper, schedulers, event system |
| `carpool-bot` | Telegram bot handlers, conversation state, group notifications |
| `carpool-admin` | webforJ admin panel (port 8082) |
| `carpool-web` | Spring Boot entry point, REST controllers, security, Flyway migrations |

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 25 |
| Framework | Spring Boot 4.0.5 |
| Database | MySQL + Flyway migrations |
| ORM | Spring Data JPA + MapStruct |
| Bot | Telegram Bot API |
| Auth | Stateless JWT + Telegram Login Widget |
| Cache | Caffeine |
| Rate Limiting | Bucket4j |
| AI | Spring AI + Ollama (llama3.1) |
| Admin UI | webforJ |
| Infrastructure | Docker + Docker Compose |

---

## Key Design Decisions

- **Event-driven notifications** — services publish domain events; `NotificationService` listens with `@Async + @TransactionalEventListener(AFTER_COMMIT)` so notifications only fire after the transaction commits
- **Immutable bot state** — conversation state is an immutable record with `with*()` builder methods, held in a Caffeine cache per chat ID
- **Pessimistic locking on booking** — `SELECT FOR UPDATE` on the ride row prevents double-booking the last seat
- **Privacy by design** — vehicle plate numbers are withheld from all public surfaces and only disclosed to passengers with a confirmed booking
- **Best-effort profile enrichment** — passenger mini-profiles in booking requests are loaded in a separate try-catch so a profile lookup failure never blocks the notification

---

## Project Status

Active — running in production for a live carpool community.

---

*Built as a personal learning project to study Spring Boot, Telegram bot development, event-driven architecture, and AI integration.*