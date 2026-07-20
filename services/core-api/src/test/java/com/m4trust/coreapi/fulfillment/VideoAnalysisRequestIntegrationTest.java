package com.m4trust.coreapi.fulfillment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.m4trust.coreapi.audit.AuditAppendPort;
import com.m4trust.coreapi.audit.AuditRecord;
import com.m4trust.coreapi.organization.LegalEntityRole;
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;
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
@Import(VideoAnalysisRequestIntegrationTest.Fakes.class)
class VideoAnalysisRequestIntegrationTest {

    private static final String LEGAL_ENTITY_HEADER = "X-M4Trust-Legal-Entity-Id";
    private static final String SHA = "a".repeat(64);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.5-alpine");

    @Autowired
    private VideoAnalysisService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RecordingStorage storage;

    @Autowired
    private AtomicBoolean failAudit;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID tenantId;
    private UUID buyerAdminUserId;
    private UUID buyerAdminEntityId;
    private UUID buyerMemberUserId;
    private UUID buyerMemberEntityId;
    private UUID sellerAdminUserId;
    private UUID sellerAdminEntityId;
    private UUID dealId;
    private UUID otherDealId;
    private UUID evidenceId;
    private long evidenceVersion;

    @BeforeEach
    void setUp() {
        storage.reset();
        failAudit.set(false);
        jdbc.execute("""
                TRUNCATE TABLE
                    fulfillment_video_analysis_result,
                    fulfillment_video_analysis_job,
                    fulfillment_evidence_submission,
                    fulfillment_milestone_rule_reference,
                    fulfillment_milestone,
                    fulfillment,
                    payment_dispatch,
                    payment_operation,
                    funding_unit,
                    funding_plan,
                    contract_intelligence_rule_set_version,
                    contract_intelligence_extraction_result_version,
                    contract_intelligence_analysis_job,
                    http_idempotency_record,
                    integration_outbox_event,
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
        buyerMemberUserId = UUID.randomUUID();
        buyerMemberEntityId = buyerAdminEntityId;
        sellerAdminUserId = UUID.randomUUID();
        sellerAdminEntityId = UUID.randomUUID();
        dealId = UUID.randomUUID();
        otherDealId = UUID.randomUUID();
        evidenceId = UUID.randomUUID();

        insertUser(buyerAdminUserId, "buyer-admin@example.test");
        insertUser(buyerMemberUserId, "buyer-member@example.test");
        insertUser(sellerAdminUserId, "seller-admin@example.test");
        jdbc.update("INSERT INTO tenant (id) VALUES (?)", tenantId);
        jdbc.update("INSERT INTO tenant_user (user_id, tenant_id) VALUES (?, ?)", buyerAdminUserId, tenantId);
        jdbc.update("INSERT INTO tenant_user (user_id, tenant_id) VALUES (?, ?)", buyerMemberUserId, tenantId);
        jdbc.update("INSERT INTO tenant_user (user_id, tenant_id) VALUES (?, ?)", sellerAdminUserId, tenantId);

        insertEntity(buyerAdminEntityId, "Buyer Co");
        insertEntity(sellerAdminEntityId, "Seller Co");
        insertMembership(buyerAdminUserId, buyerAdminEntityId, "ADMIN");
        insertMembership(buyerMemberUserId, buyerMemberEntityId, "MEMBER");
        insertMembership(sellerAdminUserId, sellerAdminEntityId, "ADMIN");

        UUID packageId = UUID.randomUUID();
        UUID otherPackageId = UUID.randomUUID();
        seedDeal(dealId, buyerAdminEntityId, sellerAdminEntityId, packageId, "DL-0000000001");
        seedDeal(otherDealId, buyerAdminEntityId, sellerAdminEntityId, otherPackageId, "DL-0000000002");
        seedFunding(dealId, packageId);
        seedFunding(otherDealId, otherPackageId);

        UUID fulfillmentId = UUID.randomUUID();
        UUID milestoneId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO fulfillment (id, deal_id, tenant_id, source_package_id, status, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'REVIEW_REQUIRED', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
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
        evidenceVersion = 1L;
    }

    @Test
    void requestIsAtomicIdempotentAndPresignsOutsideTheDatabaseTransaction() {
        UUID idempotencyKey = UUID.randomUUID();
        VideoAnalysisDetail accepted = service.request(requestContext(), dealId, evidenceId,
                new RequestVideoAnalysisRequest(evidenceVersion), idempotencyKey, UUID.randomUUID());
        VideoAnalysisDetail replay = service.request(requestContext(), dealId, evidenceId,
                new RequestVideoAnalysisRequest(evidenceVersion), idempotencyKey, UUID.randomUUID());

        assertEquals(VideoAnalysisPublicStatus.QUEUED, accepted.status());
        assertEquals(accepted, replay);
        assertEquals(1, count("fulfillment_video_analysis_job"));
        assertEquals(1, count("integration_outbox_event"));
        assertEquals(1, count("audit_record"));
        assertEquals(1, count("http_idempotency_record"));
        assertEquals(1, storage.downloadCalls.get());
        assertFalse(storage.calledInsideTransaction.get());
        assertThrows(FulfillmentExceptions.Conflict.class,
                () -> service.request(requestContext(), dealId, evidenceId,
                        new RequestVideoAnalysisRequest(evidenceVersion), UUID.randomUUID(),
                        UUID.randomUUID()));
        assertEquals(1, storage.downloadCalls.get());
        assertEquals("VIDEO_ANALYSIS", jdbc.queryForObject("""
                SELECT payload->>'jobType' FROM integration_outbox_event
                """, String.class));
        assertEquals(tenantId, jdbc.queryForObject("SELECT tenant_id FROM audit_record LIMIT 1", UUID.class));
        assertEquals(tenantId, jdbc.queryForObject(
                "SELECT tenant_id FROM fulfillment_video_analysis_job LIMIT 1", UUID.class));
    }

    @Test
    void auditFailureRollsBackJobOutboxAndIdempotencyResultTogether() {
        failAudit.set(true);
        assertThrows(IllegalStateException.class, () -> service.request(requestContext(), dealId, evidenceId,
                new RequestVideoAnalysisRequest(evidenceVersion), UUID.randomUUID(), UUID.randomUUID()));
        assertEquals(0, count("fulfillment_video_analysis_job"));
        assertEquals(0, count("integration_outbox_event"));
        assertEquals(0, count("audit_record"));
        assertEquals(0, count("http_idempotency_record"));
    }

    @Test
    void participantCanReadButCannotRequest() throws Exception {
        mockMvc.perform(get(videoAnalysisPath())
                        .with(user(sellerAdminUserId.toString()))
                        .header(LEGAL_ENTITY_HEADER, sellerAdminEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_REQUESTED"))
                .andExpect(jsonPath("$.availableActions.canRequest").value(false));

        assertThrows(FulfillmentExceptions.RequestForbidden.class,
                () -> service.request(context(sellerAdminUserId, sellerAdminEntityId, LegalEntityRole.ADMIN,
                                RequestedOperation.VIDEO_ANALYSIS_REQUEST),
                        dealId, evidenceId, new RequestVideoAnalysisRequest(evidenceVersion),
                        UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void httpRequestAcceptsTheJobAndReturnsTheCommittedLocationAndBody() throws Exception {
        mockMvc.perform(post(videoAnalysisPath())
                        .with(user(buyerAdminUserId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdminEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedEvidenceVersion\": " + evidenceVersion + "}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", videoAnalysisPath()))
                .andExpect(jsonPath("$.evidenceSubmissionId").value(evidenceId.toString()))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.availableActions.canRequest").value(false));
    }

    @Test
    void httpRequestMapsForbiddenStaleVersionAndActiveJobConflicts() throws Exception {
        mockMvc.perform(post(videoAnalysisPath())
                        .with(user(buyerMemberUserId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerMemberEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedEvidenceVersion\": " + evidenceVersion + "}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VIDEO_ANALYSIS_REQUEST_FORBIDDEN"));

        mockMvc.perform(post(videoAnalysisPath())
                        .with(user(buyerAdminUserId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdminEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedEvidenceVersion\": " + (evidenceVersion + 1) + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVIDENCE_STALE_VERSION"));

        acceptHttpRequest();
        mockMvc.perform(post(videoAnalysisPath())
                        .with(user(buyerAdminUserId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdminEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedEvidenceVersion\": " + evidenceVersion + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VIDEO_ANALYSIS_ACTIVE_JOB_EXISTS"));
    }

    @Test
    void pendingVideoMp4EvidenceReturnsNonDisclosing404() throws Exception {
        UUID pendingVideoId = UUID.randomUUID();
        UUID milestoneId = jdbc.queryForObject(
                "SELECT milestone_id FROM fulfillment_evidence_submission WHERE id = ?", UUID.class, evidenceId);
        UUID fulfillmentId = jdbc.queryForObject(
                "SELECT fulfillment_id FROM fulfillment_evidence_submission WHERE id = ?", UUID.class, evidenceId);
        jdbc.update("""
                INSERT INTO fulfillment_evidence_submission (
                    id, deal_id, milestone_id, fulfillment_id, evidence_type, media_type, file_name,
                    status, object_key, client_size_bytes, client_sha256, upload_expires_at, created_at, version
                ) VALUES (?, ?, ?, ?, 'VIDEO', 'video/mp4', 'pending-delivery.mp4', 'PENDING_UPLOAD', 'obj-key-pending',
                    2048, ?, CURRENT_TIMESTAMP + INTERVAL '1 hour', CURRENT_TIMESTAMP, 2)
                """, pendingVideoId, dealId, milestoneId, fulfillmentId, SHA);

        mockMvc.perform(get(videoAnalysisPath(pendingVideoId))
                        .with(user(buyerAdminUserId.toString()))
                        .header(LEGAL_ENTITY_HEADER, buyerAdminEntityId))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(videoAnalysisPath(pendingVideoId))
                        .with(user(buyerAdminUserId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdminEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedEvidenceVersion\": 2}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void finalizedNonVideoEvidenceReturnsNonDisclosing404() throws Exception {
        UUID pdfEvidenceId = UUID.randomUUID();
        UUID milestoneId = jdbc.queryForObject(
                "SELECT milestone_id FROM fulfillment_evidence_submission WHERE id = ?", UUID.class, evidenceId);
        UUID fulfillmentId = jdbc.queryForObject(
                "SELECT fulfillment_id FROM fulfillment_evidence_submission WHERE id = ?", UUID.class, evidenceId);
        jdbc.update("""
                INSERT INTO fulfillment_evidence_submission (
                    id, deal_id, milestone_id, fulfillment_id, evidence_type, media_type, file_name,
                    status, object_key, object_version, client_size_bytes, client_sha256, verified_size_bytes,
                    verified_sha256, upload_expires_at, created_at, submitted_at, accepted_at, version
                ) VALUES (?, ?, ?, ?, 'DELIVERY_NOTE', 'application/pdf', 'note.pdf', 'ACCEPTED', 'obj-key-pdf',
                    'version-1', 2048, ?, 2048, ?, CURRENT_TIMESTAMP + INTERVAL '1 hour', CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 2)
                """, pdfEvidenceId, dealId, milestoneId, fulfillmentId, SHA, SHA);

        mockMvc.perform(get(videoAnalysisPath(pdfEvidenceId))
                        .with(user(buyerAdminUserId.toString()))
                        .header(LEGAL_ENTITY_HEADER, buyerAdminEntityId))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(videoAnalysisPath(pdfEvidenceId))
                        .with(user(buyerAdminUserId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdminEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedEvidenceVersion\": 2}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void supersededFinalizedVideoMp4RemainsReadableButNotRequestable() throws Exception {
        UUID supersededVideoId = UUID.randomUUID();
        UUID milestoneId = jdbc.queryForObject(
                "SELECT milestone_id FROM fulfillment_evidence_submission WHERE id = ?", UUID.class, evidenceId);
        UUID fulfillmentId = jdbc.queryForObject(
                "SELECT fulfillment_id FROM fulfillment_evidence_submission WHERE id = ?", UUID.class, evidenceId);
        jdbc.update("""
                INSERT INTO fulfillment_evidence_submission (
                    id, deal_id, milestone_id, fulfillment_id, evidence_type, media_type, file_name,
                    status, object_key, object_version, client_size_bytes, client_sha256, verified_size_bytes,
                    verified_sha256, upload_expires_at, created_at, submitted_at, rejected_at, rejection_reason, version
                ) VALUES (?, ?, ?, ?, 'VIDEO', 'video/mp4', 'old-delivery.mp4', 'REJECTED', 'obj-key-old', 'version-0',
                    2048, ?, 2048, ?, CURRENT_TIMESTAMP + INTERVAL '1 hour',
                    CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP - INTERVAL '1 day',
                    CURRENT_TIMESTAMP - INTERVAL '1 hour', 'superseded', 0)
                """, supersededVideoId, dealId, milestoneId, fulfillmentId, SHA, SHA);

        mockMvc.perform(get(videoAnalysisPath(supersededVideoId))
                        .with(user(sellerAdminUserId.toString()))
                        .header(LEGAL_ENTITY_HEADER, sellerAdminEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceSubmissionId").value(supersededVideoId.toString()))
                .andExpect(jsonPath("$.status").value("NOT_REQUESTED"))
                .andExpect(jsonPath("$.availableActions.canRequest").value(false));

        mockMvc.perform(post(videoAnalysisPath(supersededVideoId))
                        .with(user(buyerAdminUserId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdminEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedEvidenceVersion\": 0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VIDEO_ANALYSIS_EVIDENCE_NOT_ELIGIBLE"));
    }

    @Test
    void nonVideoEvidenceAndCrossDealEvidenceRemainHidden() throws Exception {
        UUID pdfEvidenceId = UUID.randomUUID();
        UUID milestoneId = jdbc.queryForObject(
                "SELECT milestone_id FROM fulfillment_evidence_submission WHERE id = ?", UUID.class, evidenceId);
        UUID fulfillmentId = jdbc.queryForObject(
                "SELECT fulfillment_id FROM fulfillment_evidence_submission WHERE id = ?", UUID.class, evidenceId);
        jdbc.update("""
                INSERT INTO fulfillment_evidence_submission (
                    id, deal_id, milestone_id, fulfillment_id, evidence_type, media_type, file_name,
                    status, object_key, client_size_bytes, client_sha256, upload_expires_at, created_at, version
                ) VALUES (?, ?, ?, ?, 'DELIVERY_NOTE', 'application/pdf', 'note.pdf', 'PENDING_UPLOAD', 'obj-key',
                    2048, ?, CURRENT_TIMESTAMP + INTERVAL '1 hour', CURRENT_TIMESTAMP, 2)
                """, pdfEvidenceId, dealId, milestoneId, fulfillmentId, SHA);

        mockMvc.perform(get("/api/v1/deals/" + dealId + "/fulfillment/evidence/" + pdfEvidenceId
                        + "/video-analysis")
                        .with(user(buyerAdminUserId.toString()))
                        .header(LEGAL_ENTITY_HEADER, buyerAdminEntityId))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/deals/" + otherDealId + "/fulfillment/evidence/" + evidenceId
                        + "/video-analysis")
                        .with(user(buyerAdminUserId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdminEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedEvidenceVersion\": " + evidenceVersion + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void crossTenantBuyerAdminRequestUsesActorTenantForAuditAndDealTenantForJob() {
        UUID hostTenantId = tenantId;
        UUID buyerHomeTenantId = UUID.randomUUID();
        UUID crossBuyerUserId = UUID.randomUUID();
        UUID crossBuyerEntityId = UUID.randomUUID();
        jdbc.update("INSERT INTO tenant (id) VALUES (?)", buyerHomeTenantId);
        insertUser(crossBuyerUserId, "cross-buyer-admin@example.test");
        jdbc.update("INSERT INTO tenant_user (user_id, tenant_id) VALUES (?, ?)", crossBuyerUserId,
                buyerHomeTenantId);
        jdbc.update("""
                INSERT INTO legal_entity (id, tenant_id, legal_name, registration_number)
                VALUES (?, ?, 'Cross Buyer Co', ?)
                """, crossBuyerEntityId, buyerHomeTenantId, "REG-" + crossBuyerEntityId);
        jdbc.update("""
                INSERT INTO legal_entity_membership (id, tenant_id, legal_entity_id, user_id, role)
                VALUES (?, ?, ?, ?, 'ADMIN')
                """, UUID.randomUUID(), buyerHomeTenantId, crossBuyerEntityId, crossBuyerUserId);
        jdbc.update("""
                INSERT INTO deal_participant (deal_id, tenant_id, legal_entity_id, legal_entity_tenant_id)
                VALUES (?, ?, ?, ?)
                """, dealId, hostTenantId, crossBuyerEntityId, buyerHomeTenantId);
        jdbc.update("UPDATE deal SET buyer_legal_entity_id = ? WHERE id = ?",
                crossBuyerEntityId, dealId);

        service.request(context(crossBuyerUserId, buyerHomeTenantId, crossBuyerEntityId,
                        LegalEntityRole.ADMIN, RequestedOperation.VIDEO_ANALYSIS_REQUEST),
                dealId, evidenceId, new RequestVideoAnalysisRequest(evidenceVersion), UUID.randomUUID(),
                UUID.randomUUID());

        assertEquals(buyerHomeTenantId, jdbc.queryForObject("SELECT tenant_id FROM audit_record LIMIT 1",
                UUID.class));
        assertEquals(hostTenantId, jdbc.queryForObject(
                "SELECT tenant_id FROM fulfillment_video_analysis_job LIMIT 1", UUID.class));
        assertEquals(hostTenantId.toString(), jdbc.queryForObject(
                "SELECT payload->>'tenantId' FROM integration_outbox_event LIMIT 1", String.class));
    }

    @Test
    void idempotencyKeyReuseWithDifferentCanonicalRequestIsRejected() throws Exception {
        UUID key = UUID.randomUUID();
        acceptHttpRequestWithKey(key);
        mockMvc.perform(post(videoAnalysisPath())
                        .with(user(buyerAdminUserId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdminEntityId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedEvidenceVersion\": " + (evidenceVersion + 1) + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void completedJobReturnsAlreadyCompletedConflict() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID fulfillmentId = jdbc.queryForObject(
                "SELECT fulfillment_id FROM fulfillment_evidence_submission WHERE id = ?", UUID.class, evidenceId);
        UUID milestoneId = jdbc.queryForObject(
                "SELECT milestone_id FROM fulfillment_evidence_submission WHERE id = ?", UUID.class, evidenceId);
        jdbc.update("""
                INSERT INTO fulfillment_video_analysis_job (
                    id, tenant_id, deal_id, fulfillment_id, milestone_id, evidence_submission_id,
                    object_version, input_sha256, input_size_bytes, input_media_type, input_file_name,
                    status, requested_at, completed_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, 'version-1', ?, 2048, 'video/mp4', 'delivery.mp4',
                    'RESULT_AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, jobId, tenantId, dealId, fulfillmentId, milestoneId, evidenceId, SHA);

        mockMvc.perform(post(videoAnalysisPath())
                        .with(user(buyerAdminUserId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdminEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedEvidenceVersion\": " + evidenceVersion + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VIDEO_ANALYSIS_ALREADY_COMPLETED"));
    }

    @Test
    void nonParticipantCannotReadOrRequest() throws Exception {
        UUID outsiderUserId = UUID.randomUUID();
        UUID outsiderEntityId = UUID.randomUUID();
        insertUser(outsiderUserId, "outsider@example.test");
        jdbc.update("INSERT INTO tenant_user (user_id, tenant_id) VALUES (?, ?)", outsiderUserId, tenantId);
        jdbc.update("""
                INSERT INTO legal_entity (id, tenant_id, legal_name, registration_number)
                VALUES (?, ?, 'Outsider Co', ?)
                """, outsiderEntityId, tenantId, "REG-" + outsiderEntityId);
        jdbc.update("""
                INSERT INTO legal_entity_membership (id, tenant_id, legal_entity_id, user_id, role)
                VALUES (?, ?, ?, ?, 'ADMIN')
                """, UUID.randomUUID(), tenantId, outsiderEntityId, outsiderUserId);

        mockMvc.perform(get(videoAnalysisPath())
                        .with(user(outsiderUserId.toString()))
                        .header(LEGAL_ENTITY_HEADER, outsiderEntityId))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(videoAnalysisPath())
                        .with(user(outsiderUserId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, outsiderEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedEvidenceVersion\": " + evidenceVersion + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void concurrentRequestsCommitExactlyOneQueuedJob() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger acceptedCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        Future<?> first = executor.submit(() -> recordRequestOutcome(start, UUID.randomUUID(),
                acceptedCount, conflictCount));
        Future<?> second = executor.submit(() -> recordRequestOutcome(start, UUID.randomUUID(),
                acceptedCount, conflictCount));
        start.countDown();
        first.get(30, TimeUnit.SECONDS);
        second.get(30, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertEquals(1, count("fulfillment_video_analysis_job"));
        assertEquals(1, acceptedCount.get());
        assertEquals(1, conflictCount.get());
    }

    private void recordRequestOutcome(CountDownLatch start, UUID idempotencyKey,
            AtomicInteger acceptedCount, AtomicInteger conflictCount) {
        try {
            start.await();
            service.request(requestContext(), dealId, evidenceId,
                    new RequestVideoAnalysisRequest(evidenceVersion), idempotencyKey, UUID.randomUUID());
            acceptedCount.incrementAndGet();
        } catch (FulfillmentExceptions.Conflict exception) {
            conflictCount.incrementAndGet();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private void acceptHttpRequestWithKey(UUID key) throws Exception {
        mockMvc.perform(post(videoAnalysisPath())
                        .with(user(buyerAdminUserId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdminEntityId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedEvidenceVersion\": " + evidenceVersion + "}"))
                .andExpect(status().isAccepted());
    }

    private void acceptHttpRequest() throws Exception {
        acceptHttpRequestWithKey(UUID.randomUUID());
    }

    private String videoAnalysisPath() {
        return videoAnalysisPath(evidenceId);
    }

    private String videoAnalysisPath(UUID evidenceSubmissionId) {
        return "/api/v1/deals/" + dealId + "/fulfillment/evidence/" + evidenceSubmissionId + "/video-analysis";
    }

    private OperationContext requestContext() {
        return context(buyerAdminUserId, tenantId, buyerAdminEntityId, LegalEntityRole.ADMIN,
                RequestedOperation.VIDEO_ANALYSIS_REQUEST);
    }

    private OperationContext context(UUID userId, UUID entityId, LegalEntityRole role,
            RequestedOperation operation) {
        return context(userId, tenantId, entityId, role, operation);
    }

    private OperationContext context(UUID userId, UUID actorTenantId, UUID entityId, LegalEntityRole role,
            RequestedOperation operation) {
        return new OperationContext(userId, actorTenantId, entityId, role, operation);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private void insertUser(UUID userId, String email) {
        jdbc.update("""
                INSERT INTO identity_user (id, email, password_hash, display_name, enabled)
                VALUES (?, ?, 'test-hash', 'Test User', true)
                """, userId, email);
    }

    private void insertEntity(UUID entityId, String legalName) {
        jdbc.update("""
                INSERT INTO legal_entity (id, tenant_id, legal_name, registration_number)
                VALUES (?, ?, ?, ?)
                """, entityId, tenantId, legalName, "REG-" + entityId);
    }

    private void insertMembership(UUID userId, UUID entityId, String role) {
        jdbc.update("""
                INSERT INTO legal_entity_membership (id, tenant_id, legal_entity_id, user_id, role)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), tenantId, entityId, userId, role);
    }

    private void seedDeal(UUID id, UUID buyerEntityId, UUID sellerEntityId, UUID packageId, String reference) {
        jdbc.update("""
                INSERT INTO deal (id, tenant_id, reference, title, deal_status, initiator_legal_entity_id,
                    created_by, version)
                VALUES (?, ?, ?, 'Deal', 'ACTIVE', ?, ?, 3)
                """, id, tenantId, reference, buyerEntityId, buyerAdminUserId);
        for (UUID entityId : java.util.List.of(buyerEntityId, sellerEntityId)) {
            jdbc.update("""
                    INSERT INTO deal_participant (deal_id, tenant_id, legal_entity_id, legal_entity_tenant_id)
                    VALUES (?, ?, ?, ?)
                    """, id, tenantId, entityId, tenantId);
        }
        RatificationPackageSeedSupport.SeededSnapshot snapshot = RatificationPackageSeedSupport.seedSnapshot(
                objectMapper, id, tenantId, buyerEntityId, sellerEntityId, packageId, reference, SHA);
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
        RecordingStorage recordingStorage() {
            return new RecordingStorage();
        }

        @Bean
        @Primary
        FulfillmentObjectStorage fulfillmentObjectStorage(RecordingStorage storage) {
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

    static final class RecordingStorage implements FulfillmentObjectStorage {
        private final AtomicBoolean calledInsideTransaction = new AtomicBoolean();
        private final AtomicInteger downloadCalls = new AtomicInteger();

        @Override
        public DirectUpload createDirectUpload(String objectKey, String mediaType, long contentLength) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DirectDownload createDirectDownload(String objectKey, String objectVersion) {
            calledInsideTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
            downloadCalls.incrementAndGet();
            return new DirectDownload(URI.create("https://storage.example/" + objectKey
                    + "?version=" + objectVersion), Instant.parse("2026-07-19T00:15:00Z"));
        }

        @Override
        public VerifiedObject verify(String objectKey) {
            throw new UnsupportedOperationException();
        }

        void reset() {
            calledInsideTransaction.set(false);
            downloadCalls.set(0);
        }
    }
}
