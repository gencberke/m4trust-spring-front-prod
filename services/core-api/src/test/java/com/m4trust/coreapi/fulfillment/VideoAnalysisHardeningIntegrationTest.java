package com.m4trust.coreapi.fulfillment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.jayway.jsonpath.JsonPath;
import com.m4trust.coreapi.integration.messaging.AiResultsMessageRouter;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
@Import(VideoAnalysisHardeningIntegrationTest.Fakes.class)
class VideoAnalysisHardeningIntegrationTest {

    private static final String LEGAL_ENTITY_HEADER = "X-M4Trust-Legal-Entity-Id";
    private static final String SHA = "a".repeat(64);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.5-alpine");

    @Autowired
    private VideoAnalysisService videoAnalysisService;

    @Autowired
    private AiResultsMessageRouter router;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FulfillmentObjectStorage fulfillmentObjectStorage;

    private UUID tenantId;
    private UUID buyerAdminUserId;
    private UUID buyerAdminEntityId;
    private UUID buyerMemberUserId;
    private UUID sellerAdminUserId;
    private UUID sellerAdminEntityId;
    private UUID sellerMemberUserId;
    private UUID dealId;
    private UUID evidenceId;
    private UUID fulfillmentId;
    private UUID milestoneId;
    private long evidenceVersion;

