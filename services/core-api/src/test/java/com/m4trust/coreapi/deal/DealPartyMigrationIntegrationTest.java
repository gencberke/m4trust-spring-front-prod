package com.m4trust.coreapi.deal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
class DealPartyMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.5-alpine");

    @Test
    void v10MigratesExistingV9DealsWithNullableUnassignedParties() {
        migrateToVersionNine();
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID legalEntityId = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        insertV9DealFixture(jdbcTemplate, tenantId, userId, legalEntityId,
                dealId);

        migrateToLatest();

        assertEquals("YES", jdbcTemplate.queryForObject("""
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'deal'
                  AND column_name = 'buyer_legal_entity_id'
                """, String.class));
        assertEquals("YES", jdbcTemplate.queryForObject("""
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'deal'
                  AND column_name = 'seller_legal_entity_id'
                """, String.class));
        assertNull(jdbcTemplate.queryForObject("""
                SELECT buyer_legal_entity_id
                FROM deal
                WHERE id = ?
                """, UUID.class, dealId));
        assertNull(jdbcTemplate.queryForObject("""
                SELECT seller_legal_entity_id
                FROM deal
                WHERE id = ?
                """, UUID.class, dealId));
    }

    private void migrateToVersionNine() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                        POSTGRES.getPassword())
                .target(MigrationVersion.fromVersion("9"))
                .load()
                .migrate();
    }

    private void migrateToLatest() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                        POSTGRES.getPassword())
                .load()
                .migrate();
    }

    private JdbcTemplate jdbcTemplate() {
        return new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword()));
    }

    private void insertV9DealFixture(JdbcTemplate jdbcTemplate, UUID tenantId,
            UUID userId, UUID legalEntityId, UUID dealId) {
        jdbcTemplate.update("""
                INSERT INTO identity_user (
                    id, email, password_hash, display_name, enabled
                ) VALUES (?, ?, ?, ?, true)
                """, userId, "deal-party-migration@example.com", "test-hash",
                "Deal Party Migration");
        jdbcTemplate.update("INSERT INTO tenant (id) VALUES (?)", tenantId);
        jdbcTemplate.update("""
                INSERT INTO tenant_user (user_id, tenant_id)
                VALUES (?, ?)
                """, userId, tenantId);
        jdbcTemplate.update("""
                INSERT INTO legal_entity (
                    id, tenant_id, legal_name, registration_number
                ) VALUES (?, ?, ?, ?)
                """, legalEntityId, tenantId, "Migration Legal Entity",
                "MIGRATION-1");
        jdbcTemplate.update("""
                INSERT INTO deal (
                    id, tenant_id, reference, title, deal_status,
                    initiator_legal_entity_id, created_by
                ) VALUES (?, ?, ?, ?, 'DRAFT', ?, ?)
                """, dealId, tenantId, "DL-9000000003", "Existing Deal",
                legalEntityId, userId);
        jdbcTemplate.update("""
                INSERT INTO deal_participant (
                    deal_id, tenant_id, legal_entity_id,
                    legal_entity_tenant_id
                ) VALUES (?, ?, ?, ?)
                """, dealId, tenantId, legalEntityId, tenantId);
    }
}
