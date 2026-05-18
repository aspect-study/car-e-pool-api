package com.carpool.service.scheduler;

import com.carpool.domain.entity.Booking;
import com.carpool.repository.BookingRepository;
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
 * Runs every minute and sends reminders to drivers with unanswered booking requests.
 * Bookings never auto-expire — drivers can accept or decline at any time.
 *
 * Reminder schedule (from booking creation):
 *   Reminder 1 → at 15 minutes
 *   Reminder 2 → at 30 minutes
 *   Reminder 3 → at 45 minutes
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingBookingScheduler {

    private final BookingRepository bookingRepository;
    private final ApplicationEventPublisher eventPublisher;

    // Reminder intervals in minutes from booking creation
    private static final int[] REMINDER_INTERVALS = {15, 30, 45};  // minutes

    /**
     * Runs every minute — sends reminders to drivers with pending booking requests.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void processPendingBookings() {
        sendReminders(Instant.now());
    }

    // ── Reminder logic ────────────────────────────────────────────────────────

    private void sendReminders(Instant now) {
        List<Booking> pendingBookings = bookingRepository.findPendingNeedingReminder();
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

}