    @BeforeEach
    void setUp() {
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
        buyerMemberUserId = UUID.randomUUID();
        sellerAdminUserId = UUID.randomUUID();
        sellerAdminEntityId = UUID.randomUUID();
        sellerMemberUserId = UUID.randomUUID();
        dealId = UUID.randomUUID();
        evidenceId = UUID.randomUUID();
        fulfillmentId = UUID.randomUUID();
        milestoneId = UUID.randomUUID();

        insertUser(buyerAdminUserId, "buyer-admin@example.test");
        insertUser(buyerMemberUserId, "buyer-member@example.test");
        insertUser(sellerAdminUserId, "seller-admin@example.test");
        insertUser(sellerMemberUserId, "seller-member@example.test");
        jdbc.update("INSERT INTO tenant (id) VALUES (?)", tenantId);
        jdbc.update("INSERT INTO tenant_user (user_id, tenant_id) VALUES (?, ?)", buyerAdminUserId, tenantId);
        jdbc.update("INSERT INTO tenant_user (user_id, tenant_id) VALUES (?, ?)", buyerMemberUserId, tenantId);
        jdbc.update("INSERT INTO tenant_user (user_id, tenant_id) VALUES (?, ?)", sellerAdminUserId, tenantId);
        jdbc.update("INSERT INTO tenant_user (user_id, tenant_id) VALUES (?, ?)", sellerMemberUserId, tenantId);

        insertEntity(buyerAdminEntityId, "Buyer Co");
        insertEntity(sellerAdminEntityId, "Seller Co");
        insertMembership(buyerAdminUserId, buyerAdminEntityId, "ADMIN");
        insertMembership(buyerMemberUserId, buyerAdminEntityId, "MEMBER");
        insertMembership(sellerAdminUserId, sellerAdminEntityId, "ADMIN");
        insertMembership(sellerMemberUserId, sellerAdminEntityId, "MEMBER");

        UUID packageId = UUID.randomUUID();
        seedDeal(dealId, buyerAdminEntityId, sellerAdminEntityId, packageId, "DL-0000000001");
        seedFunding(dealId, packageId);
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
    void reviewFirstAcceptMakesVideoAnalysisRequestIneligible() throws Exception {
        acceptEvidence();

        mockMvc.perform(post(videoAnalysisPath())
                        .with(user(buyerAdminUserId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdminEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedEvidenceVersion\": " + evidenceVersion + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VIDEO_ANALYSIS_EVIDENCE_NOT_ELIGIBLE"));

        assertEquals(0, count("fulfillment_video_analysis_job"));
    }

    @Test
    void requestFirstDoesNotBlockManualAccept() throws Exception {
        UUID jobId = requestAnalysisJob();

        acceptEvidence();

        assertEquals("ACCEPTED", evidenceStatus());
        assertEquals("QUEUED", jobStatus(jobId));
        assertEquals(1, count("fulfillment_video_analysis_job"));
    }

    @Test
    void reviewFirstRejectMakesVideoAnalysisRequestIneligible() throws Exception {
        rejectEvidence();

        mockMvc.perform(post(videoAnalysisPath())
                        .with(user(buyerAdminUserId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdminEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedEvidenceVersion\": " + evidenceVersion + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VIDEO_ANALYSIS_EVIDENCE_NOT_ELIGIBLE"));
    }

    @Test
    void lateCompletedResultAfterAcceptedEvidencePreservesHistoryWithoutSideEffects() throws Exception {
        UUID jobId = requestAnalysisJob();
        acceptEvidence();
        BusinessSnapshot before = captureBusinessSnapshot();

        router.consume(json(completedEvent(UUID.randomUUID(), jobId)));

        assertEquals("RESULT_AVAILABLE", jobStatus(jobId));
        assertEquals("ACCEPTED", evidenceStatus());
        assertBusinessSnapshotUnchanged(before);
        mockMvc.perform(get(videoAnalysisPath())
                        .with(user(buyerAdminUserId.toString()))
                        .header(LEGAL_ENTITY_HEADER, buyerAdminEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESULT_AVAILABLE"));
    }

    @Test
    void lateCompletedResultAfterRejectedEvidencePreservesHistoryWithoutSideEffects() throws Exception {
        UUID jobId = requestAnalysisJob();
        rejectEvidence();
        BusinessSnapshot before = captureBusinessSnapshot();

        router.consume(json(completedEvent(UUID.randomUUID(), jobId)));

        assertEquals("RESULT_AVAILABLE", jobStatus(jobId));
        assertEquals("REJECTED", evidenceStatus());
        assertBusinessSnapshotUnchanged(before);
    }

    @Test
    void failedJobRemainsHistoryAndSuccessfulRetryCreatesExactlyOneResult() throws Exception {
        UUID failedJobId = requestAnalysisJob();
        router.consume(json(failedEvent(UUID.randomUUID(), failedJobId)));
        assertEquals("FAILED", jobStatus(failedJobId));

        UUID retryJobId = requestAnalysisJob();
        assertFalse(failedJobId.equals(retryJobId));
        router.consume(json(completedEvent(UUID.randomUUID(), retryJobId)));

        assertEquals(2, count("fulfillment_video_analysis_job"));
        assertEquals("FAILED", jobStatus(failedJobId));
        assertEquals("RESULT_AVAILABLE", jobStatus(retryJobId));
        assertEquals(1, count("fulfillment_video_analysis_result"));
    }

    @Test
    void sameIdempotencyKeyReplayCreatesOneQueuedJob() throws Exception {
        UUID key = UUID.randomUUID();
        postAnalysisRequest(key);
        postAnalysisRequest(key);

        assertEquals(1, count("fulfillment_video_analysis_job"));
        assertEquals(1, count("http_idempotency_record"));
    }

    @Test
    void concurrentSameIdempotencyKeyCreatesOneQueuedJob() throws Exception {
        UUID key = UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger accepted = new AtomicInteger();

        Future<?> first = executor.submit(() -> recordSameKeyOutcome(start, key, accepted));
        Future<?> second = executor.submit(() -> recordSameKeyOutcome(start, key, accepted));
        start.countDown();
        first.get(30, TimeUnit.SECONDS);
        second.get(30, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertEquals(1, count("fulfillment_video_analysis_job"));
        assertTrue(accepted.get() >= 1);
    }

    @Test
    void acceptCompletesBeforeConcurrentRequestReturns409WithNoJob() throws Exception {
        CountDownLatch acceptCompleted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<?> acceptFuture = executor.submit(() -> {
            try {
                acceptEvidence();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            } finally {
                acceptCompleted.countDown();
            }
        });
        Future<Integer> requestFuture = executor.submit(() -> {
            try {
                if (!acceptCompleted.await(30, TimeUnit.SECONDS)) {
                    throw new AssertionError("accept did not complete in time");
                }
                return postAnalysisRequestRaw(UUID.randomUUID());
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });

        acceptFuture.get(30, TimeUnit.SECONDS);
        int requestStatus = requestFuture.get(30, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertEquals("ACCEPTED", evidenceStatus());
        assertEquals(409, requestStatus);
        assertEquals(0, jobCountForEvidence());
        mockMvc.perform(post(videoAnalysisPath())
                        .with(user(buyerAdminUserId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdminEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedEvidenceVersion\": " + evidenceVersion + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VIDEO_ANALYSIS_EVIDENCE_NOT_ELIGIBLE"));
        assertActiveFulfillmentLifecycle();
        assertEquals(0, count("integration_outbox_event"));
    }

    @Test
    void requestCompletesBeforeConcurrentAcceptRetainsJobAndAcceptSucceeds() throws Exception {
        CountDownLatch requestCompleted = new CountDownLatch(1);
        AtomicInteger requestStatus = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<?> requestFuture = executor.submit(() -> {
            try {
                requestStatus.set(postAnalysisRequestRaw(UUID.randomUUID()));
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            } finally {
                requestCompleted.countDown();
            }
        });
        Future<?> acceptFuture = executor.submit(() -> {
            try {
                if (!requestCompleted.await(30, TimeUnit.SECONDS)) {
                    throw new AssertionError("request did not complete in time");
                }
                acceptEvidence();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });

        requestFuture.get(30, TimeUnit.SECONDS);
        acceptFuture.get(30, TimeUnit.SECONDS);
        executor.shutdownNow();

        UUID jobId = jdbc.queryForObject(
                "SELECT id FROM fulfillment_video_analysis_job WHERE evidence_submission_id = ?",
                UUID.class, evidenceId);

        assertEquals(202, requestStatus.get());
        assertEquals(1, jobCountForEvidence());
        assertEquals("ACCEPTED", evidenceStatus());
        assertEquals("QUEUED", jobStatus(jobId));
        assertActiveFulfillmentLifecycle();
    }

    @Test
    void rejectCompletesBeforeConcurrentRequestReturns409WithNoJob() throws Exception {
        CountDownLatch rejectCompleted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<?> rejectFuture = executor.submit(() -> {
            try {
                rejectEvidence();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            } finally {
                rejectCompleted.countDown();
            }
        });
        Future<Integer> requestFuture = executor.submit(() -> {
            try {
                if (!rejectCompleted.await(30, TimeUnit.SECONDS)) {
                    throw new AssertionError("reject did not complete in time");
                }
                return postAnalysisRequestRaw(UUID.randomUUID());
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });

        rejectFuture.get(30, TimeUnit.SECONDS);
        int requestStatus = requestFuture.get(30, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertEquals("REJECTED", evidenceStatus());
        assertEquals(409, requestStatus);
        assertEquals(0, jobCountForEvidence());
        mockMvc.perform(post(videoAnalysisPath())
                        .with(user(buyerAdminUserId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdminEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedEvidenceVersion\": " + evidenceVersion + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VIDEO_ANALYSIS_EVIDENCE_NOT_ELIGIBLE"));
        assertActiveFulfillmentLifecycle();
        assertEquals(0, count("payment_dispatch"));
        assertEquals(0, count("payment_operation"));
    }

    @Test
    void requestCompletesBeforeConcurrentRejectRetainsJobAndRejectSucceeds() throws Exception {
        CountDownLatch requestCompleted = new CountDownLatch(1);
        AtomicInteger requestStatus = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<?> requestFuture = executor.submit(() -> {
            try {
                requestStatus.set(postAnalysisRequestRaw(UUID.randomUUID()));
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            } finally {
                requestCompleted.countDown();
            }
        });
        Future<?> rejectFuture = executor.submit(() -> {
            try {
                if (!requestCompleted.await(30, TimeUnit.SECONDS)) {
                    throw new AssertionError("request did not complete in time");
                }
                rejectEvidence();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });

        requestFuture.get(30, TimeUnit.SECONDS);
        rejectFuture.get(30, TimeUnit.SECONDS);
        executor.shutdownNow();

        UUID jobId = jdbc.queryForObject(
                "SELECT id FROM fulfillment_video_analysis_job WHERE evidence_submission_id = ?",
                UUID.class, evidenceId);

        assertEquals(202, requestStatus.get());
        assertEquals(1, jobCountForEvidence());
        assertEquals("REJECTED", evidenceStatus());
        assertEquals("QUEUED", jobStatus(jobId));
        assertActiveFulfillmentLifecycle();
    }

    @Test
    void failedFirstThenLateCompletedPreservesFailedTerminalState() throws Exception {
        UUID jobId = requestAnalysisJob();
        router.consume(json(failedEvent(UUID.randomUUID(), jobId)));
        assertEquals("FAILED", jobStatus(jobId));
        BusinessSnapshot before = captureBusinessSnapshot();

        router.consume(json(completedEvent(UUID.randomUUID(), jobId)));

        assertEquals("FAILED", jobStatus(jobId));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM fulfillment_video_analysis_result WHERE job_id = ?", Integer.class, jobId));
        assertBusinessSnapshotUnchanged(before);
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM audit_record WHERE action = 'AI_ANALYSIS_TERMINAL_EVENT_IGNORED'
                """, Integer.class));
        assertActiveFulfillmentLifecycle();
    }

    @Test
    void otherDealParticipantCanReadButCannotRequest() throws Exception {
        UUID otherUserId = UUID.randomUUID();
        UUID otherEntityId = UUID.randomUUID();
        insertUser(otherUserId, "observer@example.test");
        jdbc.update("INSERT INTO tenant_user (user_id, tenant_id) VALUES (?, ?)", otherUserId, tenantId);
        insertEntity(otherEntityId, "Observer Co");
        insertMembership(otherUserId, otherEntityId, "ADMIN");
        jdbc.update("""
                INSERT INTO deal_participant (deal_id, tenant_id, legal_entity_id, legal_entity_tenant_id)
                VALUES (?, ?, ?, ?)
                """, dealId, tenantId, otherEntityId, tenantId);

        requestAnalysisJob();

        mockMvc.perform(get(videoAnalysisPath())
                        .with(user(otherUserId.toString()))
                        .header(LEGAL_ENTITY_HEADER, otherEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.availableActions.canRequest").value(false));

        mockMvc.perform(post(videoAnalysisPath())
                        .with(user(otherUserId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, otherEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedEvidenceVersion\": " + evidenceVersion + "}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VIDEO_ANALYSIS_REQUEST_FORBIDDEN"));
    }

    @Test
    void initiatorOnlyParticipantWhoIsNotBuyerOrSellerCannotRequest() throws Exception {
        UUID initiatorUserId = UUID.randomUUID();
        UUID initiatorEntityId = UUID.randomUUID();
        insertUser(initiatorUserId, "initiator-admin@example.test");
        jdbc.update("INSERT INTO tenant_user (user_id, tenant_id) VALUES (?, ?)", initiatorUserId, tenantId);
        insertEntity(initiatorEntityId, "Initiator Agency");
        insertMembership(initiatorUserId, initiatorEntityId, "ADMIN");
        jdbc.update("UPDATE deal SET initiator_legal_entity_id = ? WHERE id = ?", initiatorEntityId, dealId);
        jdbc.update("""
                INSERT INTO deal_participant (deal_id, tenant_id, legal_entity_id, legal_entity_tenant_id)
                VALUES (?, ?, ?, ?)
                """, dealId, tenantId, initiatorEntityId, tenantId);

        mockMvc.perform(get(videoAnalysisPath())
                        .with(user(initiatorUserId.toString()))
                        .header(LEGAL_ENTITY_HEADER, initiatorEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_REQUESTED"))
                .andExpect(jsonPath("$.availableActions.canRequest").value(false));

        mockMvc.perform(post(videoAnalysisPath())
                        .with(user(initiatorUserId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, initiatorEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedEvidenceVersion\": " + evidenceVersion + "}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VIDEO_ANALYSIS_REQUEST_FORBIDDEN"));

        assertEquals(0, jobCountForEvidence());

        UUID jobId = requestAnalysisJob();
        assertEquals("QUEUED", jobStatus(jobId));
    }

    @Test
    void videoFinalizeFlowCreatesNoAutomaticAnalysisJob() throws Exception {
        jdbc.update("DELETE FROM fulfillment_evidence_submission WHERE id = ?", evidenceId);
        jdbc.update("UPDATE fulfillment SET status = 'EVIDENCE_REQUIRED', version = 1 WHERE id = ?", fulfillmentId);
        jdbc.update("UPDATE fulfillment_milestone SET status = 'EVIDENCE_REQUIRED', version = 1 WHERE id = ?", milestoneId);
        recordingStorage().configureVerify(2048, SHA, "version-1", "video/mp4");

        MvcResult intentResult = mockMvc.perform(post("/api/v1/deals/" + dealId
                        + "/fulfillment/evidence/upload-intents")
                        .with(user(sellerMemberUserId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, sellerAdminEntityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadIntentRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evidence.status").value("PENDING_UPLOAD"))
                .andReturn();
        String submissionId = JsonPath.read(intentResult.getResponse().getContentAsString(), "$.evidence.id");

        mockMvc.perform(post("/api/v1/deals/" + dealId
                        + "/fulfillment/evidence/" + submissionId + "/finalize")
                        .with(user(sellerMemberUserId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, sellerAdminEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sizeBytes\": 2048, \"sha256\": \"" + SHA + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        assertEquals(0, count("fulfillment_video_analysis_job"));
        assertEquals(0, count("integration_outbox_event"));
        assertActiveFulfillmentLifecycle();

        mockMvc.perform(get("/api/v1/deals/" + dealId + "/fulfillment/evidence/" + submissionId + "/video-analysis")
                        .with(user(buyerAdminUserId.toString()))
                        .header(LEGAL_ENTITY_HEADER, buyerAdminEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_REQUESTED"));
    }

    @Test
    void sellerAndBuyerMembersCanReadButCannotRequest() throws Exception {
        requestAnalysisJob();

        mockMvc.perform(get(videoAnalysisPath())
                        .with(user(sellerMemberUserId.toString()))
                        .header(LEGAL_ENTITY_HEADER, sellerAdminEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.availableActions.canRequest").value(false));

        mockMvc.perform(get(videoAnalysisPath())
                        .with(user(buyerMemberUserId.toString()))
                        .header(LEGAL_ENTITY_HEADER, buyerAdminEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableActions.canRequest").value(false));

        mockMvc.perform(post(videoAnalysisPath())
                        .with(user(sellerMemberUserId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, sellerAdminEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedEvidenceVersion\": " + evidenceVersion + "}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VIDEO_ANALYSIS_REQUEST_FORBIDDEN"));
    }

    private void assertActiveFulfillmentLifecycle() throws Exception {
        mockMvc.perform(get("/api/v1/deals/" + dealId)
                        .with(user(buyerAdminUserId.toString()))
                        .header(LEGAL_ENTITY_HEADER, buyerAdminEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.lifecycle").value("FULFILLMENT"));
    }

    private String uploadIntentRequest() {
        return """
                {
                  "evidenceType": "VIDEO",
                  "mediaType": "video/mp4",
                  "fileName": "delivery.mp4",
                  "sizeBytes": 2048,
                  "sha256": "%s"
                }
                """.formatted(SHA);
    }

    private RecordingStorage recordingStorage() {
        return (RecordingStorage) fulfillmentObjectStorage;
    }

    @Test
    void duplicateTerminalCompletedEventDoesNotCreateSecondResult() throws Exception {
        UUID jobId = requestAnalysisJob();
        UUID eventId = UUID.randomUUID();
        router.consume(json(completedEvent(eventId, jobId)));
        router.consume(json(completedEvent(eventId, jobId)));

        assertEquals(1, count("fulfillment_video_analysis_result"));
        assertEquals("RESULT_AVAILABLE", jobStatus(jobId));
    }

    private void recordSameKeyOutcome(CountDownLatch start, UUID key, AtomicInteger accepted) {
        try {
            start.await();
            int status = postAnalysisRequestRaw(key);
            if (status == 202) {
                accepted.incrementAndGet();
            } else if (status != 409) {
                throw new AssertionError("Unexpected status: " + status);
            }
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private int postAnalysisRequestRaw(UUID key) throws Exception {
        return mockMvc.perform(post(videoAnalysisPath())
                        .with(user(buyerAdminUserId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdminEntityId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedEvidenceVersion\": " + evidenceVersion + "}"))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private void postAnalysisRequest(UUID key) throws Exception {
        mockMvc.perform(post(videoAnalysisPath())
                        .with(user(buyerAdminUserId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdminEntityId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedEvidenceVersion\": " + evidenceVersion + "}"))
                .andExpect(status().isAccepted());
    }

    private UUID requestAnalysisJob() {
        VideoAnalysisDetail detail = videoAnalysisService.request(
                new OperationContext(buyerAdminUserId, tenantId, buyerAdminEntityId, LegalEntityRole.ADMIN,
                        RequestedOperation.VIDEO_ANALYSIS_REQUEST),
                dealId, evidenceId, new RequestVideoAnalysisRequest(evidenceVersion),
                UUID.randomUUID(), UUID.randomUUID());
        return detail.jobId();
    }

    private void acceptEvidence() throws Exception {
        mockMvc.perform(post("/api/v1/deals/" + dealId + "/fulfillment/evidence/" + evidenceId + "/accept")
                        .with(user(buyerAdminUserId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdminEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\": 3, \"expectedEvidenceVersion\": " + evidenceVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    private void rejectEvidence() throws Exception {
        mockMvc.perform(post("/api/v1/deals/" + dealId + "/fulfillment/evidence/" + evidenceId + "/reject")
                        .with(user(buyerAdminUserId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdminEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\": 3, \"expectedEvidenceVersion\": "
                                + evidenceVersion + ", \"reason\": \"Insufficient detail\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    private BusinessSnapshot captureBusinessSnapshot() {
        return new BusinessSnapshot(
                jdbc.queryForObject("SELECT deal_status FROM deal WHERE id = ?", String.class, dealId),
                jdbc.queryForObject("SELECT status FROM funding_unit WHERE funding_plan_id IN "
                        + "(SELECT id FROM funding_plan WHERE deal_id = ?) LIMIT 1", String.class, dealId),
                jdbc.queryForObject("SELECT status FROM fulfillment WHERE id = ?", String.class, fulfillmentId),
                jdbc.queryForObject("SELECT version FROM fulfillment WHERE id = ?", Long.class, fulfillmentId),
                jdbc.queryForObject("SELECT status FROM fulfillment_milestone WHERE id = ?", String.class, milestoneId),
                jdbc.queryForObject("SELECT version FROM fulfillment_evidence_submission WHERE id = ?",
                        Long.class, evidenceId),
                count("payment_dispatch"),
                count("payment_operation"));
    }

    private void assertBusinessSnapshotUnchanged(BusinessSnapshot before) {
        BusinessSnapshot after = captureBusinessSnapshot();
        assertEquals(before, after);
    }

    private String evidenceStatus() {
        return jdbc.queryForObject("SELECT status FROM fulfillment_evidence_submission WHERE id = ?",
                String.class, evidenceId);
    }

    private String jobStatus(UUID jobId) {
        return jdbc.queryForObject("SELECT status FROM fulfillment_video_analysis_job WHERE id = ?",
                String.class, jobId);
    }

    private int jobCountForEvidence() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM fulfillment_video_analysis_job WHERE evidence_submission_id = ?",
                Integer.class, evidenceId);
    }

    private Map<String, Object> completedEvent(UUID eventId, UUID jobId) {
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
        payload.put("technicalMetadata", Map.of(
                "pipelineVersion", "video-pipeline-1.0.0",
                "modelProvider", "internal-model-service",
                "modelFamily", "delivery-evidence",
                "modelVersion", "2026-07",
                "parserVersion", "video-parser-1.0.0",
                "privacyVersion", "default-1",
                "durationMs", 11400));
        payload.put("warnings", List.of());
        return terminalEnvelope(eventId, jobId, "ai.job.completed.v1", payload);
    }

    private Map<String, Object> failedEvent(UUID eventId, UUID jobId) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("category", "RETRYABLE_TECHNICAL");
        error.put("code", "OBJECT_STORAGE_TEMPORARILY_UNAVAILABLE");
        error.put("message", "Temporary failure.");
        error.put("retryRecommended", true);
        error.put("details", Map.of("dependency", "object-storage", "reason", "temporary unavailability",
                "retryAfterMs", 3000));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error", error);
        payload.put("attempt", Map.of("attemptNumber", 1, "maxAttempts", 3));
        payload.put("technicalMetadata", Map.of(
                "pipelineVersion", "video-pipeline-1.0.0",
                "parserVersion", "video-parser-1.0.0",
                "privacyVersion", "default-1",
                "durationMs", 3000));
        return terminalEnvelope(eventId, jobId, "ai.job.failed.v1", payload);
    }

    private Map<String, Object> terminalEnvelope(UUID eventId, UUID jobId, String eventType, Object payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", eventId.toString());
        event.put("eventType", eventType);
        event.put("schemaVersion", "1.0.0");
        event.put("occurredAt", "2026-07-19T00:00:00Z");
        event.put("correlationId", UUID.randomUUID().toString());
        event.put("causationId", null);
        event.put("jobId", jobId.toString());
        event.put("jobType", "VIDEO_ANALYSIS");
        event.put("tenantId", tenantId.toString());
        event.put("transactionId", dealId.toString());
        event.put("subjectId", evidenceId.toString());
        event.put("idempotencyKey", "video-hardening");
        event.put("producer", Map.of("service", "m4trust-ai-worker", "version", "1.0.0"));
        event.put("payload", payload);
        return event;
    }

    private String json(Map<String, Object> value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String videoAnalysisPath() {
        return "/api/v1/deals/" + dealId + "/fulfillment/evidence/" + evidenceId + "/video-analysis";
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
        for (UUID entityId : List.of(buyerEntityId, sellerEntityId)) {
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

    private record BusinessSnapshot(
            String dealStatus,
            String fundingStatus,
            String fulfillmentStatus,
            long fulfillmentVersion,
            String milestoneStatus,
            long evidenceVersion,
            int paymentDispatchCount,
            int paymentOperationCount) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Fakes {
        @Bean
        @Primary
        FulfillmentObjectStorage fulfillmentObjectStorage() {
            return new RecordingStorage();
        }
    }

    static final class RecordingStorage implements FulfillmentObjectStorage {
        private volatile long verifiedSize = 2048;
        private volatile String verifiedSha = SHA;
        private volatile String verifiedVersion = "version-1";
        private volatile String verifiedMediaType = "video/mp4";

        void configureVerify(long size, String sha, String version, String mediaType) {
            verifiedSize = size;
            verifiedSha = sha;
            verifiedVersion = version;
            verifiedMediaType = mediaType;
        }

        @Override
        public DirectUpload createDirectUpload(String objectKey, String mediaType, long contentLength) {
            Instant expiresAt = Instant.now().plusSeconds(600);
            return new DirectUpload(URI.create("https://storage.example/" + objectKey),
                    Map.of("Content-Type", mediaType), expiresAt);
        }

        @Override
        public DirectDownload createDirectDownload(String objectKey, String objectVersion) {
            TransactionSynchronizationManager.isActualTransactionActive();
            return new DirectDownload(URI.create("https://storage.example/" + objectKey
                    + "?version=" + objectVersion), Instant.parse("2026-07-19T00:15:00Z"));
        }

        @Override
        public VerifiedObject verify(String objectKey) {
            return new VerifiedObject(verifiedSize, verifiedSha, verifiedVersion, verifiedMediaType);
        }
    }
}
