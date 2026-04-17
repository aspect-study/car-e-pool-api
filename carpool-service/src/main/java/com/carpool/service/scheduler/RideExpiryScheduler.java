package com.carpool.service.scheduler;

import com.carpool.service.ride.RideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that auto-expires rides whose departure time has passed.
 * Runs every 30 minutes — prevents passengers from booking expired rides
 * and keeps ride statuses accurate.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RideExpiryScheduler {

    private final RideService rideService;

    /**
     * Runs every 30 minutes.
     * fixedDelay = wait 30 min AFTER last execution completes — prevents overlap.
     */
    @Scheduled(fixedDelay = 30 * 60 * 1000)
    public void expireStaleRides() {
        log.debug("Running ride expiry check...");
        rideService.expireStaleRides();
    }
}