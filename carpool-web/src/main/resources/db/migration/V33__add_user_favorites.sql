CREATE TABLE user_favorites (
                                id          BIGINT      AUTO_INCREMENT PRIMARY KEY,
                                follower_id BIGINT      NOT NULL,
                                favorite_id BIGINT      NOT NULL,
                                created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

                                CONSTRAINT fk_favorite_follower FOREIGN KEY (follower_id) REFERENCES users(id),
                                CONSTRAINT fk_favorite_favorite FOREIGN KEY (favorite_id) REFERENCES users(id),

    -- No duplicate favorites
                                CONSTRAINT uq_favorite_follower_favorite UNIQUE (follower_id, favorite_id),

                                INDEX idx_favorite_follower (follower_id),
                                INDEX idx_favorite_favorite (favorite_id)
);