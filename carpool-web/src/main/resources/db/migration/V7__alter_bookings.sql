-- This changes the column from TINYINT to SMALLINT, which Hibernate expects because I use Short type.
ALTER TABLE bookings
    MODIFY seats_reserved INT NOT NULL DEFAULT 1;
