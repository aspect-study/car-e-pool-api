-- ============================================================
-- V1__schema.sql
-- Initial schema for Carpool API
-- ============================================================

CREATE TABLE hubs (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    code         VARCHAR(50)  NULL UNIQUE,
    name         VARCHAR(150) NOT NULL,
    area         VARCHAR(100) NOT NULL,
    suggested_by BIGINT       NULL,
    status       ENUM('ACTIVE','PENDING','REJECTED') NOT NULL DEFAULT 'ACTIVE',
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_hub_status (status)
);

CREATE TABLE users (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    telegram_id      BIGINT       NOT NULL UNIQUE,
    telegram_handle  VARCHAR(100) NULL,
    full_name        VARCHAR(150) NOT NULL,
    photo_url        VARCHAR(500) NULL,
    role             ENUM('PASSENGER','DRIVER','BOTH') NOT NULL DEFAULT 'PASSENGER',
    status           ENUM('ACTIVE','SUSPENDED','BANNED') NOT NULL DEFAULT 'ACTIVE',
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
);

-- Add FK after users table exists
ALTER TABLE hubs
    ADD CONSTRAINT fk_hub_suggester
    FOREIGN KEY (suggested_by) REFERENCES users(id);

CREATE TABLE rides (
    id                   BIGINT          NOT NULL AUTO_INCREMENT,
    driver_id            BIGINT          NOT NULL,
    origin_hub_id        BIGINT          NOT NULL,
    destination_hub_id   BIGINT          NOT NULL,
    direction            ENUM('HOME_TO_WORK','WORK_TO_HOME','OTHER') NOT NULL DEFAULT 'OTHER',
    departure_time       DATETIME        NOT NULL,
    total_seats          TINYINT         NOT NULL,
    available_seats      TINYINT         NOT NULL,
    contribution_amount  DECIMAL(8,2)    NOT NULL DEFAULT 0.00,
    notes                VARCHAR(1000)   NULL,
    status               ENUM('DRAFT','ACTIVE','FULL','COMPLETED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
    created_at           DATETIME(6)     NOT NULL,
    updated_at           DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ride_driver      FOREIGN KEY (driver_id)           REFERENCES users(id),
    CONSTRAINT fk_ride_origin      FOREIGN KEY (origin_hub_id)       REFERENCES hubs(id),
    CONSTRAINT fk_ride_destination FOREIGN KEY (destination_hub_id)  REFERENCES hubs(id),
    INDEX idx_ride_search    (origin_hub_id, destination_hub_id, departure_time, status),
    INDEX idx_ride_driver    (driver_id, status),
    INDEX idx_ride_direction (direction, departure_time, status)
);

CREATE TABLE ride_waypoints (
    id              BIGINT   NOT NULL AUTO_INCREMENT,
    ride_id         BIGINT   NOT NULL,
    hub_id          BIGINT   NOT NULL,
    sequence_order  TINYINT  NOT NULL,
    is_pickup       BOOLEAN  NOT NULL DEFAULT TRUE,
    is_dropoff      BOOLEAN  NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT fk_wp_ride FOREIGN KEY (ride_id) REFERENCES rides(id),
    CONSTRAINT fk_wp_hub  FOREIGN KEY (hub_id)  REFERENCES hubs(id),
    UNIQUE INDEX uq_waypoint (ride_id, hub_id),
    INDEX idx_wp_hub (hub_id)
);

CREATE TABLE bookings (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    ride_id              BIGINT       NOT NULL,
    passenger_id         BIGINT       NOT NULL,
    seats_reserved       TINYINT      NOT NULL DEFAULT 1,
    pickup_waypoint_id   BIGINT       NULL,
    dropoff_waypoint_id  BIGINT       NULL,
    status               ENUM('PENDING','CONFIRMED','CANCELLED_BY_PASSENGER',
                               'CANCELLED_BY_DRIVER','COMPLETED') NOT NULL DEFAULT 'PENDING',
    contribution_due     DECIMAL(8,2) NOT NULL DEFAULT 0.00,
    contribution_paid    DECIMAL(8,2) NOT NULL DEFAULT 0.00,
    payment_method       ENUM('CASH','GCASH','MAYA') NOT NULL DEFAULT 'CASH',
    payment_status       ENUM('UNPAID','PARTIALLY_PAID','PAID') NOT NULL DEFAULT 'UNPAID',
    payment_reference    VARCHAR(100) NULL,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_booking_ride      FOREIGN KEY (ride_id)             REFERENCES rides(id),
    CONSTRAINT fk_booking_passenger FOREIGN KEY (passenger_id)        REFERENCES users(id),
    CONSTRAINT fk_booking_pickup    FOREIGN KEY (pickup_waypoint_id)  REFERENCES ride_waypoints(id),
    CONSTRAINT fk_booking_dropoff   FOREIGN KEY (dropoff_waypoint_id) REFERENCES ride_waypoints(id),
    UNIQUE INDEX uq_booking            (ride_id, passenger_id),
    INDEX       idx_booking_passenger  (passenger_id, status)
);

CREATE TABLE notifications (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    type       VARCHAR(50) NOT NULL,
    channel    ENUM('TELEGRAM','SMS','EMAIL') NOT NULL DEFAULT 'TELEGRAM',
    payload    JSON        NULL,
    status     ENUM('PENDING','SENT','FAILED') NOT NULL DEFAULT 'PENDING',
    sent_at    DATETIME    NULL,
    created_at DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_notif_user (user_id, status)
);
