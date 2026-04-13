-- This changes the column from TINYINT to SMALLINT, which Hibernate expects because I use Short type.
ALTER TABLE rides
    MODIFY available_seats INT NOT NULL;
