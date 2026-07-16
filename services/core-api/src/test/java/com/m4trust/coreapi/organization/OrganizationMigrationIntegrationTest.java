package com.m4trust.coreapi.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class OrganizationMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.5-alpine");

    @Test
    void v4BackfillsOneDistinctTenantForAPreExistingIdentity() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target(MigrationVersion.fromVersion("3"))
                .load()
                .migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO identity_user (
                    id,
                    email,
                    password_hash,
                    display_name,
                    enabled
                )
                VALUES (?, ?, ?, ?, ?)
                """, userId, "existing@example.com", "test-hash", "Existing User", true);

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();

        UUID tenantId = jdbcTemplate.queryForObject("""
                SELECT tenant_id
                FROM tenant_user
                WHERE user_id = ?
                """, UUID.class, userId);
        assertNotNull(tenantId);
        assertNotEquals(userId, tenantId);
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM tenant_user WHERE user_id = ?",
                Integer.class, userId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM tenant WHERE id = ?",
                Integer.class, tenantId));
    }
}
