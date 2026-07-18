package com.m4trust.coreapi.deal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DealParticipantExpandMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.5-alpine");

    @Test
    void v6BackfillsParticipantsAndKeepsV5WritesCompatible() {
        migrateToVersionFive();
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID legalEntityId = UUID.randomUUID();
        UUID existingDealId = UUID.randomUUID();
        insertV5Fixture(jdbcTemplate, tenantId, userId, legalEntityId,
                existingDealId);

        migrateToLatest();

        assertEquals(tenantId, jdbcTemplate.queryForObject("""
                SELECT legal_entity_tenant_id
                FROM deal_participant
                WHERE deal_id = ?
                  AND legal_entity_id = ?
                """, UUID.class, existingDealId, legalEntityId));
        assertEquals("YES", jdbcTemplate.queryForObject("""
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'deal_participant'
                  AND column_name = 'legal_entity_tenant_id'
                """, String.class));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM pg_constraint
                WHERE conrelid = 'deal_participant'::regclass
                  AND conname = 'deal_participant_entity_tenant_fk'
                """, Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM pg_constraint
                WHERE conrelid = 'deal_participant'::regclass
                  AND conname = 'deal_participant_entity_legal_tenant_fk'
                """, Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'deal_participant'
                  AND indexname =
                      'deal_participant_entity_legal_tenant_deal_idx'
                """, Integer.class));

        UUID legacyImageDealId = UUID.randomUUID();
        insertDeal(jdbcTemplate, legacyImageDealId, tenantId, userId,
                legalEntityId, "DL-9000000002");
        jdbcTemplate.update("""
                INSERT INTO deal_participant (
                    deal_id,
                    tenant_id,
                    legal_entity_id
                )
                VALUES (?, ?, ?)
                """, legacyImageDealId, tenantId, legalEntityId);

        assertNull(jdbcTemplate.queryForObject("""
                SELECT legal_entity_tenant_id
                FROM deal_participant
                WHERE deal_id = ?
                  AND legal_entity_id = ?
                """, UUID.class, legacyImageDealId, legalEntityId));
        assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM deal
                WHERE deal.tenant_id = ?
                  AND EXISTS (
                      SELECT 1
                      FROM deal_participant participant
                      WHERE participant.deal_id = deal.id
                        AND participant.tenant_id = deal.tenant_id
                        AND participant.legal_entity_id = ?
                  )
                """, Integer.class, tenantId, legalEntityId));

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("""
                        UPDATE deal_participant
                        SET legal_entity_tenant_id = ?
                        WHERE deal_id = ?
                        """, UUID.randomUUID(), existingDealId));
    }

    private void migrateToVersionFive() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                        POSTGRES.getPassword())
                .target(MigrationVersion.fromVersion("5"))
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

    private void insertV5Fixture(JdbcTemplate jdbcTemplate, UUID tenantId,
            UUID userId, UUID legalEntityId, UUID dealId) {
        jdbcTemplate.update("""
                INSERT INTO identity_user (
                    id,
                    email,
                    password_hash,
                    display_name,
                    enabled
                )
                VALUES (?, ?, ?, ?, true)
                """, userId, "existing-deal@example.com", "test-hash",
                "Existing Deal Owner");
        jdbcTemplate.update("INSERT INTO tenant (id) VALUES (?)", tenantId);
        jdbcTemplate.update("""
                INSERT INTO tenant_user (user_id, tenant_id)
                VALUES (?, ?)
                """, userId, tenantId);
        jdbcTemplate.update("""
                INSERT INTO legal_entity (
                    id,
                    tenant_id,
                    legal_name,
                    registration_number
                )
                VALUES (?, ?, ?, ?)
                """, legalEntityId, tenantId, "Existing Legal Entity",
                "EXISTING-1");
        insertDeal(jdbcTemplate, dealId, tenantId, userId, legalEntityId,
                "DL-9000000001");
        jdbcTemplate.update("""
                INSERT INTO deal_participant (
                    deal_id,
                    tenant_id,
                    legal_entity_id
                )
                VALUES (?, ?, ?)
                """, dealId, tenantId, legalEntityId);
    }

    private void insertDeal(JdbcTemplate jdbcTemplate, UUID dealId,
            UUID tenantId, UUID userId, UUID legalEntityId, String reference) {
        jdbcTemplate.update("""
                INSERT INTO deal (
                    id,
                    tenant_id,
                    reference,
                    title,
                    deal_status,
                    initiator_legal_entity_id,
                    created_by
                )
                VALUES (?, ?, ?, ?, 'DRAFT', ?, ?)
                """, dealId, tenantId, reference, "Existing Deal",
                legalEntityId, userId);
    }
}
