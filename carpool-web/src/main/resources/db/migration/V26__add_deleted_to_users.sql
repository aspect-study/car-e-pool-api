ALTER TABLE users
    ADD COLUMN deleted     TINYINT(1)  NOT NULL DEFAULT 0
        COMMENT 'Soft delete flag — account anonymized but retained for history integrity',
    ADD COLUMN deleted_at  DATETIME    NULL
        COMMENT 'Timestamp when account was deleted';