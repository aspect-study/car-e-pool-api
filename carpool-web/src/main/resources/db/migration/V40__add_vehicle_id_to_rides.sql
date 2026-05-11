ALTER TABLE rides
    ADD COLUMN vehicle_id BIGINT NULL AFTER group_message_id,
    ADD CONSTRAINT fk_ride_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(id) ON DELETE SET NULL;