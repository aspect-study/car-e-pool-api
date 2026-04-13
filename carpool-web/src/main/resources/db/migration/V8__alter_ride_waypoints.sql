-- This changes the column from TINYINT to SMALLINT, which Hibernate expects because I use Short type.
ALTER TABLE ride_waypoints
    MODIFY sequence_order INT NOT NULL;
