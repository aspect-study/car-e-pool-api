CREATE TABLE driver_notes (
                              id           BIGINT PRIMARY KEY AUTO_INCREMENT,
                              user_id      BIGINT NOT NULL,
                              content      VARCHAR(500) NOT NULL,
                              content_hash CHAR(64) NOT NULL,
                              used_count   INT DEFAULT 1,
                              last_used_at TIMESTAMP NOT NULL,
                              created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Enforce dedup at DB level — same user cannot have same note twice
                              CONSTRAINT uq_user_note UNIQUE (user_id, content_hash),

    -- FK to users
                              CONSTRAINT fk_driver_note_user FOREIGN KEY (user_id)
                                  REFERENCES users(id) ON DELETE CASCADE,

    -- Efficient retrieval of user's notes sorted by most recent
                              INDEX idx_user_last_used (user_id, last_used_at DESC)
);