CREATE TABLE ride_ratings (
                              id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                              ride_id     BIGINT       NOT NULL,
                              rater_id    BIGINT       NOT NULL,
                              ratee_id    BIGINT       NOT NULL,
                              stars       TINYINT      NOT NULL,
                              comment     VARCHAR(1000) NULL,
                              rater_role  VARCHAR(20)  NOT NULL,
                              created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

                              CONSTRAINT fk_rating_ride   FOREIGN KEY (ride_id)   REFERENCES rides(id),
                              CONSTRAINT fk_rating_rater  FOREIGN KEY (rater_id)  REFERENCES users(id),
                              CONSTRAINT fk_rating_ratee  FOREIGN KEY (ratee_id)  REFERENCES users(id),

    -- One rating per person per ride
                              CONSTRAINT uq_rating_ride_rater UNIQUE (ride_id, rater_id),

                              INDEX idx_rating_ratee  (ratee_id),
                              INDEX idx_rating_ride   (ride_id),
                              INDEX idx_rating_rater  (rater_id)
);