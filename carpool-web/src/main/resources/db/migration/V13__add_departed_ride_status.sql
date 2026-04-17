-- Already applied manually — this ensures Flyway history is consistent
-- Safe to run again since MODIFY COLUMN is idempotent for same definition
ALTER TABLE rides
    MODIFY COLUMN status ENUM('DRAFT','ACTIVE','FULL','DEPARTED','COMPLETED','CANCELLED') NOT NULL;