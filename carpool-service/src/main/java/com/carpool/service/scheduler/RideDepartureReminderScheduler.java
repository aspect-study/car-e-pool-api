package com.carpool.service.scheduler;

import com.carpool.domain.entity.Ride;
import com.carpool.domain.enums.NotificationTypes;
import com.carpool.domain.enums.RideStatus;
import com.carpool.repository.BookingRepository;
import com.carpool.repository.NotificationRepository;
import com.carpool.repository.RideRepository;
import com.carpool.service.event.RideEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Sends departure reminders 30 minutes before ride departure.
 *
 * Runs every 5 minutes — checks for rides departing in the next 25–35 minute window.
 * Duplicate prevention: checks notifications table before sending — if a
 * RIDE_DEPARTURE_REMINDER already exists for this ride, skip it.
 *
 * Notifies: driver + all confirmed passengers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RideDepartureReminderScheduler {

    private final RideRepository         rideRepository;
    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void sendDepartureReminders() {
        LocalDateTime now     = LocalDateTime.now(ZoneId.of("Asia/Manila"));
        LocalDateTime windowStart = now.plusMinutes(25);
        LocalDateTime windowEnd   = now.plusMinutes(35);

        // Find ACTIVE or FULL rides departing in the 25-35 min window
        List<Ride> upcoming = rideRepository.findRidesDepartingBetween(
                windowStart, windowEnd,
                List.of(RideStatus.ACTIVE, RideStatus.FULL));

        for (Ride ride : upcoming) {
            // Duplicate check — skip if reminder already sent for this ride
            boolean alreadySent = notificationRepository
                    .existsByRideIdAndType(ride.getId(),
                            NotificationTypes.RIDE_DEPARTURE_REMINDER);

            if (alreadySent) {
                log.debug("Departure reminder already sent for rideId={} — skipping",
                        ride.getId());
                continue;
            }

            log.info("Sending departure reminder for rideId={} departing at {}",
                    ride.getId(), ride.getDepartureTime());
            eventPublisher.publishEvent(new RideEvents.RideDepartureReminderEvent(ride));
        }
    }
}