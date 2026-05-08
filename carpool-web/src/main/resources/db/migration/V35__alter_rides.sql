ALTER TABLE `car_e_pool_db`.`rides`
    MODIFY COLUMN `notes` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL AFTER `contribution_amount`;