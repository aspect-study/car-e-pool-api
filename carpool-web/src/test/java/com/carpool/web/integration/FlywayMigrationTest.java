package com.carpool.web.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Flyway migrations ran correctly against real MySQL.
 * Pinaka-simple na integration test — sanity check ng DB setup.
 */
@DisplayName("Flyway Migration")
class FlywayMigrationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Test
    @DisplayName("should apply all migrations successfully")
    void shouldApplyAllMigrations() {
        var info = flyway.info();
        // All migrations should be in SUCCESS state
        assertThat(info.applied()).isNotEmpty();
        assertThat(info.pending()).isEmpty();
    }

    @Test
    @DisplayName("should have created all expected tables")
    void shouldHaveCreatedAllTables() {
        // Verify all 6 tables exist from V1 migration
        assertThat(tableExists("hubs")).isTrue();
        assertThat(tableExists("users")).isTrue();
        assertThat(tableExists("rides")).isTrue();
        assertThat(tableExists("ride_waypoints")).isTrue();
        assertThat(tableExists("bookings")).isTrue();
        assertThat(tableExists("notifications")).isTrue();
    }

    @Test
    @DisplayName("should have seeded 31 active hubs from V2 migration")
    void shouldHaveSeeded31Hubs() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM hubs WHERE status = 'ACTIVE'",
                Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(31);
    }

    @Test
    @DisplayName("should have seeded BGC High Street hub")
    void shouldHaveSeededBgcHub() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM hubs WHERE code = 'BGC_HIGH_STREET'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                        "WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class, tableName);
        return count != null && count > 0;
    }
}