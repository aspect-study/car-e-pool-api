package com.carpool.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Carpool API entry point.
 *
 * @EnableJpaAuditing  — activates @CreatedDate / @LastModifiedDate in BaseEntity
 * @EnableCaching      — activates @Cacheable / @CacheEvict (Caffeine)
 * @EnableAsync        — activates @Async for notification event listeners
 *
 * ComponentScan covers all com.carpool sub-packages across modules
 * because they share the same root package.
 */
@SpringBootApplication(scanBasePackages = "com.carpool")
@EntityScan(basePackages = "com.carpool.domain.entity")
@EnableJpaRepositories(basePackages = "com.carpool.repository")
@EnableJpaAuditing
@EnableScheduling
@EnableCaching
@EnableAsync
public class CarpoolApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CarpoolApiApplication.class, args);
    }
}
