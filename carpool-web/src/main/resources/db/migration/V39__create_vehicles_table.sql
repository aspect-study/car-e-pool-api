CREATE TABLE vehicles (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    plate_number VARCHAR(20)  NOT NULL,
    model        VARCHAR(100) NOT NULL,
    color        VARCHAR(50),
    seat_capacity TINYINT     NOT NULL DEFAULT 4,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at   TIMESTAMP    NULL,
    PRIMARY KEY (id),
    INDEX idx_vehicle_user (user_id),
    CONSTRAINT fk_vehicle_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
