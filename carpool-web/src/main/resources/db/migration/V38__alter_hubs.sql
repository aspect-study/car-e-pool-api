ALTER TABLE `car_e_pool_db`.`hubs`
    MODIFY COLUMN `code` varchar(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL AFTER `id`;