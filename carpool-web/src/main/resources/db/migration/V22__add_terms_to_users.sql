-- V22: Add terms acceptance tracking to users table.
-- terms_version_accepted NULL  = not yet accepted
-- terms_version_accepted '1.0' = accepted current version
-- Future terms updates: bump version string, re-prompt users on mismatch.

ALTER TABLE users
    ADD COLUMN terms_version_accepted VARCHAR(10)  NULL DEFAULT NULL,
    ADD COLUMN terms_accepted_at      DATETIME(6)  NULL DEFAULT NULL,
    ADD COLUMN terms_declined_at      DATETIME(6)  NULL DEFAULT NULL;