-- V23: Add ride_id to notifications for efficient departure reminder duplicate checks.
-- Nullable — most notifications are booking-level, not ride-level.
ALTER TABLE notifications
    ADD COLUMN ride_id BIGINT NULL DEFAULT NULL,
    ADD INDEX idx_notif_ride_type (ride_id, type);