# Car-E-Pool — User Manual
**Version 1.6 | May 2026**

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [Getting Started](#2-getting-started)
3. [Core Functionalities](#3-core-functionalities)
   - 3.1 Posting a Ride (Driver)
   - 3.2 Finding & Booking a Ride (Passenger)
   - 3.3 Managing Your Ride (Driver) — incl. Editing Departure Time
   - 3.4 Managing Your Bookings (Passenger)
   - 3.5 Ratings & Favorites
   - 3.6 Hubs
   - 3.7 Profile & Vehicle Setup
4. [Business Rules & Validations](#4-business-rules--validations)
5. [User Roles & Permissions](#5-user-roles--permissions)
6. [Troubleshooting & FAQs](#6-troubleshooting--faqs)
   - 6.1 Registration & Login
   - 6.2 Posting a Ride
   - 6.3 Booking a Ride
   - 6.4 Ratings & Favorites
   - 6.5 Vehicle & Profile
   - 6.6 Hubs
   - 6.7 Notifications
   - 6.8 Managing Your Ride (Driver)
   - 6.9 Bookings & Seat Management (Passenger)

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
- 👥 **My Passengers** | 🚀 **Start Ride**
- ⏳ **Pending (N)** | ❌ **Cancel Ride** — Shown when you have pending booking requests
- 📢 **Re-announce (N left)** — Shown when re-announces are still available
- ✏️ **Edit Time** — Change the departure time of your active or full ride
- 📜 **My Bookings (N)** — Shown when you also have active bookings as a passenger
- 🔍 **Find a Ride** | 👤 **My Profile**

**When your ride has departed:**
- 👥 **My Passengers** | ✅ **Complete Ride**
- 📜 **My Bookings (N)** — Shown when you also have active bookings as a passenger
- 👤 **My Profile**

> **Button naming:** **👥 My Passengers** shows the passengers booked onto *your* ride (driver view). **📜 My Bookings** shows *your own* bookings as a passenger on someone else's ride. Both can appear at the same time if you have an active ride and a passenger booking in a different direction.

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

After picking a date, an **inline time picker** appears with up to 20 time slots in 15-minute increments. Tap your departure time. Use **◀️ Earlier** or **Later ▶️** to shift the visible window by 5 hours.

- HOME → WORK direction defaults to a 5:00 AM starting window
- WORK → HOME direction defaults to a 3:00 PM starting window
- If **today** is selected, the picker automatically opens on the page containing the next available time slot — past times are hidden and you will never land on an empty page
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
   > **Privacy:** Your vehicle's plate number is **not** shown in the group announcement. It is shared privately only with passengers whose booking you accept.
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

| Event | Your Notification |
|-------|------------------|
| Driver accepts | ✅ Your booking is confirmed! The confirmation DM includes a **📋 View My Booking** button for quick access to your booking details. |
| Driver declines | ❌ Booking declined. Reason: [text] |
| No response after reminders | ⏳ Booking stays pending — you may cancel it manually if needed |

> **Reminders:** If the driver does not respond, they receive automatic reminders at 15, 30, and 45 minutes. After 3 reminders there is no further automatic action — the booking stays pending until the driver responds or you cancel it manually.

---

### 3.3 Managing Your Ride (Driver)

When you have an active ride, all management options appear **directly on the main menu** (see §2.5). Tap **👥 My Passengers** to see all confirmed and pending passengers on your active ride.

#### Viewing Pending Booking Requests

Each pending request shows:
- Passenger name and **member badge** (role, completed bookings, member-since date)
- Number of seats requested
- Passenger's message to you (if any)
- **Countdown to expire** (e.g., "42 min remaining")

#### Accepting a Booking

Tap **✅ Accept** on a pending request.

- Booking status changes to **CONFIRMED**
- Passenger receives a confirmation notification including your **vehicle details (color, model, and plate number)** and contact
- The seat count on your ride decreases
- If your last seat is filled, your ride status changes from **ACTIVE** to **FULL**
- Any other **PENDING** booking requests on the same ride are automatically cancelled and those passengers are notified

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
- You may re-announce a maximum of **9 times** after the initial post (10 group posts total). The button shows how many are remaining: `📢 Re-announce (9 left)`. Once the limit is reached the button disappears.

#### Re-Announce with Seat Count Edit

When you tap **📢 Re-announce**, the bot asks: *"How many available seats do you want to show?"* Type the number and send it.

- **Enter a number ≥ 1:** The seat count on your ride is updated and a fresh group announcement is posted. The bot confirms "📢 Ride Re-announced!" with the number of re-announces remaining.
- **Enter 0:** Your ride is marked as **FULL** — the existing group announcement is removed and no new one is posted. The bot confirms "🚫 Ride Marked as Full." The ride remains open in the system; if a confirmed passenger later cancels, the group post will be automatically refreshed.

> **Tip:** Use the 0-seats option to close bookings without cancelling the ride entirely.

#### Removing a Confirmed Passenger

In your **My Passengers** list, each confirmed passenger has a **🗑️ Remove** button. Tapping it removes that passenger from your ride.

- The removed passenger is notified immediately.
- Their seat is freed back to your ride (FULL rides revert to ACTIVE).
- The community group announcement is automatically refreshed to show the newly available seat.

> **Note:** Only confirmed passengers can be removed. Pending requests can be declined normally.

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

> **Note:** If you already have an **ACTIVE** or **FULL** ride in the same direction, tapping **🔄 Repost** will immediately show: *"You already have an active ride post for this direction. Please cancel it first."* Cancel or complete your current ride before reposting.

#### Editing Departure Time

If your ride is **ACTIVE** or **FULL**, you can change its departure time by tapping **✏️ Edit Time** from the main menu (or from the ride details card).

The bot opens the same calendar → time picker flow used when posting a ride. After selecting the date and time, a **confirmation screen** appears showing the route, the current departure time, and the new departure time. Tap **✅ Confirm Update** to apply the change, or **❌ Cancel** to return to the main menu without making any changes.

**Rules:**
- Only available for ACTIVE or FULL rides (not DEPARTED, COMPLETED, or CANCELLED)
- The new time must be **at least 15 minutes in the future** (Manila time)
- The new time must differ from the current scheduled time

**What happens after confirming:**

1. The ride's departure time is updated immediately.
2. All **confirmed passengers** receive a private notification:
   ```
   ⏰ Ride Time Updated

   Your driver updated the departure time for your upcoming ride.

   📍 [Origin] → [Destination]
   🕐 New time: Thu, May 22 at 7:30 AM

   Does this still work for you?
   [✅ Keep Booking]  [❌ Cancel Booking]
   ```
3. The **community group announcement** is deleted and reposted immediately with the updated time — no re-announce slot is consumed.

> **Tip:** Passengers who tap ❌ Cancel Booking from this notification are cancelled automatically — no driver action required. Their seat is freed back to the ride.

---

### 3.4 Managing Your Bookings (Passenger)

Access from **📜 My Bookings (N)** in the main menu (the button is shown when you have active bookings).

#### Viewing Active Bookings

You will see:
- Ride details (origin → destination, departure time)
- Driver name and rating
- Your booking status (PENDING / CONFIRMED / COMPLETED)
- Contribution amount owed
- **Vehicle details (color, model, and plate number)** — shown for CONFIRMED and COMPLETED bookings only

#### Driver Changes Departure Time

If the driver updates the ride's departure time, you receive a notification (see §3.3 Editing Departure Time) with two inline buttons:

| Button | Action |
|--------|--------|
| ✅ **Keep Booking** | Your booking remains confirmed at the new time |
| ❌ **Cancel Booking** | Your booking is cancelled immediately; your seat is freed |

> **Note:** Tapping Cancel Booking from this notification is equivalent to cancelling your booking manually — no further confirmation is needed.

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

Every ride announcement posted in the community group includes two inline buttons:

- **🚘#N - Request 💺** — opens the bot privately and shows the full ride card with a Book This Ride button.
- **✚ Follow** — opens the bot privately and does two things in one step:
  1. **Follows the driver** — adds them to your Favorite Drivers list so you receive alerts when they post new rides.
  2. **Shows the full ride card** — with a Book This Ride button so you can request a seat immediately.

| Scenario (tapping ✚ Follow) | What You See |
|----------|-------------|
| First time following this driver | "⭐ You're now following [Driver Name]!" + ride card + Unfollow button |
| Already following this driver | Ride card only (no duplicate follow action taken) |
| You are the driver | Your driver ride card with My Passengers button |

> **Note:** The follow happens automatically when you tap ✚ Follow — there is no separate confirmation step.

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
| One active ride per direction | A driver cannot have more than one **ACTIVE** or **FULL** ride in the same direction. This is enforced at both ride creation and publication (DRAFT → ACTIVE). A DEPARTED ride does not block posting a new ride in the same direction. |
| Direction-scoped conflict | A driver cannot post a ride in a given direction while they have a PENDING or CONFIRMED booking as a passenger in the **same direction**. The two directions (HOME→WORK and WORK→HOME) are independent: driving home-to-work and holding a work-to-home passenger booking at the same time is allowed. |
| Future departure only | Departure time must be in the future (Manila time). Past times are rejected |
| Origin ≠ Destination | You cannot select the same hub for both pickup and drop-off |
| Seat range | Minimum 1, maximum 7 seats per ride |
| Contribution amount | Minimum ₱0.00 (free); no maximum |
| Notes length | Maximum 300 characters |
| Vehicle required | Driver must select a vehicle during posting. If no vehicle is saved, the bot prompts to add one before the confirmation step. |
| Departure time edit | Only allowed on ACTIVE or FULL rides; new time must be ≥ 15 minutes from now and must differ from the current scheduled time. Triggers passenger notifications and group post refresh. |

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
| Direction-scoped driver conflict | A passenger who has an ACTIVE, FULL, or DEPARTED driver ride in the **same direction** cannot book a passenger seat in that direction |
| Booking message | Optional, maximum 300 characters |
| Response reminders | 3 automatic reminders sent to the driver at 15, 30, and 45 min after the request |
| No auto-expiry | If the driver does not respond after 3 reminders, the booking remains PENDING indefinitely — cancel it manually if needed |

### 4.4 Booking Status Flow

```
PENDING
  ├── Driver accepts ──► CONFIRMED ──► COMPLETED (ride ends)
  │                         │
  │                         └─► CANCELLED_BY_PASSENGER
  │                         └─► CANCELLED_BY_DRIVER
  │
  └── Driver declines ──► DECLINED

  (No auto-expiry — stays PENDING until driver responds or passenger cancels)
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
| Booking Reminders | Every 60 sec | Sends driver reminders at 15, 30, and 45 min for unanswered requests; no auto-expiry after that |
| Departure Reminder | Every 5 min | Notifies driver and confirmed passengers 30 minutes before departure (once per ride) |
| Announcement Refresh | Every 4 hours | Automatically re-posts group announcements that have been live for 36+ hours so they stay within Telegram's 48-hour deletion window — no driver action required |

### 4.9 Group Announcement Rules

| Rule | Detail |
|------|--------|
| Auto-post | When a ride is published (ACTIVE), an announcement is posted to the community group |
| Topic routing | HOME → WORK rides go to the morning topic; WORK → HOME to the evening topic |
| Auto-refresh on booking | When a driver accepts a booking, the group post is automatically refreshed to show the updated available seat count |
| Auto-delete on FULL | When the last seat is filled (ride becomes FULL), the group post is deleted automatically |
| Re-announce | Replaces the existing group post (old deleted, new posted); maximum 9 re-announces per ride (10 total group posts) |
| Re-announce with seat edit | When re-announcing, driver can update the seat count; entering 0 removes the post and marks ride FULL |
| Auto-refresh on seat freed | When a confirmed passenger is removed by the driver (or auto-cancelled due to FULL), the group post is automatically refreshed to show the updated seat count |
| Auto-delete | Announcement is deleted when ride is DEPARTED, COMPLETED, or CANCELLED |
| Auto-refresh before 48h | Active ride announcements that have been live for 36+ hours are automatically re-posted by the system before Telegram's 48-hour deletion limit is reached — the post reappears fresh in the group with no action required from the driver |
| 48-hour safety | If the auto-refresh scheduler was down and a message has already exceeded 48 hours, it is skipped rather than causing an error (Telegram API limitation) |
| Announcement buttons | Each announcement includes "🚘#N - Request 💺" (view/book the ride) and "✚ Follow" (follow driver + show ride card). Tapping ✚ Follow opens the bot privately, follows the driver if not already following, and shows the full ride card |
| Vehicle plate privacy | Group announcements show vehicle color and model only; the plate number is not visible to the public — it is shared exclusively in the booking confirmation DM sent to the confirmed passenger |

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
A: You can only have one ACTIVE or FULL ride per direction at a time. Go to **My Rides**, complete or cancel your existing ride in that direction first, then post a new one.

---

**Q: I tapped 🔄 Repost on an old ride but got a message saying I already have an active post.**
A: The repost guard checks whether you have an ACTIVE or FULL ride in the same direction as the ride you are trying to repost. Cancel or complete your current ride first, then tap **🔄 Repost** again. This check also applies to old **Repost** buttons visible in your Telegram chat history — tapping one while you already have an active ride in that direction will always be blocked.

---

**Q: My departure time was rejected.**
A: The system requires departure times to be in the **future**. Check that you entered the correct date and time (including AM/PM).

---

**Q: My landmark is not in the hub list.**
A: Type the name and select **"Use '[your text]'"**. Your location will be saved as a pending hub and immediately usable on your ride. An admin will review and approve it for the wider community.

---

**Q: I cannot post a ride — I got a conflict error about a passenger booking.**
A: Conflict checks are **direction-specific**. You cannot post a **home-to-work** ride while you have a PENDING or CONFIRMED booking on another **home-to-work** ride as a passenger (and the same rule applies to work-to-home). However, you *can* drive home-to-work while holding a work-to-home passenger booking — those directions do not conflict. To resolve a conflict, cancel the booking in the same direction first, then post your ride.

---

**Q: The bot stopped responding in the middle of posting a ride.**
A: Your session may have timed out (30 minutes of inactivity resets all flow state). Tap **🏠 Menu** to return to the main menu, then start the post-ride flow again. You will need to re-enter all steps from the beginning — the bot does not save partial progress. Complete the full flow in one sitting to avoid this.

---

**Q: I posted a ride but no announcement appeared in the community group.**
A: The group announcement is sent asynchronously — allow up to 30 seconds after posting. If it still does not appear, the bot may have lost access to the group topic (e.g., was removed from the group, lost admin rights, or the topic was archived). Contact your community admin to verify the bot's group permissions.

---

**Q: Can I edit my ride after posting — departure time, route, or number of seats?**
A: **Departure time** can be changed on any ACTIVE or FULL ride using **✏️ Edit Time** from the main menu. The new time must be at least 15 minutes in the future. All confirmed passengers are notified automatically and can choose to keep or cancel their booking. **Route** cannot be changed after posting — to correct it, cancel the ride and repost using the **🔄 Repost** shortcut (which pre-fills all original details for editing). To adjust only the **available seat count**, use **📢 Re-announce** — it lets you enter a new seat count before reposting to the group.

---

### 6.3 Booking a Ride

**Q: The ride I want shows "FULL".**
A: All seats are taken. You can check back later — if a confirmed passenger cancels, the ride reopens. You can also search for alternative rides.

---

**Q: The driver hasn't responded to my booking request.**
A: Three reminders have been sent to the driver at 15, 30, and 45 minutes. If they still do not respond, the booking will stay pending — there is no automatic expiry. You can cancel the booking manually and look for another ride. If the driver frequently misses requests, they may have blocked the bot or muted the chat.

---

**Q: I cannot cancel my booking.**
A: Cancellation is only possible while the booking is **PENDING** or **CONFIRMED** and the ride has not yet **DEPARTED**. Once the ride has departed, bookings are locked.

---

**Q: I want to cancel but I see an error.**
A: If the ride is DEPARTED or COMPLETED, cancellation is no longer possible. Contact your community admin if this is an urgent case.

---

**Q: I sent a booking request but it disappeared from My Bookings.**
A: Your booking was likely auto-cancelled because the driver accepted another passenger that filled the last seat — your pending request was automatically withdrawn and you will have received an "ℹ️ Booking Request Withdrawn" message. You are free to book a different ride.

---

**Q: Can I submit booking requests to multiple rides at the same time?**
A: Yes. You may have multiple pending requests open simultaneously across different rides. However, once a driver confirms one of them and that fills the last seat, any other pending requests you had on that same ride are automatically cancelled. You can hold only one confirmed booking per ride at a time.

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

**Q: The driver never responded and my booking is still pending.**
A: The system sends three reminders to the driver at 15, 30, and 45 minutes after your request. After that there is no further automatic action — bookings do not expire on their own. If the driver still has not responded, cancel the booking manually and look for another ride. If the driver frequently misses requests, they may have blocked the bot or muted the chat.

---

**Q: I did not receive the 30-minute departure reminder.**
A: Departure reminders are sent once per ride approximately 30 minutes before the scheduled departure. If the scheduler ran slightly late or the ride was modified close to that window, the reminder may have been skipped. This is a best-effort notification — always verify departure time directly from your booking confirmation.

---

### 6.8 Managing Your Ride (Driver)

**Q: I need to change my departure time after posting. Can I do that?**
A: Yes. Tap **✏️ Edit Time** from the main menu (available on ACTIVE and FULL rides). A calendar and time picker appear — select the new date and time. The new time must be at least 15 minutes from now. Once confirmed: (1) your ride is updated, (2) all confirmed passengers receive a notification with Keep/Cancel buttons, and (3) the community group announcement is refreshed automatically. Note that the ✏️ Edit Time button disappears once the ride has departed.

---

**Q: I changed the departure time. Some passengers tapped "Cancel Booking" — do I need to do anything?**
A: No action is needed. When a passenger taps **❌ Cancel Booking** on the time-change notification, their booking is cancelled automatically and their seat is freed back to your ride. You will receive the same "passenger cancelled" notification you would get from a manual cancellation.

---

**Q: I tapped "Start Ride" but the bot showed a countdown instead of departing. Why?**
A: You can only start a ride within **60 minutes** of the scheduled departure time. If you tap too early, the bot shows a message like "You can start the ride in X hours Y minutes." This guard prevents accidental early departures. Tap again once the countdown has elapsed.

---

**Q: My ride is FULL. Can passengers still book?**
A: No. FULL rides do not appear in passenger search results and new booking requests are blocked. Seats become available again if: (a) you remove a confirmed passenger with the **🗑️ Remove** button, (b) a confirmed passenger cancels their own booking, or (c) you use **📢 Re-announce** and enter a higher seat count. In all cases the ride automatically returns to ACTIVE and the group announcement refreshes.

---

**Q: I accepted a booking that filled my last seat, and all remaining pending requests were automatically cancelled. Is this expected?**
A: Yes. When the last seat is taken the ride transitions to FULL, and all remaining PENDING requests on that ride are automatically declined. Each affected passenger receives a notification explaining the ride is now full. This prevents the ride from being oversold.

---

**Q: I removed a confirmed passenger. Will the community group announcement update?**
A: Yes. Removing a passenger triggers an automatic group post refresh — the old announcement is deleted and a new one is posted showing the updated (higher) available seat count. No manual re-announce is needed.

---

**Q: I cancelled my ride. Will passengers I had previously removed also receive the cancellation notification?**
A: No. Only passengers with an **active** booking (PENDING or CONFIRMED) at the moment of cancellation are notified. Passengers you removed earlier with the **🗑️ Remove** button were already notified at that time and receive no second message when the ride is cancelled.

---

**Q: I have used all 10 re-announcement slots. How do I bump my ride again?**
A: Once the limit is reached, the **📢 Re-announce** button disappears and no further bumps are possible for that ride. Your options are: (1) leave the ride as-is and wait for passengers to find it through search, or (2) cancel the current ride and repost — the **🔄 Repost** shortcut on cancelled rides pre-fills all original details so it takes less than a minute.

---

**Q: I want to reduce the seat count on my active ride because a friend is joining informally. How?**
A: Tap **📢 Re-announce** and enter the new (lower) available seat count when prompted. The system updates the seat count and reposts a fresh group announcement. Enter **0** to mark the ride as FULL, remove the group post, and close it to new bookings entirely.

---

**Q: The community group announcement shows an outdated seat count.**
A: The announcement refreshes automatically on every booking acceptance and passenger removal. If it still looks wrong, use **📢 Re-announce** to force a fresh post. If there is no announcement at all (e.g., after a bot restart or a Telegram API glitch), re-announcing will recreate it.

---

### 6.9 Bookings & Seat Management (Passenger)

**Q: I received a "Ride Time Updated" notification with Keep/Cancel buttons. What do I do?**
A: The driver changed the departure time for your confirmed booking. Review the new time shown in the notification and tap the button that matches your decision:
- **✅ Keep Booking** — you stay on the ride at the new time; no further action needed.
- **❌ Cancel Booking** — your booking is cancelled immediately and your seat is released. You can then search for another ride.

There is no deadline for responding — you can tap either button at any time before the ride departs.

---

**Q: I received a "Removed from Ride" notification. What does this mean?**
A: The driver manually removed you from their ride. Your seat was freed (the ride may have returned to ACTIVE), and you are free to book a different ride. If you believe the removal was in error, reach out to the driver directly — their Telegram handle appears in your original booking confirmation message.

---

**Q: I was confirmed on a ride that was later cancelled by the driver. Is there a refund process?**
A: Car-E-Pool does not process payments. Gas contribution is settled directly between you and the driver in cash. If you had already paid and the driver cancelled, resolve this with the driver directly — the system has no mechanism to reverse cash transactions.

---

**Q: My booking is still pending after a long time. Can I rebook on another ride?**
A: Yes. There is no auto-expiry — pending bookings stay open until the driver responds or you cancel manually. If you find a better option, cancel your pending booking (from **📜 My Bookings**) and book the other ride.

---

**Q: The ride's departure time has already passed, but my booking still shows CONFIRMED. Is this a bug?**
A: Not a bug — the expiry scheduler runs every 30 minutes. If the departure time just passed, the ride has not been processed yet. Within the next scheduler cycle (at most 30 minutes) the ride will be marked CANCELLED and you will receive a "Ride Did Not Push Through" notification. No action is needed on your part.

---

**Q: I received a "Ride Did Not Push Through" notification. What does it mean?**
A: The driver never started the ride and the scheduled departure time has passed. The system automatically expired the ride and cancelled all active bookings. You only receive this notification if the departure was within the last 2 hours — if the departure was more than 2 hours ago, no notification is sent (passengers are assumed to have already made alternative arrangements).

---

**Q: Can I change the number of seats in my booking after it is confirmed?**
A: No. Seat count is fixed at the time of booking and cannot be modified afterward. To change it, cancel your current booking and submit a new request — subject to availability. Note that the ride may be FULL by the time you rebook if other passengers have taken the remaining seats.

---

*End of Car-E-Pool User Manual — Version 1.6*
