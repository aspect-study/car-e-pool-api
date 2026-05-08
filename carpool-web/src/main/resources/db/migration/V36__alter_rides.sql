ALTER TABLE `car_e_pool_db`.`driver_notes`
    MODIFY COLUMN `content` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL AFTER `user_id`;