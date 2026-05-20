package com.carpool.bot.scheduler;

import com.carpool.bot.service.GroupNotificationService;
import com.carpool.domain.entity.Ride;
import com.carpool.domain.enums.RideStatus;
import com.carpool.repository.RideRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StaleAnnouncementRefreshScheduler {

    private final RideRepository rideRepository;
    private final GroupNotificationService groupNotificationService;

    @Scheduled(fixedDelay = 4 * 60 * 60 * 1000, initialDelay = 10 * 60 * 1000)
    public void refreshStaleAnnouncements() {
        Instant threshold = Instant.now().minus(36, ChronoUnit.HOURS);
        List<Ride> staleRides = rideRepository.findRidesWithStaleGroupAnnouncement(
                List.of(RideStatus.ACTIVE, RideStatus.FULL), threshold);

        if (staleRides.isEmpty()) return;

        log.info("Refreshing {} stale group announcement(s)", staleRides.size());
        for (Ride ride : staleRides) {
            try {
                groupNotificationService.refreshGroupAnnouncementForRide(ride.getId());
            } catch (Exception e) {
                log.error("Failed to refresh stale announcement: rideId={} error={}",
                        ride.getId(), e.getMessage());
            }
        }
    }
}