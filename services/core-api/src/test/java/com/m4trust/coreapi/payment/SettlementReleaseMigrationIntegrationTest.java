package com.m4trust.coreapi.payment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class SettlementReleaseMigrationIntegrationTest {

    private static final AtomicLong NEXT_DEAL_REFERENCE = new AtomicLong(1);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.5-alpine");

    static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    @Test
    void enforcesSettlementAndReleaseCardinality() {
        UUID dealId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID fundingUnitId = UUID.randomUUID();
        UUID settlementId = UUID.randomUUID();
        seedDeal(dealId, tenantId, fundingUnitId);

        assertDoesNotThrow(() -> jdbc.update("""
                INSERT INTO settlement (
                    id, deal_id, funding_unit_id, tenant_id, status, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'NOT_READY', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, settlementId, dealId, fundingUnitId, tenantId));

        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO settlement (
                    id, deal_id, funding_unit_id, tenant_id, status, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'NOT_READY', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), dealId, fundingUnitId, tenantId));

        UUID operationId = UUID.randomUUID();
        UUID providerKey = UUID.randomUUID();
        assertDoesNotThrow(() -> jdbc.update("""
                INSERT INTO release_operation (
                    id, settlement_id, provider_key, status, version, created_at, updated_at
                ) VALUES (?, ?, ?, 'QUEUED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, operationId, settlementId, providerKey));

        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO release_operation (
                    id, settlement_id, provider_key, status, version, created_at, updated_at
                ) VALUES (?, ?, ?, 'QUEUED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), settlementId, UUID.randomUUID()));
    }

    private void seedDeal(UUID dealId, UUID tenantId, UUID fundingUnitId) {
        UUID userId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO identity_user (id, email, password_hash, display_name, enabled)
                VALUES (?, 'settlement@test', 'hash', 'User', true)
                """, userId);
        jdbc.update("INSERT INTO tenant (id) VALUES (?)", tenantId);
        jdbc.update("INSERT INTO tenant_user (user_id, tenant_id) VALUES (?, ?)", userId, tenantId);
        for (UUID entityId : java.util.List.of(buyerId, sellerId)) {
            jdbc.update("""
                    INSERT INTO legal_entity (id, tenant_id, legal_name, registration_number)
                    VALUES (?, ?, 'Entity', ?)
                    """, entityId, tenantId, "REG-" + entityId);
            jdbc.update("""
                    INSERT INTO legal_entity_membership (id, tenant_id, legal_entity_id, user_id, role)
                    VALUES (?, ?, ?, ?, 'ADMIN')
                    """, UUID.randomUUID(), tenantId, entityId, userId);
        }
        jdbc.update("""
                INSERT INTO deal (
                    id, tenant_id, reference, title, deal_status,
                    initiator_legal_entity_id, created_by, created_at, updated_at, version
                ) VALUES (?, ?, ?, 'Deal', 'DRAFT', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, dealId, tenantId, nextDealReference(), buyerId, userId);
        for (UUID entityId : java.util.List.of(buyerId, sellerId)) {
            jdbc.update("""
                    INSERT INTO deal_participant (deal_id, tenant_id, legal_entity_id, legal_entity_tenant_id)
                    VALUES (?, ?, ?, ?)
                    """, dealId, tenantId, entityId, tenantId);
        }
        jdbc.update("""
                UPDATE deal
                SET deal_status = 'ACTIVE',
                    buyer_legal_entity_id = ?,
                    seller_legal_entity_id = ?,
                    version = 0
                WHERE id = ?
                """, buyerId, sellerId, dealId);
        UUID snapshotId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ratification_package_snapshot (id, schema_version, canonical_snapshot, content_hash, created_at)
                VALUES (?, 2, '{"schemaVersion":2,"disputeWindowDays":0}', ?, CURRENT_TIMESTAMP)
                """, snapshotId, "a".repeat(64));
        jdbc.update("""
                INSERT INTO ratification_package (
                    id, deal_id, snapshot_id, buyer_legal_entity_id, seller_legal_entity_id,
                    amount_minor, currency, status, version, created_at
                ) VALUES (?, ?, ?, ?, ?, 1000, 'TRY', 'RATIFIED', 0, CURRENT_TIMESTAMP)
                """, packageId, dealId, snapshotId, buyerId, sellerId);
        jdbc.update("UPDATE deal SET current_ratification_package_id = ? WHERE id = ?", packageId, dealId);
        UUID planId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO funding_plan (
                    id, deal_id, ratification_package_id, tenant_id, amount_minor, currency,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 1000, 'TRY', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, planId, dealId, packageId, tenantId);
        jdbc.update("""
                INSERT INTO funding_unit (
                    id, funding_plan_id, sequence_no, amount_minor, currency, status,
                    version, created_at, updated_at
                ) VALUES (?, ?, 1, 1000, 'TRY', 'FUNDED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, fundingUnitId, planId);
    }

    private static String nextDealReference() {
        return "DL-" + String.format("%010d", NEXT_DEAL_REFERENCE.getAndIncrement());
    }
}
