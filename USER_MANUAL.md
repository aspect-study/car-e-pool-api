# Car-E-Pool — User Manual
**Version 1.0 | May 2026**

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [Getting Started](#2-getting-started)
3. [Core Functionalities](#3-core-functionalities)
   - 3.1 Posting a Ride (Driver)
   - 3.2 Finding & Booking a Ride (Passenger)
   - 3.3 Managing Your Ride (Driver)
   - 3.4 Managing Your Bookings (Passenger)
   - 3.5 Ratings & Favorites
   - 3.6 Hubs
   - 3.7 Profile & Vehicle Setup
4. [Business Rules & Validations](#4-business-rules--validations)
5. [User Roles & Permissions](#5-user-roles--permissions)
6. [Troubleshooting & FAQs](#6-troubleshooting--faqs)

---

## 1. Introduction

### 1.1 Purpose of This Manual

This manual is the complete reference for all users of **Car-E-Pool** — a community-based carpooling platform built for daily commuters. It describes every feature, rule, and workflow so that any new user can operate the system independently without prior training.

### 1.2 What Is Car-E-Pool?

Car-E-Pool is a **Telegram-based carpooling coordination system** designed for commuters sharing a common corridor (e.g., South Metro Manila ↔ BGC / Makati). It connects drivers who have empty seats with passengers looking for a comfortable, shared ride.

The entire user experience operates through a **Telegram bot** (private chat). There is no separate mobile app to download or website to visit. Everything — from booking to rating to notifications — happens within Telegram.

### 1.3 How the System Works at a Glance

```
Driver posts ride → System announces in community group
Passenger searches → Sends booking request to driver
Driver accepts → Both receive confirmation
Ride departs & completes → Both parties rate each other
Passenger optionally follows driver → Gets alerts for future rides
Group members can tap ⭐ Follow Driver on any announcement → Follow driver + view ride in one tap
```

### 1.4 Key Concepts

| Term | Meaning |
|------|---------|
| **Ride** | A trip offered by a driver with available seats |
| **Booking** | A passenger's request to join a ride |
| **Hub** | A named pickup/dropoff landmark (e.g., "SM Southmall", "BGC High Street") |
| **Gas Contribution** | The per-passenger cost share for fuel (set by the driver) |
| **Favorite Driver** | A driver you follow to receive alerts when they post new rides |
| **Group Announcement** | An automatic post in the community Telegram group when a ride is published |
| **Direction** | HOME → WORK (morning) or WORK → HOME (evening) |

---

## 2. Getting Started

### 2.1 System Requirements

| Requirement | Details |
|-------------|---------|
| Device | Any smartphone, tablet, or computer |
| App | Telegram (any version supporting bots) |
| Internet | Active internet connection |
| Account | A Telegram account |

No additional software, app, or registration form is needed.

### 2.2 Accessing the Bot

1. Open Telegram and search for the Car-E-Pool bot (the bot username is configured by your community admin).
2. Tap **START** or send `/start`.
3. The bot will greet you and prompt you to log in via Telegram authentication.

> **Note:** The bot only responds to **private chat messages**. It reads group messages from the community channel only to post and delete ride announcements — it does not reply to them.

### 2.3 Registration & First Login

Registration is **automatic** — there is no separate sign-up form. The process:

1. You send `/start` to the bot.
2. The bot verifies your Telegram identity automatically using your Telegram account data.
3. If this is your **first time**, an account is created for you instantly using:
   - Your Telegram full name
   - Your Telegram username (handle), if set
   - Your profile photo (if public)
4. On **subsequent logins**, your profile information is refreshed from Telegram automatically.

**What you do NOT need to enter manually at registration:** email, password, or phone number. Telegram handles identity.

### 2.4 Terms & Conditions

On your first login (and periodically if you have previously declined), the bot will present the **Community Terms & Conditions**.

| Your Action | Result |
|------------|---------|
| **Accept** | Your acceptance is recorded. You may use all features. |
| **Decline** | Your account remains. You will be re-prompted after 7 days. |

> **Important:** Declining the Terms does NOT delete your account. However, you are expected to accept before participating in the community.

### 2.5 Navigating the Bot

The bot uses **inline keyboard buttons** — tappable options that appear beneath messages. You rarely need to type anything manually; most actions are button-driven.

Buttons are **color-coded** by intent:

| Color | Meaning | Examples |
|-------|---------|---------|
| 🔵 Blue (Primary) | Navigation or neutral action | 🏠 Menu, ◀️ Back |
| 🟢 Green (Success) | Confirm or positive action | ✅ Post Ride, ✅ Book This Ride, ➕ Add Vehicle |
| 🔴 Red (Danger) | Cancel or destructive action | ❌ Cancel, ❌ Decline |

**The main menu is context-aware** — it changes based on whether you have an active ride.

**When you have no active ride:**
- 🏠 **Home to Work** | 🏢 **Work to Home** — Select your direction to post or find a ride
- 📜 **My Bookings (N)** — Shown when you have active bookings as a passenger
- 🔄 **Repost a Ride** — Shown when you have past completed or cancelled rides
- 👤 **My Profile** — View your stats, vehicles, and ratings

**When you have an active (posted) ride, the menu shows your ride card and:**
- 📋 **View Bookings** | 🚀 **Start Ride**
- ⏳ **Pending (N)** | ❌ **Cancel Ride** — Shown when you have pending booking requests
- 📢 **Re-announce (N left)** — Shown when re-announces are still available
- 🔍 **Find a Ride** | 👤 **My Profile**

**When your ride has departed:**
- 📋 **View Bookings** | ✅ **Complete Ride**
- 👤 **My Profile**

Press **🏠 Menu** at any time to return to the main menu and cancel the current flow.

Available commands: `/start`, `/postride`, `/findride`, `/myrides`, `/mybookings`, `/profile`, `/vehicle`, `/help`.

> **Session Note:** The bot maintains your current conversation step for **30 minutes** of inactivity. After 30 minutes idle, your session resets. Buttons from old messages remain visible in Telegram but will show a "session expired" prompt if tapped after a reset.

---

## 3. Core Functionalities

---

### 3.1 Posting a Ride (Driver)

**Who can post a ride:** Users with a **Driver** or **Both** role who have registered their vehicle.

**Step-by-step:**

#### Step 1 — Select Direction
The bot asks: *"Which direction is this ride?"*

| Button | Meaning |
|--------|---------|
| 🏠 Home → Work | Morning commute direction |
| 🏢 Work → Home | Evening commute direction |

#### Step 2 — Select Departure Date & Time
A **calendar picker** appears. Tap the day you are departing.

After picking a date, an **inline time picker** appears with 10 time slots in 30-minute increments. Tap your departure time. Use **◀️ Earlier** or **Later ▶️** to shift the visible window by 2 hours.

- HOME → WORK direction defaults to a 5:00 AM starting window
- WORK → HOME direction defaults to a 4:00 PM starting window
- To pick a time not shown in the picker, type it in the format `MM/DD HH:MM` (e.g., `05/15 07:30`)
- **Rule:** Departure time must be in the future. Past times are rejected.

#### Step 3 — Select Origin Hub (Pickup Point)

Type at least **3 characters** of a landmark name. The bot will suggest matching hubs from the active hub list.

- Tap a hub name button to select it.
- If no match is found, the bot offers: **"Use '[your text]'"** — this saves your custom location as a pending hub for admin review and uses it for this ride.
- The bot also shows your **5 most recently used** pickup points for quick reselection.

#### Step 4 — Select Destination Hub (Drop-off Point)

Same as Step 3. The origin hub is excluded from suggestions automatically.

**Rule:** Origin and destination cannot be the same location.

#### Step 5 — Number of Seats
Enter how many passenger seats you are offering.

- **Minimum:** 1 seat
- **Maximum:** 7 seats

#### Step 6 — Gas Contribution Amount
Enter the amount each passenger should contribute for fuel (in ₱).

- **Minimum:** ₱0.00 (free ride)
- No upper limit
- This is the **per-passenger** cost

#### Step 7 — Add Notes (Optional)
Add details that help passengers know what to expect — exact pickup spot, en-route stops, drop-off instructions, or reminders (e.g., exact change preferred, no pets).

- **Maximum:** 300 characters
- If you have used notes on previous rides, they appear as quick-select buttons (📌). Tap one to reuse it, or tap **✏️ Write new note** to write something different.
- Tap **⏭️ Skip** to post without a note.

#### Step 8 — Select Vehicle
The bot shows your saved vehicles as buttons. Tap the one you are using for this ride.

```
🚘 Silver Toyota Vios | 🔢 ABC1234
🚘 White Honda City   | 🔢 XYZ5678
➕ Add New Vehicle
```

- You can save up to **3 vehicles** under your account. If you have fewer than 3, an **➕ Add New Vehicle** button appears so you can register an additional vehicle on the spot.
- If you have **no vehicle saved yet**, the bot skips straight to the vehicle registration flow — add your vehicle details and it will be saved for future rides.
- After selecting a vehicle, the bot proceeds to the confirmation screen.

#### Step 9 — Review & Confirm
A summary of your ride is shown. Review all details carefully.

- Tap **✅ Post Ride** to publish.
- Tap **❌ Cancel** to discard without posting.

#### What Happens After Posting

1. Your ride is published to the community **Telegram group** in the appropriate topic (Home → Work topic or Work → Home topic) with full details and a "View | Book" link.
2. All users who have saved you as a **Favorite Driver** receive a private alert notification with an Unfollow option.
3. Your ride status becomes **ACTIVE** and is searchable by passengers.

---

### 3.2 Finding & Booking a Ride (Passenger)

**Who can book:** Users with a **Passenger** or **Both** role.

#### Step 1 — Choose Direction
Tap **🔍 Find a Ride** from the main menu. Select your commute direction.

#### Step 2 — Select Date
A calendar picker appears. Choose your travel date.

#### Step 3 — Select Time Window
Choose the departure time range you prefer:

| Option | Time Range |
|--------|-----------|
| 🌙 Early Bird (1-4 AM) | 1:00 AM – 4:00 AM |
| 🌙 Early Morning (4-6 AM) | 4:00 AM – 6:00 AM |
| 🌅 Morning Rush (6-9 AM) | 6:00 AM – 9:00 AM |
| ☀️ Late Morning (9-12 PM) | 9:00 AM – 12:00 PM |
| 🌤️ Noon (12-3 PM) | 12:00 PM – 3:00 PM |
| 🌇 Afternoon (3-6 PM) | 3:00 PM – 6:00 PM |
| 🌆 Evening (6-12 PM) | 6:00 PM – 12:00 AM |
| 🔍 Show All | All available times for the selected date |
| 📅 Custom Date & Time | Enter your own date and time as text |

#### Step 4 — Browse Results

Results show available rides matching your criteria:

```
📍 SM Southmall → BGC High Street
📅 May 15 • 7:30 AM
💺 3 seats available
💰 ₱80.00 per seat
👤 Juan dela Cruz ⭐ 4.8
```

Use **◀️ Prev / Next ▶️** to navigate pages (3 results per page).

**Filtering results:**

Tap **🔧 Adjust Filters** to narrow results by:
- **Max Price** (₱): Only shows rides at or below this contribution amount
- **Min Seats**: Only shows rides with at least this many available seats
- **Sort by**: Earliest departure (default) | Cheapest | Most seats available

Tap **Reset** to clear filters.

#### Step 5 — View Ride Details

Tap **View #N** (e.g., "View #1", "View #2") to see the full ride card for that result:

```
🏠→🏢 SM Southmall → BGC High Street
🕐 Thu, May 15 at 7:30 AM
🪑 3 of 3 seats available
⛽ ₱80.00 gas share/seat
👤 Juan dela Cruz (@juandc) ⭐ 4.8
✅ Driver | 26 rides done | Since Jan 2025
🕓 Posted 5m ago
📝 Cash only. AC on.
```

#### Step 6 — Send Booking Request

Tap **✅ Book This Ride**.

The bot asks: *"Any message for the driver?"* (optional)

- This is your introduction — share your exact pickup spot, if you have a companion, or any question for the driver.
- **Maximum:** 300 characters
- Tap **⏭️ Skip** if you have nothing to add.

Your booking request is sent. You will see:

> ⏳ **Booking Request Sent!** Waiting for the driver to accept.

#### What Happens Next

| Event | Time | Your Notification |
|-------|------|------------------|
| Driver accepts | Immediately | ✅ Your booking is confirmed! |
| Driver declines | Immediately | ❌ Booking declined. Reason: [text] |
| No response | 60 minutes | ⏰ Request expired — driver did not respond |

> **Reminders:** If the driver does not respond, they receive automatic reminders at 15, 30, and 45 minutes. At 60 minutes with no response, the request is automatically declined.

---

### 3.3 Managing Your Ride (Driver)

When you have an active ride, all management options appear **directly on the main menu** (see §2.5). Tap **📋 View Bookings** to see all confirmed and pending passengers on your active ride.

#### Viewing Pending Booking Requests

Each pending request shows:
- Passenger name
- Number of seats requested
- Passenger's message to you (if any)
- **Countdown to auto-decline** (e.g., "42 min remaining")

#### Accepting a Booking

Tap **✅ Accept** on a pending request.

- Booking status changes to **CONFIRMED**
- Passenger receives a confirmation notification with your vehicle info and contact
- The seat count on your ride decreases
- If your last seat is filled, your ride status changes from **ACTIVE** to **FULL**

#### Declining a Booking

Tap **❌ Decline** on a pending request.

The bot shows preset decline reasons — select one:
- 🚗 Fully booked already
- 📍 Route change
- 🔧 Vehicle issue
- ❌ Other reason

The passenger is notified with your selected reason, and the seat is freed back to your ride.

#### Starting Your Ride

When you are physically starting the journey, tap **🚀 Start Ride**.

> **Rule:** You can only start the ride up to 1 hour before the scheduled departure time. Tapping too early shows a countdown to when you can start.

- Ride status changes to **DEPARTED**
- No new bookings can be accepted
- All confirmed passengers receive a departure notification
- The **group announcement is deleted** from the community channel

#### Completing Your Ride

After arrival at the destination, tap **✅ Complete Ride**.

- Ride status changes to **COMPLETED**
- All confirmed passengers' bookings are marked COMPLETED
- Both you and all confirmed passengers receive a **rating prompt**
- The group announcement is deleted (if still present)

The bot then asks: *"Would you like to post another ride?"*

| Button | Action |
|--------|--------|
| 🚗 **Yes, Post New Ride** | Starts the post-ride flow immediately (direction select) |
| ❌ **No, Thanks** | Returns to the main menu |

#### Cancelling Your Ride

You may cancel from the **main menu** at any point before departing.

Tap **❌ Cancel Ride**. If you have active passengers, the bot shows preset cancellation reasons — select one:
- 🔧 Vehicle issue
- 📍 Route change
- 🏠 Personal reason
- ❌ Other reason

- All pending and confirmed passengers are notified of the cancellation with your reason
- Their bookings are marked as CANCELLED BY DRIVER
- The group announcement is deleted

> **Please cancel promptly** if your plans change. Passengers are holding a seat and may have turned down alternatives.

#### Re-Announcing an Active Ride

If your ride is already posted but you want to bump it back to the top of the group channel, tap **📢 Re-announce** from the main menu (visible while you have an ACTIVE or FULL ride).

- The previous group announcement is **deleted** and a fresh one is posted in its place — no duplicate posts.
- **Follower alerts are not re-sent** on re-announces. Your followers already received a DM when you first posted; they will not be notified again.
- You may re-announce a maximum of **2 times** after the initial post (3 group posts total). The button shows how many are remaining: `📢 Re-announce (2 left)`. Once the limit is reached the button disappears.

#### Re-Posting a Recent Ride

Your last 3 completed or cancelled rides are listed under **My Rides** with a **🔄 Repost** button. Tapping it opens an **edit screen** pre-filled with all details from the original ride:

```
🔄 Edit Ride for Repost

↔️ Direction: 🏠 Home → Work
📍 From: SM Southmall
🏁 To: BGC High Street
🪑 Seats: 3
⛽ Gas share: ₱80/seat
📝 Note: Cash only

[📍 Edit Start]  [🏁 Edit End]
[🪑 Edit Seats]  [⛽ Edit Share]
[📝 Edit Note]
[✅ Continue]    [❌ Cancel]
```

Tap any field button to change it. When you are satisfied, tap **✅ Continue** to pick a new departure date and time, then select your vehicle, then review and confirm.

---

### 3.4 Managing Your Bookings (Passenger)

Access from **📜 My Bookings (N)** in the main menu (the button is shown when you have active bookings).

#### Viewing Active Bookings

You will see:
- Ride details (origin → destination, departure time)
- Driver name and rating
- Your booking status (PENDING / CONFIRMED)
- Contribution amount owed

#### Cancelling a Booking

You may cancel a booking that is **PENDING** or **CONFIRMED**, as long as the ride has not yet departed.

Tap **❌ Cancel Booking** and confirm.

- Your seat is freed back to the driver
- The driver is notified
- If the ride was FULL, it reopens to ACTIVE and becomes bookable again

> **Note:** You cannot cancel a booking after the ride has already departed.

---

### 3.5 Ratings & Favorites

#### Rating After a Completed Ride

After a ride is marked **COMPLETED**, both the driver and all confirmed passengers receive a rating prompt automatically.

**Rating scale:** ⭐ 1 to ⭐⭐⭐⭐⭐ (1 = Poor, 5 = Excellent)

**Optional comment:** Up to 1,000 characters. Share your experience.

**Who rates whom:**
- Passengers rate the driver (one rating per ride)
- The driver rates each passenger individually

Ratings are permanent and contribute to each user's public rating displayed on ride cards and profiles.

**Rules:**
- You can only rate once per ride as a passenger
- Drivers can rate each of their passengers once per ride
- Rating is only available for COMPLETED rides you participated in

#### Following a Driver from the Community Group

Every ride announcement posted in the community group includes a **⭐ Follow Me | Request a Ride** button. Tapping it opens the bot in a private chat and does two things in one step:

1. **Follows the driver** — adds them to your Favorite Drivers list so you receive alerts when they post new rides.
2. **Shows the full ride card** — with a Book This Ride button so you can request a seat immediately.

| Scenario | What You See |
|----------|-------------|
| First time following this driver | "⭐ You're now following [Driver Name]!" + ride card + Unfollow button |
| Already following this driver | Ride card only (no duplicate follow action taken) |
| You are the driver | Your driver ride card with View Bookings button |

> **Note:** The follow happens automatically when you tap the button — there is no separate confirmation step.

---

#### Saving a Driver as Favorite

After rating a driver, the bot asks:

> *"Would you like to save [Driver Name] as a Favorite? You'll be notified when they post a new ride."*

Tap **⭐ Save as Favorite** to follow this driver.

- Tap **Skip** to skip this step.
- If the driver is already in your favorites, you will see a confirmation and no duplicate is created.

#### Receiving Favorite Driver Alerts

When a driver you follow posts a new ride, you receive a private notification:

```
🔔 Your favorite driver just posted a ride!

👤 Juan dela Cruz
📍 SM Southmall → BGC High Street
📅 Thu, May 15
🕐 Pickup: 7:30 AM

[👀 View Ride]  [🎫 Book Ride]
[🔕 Unfollow]
```

**You can unfollow directly from this alert** by tapping **🔕 Unfollow** — no need to go into any menu. The notification updates to confirm the unfollow.

> **Note:** You will only receive one alert per ride. If the driver re-announces the same ride, no additional DM is sent to followers.

---

### 3.6 Hubs

**Hubs are named pickup and dropoff landmarks** shared across the community. Using consistent hub names ensures drivers and passengers can find each other reliably.

#### Searching for a Hub

When selecting an origin or destination during ride posting or booking, type at least 3 characters of the landmark name. The bot suggests matching hubs using intelligent fuzzy matching (small typos are handled automatically).

Examples:
- Typing `south` → suggests "SM Southmall", "Southmall Terminal"
- Typing `bgc high` → suggests "BGC High Street"

#### Custom Hub Suggestion

If your landmark is not in the system:

1. Type the location name when prompted.
2. The bot shows: **"Use '[your text]'"**
3. Tap it. Your location is saved as a **PENDING** hub and is immediately usable for your current ride.
4. A community admin will review and either approve (making it available to all users) or reject it.

#### Hub Status

| Status | Meaning |
|--------|---------|
| **ACTIVE** | Admin-approved; visible to all users in searches |
| **PENDING** | User-suggested; usable but not yet in the main list |
| **REJECTED** | Admin declined the suggestion |

If a hub you previously suggested was **REJECTED** and you suggest the same location again, it automatically re-enters the **PENDING** review queue.

---

### 3.7 Profile & Vehicle Setup

Access from **👤 My Profile** in the main menu.

#### What Your Profile Shows

```
👤 Juan dela Cruz (@juandc)
🚗 Driver & 🧳 Passenger
📅 Member since: January 2025

🎨 Silver 🚘 Toyota Vios | 🔢 ABC1234

── Driver Stats ──
⭐ 4.8 (24 ratings)
✅ 87% Completion Rate
🚗 Rides posted: 30
✅ Completed: 26
👥 Passengers served: 78
❌ Cancelled: 4

── Passenger Stats ──
⭐ 4.9 (18 ratings)
✅ 95% Completion Rate
📦 Bookings made: 20
✅ Completed: 19
❌ Cancelled by me: 1
```

#### Viewing Your Followers

Drivers see a **👥 My Followers (N)** button on their profile screen, where N is the current follower count. Tap it to see the list of passengers who have saved you as a Favorite Driver.

The followers screen shows:
- Follower name and Telegram handle (if set)
- The date they started following you

Results are paginated at 8 per page. Use **◀️ Prev** / **Next ▶️** to navigate. The screen is read-only — followers can only be removed by the follower themselves (via the **🔕 Unfollow** button on alert notifications).

> **Note:** The followers button only appears for users with a Driver or Both role.

---

#### Managing Your Vehicles

Go to **My Profile → 🚘 My Vehicle** to manage your registered vehicles.

You can save **up to 3 vehicles**. The screen lists all your current vehicles and lets you:
- **🗑️ Remove** any vehicle individually.
- **➕ Add Vehicle** — register a new vehicle (available while you have fewer than 3).

If you already have 3 vehicles saved and add a new one (e.g., during the ride-posting flow), the **oldest** vehicle is automatically replaced.

**Vehicle fields:**

| Field | Required | Example |
|-------|----------|---------|
| Car Color | ❌ Optional | Silver |
| Car Model | ✅ Yes | Toyota Vios |
| Plate Number | ✅ Yes | ABC1234 |
| Seat Capacity | ✅ Yes (1–7) | 4 |

**Rules:**
- Plate numbers must be unique across all users. If the plate is already registered by another user, the registration will be rejected.
- Vehicles are not edited in place — to change a vehicle's details, remove it and add a new entry.
- The vehicle you select during a ride-posting flow is recorded with that ride. Removing a vehicle later does not affect rides already posted with it.

---

## 4. Business Rules & Validations

This section lists all system-enforced rules. These cannot be bypassed.

### 4.1 Ride Rules

| Rule | Detail |
|------|--------|
| One active ride at a time | A driver cannot post a new ride while they have an ACTIVE, FULL, or DEPARTED ride |
| No cross-role conflict | A driver cannot post a ride while they have a PENDING or CONFIRMED booking as a passenger |
| Future departure only | Departure time must be in the future (Manila time). Past times are rejected |
| Origin ≠ Destination | You cannot select the same hub for both pickup and drop-off |
| Seat range | Minimum 1, maximum 7 seats per ride |
| Contribution amount | Minimum ₱0.00 (free); no maximum |
| Notes length | Maximum 300 characters |
| Vehicle required | Driver must select a vehicle during posting. If no vehicle is saved, the bot prompts to add one before the confirmation step. |

### 4.2 Ride Status Flow

Rides move through a defined lifecycle. Only valid transitions are allowed:

```
DRAFT
  │
  ▼ (Driver publishes)
ACTIVE ◄────────────► FULL (no seats available)
  │                    │
  └────────┬───────────┘
           ▼ (Driver departs)
        DEPARTED
           │
           ▼ (Driver completes, or auto after 2h)
        COMPLETED

[Can be CANCELLED from DRAFT, ACTIVE, or FULL]
```

### 4.3 Booking Rules

| Rule | Detail |
|------|--------|
| Active ride only | Can only book rides with status ACTIVE |
| No self-booking | A driver cannot book their own ride |
| No duplicate booking | You cannot have two active bookings on the same ride |
| Seat availability | Cannot book if no seats are available |
| Booking message | Optional, maximum 300 characters |
| Response deadline | Driver has 60 minutes to respond; 3 reminders sent at 15, 30, 45 min |
| Auto-decline | After 60 minutes with no response, the booking is automatically marked TIMED OUT |

### 4.4 Booking Status Flow

```
PENDING
  ├── Driver accepts ──► CONFIRMED ──► COMPLETED (ride ends)
  │                         │
  │                         └─► CANCELLED_BY_PASSENGER
  │                         └─► CANCELLED_BY_DRIVER
  │
  ├── Driver declines ──► DECLINED
  │
  └── 60 min no response ──► TIMED_OUT
```

### 4.5 Rating Rules

| Rule | Detail |
|------|--------|
| Eligible rides | Rating is only available for COMPLETED rides |
| Timing | Rating prompt is sent after driver taps Complete |
| Passenger rating | One rating per ride per passenger (for the driver) |
| Driver rating | One rating per passenger per ride (can rate each passenger once) |
| Star range | 1 (minimum) to 5 (maximum) |
| Comment | Optional, maximum 1,000 characters |
| Duplicate prevention | Second rating attempt for the same ride/person combination is rejected |
| Per-ride scope | Ratings are per-ride. The same driver and passenger may rate each other again on each separate completed ride. |

### 4.6 Hub Rules

| Rule | Detail |
|------|--------|
| Immediate usability | PENDING hubs can be used on rides immediately after suggestion |
| Duplicate prevention | Re-suggesting an existing hub returns the existing one without creating a duplicate |
| REJECTED re-suggestion | Suggesting a previously rejected hub bumps it back to PENDING for re-review |
| Code auto-generation | If admin approves without a code, one is auto-generated (e.g., "SM South Mall" → `SM_SOUTH_MALL`) |

### 4.7 Security & Rate Limiting

| Rule | Detail |
|------|--------|
| Login validity | Telegram login data must be no older than 24 hours |
| JWT session | Login sessions expire after 24 hours; re-login required |
| API rate limit | Maximum 60 requests per minute per IP address. Exceeding this returns "Too Many Requests" |
| Account blocking | Accounts suspended or deleted by admins cannot log in |

### 4.8 Automatic Scheduler Actions

These happen without any user action:

| Scheduler | Frequency | What It Does |
|-----------|-----------|--------------|
| Ride Expiry | Every 30 min | Marks ACTIVE/FULL rides whose departure time has passed as CANCELLED |
| Ride Auto-Complete | Every 30 min | Marks rides that departed 2+ hours ago as COMPLETED automatically |
| Booking Reminders | Every 60 sec | Sends driver reminders at 15, 30, 45 min for unanswered requests; auto-declines at 60 min |
| Departure Reminder | Every 5 min | Notifies driver and confirmed passengers 30 minutes before departure (once per ride) |

### 4.9 Group Announcement Rules

| Rule | Detail |
|------|--------|
| Auto-post | When a ride is published (ACTIVE), an announcement is posted to the community group |
| Topic routing | HOME → WORK rides go to the morning topic; WORK → HOME to the evening topic |
| Re-announce | Replaces the existing group post (old deleted, new posted); maximum 2 re-announces per ride |
| Auto-delete | Announcement is deleted when ride is DEPARTED, COMPLETED, or CANCELLED |
| 48-hour safety | Announcements for rides older than 48 hours are NOT deleted (Telegram API limitation) |
| Follow button | Each announcement includes "⭐ Follow Driver \| View Ride" — tapping opens the bot privately, follows the driver if not already following, and shows the full ride card |

### 4.10 Field Validation Reference

| Field | Min | Max | Required | Notes |
|-------|-----|-----|----------|-------|
| Ride — Total Seats | 1 | 7 | ✅ | |
| Ride — Contribution Amount | ₱0.00 | — | ✅ | |
| Ride — Notes | — | 300 chars | ❌ | |
| Booking — Passenger Message | — | 300 chars | ❌ | |
| Rating — Stars | 1 | 5 | ✅ | |
| Rating — Comment | — | 1,000 chars | ❌ | |
| Vehicle — Car Model | — | 100 chars | ✅ (driver) | |
| Vehicle — Plate Number | — | 20 chars | ✅ (driver) | Unique across all users |
| Vehicle — Car Color | — | 50 chars | ❌ | |
| Vehicle — Seat Capacity | 1 | 7 | ✅ (driver) | Max 3 vehicles per user |
| Hub — Name | — | 150 chars | ✅ | |
| Hub — Area | — | 100 chars | ✅ | |

---

## 5. User Roles & Permissions

### 5.1 Role Overview

| Role | Can Post Rides | Can Book Rides | Can Approve Hubs |
|------|:--------------:|:--------------:|:----------------:|
| **Passenger** | ❌ | ✅ | ❌ |
| **Driver** | ✅ | ❌ | ❌ |
| **Both** | ✅ | ✅ | ❌ |
| **Admin** | ✅ | ✅ | ✅ |

Your role is set by a community administrator. Contact your admin to request a role upgrade (e.g., from Passenger to Driver).

### 5.2 Becoming a Driver

To be upgraded to the **Driver** or **Both** role:
1. Register your vehicle in My Profile (car model + plate number required)
2. Ask your community admin to update your role

Once upgraded, the **🚗 Post a Ride** option appears in your main menu.

### 5.3 Admin Capabilities

Admins have access to additional options under **My Profile**:

- **🏘️ Pending Hubs** — View all user-submitted hubs awaiting review
  - **Approve** a hub (with or without a custom code)
  - **Reject** a hub
- **📊 Admin Stats** — Community-level statistics
- **Role Management** — Change a user's role (via REST API or admin panel)

Admins are identified by their **Telegram ID**, configured by the system operator.

### 5.4 What Roles Cannot Do

| Action | Restriction |
|--------|------------|
| Post a ride as Passenger | Not permitted; "Post a Ride" is hidden from menu |
| Book a ride as Driver | Not permitted; use "Both" role for dual capability |
| Approve/reject hubs | Admin only |
| Update another user's role | Admin only |
| View another user's private messages | Not possible; messages are delivered privately |

---

## 6. Troubleshooting & FAQs

### 6.1 Registration & Login

**Q: The bot is not responding to my `/start` command.**
A: Ensure you are messaging the bot in a **private chat**, not in a group. Find the bot by username, open a private chat, and send `/start`.

---

**Q: I see "Please register first via /start" when I tap a button.**
A: Your Telegram account may not be linked yet. Send `/start` to the bot to authenticate and create your account.

---

**Q: I declined the Terms & Conditions. Can I still use the app?**
A: Your account remains active, but you will be re-prompted every 7 days. Accept the Terms to gain full access to all features.

---

### 6.2 Posting a Ride

**Q: The "Post a Ride" button does not appear in my menu.**
A: Only users with the **Driver** or **Both** role can post rides. Contact your community admin to request a role upgrade.

---

**Q: I get an error saying "You already have an active ride."**
A: You can only have one active ride at a time. Go to **My Rides**, complete or cancel your existing ride first, then post a new one.

---

**Q: My departure time was rejected.**
A: The system requires departure times to be in the **future**. Check that you entered the correct date and time (including AM/PM).

---

**Q: My landmark is not in the hub list.**
A: Type the name and select **"Use '[your text]'"**. Your location will be saved as a pending hub and immediately usable on your ride. An admin will review and approve it for the wider community.

---

**Q: I cannot post a ride — I have a booking as a passenger.**
A: You cannot post a ride as a driver while you have an active booking (PENDING or CONFIRMED) as a passenger. Cancel your existing booking first, or use the **Both** role to manage them separately.

---

### 6.3 Booking a Ride

**Q: The ride I want shows "FULL".**
A: All seats are taken. You can check back later — if a confirmed passenger cancels, the ride reopens. You can also search for alternative rides.

---

**Q: My booking request expired (TIMED OUT).**
A: The driver did not respond within 60 minutes. Three reminders were sent on your behalf. You are free to book a different ride. The seat is automatically freed back to the driver.

---

**Q: I cannot cancel my booking.**
A: Cancellation is only possible while the booking is **PENDING** or **CONFIRMED** and the ride has not yet **DEPARTED**. Once the ride has departed, bookings are locked.

---

**Q: I want to cancel but I see an error.**
A: If the ride is DEPARTED or COMPLETED, cancellation is no longer possible. Contact your community admin if this is an urgent case.

---

### 6.4 Ratings & Favorites

**Q: I cannot rate the driver.**
A: Ratings are only available after the ride status is **COMPLETED**. If the driver has not tapped "Complete Ride," the rating prompt will not appear. Auto-completion occurs 2 hours after departure if the driver does not manually complete.

---

**Q: I accidentally tapped "Skip" on the rating prompt. Can I rate later?**
A: Currently, the rating prompt is only shown once immediately after ride completion. If skipped, the option does not reappear from the menu. Contact your admin if you need a rating reopened.

---

**Q: I rode with the same driver again on a different ride. Can I rate them again?**
A: Yes. Ratings are per-ride, not per person. Each completed ride is an independent rating opportunity. If you and the same driver share 10 rides, you each leave 10 separate ratings — all of which contribute to each other's running average.

---

**Q: I keep receiving alerts from a driver I no longer want to follow.**
A: Tap **🔕 Unfollow** on any alert notification from that driver. The button is embedded directly in the alert message — no menu navigation needed. Your unfollow takes effect immediately.

---

### 6.5 Vehicle & Profile

**Q: Where do I add or manage my vehicles?**
A: Go to **My Profile → 🚘 My Vehicle**. You will see all your saved vehicles and can add new ones (up to 3) or remove existing ones individually.

---

**Q: I cannot register my plate number — it says "already registered by another user."**
A: Plate numbers must be unique. If you recently sold your vehicle and someone else registered it, contact your admin to resolve the conflict. If you believe it is an error, your admin can clear the other user's plate entry.

---

**Q: I have 3 vehicles saved and need to add another.**
A: The maximum is 3. Remove one of your existing vehicles first (via **My Profile → 🚘 My Vehicle → 🗑️ Remove**), then add the new one. Alternatively, if you add a vehicle during the ride-posting flow while already at the 3-vehicle limit, the system automatically replaces your oldest saved vehicle.

---

**Q: Can I edit a vehicle's details?**
A: Not in place — the system does not have an "edit vehicle" function. To correct details (e.g., a typo in the plate number), remove the incorrect vehicle and add a corrected entry.

---

**Q: How do I see who is following me?**
A: Go to **My Profile** and tap **👥 My Followers (N)**. This shows all passengers who have saved you as a Favorite Driver, including their name, handle, and the date they started following. The button only appears if you have a Driver or Both role.

---

**Q: Can I remove a follower?**
A: No — drivers cannot remove followers directly. A follower can remove themselves by tapping **🔕 Unfollow** on any favorite driver alert notification they have received from you.

---

**Q: My profile photo or name did not update.**
A: Your Telegram profile data (name, photo, handle) is refreshed automatically the next time you interact with the bot via `/start`. Update your Telegram profile first, then send `/start` to the bot.

---

### 6.6 Hubs

**Q: My suggested hub has been pending for a long time.**
A: Hub approvals are done manually by admins. Your suggested hub is usable on your rides immediately, even while PENDING. Contact your admin if you need it promoted to ACTIVE urgently.

---

**Q: My previously suggested hub was rejected. Can I suggest it again?**
A: Yes. Type the same landmark name when prompted during ride posting. The system detects the rejected hub and automatically re-queues it for admin review (status changes from REJECTED back to PENDING).

---

### 6.7 Notifications

**Q: I stopped receiving notifications from the bot.**
A: You may have blocked the bot on Telegram. Open the bot's profile in Telegram, scroll down, and unblock it. You can also try sending `/start` again.

---

**Q: I received a departure reminder but the ride was cancelled.**
A: If a ride is cancelled very close to the 30-minute reminder window, you may receive the reminder and the cancellation notification in quick succession. The cancellation notification is the authoritative one.

---

**Q: My booking was auto-declined (TIMED OUT). The driver says they never received a reminder.**
A: The system sends three reminders to the driver (at 15, 30, and 45 minutes). They may have missed the notifications. If this happens frequently with a driver, they should check that they have not blocked the bot or muted the chat.

---

*End of Car-E-Pool User Manual — Version 1.0*