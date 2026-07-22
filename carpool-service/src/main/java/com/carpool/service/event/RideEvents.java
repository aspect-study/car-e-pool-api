package com.carpool.service.event;

import com.carpool.domain.entity.Booking;
import com.carpool.domain.entity.Ride;

import java.util.List;

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
     * affectedBookingIds contains only the bookings that were ACTIVE (CONFIRMED/PENDING)
     * at the moment of cancellation — previously-removed passengers are excluded.
     * Triggers: notify those specific passengers (ride cancelled).
     */
    public record RideCancelledEvent(Ride ride, String reason, List<Long> affectedBookingIds) {}

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
     * Triggers: notify passenger (request declined, seats restored), refresh group announcement.
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

    /**
     * Published when a driver taps Start Ride (ACTIVE/FULL → DEPARTED).
     * Triggers: notify all confirmed passengers (driver is on the way).
     */
    public record RideDepartedEvent(Ride ride) {}

    /**
     * Published when a driver removes an individual confirmed passenger.
     * Triggers: notify the removed passenger, refresh group announcement.
     */
    public record BookingCancelledByDriverEvent(Booking booking) {}

    /**
     * Published when the system auto-cancels a passenger's OTHER pending bookings
     * after one of their requests is confirmed by a driver.
     * Distinct from BookingCancelledByPassengerEvent — the passenger did not choose to cancel.
     * Triggers: notify the other ride's driver with correct context.
     */
    public record BookingAutoSyncedEvent(Booking booking) {}

    /**
     * Published when a driver updates the departure time of an active ride.
     * Triggers: notify all confirmed passengers (time changed — keep or cancel booking).
     */
    public record RideTimeChangedEvent(Ride ride) {}

    /**
     * Published when a driver changes the origin and/or destination of an active ride.
     * Carries the pre-change hub names so passenger DMs can show old → new.
     * Triggers: notify all confirmed passengers (route changed — keep or cancel booking),
     * refresh group announcement.
     */
    public record RideRouteChangedEvent(Ride ride, String oldOriginName, String oldDestinationName) {}
}
