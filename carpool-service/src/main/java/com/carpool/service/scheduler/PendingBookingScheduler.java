package com.carpool.service.scheduler;

import com.carpool.domain.entity.Booking;
import com.carpool.domain.entity.Ride;
import com.carpool.domain.enums.BookingStatus;
import com.carpool.domain.enums.RideStatus;
import com.carpool.repository.BookingRepository;
import com.carpool.repository.RideRepository;
import com.carpool.service.event.RideEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Scheduler for pending booking approval flow.
 *
 * Runs every minute and handles two tasks:
 *   1. Send reminders to drivers with unanswered booking requests
 *   2. Auto-decline bookings that have expired after 3 reminders
 *
 * Reminder schedule (from booking creation):
 *   Reminder 1 → at 15 minutes
 *   Reminder 2 → at 30 minutes
 *   Reminder 3 → at 45 minutes
 *   Auto-decline → at 60 minutes
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingBookingScheduler {

    private final BookingRepository      bookingRepository;
    private final RideRepository         rideRepository;
    private final ApplicationEventPublisher eventPublisher;

    // Reminder intervals in minutes from booking creation
    private static final int[] REMINDER_INTERVALS = {15, 30, 45};  // minutes
    private static final int   EXPIRY_MINUTES      = 60;

    /**
     * Runs every minute — checks for pending bookings needing reminders or expiry.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void processPendingBookings() {
        Instant now = Instant.now();

        sendReminders(now);
        expireTimedOutBookings(now);
    }

    // ── Reminder logic ────────────────────────────────────────────────────────

    private void sendReminders(Instant now) {
        List<Booking> pendingBookings = bookingRepository.findPendingNeedingReminder(now);
        List<Booking> toSave = new ArrayList<>();

        for (Booking booking : pendingBookings) {
            int reminderCount = booking.getReminderCount();

            if (!isTimeForReminder(booking, reminderCount, now)) {
                continue;
            }

            int nextReminder = reminderCount + 1;
            booking.setReminderCount(nextReminder);
            toSave.add(booking);

            log.info("Sending reminder {}/3 for bookingId={} driverId={}",
                    nextReminder, booking.getId(),
                    booking.getRide().getDriver().getId());

            eventPublisher.publishEvent(
                    new RideEvents.BookingReminderEvent(booking, nextReminder));
        }

        if (!toSave.isEmpty()) {
            bookingRepository.saveAll(toSave); // ← one batch save
        }
    }

    /**
     * Checks if it's time to send the next reminder based on reminder count
     * and elapsed time since booking creation.
     */
    private boolean isTimeForReminder(Booking booking, int reminderCount, Instant now) {
        if (reminderCount >= 3) return false;

        // Get the interval for the next reminder
        int intervalMinutes = REMINDER_INTERVALS[reminderCount];

        // createdAt is from BaseEntity
        Instant reminderDue = booking.getCreatedAt().plus(intervalMinutes, ChronoUnit.MINUTES);
        return now.isAfter(reminderDue);
    }

    // ── Expiry logic ──────────────────────────────────────────────────────────

    private void expireTimedOutBookings(Instant now) {
        List<Booking> expiredBookings = bookingRepository.findExpiredPendingBookings(now);

        if (expiredBookings.isEmpty()) return;

        log.info("Auto-declining {} expired pending bookings", expiredBookings.size());

        for (Booking booking : expiredBookings) {
            try {
                autoDeclineBooking(booking);
            } catch (Exception e) {
                log.error("Failed to auto-decline bookingId={}: {}",
                        booking.getId(), e.getMessage());
            }
        }
    }

    private void autoDeclineBooking(Booking booking) {
        booking.setStatus(BookingStatus.TIMED_OUT);

        // Restore seats using pessimistic lock
        Ride ride = rideRepository.findByIdWithLock(booking.getRide().getId())
                .orElse(null);

        if (ride != null) {
            int restoredSeats = ride.getAvailableSeats() + booking.getSeatsReserved();
            ride.setAvailableSeats(restoredSeats);

            if (ride.getStatus() == RideStatus.FULL) {
                ride.setStatus(RideStatus.ACTIVE);
                log.info("Ride {} re-opened to ACTIVE after booking timed out", ride.getId());
            }

            rideRepository.save(ride);
        }

        bookingRepository.save(booking);

        log.info("Booking auto-declined (timed out): bookingId={} passengerId={}",
                booking.getId(), booking.getPassenger().getId());

        eventPublisher.publishEvent(new RideEvents.BookingTimedOutEvent(booking));
    }
}