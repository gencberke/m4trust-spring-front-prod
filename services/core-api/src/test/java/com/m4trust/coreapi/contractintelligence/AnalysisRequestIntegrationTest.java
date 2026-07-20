package com.m4trust.coreapi.contractintelligence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.m4trust.coreapi.audit.AuditAppendPort;
import com.m4trust.coreapi.audit.AuditRecord;
import com.m4trust.coreapi.document.DocumentObjectStorage;
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "app.messaging.topology.enabled=false",
        "app.messaging.relay.enabled=false",
        "spring.main.allow-bean-definition-overriding=true"
})
@ActiveProfiles("local")
@Testcontainers
@AutoConfigureMockMvc
@Import(AnalysisRequestIntegrationTest.Fakes.class)
class AnalysisRequestIntegrationTest {

    private static final String SHA = "a".repeat(64);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.5-alpine");

    @Autowired
    private AnalysisService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RecordingStorage storage;

    @Autowired
    private AtomicBoolean failAudit;

    @Autowired
    private MockMvc mockMvc;

    private UUID userId;
    private UUID tenantId;
    private UUID legalEntityId;
    private UUID dealId;
    private UUID documentId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE integration_outbox_event,
                    payment_dispatch, payment_operation, funding_unit, funding_plan,
                    contract_intelligence_rule_set_version,
                    contract_intelligence_extraction_result_version,
                    contract_intelligence_analysis_job,
                    http_idempotency_record, audit_record, deal_invitation, deal_participant, document,
                    ratification_package_approval, ratification_package, ratification_package_snapshot,
                    fulfillment_video_analysis_result, fulfillment_video_analysis_job,
                    fulfillment_evidence_submission, fulfillment_milestone_rule_reference,
                    fulfillment_milestone, fulfillment, deal,
                    legal_entity_membership, legal_entity, tenant_user, tenant, identity_user
                """);
        userId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        legalEntityId = UUID.randomUUID();
        dealId = UUID.randomUUID();
        documentId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO identity_user (id, email, password_hash, display_name, enabled)
                VALUES (?, ?, 'test-hash', 'Analysis User', true)
                """, userId, userId + "@example.test");
        jdbcTemplate.update("INSERT INTO tenant (id) VALUES (?)", tenantId);
        jdbcTemplate.update("INSERT INTO tenant_user (user_id, tenant_id) VALUES (?, ?)",
                userId, tenantId);
        jdbcTemplate.update("""
                INSERT INTO legal_entity (id, tenant_id, legal_name, registration_number)
                VALUES (?, ?, 'Analysis Entity', 'ANALYSIS-ENTITY')
                """, legalEntityId, tenantId);
        jdbcTemplate.update("""
                INSERT INTO legal_entity_membership (id, tenant_id, legal_entity_id, user_id, role)
                VALUES (?, ?, ?, ?, 'ADMIN')
                """, UUID.randomUUID(), tenantId, legalEntityId, userId);
        jdbcTemplate.update("""
                INSERT INTO deal (id, tenant_id, reference, title, deal_status,
                    initiator_legal_entity_id, created_by)
                VALUES (?, ?, 'DL-0000000001', 'Analysis Deal', 'DRAFT', ?, ?)
                """, dealId, tenantId, legalEntityId, userId);
        jdbcTemplate.update("""
                INSERT INTO deal_participant (deal_id, tenant_id, legal_entity_id,
                    legal_entity_tenant_id)
                VALUES (?, ?, ?, ?)
                """, dealId, tenantId, legalEntityId, tenantId);
        jdbcTemplate.update("""
                INSERT INTO document (id, deal_id, file_name, media_type, document_status,
                    object_key, declared_size_bytes, declared_sha256, upload_expires_at,
                    verified_size_bytes, verified_sha256, object_version, created_at,
                    available_at, updated_at)
                VALUES (?, ?, 'contract.pdf', 'application/pdf', 'AVAILABLE', ?, 12, ?,
                    CURRENT_TIMESTAMP + INTERVAL '1 hour', 12, ?, 'immutable-version',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, documentId, dealId, "documents/" + documentId, SHA, SHA);
        jdbcTemplate.update("""
                UPDATE deal SET current_document_id = ?, current_document_status = 'AVAILABLE'
                WHERE id = ?
                """, documentId, dealId);
        storage.reset();
        failAudit.set(false);
    }

    @Test
    void requestIsAtomicIdempotentAndPresignsOutsideTheDatabaseTransaction() {
        UUID idempotencyKey = UUID.randomUUID();
        DealDocumentAnalysis accepted = service.request(requestContext(), dealId,
                idempotencyKey, UUID.randomUUID());
        DealDocumentAnalysis replay = service.request(requestContext(), dealId,
                idempotencyKey, UUID.randomUUID());

        assertEquals(AnalysisJobStatus.QUEUED, accepted.status());
        assertEquals(accepted, replay);
        assertEquals(1, count("contract_intelligence_analysis_job"));
        assertEquals(1, count("integration_outbox_event"));
        assertEquals(1, count("audit_record"));
        assertEquals(1, count("http_idempotency_record"));
        assertEquals(1, storage.aiDownloadCalls.get());
        assertFalse(storage.calledInsideTransaction.get());
        assertThrows(AnalysisExceptions.Conflict.class,
                () -> service.request(requestContext(), dealId, UUID.randomUUID(),
                        UUID.randomUUID()));
        assertEquals(1, storage.aiDownloadCalls.get());
        assertEquals("m4trust-core-api", jdbcTemplate.queryForObject("""
                SELECT payload->'producer'->>'service' FROM integration_outbox_event
                """, String.class));
    }

    @Test
    void auditFailureRollsBackJobOutboxAndIdempotencyResultTogether() {
        failAudit.set(true);

        assertThrows(IllegalStateException.class, () -> service.request(requestContext(), dealId,
                UUID.randomUUID(), UUID.randomUUID()));

        assertEquals(0, count("contract_intelligence_analysis_job"));
        assertEquals(0, count("integration_outbox_event"));
        assertEquals(0, count("audit_record"));
        assertEquals(0, count("http_idempotency_record"));
    }

    @Test
    void participantCanReadButCannotRequestAndUnavailableCurrentDocumentConflicts() {
        UUID participantUserId = UUID.randomUUID();
        UUID participantEntityId = UUID.randomUUID();
        createParticipant(participantUserId, participantEntityId);

        assertEquals(AnalysisJobStatus.NOT_REQUESTED,
                service.get(context(participantUserId, participantEntityId,
                        RequestedOperation.DEAL_DOCUMENT_ANALYSIS_READ), dealId).status());
        assertThrows(AnalysisExceptions.RequestForbidden.class,
                () -> service.request(context(participantUserId, participantEntityId,
                        RequestedOperation.DEAL_DOCUMENT_ANALYSIS_REQUEST), dealId,
                        UUID.randomUUID(), UUID.randomUUID()));
        jdbcTemplate.update("""
                UPDATE deal SET current_document_id = NULL, current_document_status = NULL
                WHERE id = ?
                """, dealId);
        assertThrows(AnalysisExceptions.Conflict.class,
                () -> service.request(requestContext(), dealId, UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void databaseActiveJobConstraintRejectsASecondQueuedJob() {
        insertQueuedJob(UUID.randomUUID());

        assertThrows(DuplicateKeyException.class, () -> insertQueuedJob(UUID.randomUUID()));
    }

    @Test
    void terminalDealsConflictAndNonParticipantsCannotReadTheProjection() {
        jdbcTemplate.update("UPDATE deal SET deal_status = 'CANCELLED' WHERE id = ?", dealId);
        assertThrows(AnalysisExceptions.Conflict.class,
                () -> service.request(requestContext(), dealId, UUID.randomUUID(), UUID.randomUUID()));

        UUID outsiderUserId = UUID.randomUUID();
        UUID outsiderEntityId = UUID.randomUUID();
        createEntity(outsiderUserId, outsiderEntityId, "Outsider Entity", "OUTSIDER-");
        assertThrows(AnalysisExceptions.DealNotFound.class,
                () -> service.get(context(outsiderUserId, outsiderEntityId,
                        RequestedOperation.DEAL_DOCUMENT_ANALYSIS_READ), dealId));
    }

    @Test
    void httpRequestAcceptsTheJobAndReturnsTheCommittedLocationAndBody() throws Exception {
        UUID key = UUID.randomUUID();

        mockMvc.perform(post(analysisPath())
                        .with(user(userId.toString()))
                        .with(csrf())
                        .header(legalEntityHeader(), legalEntityId)
                        .header("Idempotency-Key", key))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", analysisPath()))
                .andExpect(jsonPath("$.currentDocumentId").value(documentId.toString()))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void httpRequestMapsMalformedUnauthorizedAndConflictCasesToStableProblems() throws Exception {
        mockMvc.perform(post(analysisPath())
                        .with(user(userId.toString()))
                        .with(csrf())
                        .header(legalEntityHeader(), legalEntityId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mockMvc.perform(post(analysisPath())
                        .with(user(userId.toString()))
                        .with(csrf())
                        .header(legalEntityHeader(), legalEntityId)
                        .header("Idempotency-Key", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        UUID participantUserId = UUID.randomUUID();
        UUID participantEntityId = UUID.randomUUID();
        createParticipant(participantUserId, participantEntityId);
        mockMvc.perform(post(analysisPath())
                        .with(user(participantUserId.toString()))
                        .with(csrf())
                        .header(legalEntityHeader(), participantEntityId)
                        .header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DEAL_ANALYSIS_REQUEST_FORBIDDEN"));

        acceptHttpRequest();
        mockMvc.perform(post(analysisPath())
                        .with(user(userId.toString()))
                        .with(csrf())
                        .header(legalEntityHeader(), legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEAL_DOCUMENT_ANALYSIS_ACTIVE_JOB_EXISTS"));
    }

    @Test
    void httpRequestMapsNoDocumentAndTerminalDealConflicts() throws Exception {
        jdbcTemplate.update("""
                UPDATE deal SET current_document_id = NULL, current_document_status = NULL
                WHERE id = ?
                """, dealId);
        mockMvc.perform(post(analysisPath())
                        .with(user(userId.toString()))
                        .with(csrf())
                        .header(legalEntityHeader(), legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("DEAL_DOCUMENT_ANALYSIS_DOCUMENT_NOT_AVAILABLE"));

        jdbcTemplate.update("UPDATE deal SET deal_status = 'CANCELLED' WHERE id = ?", dealId);
        mockMvc.perform(post(analysisPath())
                        .with(user(userId.toString()))
                        .with(csrf())
                        .header(legalEntityHeader(), legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEAL_STATE_CONFLICT"));
    }

    @Test
    void httpReadAllowsParticipantsAndHidesNonParticipants() throws Exception {
        UUID participantUserId = UUID.randomUUID();
        UUID participantEntityId = UUID.randomUUID();
        createParticipant(participantUserId, participantEntityId);
        mockMvc.perform(get(analysisPath())
                        .with(user(participantUserId.toString()))
                        .header(legalEntityHeader(), participantEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_REQUESTED"));

        UUID outsiderUserId = UUID.randomUUID();
        UUID outsiderEntityId = UUID.randomUUID();
        createEntity(outsiderUserId, outsiderEntityId, "Outsider Entity", "OUTSIDER-");
        mockMvc.perform(get(analysisPath())
                        .with(user(outsiderUserId.toString()))
                        .header(legalEntityHeader(), outsiderEntityId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEAL_NOT_FOUND"));
    }

    private void insertQueuedJob(UUID jobId) {
        jdbcTemplate.update("""
                INSERT INTO contract_intelligence_analysis_job (
                    id, tenant_id, deal_id, document_id, object_version, input_sha256,
                    status, requested_at, version
                ) VALUES (?, ?, ?, ?, 'immutable-version', ?, 'QUEUED', CURRENT_TIMESTAMP, 0)
                """, jobId, tenantId, dealId, documentId, SHA);
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private void acceptHttpRequest() throws Exception {
        mockMvc.perform(post(analysisPath())
                        .with(user(userId.toString()))
                        .with(csrf())
                        .header(legalEntityHeader(), legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isAccepted());
    }

    private String analysisPath() {
        return "/api/v1/deals/" + dealId + "/document-analysis";
    }

    private static String legalEntityHeader() {
        return "X-M4Trust-Legal-Entity-Id";
    }

    private OperationContext requestContext() {
        return context(userId, legalEntityId, RequestedOperation.DEAL_DOCUMENT_ANALYSIS_REQUEST);
    }

    private OperationContext context(UUID actorUserId, UUID entityId, RequestedOperation operation) {
        return new OperationContext(actorUserId, tenantId, entityId,
                com.m4trust.coreapi.organization.LegalEntityRole.ADMIN, operation);
    }

    private void createParticipant(UUID participantUserId, UUID participantEntityId) {
        createEntity(participantUserId, participantEntityId, "Participant Entity", "PARTICIPANT-");
        jdbcTemplate.update("""
                INSERT INTO deal_participant (deal_id, tenant_id, legal_entity_id,
                    legal_entity_tenant_id)
                VALUES (?, ?, ?, ?)
                """, dealId, tenantId, participantEntityId, tenantId);
    }

    private void createEntity(UUID participantUserId, UUID participantEntityId,
            String legalName, String registrationPrefix) {
        jdbcTemplate.update("""
                INSERT INTO identity_user (id, email, password_hash, display_name, enabled)
                VALUES (?, ?, 'test-hash', 'Participant User', true)
                """, participantUserId, participantUserId + "@example.test");
        jdbcTemplate.update("INSERT INTO tenant_user (user_id, tenant_id) VALUES (?, ?)",
                participantUserId, tenantId);
        jdbcTemplate.update("""
                INSERT INTO legal_entity (id, tenant_id, legal_name, registration_number)
                VALUES (?, ?, ?, ?)
                """, participantEntityId, tenantId, legalName,
                registrationPrefix + participantEntityId);
        jdbcTemplate.update("""
                INSERT INTO legal_entity_membership (id, tenant_id, legal_entity_id, user_id, role)
                VALUES (?, ?, ?, ?, 'ADMIN')
                """, UUID.randomUUID(), tenantId, participantEntityId, participantUserId);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Fakes {
        @Bean
        RecordingStorage recordingStorage() {
            return new RecordingStorage();
        }

        @Bean
        @Primary
        DocumentObjectStorage documentObjectStorage(RecordingStorage storage) {
            return storage;
        }

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
                        """, record.id(), record.tenantId(), record.actorUserId(),
                        record.legalEntityId(), record.subjectType(), record.subjectId(),
                        record.action(), record.correlationId(), record.causationId(),
                        Timestamp.from(record.occurredAt()));
            };
        }
    }

    static final class RecordingStorage implements DocumentObjectStorage {
        private final AtomicBoolean calledInsideTransaction = new AtomicBoolean();
        private final AtomicInteger aiDownloadCalls = new AtomicInteger();

        @Override
        public DirectUpload createDirectUpload(String objectKey, String mediaType,
                long contentLength) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DirectDownload createDirectDownload(String objectKey, String objectVersion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DirectDownload createAiDirectDownload(String objectKey, String objectVersion) {
            calledInsideTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
            aiDownloadCalls.incrementAndGet();
            return new DirectDownload(URI.create("https://storage.example/" + objectKey
                    + "?versionId=" + objectVersion), Instant.parse("2026-07-19T00:15:00Z"));
        }

        @Override
        public VerifiedObject verify(String objectKey) {
            throw new UnsupportedOperationException();
        }

        void reset() {
            calledInsideTransaction.set(false);
            aiDownloadCalls.set(0);
        }
    }
}
