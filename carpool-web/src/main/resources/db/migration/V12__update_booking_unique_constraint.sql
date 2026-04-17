-- Step 1: Drop FK constraints that use ride_id and passenger_id
ALTER TABLE bookings DROP FOREIGN KEY fk_booking_ride;
ALTER TABLE bookings DROP FOREIGN KEY fk_booking_passenger;

-- Step 2: Drop the unique index
ALTER TABLE bookings DROP INDEX uq_booking;

-- Step 3: Re-add the FK constraints
ALTER TABLE bookings
    ADD CONSTRAINT fk_booking_ride
        FOREIGN KEY (ride_id) REFERENCES rides(id);

ALTER TABLE bookings
    ADD CONSTRAINT fk_booking_passenger
        FOREIGN KEY (passenger_id) REFERENCES users(id);