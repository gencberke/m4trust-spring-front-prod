package com.m4trust.coreapi.document;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DocumentMigrationIntegrationTest {

    private static final String SHA_256 = "a".repeat(64);
    private static final Instant CREATED_AT = Instant.parse("2026-07-18T10:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.5-alpine");

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                        POSTGRES.getPassword())
                .load()
                .migrate();
        jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword()));
    }

    @Test
    void currentDocumentPointerRequiresAnAvailableDocumentOfTheSameDeal() {
        Fixture fixture = insertFixture();
        UUID pendingDocumentId = UUID.randomUUID();
        UUID availableDocumentId = UUID.randomUUID();
        UUID otherDealDocumentId = UUID.randomUUID();
        insertPending(pendingDocumentId, fixture.dealId());
        insertAvailable(availableDocumentId, fixture.dealId());
        insertAvailable(otherDealDocumentId, fixture.otherDealId());

        assertThrows(DataIntegrityViolationException.class, () ->
                setCurrentDocument(fixture.dealId(), pendingDocumentId));
        assertThrows(DataIntegrityViolationException.class, () ->
                setCurrentDocument(fixture.dealId(), otherDealDocumentId));
        assertDoesNotThrow(() -> setCurrentDocument(
                fixture.dealId(), availableDocumentId));
        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("""
                        UPDATE document
                        SET document_status = 'SUPERSEDED',
                            superseded_at = ?, updated_at = ?
                        WHERE id = ?
                        """, java.sql.Timestamp.from(CREATED_AT.plusSeconds(20)),
                java.sql.Timestamp.from(CREATED_AT.plusSeconds(20)), availableDocumentId));
    }

    @Test
    void finalizedObjectVersionMetadataCannotBeChanged() {
        Fixture fixture = insertFixture();
        UUID documentId = UUID.randomUUID();
        insertAvailable(documentId, fixture.dealId());

        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("""
                        UPDATE document SET object_version = 'replacement-version'
                        WHERE id = ?
                        """, documentId));
    }

    private Fixture insertFixture() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID legalEntityId = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        UUID otherDealId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO identity_user (id, email, password_hash, display_name, enabled)
                VALUES (?, ?, 'test-hash', 'Document Owner', true)
                """, userId, userId + "@example.com");
        jdbcTemplate.update("INSERT INTO tenant (id) VALUES (?)", tenantId);
        jdbcTemplate.update("INSERT INTO tenant_user (user_id, tenant_id) VALUES (?, ?)",
                userId, tenantId);
        jdbcTemplate.update("""
                INSERT INTO legal_entity (id, tenant_id, legal_name, registration_number)
                VALUES (?, ?, 'Document Entity', ?)
                """, legalEntityId, tenantId, "REG-" + UUID.randomUUID());
        insertDeal(dealId, tenantId, legalEntityId, userId, "DL-" + randomReference());
        insertDeal(otherDealId, tenantId, legalEntityId, userId, "DL-" + randomReference());
        return new Fixture(dealId, otherDealId);
    }

    private void insertDeal(UUID dealId, UUID tenantId, UUID legalEntityId,
            UUID userId, String reference) {
        jdbcTemplate.update("""
                INSERT INTO deal (
                    id, tenant_id, reference, title, deal_status,
                    initiator_legal_entity_id, created_by
                ) VALUES (?, ?, ?, 'Document Deal', 'DRAFT', ?, ?)
                """, dealId, tenantId, reference, legalEntityId, userId);
    }

    private void insertPending(UUID documentId, UUID dealId) {
        jdbcTemplate.update("""
                INSERT INTO document (
                    id, deal_id, file_name, media_type, document_status, object_key,
                    declared_size_bytes, declared_sha256, upload_expires_at, created_at, updated_at
                ) VALUES (?, ?, 'contract.pdf', 'application/pdf', 'PENDING_UPLOAD', ?,
                          12, ?, ?, ?, ?)
                """, documentId, dealId, "documents/" + documentId, SHA_256,
                java.sql.Timestamp.from(CREATED_AT.plusSeconds(300)),
                java.sql.Timestamp.from(CREATED_AT),
                java.sql.Timestamp.from(CREATED_AT));
    }

    private void insertAvailable(UUID documentId, UUID dealId) {
        jdbcTemplate.update("""
                INSERT INTO document (
                    id, deal_id, file_name, media_type, document_status, object_key,
                    declared_size_bytes, declared_sha256, upload_expires_at,
                    verified_size_bytes, verified_sha256, object_version,
                    created_at, available_at, updated_at
                ) VALUES (?, ?, 'contract.pdf', 'application/pdf', 'AVAILABLE', ?,
                          12, ?, ?, 12, ?, 'immutable-version', ?, ?, ?)
                """, documentId, dealId, "documents/" + documentId, SHA_256,
                java.sql.Timestamp.from(CREATED_AT.plusSeconds(300)), SHA_256,
                java.sql.Timestamp.from(CREATED_AT),
                java.sql.Timestamp.from(CREATED_AT.plusSeconds(10)),
                java.sql.Timestamp.from(CREATED_AT.plusSeconds(10)));
    }

    private void setCurrentDocument(UUID dealId, UUID documentId) {
        jdbcTemplate.update("""
                UPDATE deal
                SET current_document_id = ?, current_document_status = 'AVAILABLE'
                WHERE id = ?
                """, documentId, dealId);
    }

    private static String randomReference() {
        return String.format("%010d", Math.floorMod(UUID.randomUUID().getLeastSignificantBits(),
                10_000_000_000L));
    }

    private record Fixture(UUID dealId, UUID otherDealId) {
    }
}
