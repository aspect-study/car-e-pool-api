-- Add pending approval columns to bookings table
ALTER TABLE bookings
    ADD COLUMN passenger_message VARCHAR(800)  NULL COMMENT 'Optional message from passenger to driver',
    ADD COLUMN decline_reason    VARCHAR(255)  NULL COMMENT 'Optional reason from driver when declining',
    ADD COLUMN reminder_count    TINYINT       NOT NULL DEFAULT 0 COMMENT 'Number of reminders sent to driver (max 3)',
    ADD COLUMN expires_at        TIMESTAMP     NULL COMMENT 'Auto-decline deadline — set when booking is created as PENDING';

-- Index for scheduler — efficiently finds PENDING bookings approaching expiry
CREATE INDEX idx_bookings_pending_expires
    ON bookings(status, expires_at);