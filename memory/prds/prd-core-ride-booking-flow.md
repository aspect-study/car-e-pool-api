# PRD: Core Ride Booking Flow

**Status:** Shipped  
**Module owners:** carpool-service, carpool-bot, carpool-web

## Problem

Employees at a company need to coordinate daily carpools between home and work. There's no structured way to offer or find a ride — coordination happens ad-hoc in Telegram groups with no booking confirmation, no seat tracking, and no ride history.

## Solution

A Telegram-bot-first ride-sharing platform where drivers post daily rides and passengers book seats. All interaction happens in a Telegram private chat; ride announcements are auto-posted to the company group.

## Core Flows

### Driver: Post a Ride
1. Open bot → Main Menu → "Post Ride"
2. Select direction (Home→Work or Work→Home)
3. Select origin hub (list of approved company hubs)
4. Pick departure time (calendar + time picker)
5. Set available seats (1–4)
6. Set contribution amount (optional)
7. Confirm → ride created → announcement posted to group topic thread

### Passenger: Book a Seat
1. Find the ride announcement in the group (or find via bot)
2. Tap "Book" inline button
3. Bot confirms: "Seat reserved. Driver notified."
4. Driver receives notification: "New booking from [name]"

### Driver: Manage a Ride
- Edit departure time → group announcement updated
- Cancel ride → all passengers notified → bookings cancelled
- Re-announce → re-post announcement with current seat count
- View bookings → list of passengers with mini-profile

### Passenger: Manage a Booking
- Cancel booking → driver notified → seat freed → announcement refreshed

## Constraints

- Max 4 seats per ride
- One active ride per driver per direction per day (direction-scoped conflict check)
- Plate number masked in all outputs (first 3 chars + ***)
- Bot processes only private chat messages; group is announce-only
- Departure time editable until T-30 minutes before departure

## Key Entities

- `Ride` — driver, direction, originHub, departureTime, availableSeats, status (ACTIVE / CANCELLED / EXPIRED / FULL)
- `Booking` — ride, passenger, status (CONFIRMED / CANCELLED / PENDING)
- `User` — telegramId, chatId, firstName, role (DRIVER / PASSENGER / BOTH)
- `Vehicle` — user, plateNumber (masked in outputs), make, model
- `Hub` — name, location, status (PENDING / APPROVED / REJECTED)

## Planned Extensions (in progress)

- **Next.js web app** — browser-based interface for passengers and drivers (ADR-008)
- **Flutter mobile app** — iOS and Android app consuming the same REST API (ADR-009)

## Non-Goals (this iteration)

- Passenger-initiated ride requests (deferred — see project backlog)
- Payment integration
- Route optimization or map integration