package com.carpool.web.integration;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Base class for integration tests.
 * Tagged as "integration" — excluded from CI, run locally only.
 *
 * Requires: docker-compose up mysql -d (MySQL must be running)
 *
 * To run locally:
 *   mvn test -pl carpool-web
 *
 * To skip integration tests:
 *   mvn test -pl carpool-web -Dgroups="!integration"
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3308/car_e_pool_db" +
                "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Manila",
        "spring.datasource.username=carpool",
        "spring.datasource.password=carpool",
        "carpool.telegram.bot-token="
})
public abstract class BaseIntegrationTest {
}