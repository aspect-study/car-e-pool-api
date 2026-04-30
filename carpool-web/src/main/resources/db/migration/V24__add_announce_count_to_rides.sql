ALTER TABLE rides
    ADD COLUMN announce_count TINYINT NOT NULL DEFAULT 1
    COMMENT 'Number of times this ride has been announced to the group. Max 3.';