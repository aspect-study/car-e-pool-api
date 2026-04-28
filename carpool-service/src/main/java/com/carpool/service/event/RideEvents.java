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
    public record RideCancelledEvent(Ride ride, String reason) {}

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

    /**
     * Published when a passenger submits a booking request.
     * Triggers: notify driver (new pending request — accept or decline).
     */
    public record BookingRequestedEvent(Booking booking) {}

    /**
     * Published when a driver declines a booking request.
     * Triggers: notify passenger (request declined, seats restored).
     */
    public record BookingDeclinedEvent(Booking booking) {}

    /**
     * Published when a pending booking is auto-declined after 3 reminders.
     * Triggers: notify passenger (request timed out).
     */
    public record BookingTimedOutEvent(Booking booking) {}

    /**
     * Published when driver reminder is sent for a pending booking.
     * Triggers: remind driver of pending request.
     */
    public record BookingReminderEvent(Booking booking, int reminderNumber) {}

    /**
     * Published by RideDepartureReminderScheduler 30 minutes before departure.
     * Triggers: notify driver + all confirmed passengers (ride departs soon).
     */
    public record RideDepartureReminderEvent(Ride ride) {}

    /**
     * Published when a driver successfully posts a ride (DRAFT → ACTIVE).
     * Triggers: post ride announcement to Telegram group topic.
     */
    public record RidePostedEvent(Ride ride) {}
}
