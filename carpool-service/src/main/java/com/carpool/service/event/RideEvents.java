package com.carpool.service.event;

import com.carpool.domain.entity.Booking;
import com.carpool.domain.entity.Ride;

/**
 * Spring ApplicationEvents published by service layer.
 * NotificationEventListener handles these asynchronously — decoupling
 * business logic from notification delivery.
 *
 * Using records for immutability — events should never be mutated after publish.
 */
public final class RideEvents {

    private RideEvents() {}

    /**
     * Published when a passenger successfully books a seat.
     * Triggers: notify driver (new booking), notify passenger (confirmation).
     */
    public record BookingConfirmedEvent(Booking booking) {}

    /**
     * Published when a passenger cancels their own booking.
     * Triggers: notify driver (passenger cancelled).
     */
    public record BookingCancelledByPassengerEvent(Booking booking) {}

    /**
     * Published when a driver cancels an entire ride.
     * Triggers: notify ALL confirmed passengers (ride cancelled).
     */
    public record RideCancelledEvent(Ride ride) {}

    /**
     * Published when a driver marks a ride as completed.
     * Triggers: notify all passengers (ride done, please settle payment).
     */
    public record RideCompletedEvent(Ride ride) {}

    /**
     * Published when system auto-expires a ride past its departure time.
     * Triggers: notify booked passengers (ride expired, not driver-cancelled).
     */
    public record RideExpiredEvent(Ride ride) {}
}
