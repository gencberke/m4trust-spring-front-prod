package com.m4trust.coreapi.casework;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Exercises the V22 casework database boundary after a complete V1..V22 Flyway migration. */
@Testcontainers
class DisputeCaseworkMigrationIntegrationTest {

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
    void enforcesDisputeCaseSnapshotAndCommentInvariants() {
        Fixture fixture = fixture();
        OpenContext open = openContext(fixture);

        UUID firstCase = disputeCase(open, "OPEN", null, null);
        assertThrows(DataAccessException.class,
                () -> disputeCase(open, "OPEN", null, null),
                "only one active dispute per Deal is allowed");
        assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE dispute_case SET subject='changed' WHERE id=?", firstCase),
                "opening subject is immutable");
        assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE dispute_case SET opening_legal_entity_id=? WHERE id=?",
                        fixture.sellerId(), firstCase),
                "opening legal entity is immutable");
        assertThrows(DataAccessException.class,
                () -> jdbc.update("DELETE FROM dispute_case WHERE id=?", firstCase),
                "dispute case history is retained");

        assertDoesNotThrow(() -> jdbc.update("""
                UPDATE dispute_case
                SET status='UNDER_REVIEW', acknowledged_at=CURRENT_TIMESTAMP, version=1, updated_at=CURRENT_TIMESTAMP
                WHERE id=?
                """, firstCase));

        assertThrows(DataAccessException.class,
                () -> jdbc.update("""
                        UPDATE dispute_case
                        SET version = 0
                        WHERE id = ?
                        """, firstCase),
                "version rollback is rejected");
        assertThrows(DataAccessException.class,
                () -> jdbc.update("""
                        UPDATE dispute_case
                        SET status = 'RESOLVED'
                        WHERE id = ?
                        """, firstCase),
                "RESOLVED transition is rejected in Slice 14A");

        assertThrows(DataAccessException.class,
                () -> jdbc.update("""
                        INSERT INTO dispute_case (
                            id, deal_id, tenant_id, fulfillment_id, milestone_id, ratification_package_id,
                            fulfillment_status_at_open, fulfillment_version_at_open, milestone_version_at_open,
                            reason_code, subject, statement, status, opening_tenant_id, opening_legal_entity_id,
                            opening_user_id, opening_legal_name, opened_at, version, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, 'REVIEW_REQUIRED', 0, 0, 'NON_DELIVERY', 'subject', 'statement', 'RESOLVED',
                            ?, ?, ?, 'Buyer', CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """, UUID.randomUUID(), fixture.dealId(), fixture.tenant(), open.fulfillmentId(),
                        open.milestoneId(), open.packageId(), open.actorTenantId(), open.openingLegalEntityId(),
                        open.openingUserId()),
                "RESOLVED insert is rejected in Slice 14A");

        UUID rejectedEvidence = rejectedEvidence(open.dealId(), open.milestoneId(), open.fulfillmentId());
        assertDoesNotThrow(() -> jdbc.update("""
                INSERT INTO dispute_evidence_snapshot (
                    id, dispute_case_id, deal_id, evidence_submission_id, status_at_open, version_at_open,
                    evidence_type, media_type, file_name, object_version, verified_size_bytes, verified_sha256,
                    created_at, submitted_at, rejected_at, rejection_reason
                ) VALUES (?, ?, ?, ?, 'REJECTED', 1, 'INVOICE', 'application/pdf', 'invoice.pdf', 'version-1',
                    12, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'Missing tax number')
                """, UUID.randomUUID(), firstCase, open.dealId(), rejectedEvidence, "a".repeat(64)));

        UUID snapshot = evidenceSnapshot(firstCase, fixture.dealId(), open.evidenceId(), null, null);
        assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE dispute_evidence_snapshot SET file_name='changed' WHERE id=?", snapshot),
                "evidence snapshots are immutable");
        assertThrows(DataAccessException.class,
                () -> jdbc.update("DELETE FROM dispute_evidence_snapshot WHERE id=?", snapshot),
                "evidence snapshots cannot be deleted");
        assertThrows(DataAccessException.class,
                () -> evidenceSnapshot(firstCase, fixture.dealId(), open.otherDealEvidenceId(), null, null),
                "snapshot evidence must belong to the same Deal");

        UUID comment = comment(firstCase, fixture.dealId(), fixture);
        assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE dispute_comment SET body='changed' WHERE id=?", comment),
                "comments are append-only");
        assertThrows(DataAccessException.class,
                () -> jdbc.update("DELETE FROM dispute_comment WHERE id=?", comment),
                "comments cannot be deleted");
        assertThrows(DataAccessException.class,
                () -> comment(firstCase, fixture.otherDealId(), fixture),
                "comments must reference the same Deal as the case");

        UUID withdrawnCase = disputeCase(open, "WITHDRAWN", null, Instant.now());
        assertDoesNotThrow(() -> jdbc.update("""
                UPDATE dispute_case
                SET status='WITHDRAWN', withdrawn_at=CURRENT_TIMESTAMP, version=1, updated_at=CURRENT_TIMESTAMP
                WHERE id=?
                """, withdrawnCase));
    }

    @Test
    void rejectsInvalidStatusTimestampAndTextBounds() {
        Fixture fixture = fixture();
        OpenContext open = openContext(fixture);

        assertThrows(DataAccessException.class,
                () -> jdbc.update("""
                        INSERT INTO dispute_case (
                            id, deal_id, tenant_id, fulfillment_id, milestone_id, ratification_package_id,
                            fulfillment_status_at_open, fulfillment_version_at_open, milestone_version_at_open,
                            reason_code, subject, statement, status, opening_tenant_id, opening_legal_entity_id,
                            opening_user_id, opening_legal_name, opened_at, version, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, 'IN_PROGRESS', 0, 0, 'OTHER', ' ', 'statement', 'OPEN',
                            ?, ?, ?, 'Buyer', CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """, UUID.randomUUID(), fixture.dealId(), fixture.tenant(), open.fulfillmentId(),
                        open.milestoneId(), open.packageId(), fixture.actorTenant(), fixture.buyerId(),
                        fixture.userId()),
                "blank subject is rejected");
        assertThrows(DataAccessException.class,
                () -> jdbc.update("""
                        INSERT INTO dispute_case (
                            id, deal_id, tenant_id, fulfillment_id, milestone_id, ratification_package_id,
                            fulfillment_status_at_open, fulfillment_version_at_open, milestone_version_at_open,
                            reason_code, subject, statement, status, opening_tenant_id, opening_legal_entity_id,
                            opening_user_id, opening_legal_name, opened_at, version, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, 'BAD_STATUS', 0, 0, 'OTHER', 'subject', 'statement', 'OPEN',
                            ?, ?, ?, 'Buyer', CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """, UUID.randomUUID(), fixture.dealId(), fixture.tenant(), open.fulfillmentId(),
                        open.milestoneId(), open.packageId(), fixture.actorTenant(), fixture.buyerId(),
                        fixture.userId()),
                "invalid fulfillment status at open is rejected");
        assertThrows(DataAccessException.class,
                () -> jdbc.update("""
                        INSERT INTO dispute_case (
                            id, deal_id, tenant_id, fulfillment_id, milestone_id, ratification_package_id,
                            fulfillment_status_at_open, fulfillment_version_at_open, milestone_version_at_open,
                            reason_code, subject, statement, status, opening_tenant_id, opening_legal_entity_id,
                            opening_user_id, opening_legal_name, opened_at, acknowledged_at, version, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, 'IN_PROGRESS', 0, 0, 'OTHER', 'subject', 'statement', 'UNDER_REVIEW',
                            ?, ?, ?, 'Buyer', CURRENT_TIMESTAMP, NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """, UUID.randomUUID(), fixture.dealId(), fixture.tenant(), open.fulfillmentId(),
                        open.milestoneId(), open.packageId(), fixture.actorTenant(), fixture.buyerId(),
                        fixture.userId()),
                "UNDER_REVIEW without acknowledgement timestamp is rejected");
    }

    @Test
    void allowsCrossTenantOpeningWhenActorTenantMatchesEntityAndUser() {
        Fixture fixture = fixture();
        OpenContext open = openContext(fixture);
        UUID actorTenant = fixture.actorTenant();
        UUID crossEntity = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO legal_entity (id, tenant_id, legal_name, registration_number)
                VALUES (?, ?, 'Cross Buyer', ?)
                """, crossEntity, actorTenant, "REG-" + crossEntity);
        jdbc.update("INSERT INTO tenant_user (user_id, tenant_id) VALUES (?, ?)",
                fixture.userId(), actorTenant);
        jdbc.update("""
                INSERT INTO legal_entity_membership (id, tenant_id, legal_entity_id, user_id, role)
                VALUES (?, ?, ?, ?, 'ADMIN')
                """, UUID.randomUUID(), actorTenant, crossEntity, fixture.userId());
        jdbc.update("""
                INSERT INTO deal_participant (deal_id, tenant_id, legal_entity_id, legal_entity_tenant_id)
                VALUES (?, ?, ?, ?)
                """, fixture.dealId(), fixture.tenant(), crossEntity, actorTenant);

        assertDoesNotThrow(() -> jdbc.update("""
                INSERT INTO dispute_case (
                    id, deal_id, tenant_id, fulfillment_id, milestone_id, ratification_package_id,
                    fulfillment_status_at_open, fulfillment_version_at_open, milestone_version_at_open,
                    reason_code, subject, statement, status, opening_tenant_id, opening_legal_entity_id,
                    opening_user_id, opening_legal_name, opened_at, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'REVIEW_REQUIRED', 0, 0, 'NON_DELIVERY', 'subject', 'statement', 'OPEN',
                    ?, ?, ?, 'Cross Buyer', CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), fixture.dealId(), fixture.tenant(), open.fulfillmentId(),
                open.milestoneId(), open.packageId(), actorTenant, crossEntity, fixture.userId()));
    }

    @Test
    void rejectsOpeningActorTenantMismatchWithEntityTenant() {
        Fixture fixture = fixture();
        OpenContext open = openContext(fixture);
        UUID actorTenant = fixture.actorTenant();

        assertThrows(DataAccessException.class,
                () -> jdbc.update("""
                        INSERT INTO dispute_case (
                            id, deal_id, tenant_id, fulfillment_id, milestone_id, ratification_package_id,
                            fulfillment_status_at_open, fulfillment_version_at_open, milestone_version_at_open,
                            reason_code, subject, statement, status, opening_tenant_id, opening_legal_entity_id,
                            opening_user_id, opening_legal_name, opened_at, version, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, 'REVIEW_REQUIRED', 0, 0, 'NON_DELIVERY', 'subject', 'statement', 'OPEN',
                            ?, ?, ?, 'Buyer', CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """, UUID.randomUUID(), fixture.dealId(), fixture.tenant(), open.fulfillmentId(),
                        open.milestoneId(), open.packageId(), actorTenant, fixture.buyerId(), fixture.userId()),
                "opening actor tenant must match opening legal entity tenant");
    }

    @Test
    void rejectsCrossEvidenceVideoReference() {
        Fixture fixture = fixture();
        OpenContext open = openContext(fixture);
        UUID caseId = disputeCase(open, "OPEN", null, null);
        UUID jobId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO fulfillment_video_analysis_job (
                    id, tenant_id, deal_id, fulfillment_id, milestone_id, evidence_submission_id,
                    object_version, input_sha256, input_size_bytes, input_media_type, input_file_name,
                    status, requested_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, 'version-1', ?, 12, 'application/pdf', 'invoice.pdf',
                    'RESULT_AVAILABLE', CURRENT_TIMESTAMP, 0)
                """, jobId, fixture.tenant(), fixture.dealId(), open.fulfillmentId(), open.milestoneId(),
                open.otherDealEvidenceId(), "a".repeat(64));
        jdbc.update("""
                INSERT INTO fulfillment_video_analysis_result (id, job_id, schema_version, canonical_result, created_at)
                VALUES (?, ?, '1.0.0', '{"result":{"durationMs":1,"observations":[],"anomalies":[],"summary":{}}}'::jsonb,
                    CURRENT_TIMESTAMP)
                """, resultId, jobId);

        assertThrows(DataAccessException.class,
                () -> evidenceSnapshot(caseId, fixture.dealId(), open.evidenceId(), jobId, resultId),
                "video job must belong to the same evidence submission");
    }

    private static UUID disputeCase(OpenContext open, String status, Instant acknowledgedAt, Instant withdrawnAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO dispute_case (
                    id, deal_id, tenant_id, fulfillment_id, milestone_id, ratification_package_id,
                    fulfillment_status_at_open, fulfillment_version_at_open, milestone_version_at_open,
                    reason_code, subject, statement, status, opening_tenant_id, opening_legal_entity_id,
                    opening_user_id, opening_legal_name, opened_at, acknowledged_at, withdrawn_at,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'REVIEW_REQUIRED', 0, 0, 'NON_DELIVERY', 'subject', 'statement', ?,
                    ?, ?, ?, 'Buyer', CURRENT_TIMESTAMP, ?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                id, open.dealId(), open.tenantId(), open.fulfillmentId(), open.milestoneId(), open.packageId(),
                status, open.actorTenantId(), open.openingLegalEntityId(), open.openingUserId(),
                timestamp(acknowledgedAt), timestamp(withdrawnAt));
        return id;
    }

    private static UUID rejectedEvidence(UUID dealId, UUID milestoneId, UUID fulfillmentId) {
        UUID id = UUID.randomUUID();
        String sha256 = "b".repeat(64);
        jdbc.update("""
                INSERT INTO fulfillment_evidence_submission (
                    id, deal_id, milestone_id, fulfillment_id, evidence_type, media_type, file_name,
                    status, object_key, object_version, client_size_bytes, client_sha256, verified_size_bytes,
                    verified_sha256, upload_expires_at, created_at, submitted_at, rejected_at, rejection_reason, version
                ) VALUES (?, ?, ?, ?, 'INVOICE', 'application/pdf', 'invoice.pdf', 'REJECTED', 'obj-key',
                    'version-1', 12, ?, 12, ?, CURRENT_TIMESTAMP + INTERVAL '1 hour', CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'Missing tax number', 1)
                """, id, dealId, milestoneId, fulfillmentId, sha256, sha256);
        return id;
    }

    private static UUID evidenceSnapshot(UUID disputeCaseId, UUID dealId, UUID evidenceId,
            UUID videoJobId, UUID videoResultId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO dispute_evidence_snapshot (
                    id, dispute_case_id, deal_id, evidence_submission_id, status_at_open, version_at_open,
                    evidence_type, media_type, file_name, object_version, verified_size_bytes, verified_sha256,
                    created_at, submitted_at, video_job_id, video_result_id
                ) VALUES (?, ?, ?, ?, 'SUBMITTED', 1, 'INVOICE', 'application/pdf', 'invoice.pdf', 'version-1',
                    12, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?)
                """, id, disputeCaseId, dealId, evidenceId, "a".repeat(64), videoJobId, videoResultId);
        return id;
    }

    private static UUID comment(UUID disputeCaseId, UUID dealId, Fixture fixture) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO dispute_comment (
                    id, dispute_case_id, deal_id, body, author_tenant_id, author_legal_entity_id,
                    author_user_id, author_legal_name, author_display_name, created_at
                ) VALUES (?, ?, ?, 'comment body', ?, ?, ?, 'Buyer', 'Buyer Admin', CURRENT_TIMESTAMP)
                """, id, disputeCaseId, dealId, fixture.tenant(), fixture.buyerId(), fixture.userId());
        return id;
    }

    private static OpenContext openContext(Fixture fixture) {
        UUID snapshotId = snapshot();
        UUID packageId = ratificationPackage(fixture.dealId(), fixture.buyerId(), fixture.sellerId(), snapshotId);
        jdbc.update("UPDATE deal SET deal_status='ACTIVE', buyer_legal_entity_id=?, seller_legal_entity_id=?, current_ratification_package_id=? WHERE id=?",
                fixture.buyerId(), fixture.sellerId(), packageId, fixture.dealId());
        UUID fulfillmentId = fulfillment(fixture.dealId(), fixture.tenant(), packageId);
        UUID milestoneId = milestone(fulfillmentId, fixture.dealId());
        String sha256 = "a".repeat(64);
        UUID evidenceId = evidence(UUID.randomUUID(), fixture.dealId(), milestoneId, fulfillmentId, sha256);

        UUID otherFulfillmentId = fulfillment(fixture.otherDealId(), fixture.tenant(), packageId);
        UUID otherMilestoneId = milestone(otherFulfillmentId, fixture.otherDealId());
        UUID otherDealEvidenceId = evidence(UUID.randomUUID(), fixture.otherDealId(), otherMilestoneId,
                otherFulfillmentId, sha256);

        return new OpenContext(fixture.dealId(), fixture.tenant(), fixture.tenant(), fixture.buyerId(),
                fixture.userId(), fulfillmentId, milestoneId, packageId, evidenceId, otherDealEvidenceId);
    }

    private static UUID snapshot() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ratification_package_snapshot (id, schema_version, canonical_snapshot, content_hash, created_at)
                VALUES (?, 1, '{}', ?, CURRENT_TIMESTAMP)
                """, id, "a".repeat(64));
        return id;
    }

    private static UUID ratificationPackage(UUID dealId, UUID buyerId, UUID sellerId, UUID snapshotId) {
        UUID packageId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ratification_package (
                    id, deal_id, snapshot_id, status, buyer_legal_entity_id, seller_legal_entity_id,
                    amount_minor, currency, created_at
                ) VALUES (?, ?, ?, 'RATIFIED', ?, ?, 1, 'TRY', CURRENT_TIMESTAMP)
                """, packageId, dealId, snapshotId, buyerId, sellerId);
        return packageId;
    }

    private static UUID fulfillment(UUID dealId, UUID tenantId, UUID packageId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO fulfillment (id, deal_id, tenant_id, source_package_id, status, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'REVIEW_REQUIRED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, dealId, tenantId, packageId);
        return id;
    }

    private static UUID milestone(UUID fulfillmentId, UUID dealId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO fulfillment_milestone (id, fulfillment_id, deal_id, title, status, version, created_at, updated_at)
                VALUES (?, ?, ?, 'Primary', 'REVIEW_REQUIRED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, fulfillmentId, dealId);
        return id;
    }

    private static UUID evidence(UUID id, UUID dealId, UUID milestoneId, UUID fulfillmentId, String sha256) {
        jdbc.update("""
                INSERT INTO fulfillment_evidence_submission (
                    id, deal_id, milestone_id, fulfillment_id, evidence_type, media_type, file_name,
                    status, object_key, object_version, client_size_bytes, client_sha256, verified_size_bytes,
                    verified_sha256, upload_expires_at, created_at, submitted_at, version
                ) VALUES (?, ?, ?, ?, 'INVOICE', 'application/pdf', 'invoice.pdf', 'SUBMITTED', 'obj-key',
                    'version-1', 12, ?, 12, ?, CURRENT_TIMESTAMP + INTERVAL '1 hour', CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP, 1)
                """, id, dealId, milestoneId, fulfillmentId, sha256, sha256);
        return id;
    }

    private static Fixture fixture() {
        UUID tenant = UUID.randomUUID();
        UUID actorTenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID deal = UUID.randomUUID();
        UUID otherDeal = UUID.randomUUID();

        jdbc.update("INSERT INTO identity_user(id, email, password_hash, display_name, enabled) VALUES (?, ?, 'x', 'User', true)",
                user, user + "@example.com");
        jdbc.update("INSERT INTO tenant(id) VALUES (?)", tenant);
        jdbc.update("INSERT INTO tenant(id) VALUES (?)", actorTenant);
        jdbc.update("INSERT INTO tenant_user(user_id, tenant_id) VALUES (?, ?)", user, tenant);

        for (UUID entity : java.util.List.of(buyer, seller)) {
            jdbc.update("INSERT INTO legal_entity(id, tenant_id, legal_name, registration_number) VALUES (?, ?, 'Entity', ?)",
                    entity, tenant, "R" + entity);
            jdbc.update("INSERT INTO legal_entity_membership(id, tenant_id, legal_entity_id, user_id, role) VALUES (?, ?, ?, ?, 'ADMIN')",
                    UUID.randomUUID(), tenant, entity, user);
        }

        deal(deal, tenant, buyer, user, "DL-0000000001");
        deal(otherDeal, tenant, buyer, user, "DL-0000000002");
        for (UUID d : java.util.List.of(deal, otherDeal)) {
            for (UUID entity : java.util.List.of(buyer, seller)) {
                jdbc.update("""
                        INSERT INTO deal_participant(deal_id, tenant_id, legal_entity_id, legal_entity_tenant_id)
                        VALUES (?, ?, ?, ?)
                        """, d, tenant, entity, tenant);
            }
        }
        return new Fixture(tenant, actorTenant, user, buyer, seller, deal, otherDeal);
    }

    private static void deal(UUID id, UUID tenant, UUID entity, UUID user, String reference) {
        jdbc.update("""
                INSERT INTO deal(id, tenant_id, reference, title, deal_status, initiator_legal_entity_id, created_by)
                VALUES (?, ?, ?, 'Deal', 'DRAFT', ?, ?)
                """, id, tenant, reference, entity, user);
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private record Fixture(UUID tenant, UUID actorTenant, UUID userId, UUID buyerId, UUID sellerId,
            UUID dealId, UUID otherDealId) {
    }

    private record OpenContext(UUID dealId, UUID tenantId, UUID actorTenantId, UUID openingLegalEntityId,
            UUID openingUserId, UUID fulfillmentId, UUID milestoneId, UUID packageId, UUID evidenceId,
            UUID otherDealEvidenceId) {
    }
}
