-- V19: Add vehicle info to users table
-- Supports single vehicle per driver (Path 1)
-- Unique index on plate_number prepares for future multi-vehicle upgrade (Path 2)
-- All columns nullable — existing users unaffected

ALTER TABLE users
    ADD COLUMN car_model    VARCHAR(100) NULL COMMENT 'Vehicle model e.g. Toyota Vios',
    ADD COLUMN car_color    VARCHAR(50)  NULL COMMENT 'Vehicle color e.g. Silver',
    ADD COLUMN plate_number VARCHAR(20)  NULL COMMENT 'PH plate number e.g. ABC 1234';

-- Unique index on plate_number (sparse — NULLs not indexed in MySQL)
-- Prevents duplicate plate registrations
-- Clean migration path to vehicles table in future
CREATE UNIQUE INDEX idx_users_plate_number
    ON users (plate_number);