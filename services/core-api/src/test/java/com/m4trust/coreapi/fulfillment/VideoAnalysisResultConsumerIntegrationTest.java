package com.m4trust.coreapi.fulfillment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.m4trust.coreapi.audit.AuditAppendPort;
import com.m4trust.coreapi.audit.AuditRecord;
import com.m4trust.coreapi.integration.messaging.AiResultsMessageRouter;
import com.m4trust.coreapi.integration.messaging.IntegrationViolation;
import com.m4trust.coreapi.ratification.RatificationPackageSeedSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "app.messaging.topology.enabled=false",
        "app.messaging.relay.enabled=false",
        "spring.main.allow-bean-definition-overriding=true"
})
@ActiveProfiles("local")
@Testcontainers
@AutoConfigureMockMvc
@Import(VideoAnalysisResultConsumerIntegrationTest.Fakes.class)
class VideoAnalysisResultConsumerIntegrationTest {

    private static final String LEGAL_ENTITY_HEADER = "X-M4Trust-Legal-Entity-Id";
    private static final String SHA = "a".repeat(64);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.5-alpine");

    @Autowired
    private AiResultsMessageRouter router;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private AtomicBoolean failAudit;

    @Autowired
    private MockMvc mockMvc;

    private UUID tenantId;
    private UUID buyerAdminUserId;
    private UUID buyerAdminEntityId;
    private UUID dealId;
    private UUID evidenceId;
    private UUID jobId;
    private UUID fulfillmentId;
    private UUID milestoneId;

    @BeforeEach
    void setUp() {
        failAudit.set(false);
        jdbc.execute("""
                TRUNCATE TABLE
                    dispute_comment,
                    dispute_evidence_snapshot,
                    dispute_case,
                    fulfillment_video_analysis_result,
                    fulfillment_video_analysis_job,
                    fulfillment_evidence_submission,
                    fulfillment_milestone_rule_reference,
                    fulfillment_milestone,
                    fulfillment,
                    release_dispatch,
                    release_operation,
                    settlement,
                    payment_dispatch,
                    payment_operation,
                    funding_unit,
                    funding_plan,
                    contract_intelligence_rule_set_version,
                    contract_intelligence_extraction_result_version,
                    contract_intelligence_analysis_job,
                    http_idempotency_record,
                    integration_outbox_event,
                    integration_inbox_event,
                    deal_invitation,
                    deal_participant,
                    document,
                    ratification_package_approval,
                    ratification_package,
                    ratification_package_snapshot,
                    deal,
                    audit_record,
                    legal_entity_membership,
                    legal_entity,
                    tenant_user,
                    tenant,
                    identity_user
                """);

        tenantId = UUID.randomUUID();
        buyerAdminUserId = UUID.randomUUID();
        buyerAdminEntityId = UUID.randomUUID();
        dealId = UUID.randomUUID();
        evidenceId = UUID.randomUUID();
        jobId = UUID.randomUUID();
        fulfillmentId = UUID.randomUUID();
        milestoneId = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO identity_user (id, email, password_hash, display_name, enabled)
                VALUES (?, ?, 'test-hash', 'Test User', true)
                """, buyerAdminUserId, "buyer-admin@example.test");
        jdbc.update("INSERT INTO tenant (id) VALUES (?)", tenantId);
        jdbc.update("INSERT INTO tenant_user (user_id, tenant_id) VALUES (?, ?)", buyerAdminUserId, tenantId);
        jdbc.update("""
                INSERT INTO legal_entity (id, tenant_id, legal_name, registration_number)
                VALUES (?, ?, 'Buyer Co', ?)
                """, buyerAdminEntityId, tenantId, "REG-" + buyerAdminEntityId);
        jdbc.update("""
                INSERT INTO legal_entity_membership (id, tenant_id, legal_entity_id, user_id, role)
                VALUES (?, ?, ?, ?, 'ADMIN')
                """, UUID.randomUUID(), tenantId, buyerAdminEntityId, buyerAdminUserId);

        UUID packageId = UUID.randomUUID();
        seedDeal(dealId, buyerAdminEntityId, UUID.randomUUID(), packageId, "DL-0000000001");
        seedFunding(dealId, packageId);
        jdbc.update("""
                INSERT INTO fulfillment (id, deal_id, tenant_id, source_package_id, status, evidence_policy, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'REVIEW_REQUIRED', 'REQUIRED', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, fulfillmentId, dealId, tenantId, packageId);
        jdbc.update("""
                INSERT INTO fulfillment_milestone (id, fulfillment_id, deal_id, title, status, version, created_at, updated_at)
                VALUES (?, ?, ?, 'Primary', 'REVIEW_REQUIRED', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, milestoneId, fulfillmentId, dealId);
        jdbc.update("""
                INSERT INTO fulfillment_evidence_submission (
                    id, deal_id, milestone_id, fulfillment_id, evidence_type, media_type, file_name,
                    status, object_key, object_version, client_size_bytes, client_sha256, verified_size_bytes,
                    verified_sha256, upload_expires_at, created_at, submitted_at, version
                ) VALUES (?, ?, ?, ?, 'VIDEO', 'video/mp4', 'delivery.mp4', 'SUBMITTED', 'obj-key', 'version-1',
                    2048, ?, 2048, ?, CURRENT_TIMESTAMP + INTERVAL '1 hour', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1)
                """, evidenceId, dealId, milestoneId, fulfillmentId, SHA, SHA);
        insertQueuedJob(jobId);
    }

    @Test
    void completedIsImmutableIdempotentAndPublicProjectionIsSafe() throws Exception {
        UUID eventId = UUID.randomUUID();
        router.consume(json(completed(eventId)));
        router.consume(json(completed(eventId)));
        assertEquals("RESULT_AVAILABLE", jobStatus());
        assertEquals(1, count("integration_inbox_event"));
        assertEquals(1, count("fulfillment_video_analysis_result"));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM audit_record WHERE action = 'AI_VIDEO_ANALYSIS_COMPLETED'
                """, Integer.class));

