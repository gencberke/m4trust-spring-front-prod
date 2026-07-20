package com.m4trust.coreapi.fulfillment;

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

/** Exercises the V21 video-analysis database boundary after a complete V1..V21 Flyway migration. */
@Testcontainers
class VideoAnalysisMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.5-alpine");

    static JdbcTemplate jdbc;
    private static final java.util.concurrent.atomic.AtomicLong NEXT_DEAL_REFERENCE =
            new java.util.concurrent.atomic.AtomicLong(1_000_000_000L);

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
    void enforcesVideoAnalysisJobAndResultInvariants() {
        Fixture fixture = fixture();
        UUID fulfillmentId = fulfillment(fixture.dealId(), fixture.tenant(), UUID.randomUUID(), "REVIEW_REQUIRED", 0L);
        UUID milestoneId = milestone(fulfillmentId, fixture.dealId(), "Primary", "REVIEW_REQUIRED", 0L);
        String sha256 = "b".repeat(64);
        UUID evidenceId = videoEvidence(UUID.randomUUID(), fixture.dealId(), milestoneId, fulfillmentId,
                "SUBMITTED", sha256, 1L);

        UUID firstQueuedJobId = queuedJob(UUID.randomUUID(), fixture.tenant(), fixture.dealId(), fulfillmentId,
                milestoneId, evidenceId, sha256, null);

        assertThrows(DataAccessException.class,
                () -> queuedJob(UUID.randomUUID(), fixture.tenant(), fixture.dealId(), fulfillmentId,
                        milestoneId, evidenceId, sha256, null),
                "at most one queued job per evidence is allowed");

        markFailed(firstQueuedJobId);
        UUID retryQueuedJobId = queuedJob(UUID.randomUUID(), fixture.tenant(), fixture.dealId(), fulfillmentId,
                milestoneId, evidenceId, sha256, firstQueuedJobId);

        UUID otherEvidenceId = videoEvidence(UUID.randomUUID(), fixture.dealId(), milestoneId, fulfillmentId,
                "REJECTED", sha256, 2L);
        assertThrows(DataAccessException.class,
                () -> queuedJob(UUID.randomUUID(), fixture.tenant(), fixture.dealId(), fulfillmentId,
                        milestoneId, otherEvidenceId, sha256, firstQueuedJobId),
                "predecessor jobs must belong to the same evidence submission");

        markFailed(retryQueuedJobId);
        UUID successfulJobId = resultAvailableJob(UUID.randomUUID(), fixture.tenant(), fixture.dealId(),
                fulfillmentId, milestoneId, evidenceId, sha256, retryQueuedJobId);
        assertThrows(DataAccessException.class,
                () -> resultAvailableJob(UUID.randomUUID(), fixture.tenant(), fixture.dealId(), fulfillmentId,
                        milestoneId, evidenceId, sha256, successfulJobId),
                "at most one successful result per evidence is allowed");

        UUID resultId = UUID.randomUUID();
        assertDoesNotThrow(() -> insertResult(resultId, successfulJobId));
        assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE fulfillment_video_analysis_result SET schema_version='9.9.9' WHERE id=?",
                        resultId),
                "video analysis results are immutable");
        assertThrows(DataAccessException.class,
                () -> jdbc.update("DELETE FROM fulfillment_video_analysis_result WHERE id=?", resultId),
                "video analysis results cannot be deleted");

        UUID otherDealFulfillmentId = fulfillment(fixture.otherDealId(), fixture.tenant(), UUID.randomUUID(),
                "REVIEW_REQUIRED", 0L);
        UUID otherMilestoneId = milestone(otherDealFulfillmentId, fixture.otherDealId(), "Other",
                "REVIEW_REQUIRED", 0L);
        UUID otherEvidence = videoEvidence(UUID.randomUUID(), fixture.otherDealId(), otherMilestoneId,
                otherDealFulfillmentId, "SUBMITTED", sha256, 1L);
        assertThrows(DataAccessException.class,
                () -> queuedJob(UUID.randomUUID(), fixture.tenant(), fixture.dealId(), fulfillmentId,
                        milestoneId, otherEvidence, sha256, null),
                "video analysis jobs must belong to the same Deal as their evidence");
    }

    @Test
    void rejectsJobTenantMismatchAgainstDealHostingTenant() {
        Fixture fixture = fixture();
        UUID fulfillmentId = fulfillment(fixture.dealId(), fixture.tenant(), UUID.randomUUID(), "REVIEW_REQUIRED", 0L);
        UUID milestoneId = milestone(fulfillmentId, fixture.dealId(), "Primary", "REVIEW_REQUIRED", 0L);
        String sha256 = "c".repeat(64);
        UUID evidenceId = videoEvidence(UUID.randomUUID(), fixture.dealId(), milestoneId, fulfillmentId,
                "SUBMITTED", sha256, 1L);
        UUID foreignTenant = UUID.randomUUID();
        jdbc.update("INSERT INTO tenant(id) VALUES (?)", foreignTenant);

        assertThrows(DataAccessException.class,
                () -> queuedJob(UUID.randomUUID(), foreignTenant, fixture.dealId(), fulfillmentId,
                        milestoneId, evidenceId, sha256, null),
                "video analysis job tenant must match Deal hosting tenant");
    }

    @Test
    void allowsDealHostingJobTenantWhenFulfillmentUsesSellerActorTenant() {
        Fixture fixture = fixture();
        UUID sellerActorTenant = UUID.randomUUID();
        jdbc.update("INSERT INTO tenant(id) VALUES (?)", sellerActorTenant);
        UUID fulfillmentId = fulfillment(fixture.dealId(), sellerActorTenant, UUID.randomUUID(),
                "REVIEW_REQUIRED", 0L);
        UUID milestoneId = milestone(fulfillmentId, fixture.dealId(), "Primary", "REVIEW_REQUIRED", 0L);
        String sha256 = "d".repeat(64);
        UUID evidenceId = videoEvidence(UUID.randomUUID(), fixture.dealId(), milestoneId, fulfillmentId,
                "SUBMITTED", sha256, 1L);

        assertDoesNotThrow(() -> queuedJob(UUID.randomUUID(), fixture.tenant(), fixture.dealId(), fulfillmentId,
                milestoneId, evidenceId, sha256, null),
                "job tenant follows Deal hosting tenant even when fulfillment tenant differs");
    }

    private static void markFailed(UUID jobId) {
        jdbc.update("""
                UPDATE fulfillment_video_analysis_job
                SET status = 'FAILED', failed_at = CURRENT_TIMESTAMP, failure_code = 'MODEL_PROVIDER_TIMEOUT',
                    retry_recommended = true, version = version + 1
                WHERE id = ?
                """, jobId);
    }

    private static UUID resultAvailableJob(UUID jobId, UUID tenantId, UUID dealId, UUID fulfillmentId,
            UUID milestoneId, UUID evidenceId, String sha256, UUID predecessorJobId) {
        jdbc.update("""
                INSERT INTO fulfillment_video_analysis_job (
                    id, tenant_id, deal_id, fulfillment_id, milestone_id, evidence_submission_id,
                    object_version, input_sha256, input_size_bytes, input_media_type, input_file_name,
                    status, predecessor_job_id, requested_at, completed_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, 'version-1', ?, 2048, 'video/mp4', 'delivery.mp4',
                    'RESULT_AVAILABLE', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, jobId, tenantId, dealId, fulfillmentId, milestoneId, evidenceId, sha256, predecessorJobId);
        return jobId;
    }

    private static UUID queuedJob(UUID jobId, UUID tenantId, UUID dealId, UUID fulfillmentId,
            UUID milestoneId, UUID evidenceId, String sha256, UUID predecessorJobId) {
        jdbc.update("""
                INSERT INTO fulfillment_video_analysis_job (
                    id, tenant_id, deal_id, fulfillment_id, milestone_id, evidence_submission_id,
                    object_version, input_sha256, input_size_bytes, input_media_type, input_file_name,
                    status, predecessor_job_id, requested_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, 'version-1', ?, 2048, 'video/mp4', 'delivery.mp4',
                    'QUEUED', ?, CURRENT_TIMESTAMP, 0)
                """, jobId, tenantId, dealId, fulfillmentId, milestoneId, evidenceId, sha256, predecessorJobId);
        return jobId;
    }

    private static void insertResult(UUID resultId, UUID jobId) {
        jdbc.update("""
                INSERT INTO fulfillment_video_analysis_result
                    (id, job_id, schema_version, canonical_result, created_at)
                VALUES (?, ?, '1.0.0', CAST('{"result":{"durationMs":1000}}' AS jsonb), ?)
                """, resultId, jobId, Timestamp.from(Instant.now()));
    }

    private static UUID fulfillment(UUID dealId, UUID tenantId, UUID sourcePackageId, String status, long version) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO fulfillment (id, deal_id, tenant_id, source_package_id, status, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, dealId, tenantId, sourcePackageId, status, version);
        return id;
    }

    private static UUID milestone(UUID fulfillmentId, UUID dealId, String title, String status, long version) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO fulfillment_milestone (id, fulfillment_id, deal_id, title, status, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, fulfillmentId, dealId, title, status, version);
        return id;
    }

    private static UUID videoEvidence(UUID id, UUID dealId, UUID milestoneId, UUID fulfillmentId,
            String status, String sha256, long version) {
        jdbc.update("""
                INSERT INTO fulfillment_evidence_submission (
                    id, deal_id, milestone_id, fulfillment_id, evidence_type, media_type, file_name,
                    status, object_key, object_version, client_size_bytes, client_sha256, verified_size_bytes,
                    verified_sha256, upload_expires_at, created_at, submitted_at, accepted_at,
                    rejected_at, rejection_reason, version
                ) VALUES (?, ?, ?, ?, 'VIDEO', 'video/mp4', 'delivery.mp4', ?, 'obj-key',
                    CASE WHEN ? IN ('SUBMITTED', 'ACCEPTED', 'REJECTED') THEN 'version-1' ELSE NULL END,
                    2048, ?,
                    CASE WHEN ? IN ('SUBMITTED', 'ACCEPTED', 'REJECTED') THEN 2048 ELSE NULL END,
                    CASE WHEN ? IN ('SUBMITTED', 'ACCEPTED', 'REJECTED') THEN ? ELSE NULL END,
                    CURRENT_TIMESTAMP + INTERVAL '1 hour', CURRENT_TIMESTAMP,
                    CASE WHEN ? IN ('SUBMITTED', 'ACCEPTED', 'REJECTED') THEN CURRENT_TIMESTAMP ELSE NULL END,
                    CASE WHEN ? = 'ACCEPTED' THEN CURRENT_TIMESTAMP ELSE NULL END,
                    CASE WHEN ? = 'REJECTED' THEN CURRENT_TIMESTAMP ELSE NULL END,
                    CASE WHEN ? = 'REJECTED' THEN 'reason' ELSE NULL END,
                    ?)
                """,
                id, dealId, milestoneId, fulfillmentId, status, status, sha256,
                status, status, sha256, status, status, status, status, version);
        return id;
    }

    private static Fixture fixture() {
        UUID tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID deal = UUID.randomUUID();
        UUID otherDeal = UUID.randomUUID();

        jdbc.update("INSERT INTO identity_user(id, email, password_hash, display_name, enabled) VALUES (?, ?, 'x', 'User', true)",
                user, user + "@example.com");
        jdbc.update("INSERT INTO tenant(id) VALUES (?)", tenant);
        jdbc.update("INSERT INTO tenant_user(user_id, tenant_id) VALUES (?, ?)", user, tenant);

        for (UUID entity : java.util.List.of(buyer, seller)) {
            jdbc.update("INSERT INTO legal_entity(id, tenant_id, legal_name, registration_number) VALUES (?, ?, 'Entity', ?)",
                    entity, tenant, "R" + entity);
            jdbc.update("INSERT INTO legal_entity_membership(id, tenant_id, legal_entity_id, user_id, role) VALUES (?, ?, ?, ?, 'ADMIN')",
                    UUID.randomUUID(), tenant, entity, user);
        }

        deal(deal, tenant, buyer, user, nextDealReference());
        deal(otherDeal, tenant, buyer, user, nextDealReference());

        for (UUID dealId : java.util.List.of(deal, otherDeal)) {
            for (UUID entity : java.util.List.of(buyer, seller)) {
                jdbc.update("INSERT INTO deal_participant(deal_id, tenant_id, legal_entity_id, legal_entity_tenant_id) VALUES (?, ?, ?, ?)",
                        dealId, tenant, entity, tenant);
            }
        }

        return new Fixture(tenant, deal, otherDeal);
    }

    private static String nextDealReference() {
        return String.format("DL-%010d", NEXT_DEAL_REFERENCE.getAndIncrement() % 10_000_000_000L);
    }

    private static void deal(UUID id, UUID tenant, UUID entity, UUID user, String reference) {
        jdbc.update("INSERT INTO deal(id, tenant_id, reference, title, deal_status, initiator_legal_entity_id, created_by) VALUES (?, ?, ?, 'Deal', 'DRAFT', ?, ?)",
                id, tenant, reference, entity, user);
    }

    record Fixture(UUID tenant, UUID dealId, UUID otherDealId) {
    }
}