        String storedCanonical = jdbc.queryForObject("""
                SELECT canonical_result::text FROM fulfillment_video_analysis_result WHERE job_id = ?
                """, String.class, jobId);
        var canonical = mapper.readTree(storedCanonical);
        assertTrue(canonical.has("technicalMetadata"));
        assertEquals("video-pipeline-1.0.0",
                canonical.path("technicalMetadata").path("pipelineVersion").asText());
        assertTrue(canonical.has("result"));
        assertTrue(canonical.path("warnings").isArray());

        mockMvc.perform(get(videoAnalysisPath())
                        .with(user(buyerAdminUserId.toString()))
                        .header(LEGAL_ENTITY_HEADER, buyerAdminEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESULT_AVAILABLE"))
                .andExpect(jsonPath("$.result.durationMs").value(92000))
                .andExpect(jsonPath("$.result.summary.advisoryOutcome").value("NO_ISSUE_DETECTED"))
                .andExpect(jsonPath("$.result.warnings").isArray())
                .andExpect(jsonPath("$.result.technicalMetadata").doesNotExist());
    }

    @Test
    void failedAndAuditFailureAreAtomic() throws Exception {
        router.consume(json(failed(UUID.randomUUID())));
        assertEquals("FAILED", jobStatus());
        assertEquals("OBJECT_STORAGE_TEMPORARILY_UNAVAILABLE", jdbc.queryForObject(
                "SELECT failure_code FROM fulfillment_video_analysis_job WHERE id = ?", String.class, jobId));

        UUID nextJob = UUID.randomUUID();
        insertQueuedJob(nextJob);
        failAudit.set(true);
        assertThrows(IllegalStateException.class, () -> router.consume(json(completed(UUID.randomUUID(), nextJob))));
        assertEquals("QUEUED", jdbc.queryForObject(
                "SELECT status FROM fulfillment_video_analysis_job WHERE id = ?", String.class, nextJob));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM fulfillment_video_analysis_result WHERE job_id = ?", Integer.class, nextJob));
    }

    @Test
    void contractIdentityImmutableInputAndSemanticViolationsRollBackInbox() throws Exception {
        assertIntegrationViolationIsAtomic(completedWithField("schemaVersion", "9.9.9"));

        for (String identity : List.of("tenantId", "transactionId", "subjectId", "jobId")) {
            assertIntegrationViolationIsAtomic(completedWithField(identity, UUID.randomUUID().toString()));
        }

        jdbc.update("UPDATE fulfillment_evidence_submission SET verified_sha256 = ? WHERE id = ?",
                "b".repeat(64), evidenceId);
        assertIntegrationViolationIsAtomic(completed(UUID.randomUUID()));

        jdbc.update("UPDATE fulfillment_evidence_submission SET verified_sha256 = ? WHERE id = ?", SHA, evidenceId);
        Map<String, Object> invalidRange = completed(UUID.randomUUID());
        observation(invalidRange).put("timeRange", Map.of("startMs", 4000, "endMs", 4000));
        assertIntegrationViolationIsAtomic(invalidRange);

        Map<String, Object> outOfDuration = completed(UUID.randomUUID());
        observation(outOfDuration).put("timeRange", Map.of("startMs", 4000, "endMs", 93000));
        assertIntegrationViolationIsAtomic(outOfDuration);
    }

    @Test
    void terminalDuplicateAndLateEventsNeverMutateBusinessState() throws Exception {
        router.consume(json(completed(UUID.randomUUID())));
        router.consume(json(failed(UUID.randomUUID())));
        assertEquals("RESULT_AVAILABLE", jobStatus());
        assertEquals(1, count("fulfillment_video_analysis_result"));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM audit_record WHERE action = 'AI_ANALYSIS_TERMINAL_EVENT_IGNORED'
                """, Integer.class));

        jdbc.update("UPDATE fulfillment_evidence_submission SET status = 'ACCEPTED', accepted_at = CURRENT_TIMESTAMP"
                + " WHERE id = ?", evidenceId);
        String fulfillmentStatusBefore = jdbc.queryForObject(
                "SELECT status FROM fulfillment WHERE id = ?", String.class, fulfillmentId);
        router.consume(json(completed(UUID.randomUUID())));
        assertEquals("RESULT_AVAILABLE", jobStatus());
        assertEquals(fulfillmentStatusBefore, jdbc.queryForObject(
                "SELECT status FROM fulfillment WHERE id = ?", String.class, fulfillmentId));
        assertEquals(1, count("fulfillment_video_analysis_result"));
    }

    private void assertIntegrationViolationIsAtomic(Map<String, Object> event) throws Exception {
        int inboxBefore = count("integration_inbox_event");
        int auditBefore = count("audit_record");
        assertThrows(IntegrationViolation.class, () -> router.consume(json(event)));
        assertEquals("QUEUED", jobStatus());
        assertEquals(inboxBefore, count("integration_inbox_event"));
        assertEquals(auditBefore, count("audit_record"));
        assertEquals(0, count("fulfillment_video_analysis_result"));
    }

    private Map<String, Object> completed(UUID eventId) {
        return completed(eventId, jobId);
    }

    private Map<String, Object> completed(UUID eventId, UUID targetJobId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("durationMs", 92000);
        result.put("observations", List.of(new LinkedHashMap<>(Map.of(
                "observationReference", "observation-1",
                "type", "OBJECT_COUNT",
                "label", "sealed_box",
                "observedValue", 4,
                "confidence", 0.93,
                "timeRange", Map.of("startMs", 4000, "endMs", 24000)))));
        result.put("anomalies", List.of());
        result.put("summary", Map.of("advisoryOutcome", "NO_ISSUE_DETECTED", "reviewReasons", List.of()));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("result", result);
        payload.put("technicalMetadata", completedTechnicalMetadata());
        payload.put("warnings", List.of());
        return envelope(eventId, targetJobId, "ai.job.completed.v1", payload);
    }

    private Map<String, Object> completedTechnicalMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("pipelineVersion", "video-pipeline-1.0.0");
        metadata.put("modelProvider", "internal-model-service");
        metadata.put("modelFamily", "delivery-evidence");
        metadata.put("modelVersion", "2026-07");
        metadata.put("promptVersion", null);
        metadata.put("retrievalVersion", null);
        metadata.put("parserVersion", "video-parser-1.0.0");
        metadata.put("privacyVersion", "default-1");
        metadata.put("durationMs", 11400);
        return metadata;
    }

    private Map<String, Object> failed(UUID eventId) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("category", "RETRYABLE_TECHNICAL");
        error.put("code", "OBJECT_STORAGE_TEMPORARILY_UNAVAILABLE");
        error.put("message", "The source media could not be downloaded temporarily.");
        error.put("retryRecommended", true);
        error.put("details", Map.of("dependency", "object-storage", "reason", "temporary unavailability",
                "retryAfterMs", 3000));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error", error);
        payload.put("attempt", Map.of("attemptNumber", 2, "maxAttempts", 3));
        payload.put("technicalMetadata", failedTechnicalMetadata());
        return envelope(eventId, jobId, "ai.job.failed.v1", payload);
    }

    private Map<String, Object> failedTechnicalMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("pipelineVersion", "video-pipeline-1.0.0");
        metadata.put("modelProvider", null);
        metadata.put("modelFamily", null);
        metadata.put("modelVersion", null);
        metadata.put("promptVersion", null);
        metadata.put("retrievalVersion", null);
        metadata.put("parserVersion", "video-parser-1.0.0");
        metadata.put("privacyVersion", "default-1");
        metadata.put("durationMs", 3000);
        return metadata;
    }

    private Map<String, Object> completedWithField(String field, Object value) throws Exception {
        Map<String, Object> event = completed(UUID.randomUUID());
        event.put(field, value);
        return event;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> observation(Map<String, Object> event) {
        Map<String, Object> payload = (Map<String, Object>) event.get("payload");
        Map<String, Object> result = (Map<String, Object>) payload.get("result");
        return (Map<String, Object>) ((List<Object>) result.get("observations")).getFirst();
    }

    private Map<String, Object> envelope(UUID eventId, UUID targetJobId, String eventType, Object payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", eventId.toString());
        event.put("eventType", eventType);
        event.put("schemaVersion", "1.0.0");
        event.put("occurredAt", "2026-07-19T00:00:00Z");
        event.put("correlationId", UUID.randomUUID().toString());
        event.put("causationId", null);
        event.put("jobId", targetJobId.toString());
        event.put("jobType", "VIDEO_ANALYSIS");
        event.put("tenantId", tenantId.toString());
        event.put("transactionId", dealId.toString());
        event.put("subjectId", evidenceId.toString());
        event.put("idempotencyKey", "video-key");
        event.put("producer", Map.of("service", "m4trust-ai-worker", "version", "1.0.0"));
        event.put("payload", payload);
        return event;
    }

    private void insertQueuedJob(UUID id) {
        jdbc.update("""
                INSERT INTO fulfillment_video_analysis_job (
                    id, tenant_id, deal_id, fulfillment_id, milestone_id, evidence_submission_id,
                    object_version, input_sha256, input_size_bytes, input_media_type, input_file_name,
                    status, requested_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, 'version-1', ?, 2048, 'video/mp4', 'delivery.mp4',
                    'QUEUED', CURRENT_TIMESTAMP, 0)
                """, id, tenantId, dealId, fulfillmentId, milestoneId, evidenceId, SHA);
    }

    private String jobStatus() {
        return jdbc.queryForObject("SELECT status FROM fulfillment_video_analysis_job WHERE id = ?",
                String.class, jobId);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private String videoAnalysisPath() {
        return "/api/v1/deals/" + dealId + "/fulfillment/evidence/" + evidenceId + "/video-analysis";
    }

    private String json(Map<String, Object> value) throws Exception {
        return mapper.writeValueAsString(value);
    }

    private void seedDeal(UUID id, UUID buyerEntityId, UUID sellerEntityId, UUID packageId, String reference) {
        jdbc.update("""
                INSERT INTO deal (id, tenant_id, reference, title, deal_status, initiator_legal_entity_id,
                    created_by, version)
                VALUES (?, ?, ?, 'Deal', 'ACTIVE', ?, ?, 3)
                """, id, tenantId, reference, buyerEntityId, buyerAdminUserId);
        jdbc.update("""
                INSERT INTO identity_user (id, email, password_hash, display_name, enabled)
                VALUES (?, ?, 'test-hash', 'Seller', true)
                """, sellerEntityId, sellerEntityId + "@example.test");
        jdbc.update("""
                INSERT INTO legal_entity (id, tenant_id, legal_name, registration_number)
                VALUES (?, ?, 'Seller Co', ?)
                """, sellerEntityId, tenantId, "REG-" + sellerEntityId);
        for (UUID entityId : List.of(buyerEntityId, sellerEntityId)) {
            jdbc.update("""
                    INSERT INTO deal_participant (deal_id, tenant_id, legal_entity_id, legal_entity_tenant_id)
                    VALUES (?, ?, ?, ?)
                    """, id, tenantId, entityId, tenantId);
        }
        RatificationPackageSeedSupport.SeededSnapshot snapshot = RatificationPackageSeedSupport.seedSnapshot(
                mapper, id, tenantId, buyerEntityId, sellerEntityId, packageId, reference, SHA);
        jdbc.update("""
                INSERT INTO ratification_package_snapshot (id, schema_version, canonical_snapshot, content_hash, created_at)
                VALUES (?, 1, CAST(? AS jsonb), ?, CURRENT_TIMESTAMP)
                """, snapshot.snapshotId(), snapshot.serializedSnapshot(), snapshot.contentHash());
        jdbc.update("""
                INSERT INTO ratification_package (id, deal_id, snapshot_id, version, status,
                    buyer_legal_entity_id, seller_legal_entity_id, amount_minor, currency, created_at)
                VALUES (?, ?, ?, 1, 'RATIFIED', ?, ?, 5000, 'TRY', CURRENT_TIMESTAMP)
                """, packageId, id, snapshot.snapshotId(), buyerEntityId, sellerEntityId);
        jdbc.update("""
                UPDATE deal
                SET buyer_legal_entity_id = ?, seller_legal_entity_id = ?, current_ratification_package_id = ?
                WHERE id = ?
                """, buyerEntityId, sellerEntityId, packageId, id);
    }

    private void seedFunding(UUID dealId, UUID packageId) {
        UUID planId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO funding_plan (id, deal_id, ratification_package_id, tenant_id,
                    amount_minor, currency, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'TRY', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, planId, dealId, packageId, tenantId, 5000L);
        jdbc.update("""
                INSERT INTO funding_unit (id, funding_plan_id, sequence_no, amount_minor,
                    currency, status, version, created_at, updated_at)
                VALUES (?, ?, 1, ?, 'TRY', 'FUNDED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), planId, 5000L);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Fakes {
        @Bean
        AtomicBoolean failAudit() {
            return new AtomicBoolean();
        }

        @Bean
        @Primary
        AuditAppendPort auditAppendPort(JdbcTemplate jdbcTemplate, AtomicBoolean failAudit) {
            return record -> {
                if (failAudit.get()) {
                    throw new IllegalStateException("forced audit failure");
                }
                jdbcTemplate.update("""
                        INSERT INTO audit_record (id, tenant_id, actor_user_id, legal_entity_id,
                            subject_type, subject_id, action, correlation_id, causation_id, occurred_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, record.id(), record.tenantId(), record.actorUserId(), record.legalEntityId(),
                        record.subjectType(), record.subjectId(), record.action(), record.correlationId(),
                        record.causationId(), Timestamp.from(record.occurredAt()));
            };
        }
    }
}
