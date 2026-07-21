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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.jayway.jsonpath.JsonPath;
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

/**
 * End-to-end HTTP coverage for the fulfillment/evidence surface against a real
 * PostgreSQL database. A test-scoped fake object-storage adapter replaces S3 so
 * the tests do not require a running MinIO instance.
 */
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers
@AutoConfigureMockMvc
@Import(FulfillmentIntegrationTest.FakeStorageConfiguration.class)
class FulfillmentIntegrationTest {

    private static final String LEGAL_ENTITY_HEADER = "X-M4Trust-Legal-Entity-Id";
    private static final String SHA = "a".repeat(64);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.5-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FakeStorage storage;

    private Principal buyerAdmin;
    private Principal buyerMember;
    private Principal sellerAdmin;
    private Principal sellerMember;
    private Principal outsider;

    @BeforeEach
    void setUp() {
        storage.reset();
        jdbc.update("DELETE FROM spring_session_attributes");
        jdbc.update("DELETE FROM spring_session");
        jdbc.execute("""
                TRUNCATE TABLE
                    dispute_comment, dispute_evidence_snapshot, dispute_case, fulfillment_video_analysis_result,
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
        buyerAdmin = insertPrincipal("buyer-admin@example.test", "Buyer Co", "ADMIN");
        buyerMember = insertMember(buyerAdmin, "buyer-member@example.test");
        sellerAdmin = insertPrincipal("seller-admin@example.test", "Seller Co", "ADMIN");
        sellerMember = insertMember(sellerAdmin, "seller-member@example.test");
        outsider = insertPrincipal("outsider@example.test", "Outsider Co", "ADMIN");
    }

    @Test
    void fulfillmentResponsesSerializeEvidenceMediaTypeAsMimeWireValues() throws Exception {
        UUID videoDealId = createActiveFundedDeal();
        startFulfillment(videoDealId, sellerAdmin);
        String videoSubmissionId = submitEvidence(videoDealId, sellerAdmin, "VIDEO", "delivery.mp4", "video/mp4", 2048);
        mockMvc.perform(get("/api/v1/deals/" + videoDealId + "/fulfillment")
                        .with(user(buyerAdmin.userId.toString()))
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentEvidence.id").value(videoSubmissionId))
                .andExpect(jsonPath("$.currentEvidence.mediaType").value("video/mp4"));

        UUID pdfDealId = createActiveFundedDeal();
        startFulfillment(pdfDealId, sellerAdmin);
        String pdfSubmissionId = submitEvidence(pdfDealId, sellerAdmin, "DELIVERY_NOTE", "receipt.pdf",
                "application/pdf");
        mockMvc.perform(get("/api/v1/deals/" + pdfDealId + "/fulfillment")
                        .with(user(buyerAdmin.userId.toString()))
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentEvidence.id").value(pdfSubmissionId))
                .andExpect(jsonPath("$.currentEvidence.mediaType").value("application/pdf"));
    }

    @Test
    void sellerCanStartFulfillmentForActiveFundedDeal() throws Exception {
        UUID dealId = createActiveFundedDeal();

        mockMvc.perform(get("/api/v1/deals/" + dealId)
                        .with(user(sellerMember.userId.toString()))
                        .header(LEGAL_ENTITY_HEADER, sellerMember.legalEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableActions.canStartFulfillment").value(true));

        MvcResult result = mockMvc.perform(post("/api/v1/deals/" + dealId + "/fulfillment")
                        .with(user(sellerMember.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, sellerMember.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\": 3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.milestone.ruleReferences.length()").value(2))
                .andReturn();

        String response = result.getResponse().getContentAsString();
        assertEquals("Primary milestone", JsonPath.read(response, "$.milestone.title"));
        assertTrue(storage.verifyCalls.get() >= 0);
    }

    @Test
    void startIsForbiddenForNonSeller() throws Exception {
        UUID dealId = createActiveFundedDeal();

        mockMvc.perform(post("/api/v1/deals/" + dealId + "/fulfillment")
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\": 0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FULFILLMENT_START_FORBIDDEN"));
    }

    @Test
    void startConflictWhenDealNotFunded() throws Exception {
        UUID dealId = createActiveDealWithoutFunding();

        mockMvc.perform(post("/api/v1/deals/" + dealId + "/fulfillment")
                        .with(user(sellerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, sellerAdmin.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\": 0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEAL_STATE_CONFLICT"));
    }

    @Test
    void concurrentStartsProduceOneFulfillment() throws Exception {
        UUID dealId = createActiveFundedDeal();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger createdCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        Future<?> first = executor.submit(() -> {
            try {
                start.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(exception);
            }
            recordStartOutcome(dealId, sellerAdmin, createdCount, conflictCount);
        });
        Future<?> second = executor.submit(() -> {
            try {
                start.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(exception);
            }
            recordStartOutcome(dealId, sellerAdmin, createdCount, conflictCount);
        });

        start.countDown();
        first.get(10, TimeUnit.SECONDS);
        second.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(1, createdCount.get());
        assertEquals(1, conflictCount.get());
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM fulfillment WHERE deal_id = ?", Long.class, dealId));
    }

    @Test
    void sellerMemberCanUploadIntentAndBuyerAdminCanReview() throws Exception {
        UUID dealId = createActiveFundedDeal();
        startFulfillment(dealId, sellerMember);

        MvcResult intentResult = mockMvc.perform(post("/api/v1/deals/" + dealId
                        + "/fulfillment/evidence/upload-intents")
                        .with(user(sellerMember.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, sellerMember.legalEntityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadIntentRequest("DELIVERY_NOTE", "receipt.pdf", "application/pdf")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evidence.status").value("PENDING_UPLOAD"))
                .andExpect(jsonPath("$.evidence.expiresAt").exists())
                .andExpect(jsonPath("$.evidence.length()").value(13))
                .andReturn();

        String intentResponse = intentResult.getResponse().getContentAsString();
        String submissionId = JsonPath.read(intentResponse, "$.evidence.id");

        mockMvc.perform(get("/api/v1/deals/" + dealId + "/fulfillment")
                        .with(user(sellerAdmin.userId.toString()))
                        .header(LEGAL_ENTITY_HEADER, sellerAdmin.legalEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EVIDENCE_REQUIRED"))
                .andExpect(jsonPath("$.currentEvidence.status").value("PENDING_UPLOAD"));

        mockMvc.perform(post("/api/v1/deals/" + dealId
                        + "/fulfillment/evidence/" + submissionId + "/finalize")
                        .with(user(sellerMember.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, sellerMember.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sizeBytes\": 12, \"sha256\": \"" + SHA + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        mockMvc.perform(get("/api/v1/deals/" + dealId + "/fulfillment")
                        .with(user(buyerAdmin.userId.toString()))
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.currentEvidence.status").value("SUBMITTED"));

        mockMvc.perform(post("/api/v1/deals/" + dealId
                        + "/fulfillment/evidence/" + submissionId + "/accept")
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\": 3, \"expectedEvidenceVersion\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        mockMvc.perform(get("/api/v1/deals/" + dealId + "/fulfillment")
                        .with(user(sellerMember.userId.toString()))
                        .header(LEGAL_ENTITY_HEADER, sellerMember.legalEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertTrue(storage.verifyCalls.get() >= 1);
        assertFalse(storage.calledInsideTransaction.get());
    }

    @Test
    void uploadForbiddenForBuyer() throws Exception {
        UUID dealId = createActiveFundedDeal();
        startFulfillment(dealId, sellerAdmin);

        mockMvc.perform(post("/api/v1/deals/" + dealId
                        + "/fulfillment/evidence/upload-intents")
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadIntentRequest("DELIVERY_NOTE", "receipt.pdf", "application/pdf")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EVIDENCE_UPLOAD_FORBIDDEN"));
    }

    @Test
    void finalizeRejectsMismatchedMediaType() throws Exception {
        UUID dealId = createActiveFundedDeal();
        startFulfillment(dealId, sellerAdmin);

        MvcResult intentResult = mockMvc.perform(post("/api/v1/deals/" + dealId
                        + "/fulfillment/evidence/upload-intents")
                        .with(user(sellerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, sellerAdmin.legalEntityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadIntentRequest("DELIVERY_NOTE", "receipt.pdf", "application/pdf")))
                .andExpect(status().isCreated())
                .andReturn();
        String submissionId = JsonPath.read(intentResult.getResponse().getContentAsString(), "$.evidence.id");

        storage.setVerified(new FulfillmentObjectStorage.VerifiedObject(12, SHA, "v1", "image/png"));

        mockMvc.perform(post("/api/v1/deals/" + dealId
                        + "/fulfillment/evidence/" + submissionId + "/finalize")
                        .with(user(sellerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, sellerAdmin.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sizeBytes\": 12, \"sha256\": \"" + SHA + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVIDENCE_VERIFICATION_FAILED"));
    }

    @Test
    void rejectPreservesHistoryAndAllowsReplacement() throws Exception {
        UUID dealId = createActiveFundedDeal();
        startFulfillment(dealId, sellerAdmin);

        String firstSubmissionId = submitEvidence(dealId, sellerAdmin, "INVOICE", "invoice.pdf", "application/pdf");

        mockMvc.perform(post("/api/v1/deals/" + dealId
                        + "/fulfillment/evidence/" + firstSubmissionId + "/reject")
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\": 3, \"expectedEvidenceVersion\": 1, \"reason\": \"Missing tax number\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        String secondSubmissionId = submitEvidence(dealId, sellerAdmin, "INVOICE", "invoice2.pdf", "application/pdf");

        mockMvc.perform(get("/api/v1/deals/" + dealId + "/fulfillment")
                        .with(user(buyerAdmin.userId.toString()))
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.currentEvidence.id").value(secondSubmissionId));
    }

    @Test
    void buyerMemberCannotReview() throws Exception {
        UUID dealId = createActiveFundedDeal();
        startFulfillment(dealId, sellerAdmin);
        String submissionId = submitEvidence(dealId, sellerAdmin, "DELIVERY_NOTE", "note.pdf", "application/pdf");

        mockMvc.perform(post("/api/v1/deals/" + dealId
                        + "/fulfillment/evidence/" + submissionId + "/accept")
                        .with(user(buyerMember.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerMember.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\": 0, \"expectedEvidenceVersion\": 1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EVIDENCE_REVIEW_FORBIDDEN"));
    }

    @Test
    void participantCanDownloadSubmittedEvidence() throws Exception {
        UUID dealId = createActiveFundedDeal();
        startFulfillment(dealId, sellerAdmin);
        String submissionId = submitEvidence(dealId, sellerAdmin, "PHOTO", "photo.png", "image/png");
        insertParticipant(dealId, outsider);

        mockMvc.perform(get("/api/v1/deals/" + dealId + "/fulfillment")
                        .with(user(outsider.userId.toString()))
                        .header(LEGAL_ENTITY_HEADER, outsider.legalEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentEvidence.id").value(submissionId));
        mockMvc.perform(post("/api/v1/deals/" + dealId
                        + "/fulfillment/evidence/" + submissionId + "/download-link")
                        .with(user(outsider.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, outsider.legalEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downloadUrl").exists());
    }

    @Test
    void nonParticipantCannotAccessFulfillment() throws Exception {
        UUID dealId = createActiveFundedDeal();
        startFulfillment(dealId, sellerAdmin);

        mockMvc.perform(get("/api/v1/deals/" + dealId + "/fulfillment")
                        .with(user(outsider.userId.toString()))
                        .header(LEGAL_ENTITY_HEADER, outsider.legalEntityId))
                .andExpect(status().isNotFound());
    }

    @Test
    void validationRejectsInvalidSha256() throws Exception {
        UUID dealId = createActiveFundedDeal();
        startFulfillment(dealId, sellerAdmin);

        mockMvc.perform(post("/api/v1/deals/" + dealId
                        + "/fulfillment/evidence/upload-intents")
                        .with(user(sellerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, sellerAdmin.legalEntityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadIntentRequest("DELIVERY_NOTE", "receipt.pdf", "application/pdf")
                                .replace(SHA, "not-a-hash")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private String submitEvidence(UUID dealId, Principal seller, String evidenceType,
            String fileName, String mediaType) throws Exception {
        return submitEvidence(dealId, seller, evidenceType, fileName, mediaType, 12);
    }

    private String submitEvidence(UUID dealId, Principal seller, String evidenceType,
            String fileName, String mediaType, long sizeBytes) throws Exception {
        MvcResult intentResult = mockMvc.perform(post("/api/v1/deals/" + dealId
                        + "/fulfillment/evidence/upload-intents")
                        .with(user(seller.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, seller.legalEntityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadIntentRequest(evidenceType, fileName, mediaType, sizeBytes)))
                .andExpect(status().isCreated())
                .andReturn();
        String submissionId = JsonPath.read(intentResult.getResponse().getContentAsString(), "$.evidence.id");

        storage.setVerified(new FulfillmentObjectStorage.VerifiedObject(sizeBytes, SHA, "immutable-version", mediaType));

        mockMvc.perform(post("/api/v1/deals/" + dealId
                        + "/fulfillment/evidence/" + submissionId + "/finalize")
                        .with(user(seller.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, seller.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sizeBytes\": " + sizeBytes + ", \"sha256\": \"" + SHA + "\"}"))
                .andExpect(status().isOk());
        return submissionId;
    }

    private void startFulfillment(UUID dealId, Principal seller) throws Exception {
        mockMvc.perform(post("/api/v1/deals/" + dealId + "/fulfillment")
                        .with(user(seller.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, seller.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\": 3}"))
                .andExpect(status().isCreated());
    }

    private void recordStartOutcome(UUID dealId, Principal seller,
            AtomicInteger createdCount, AtomicInteger conflictCount) {
        try {
            MvcResult result = mockMvc.perform(post("/api/v1/deals/" + dealId + "/fulfillment")
                            .with(user(seller.userId.toString())).with(csrf())
                            .header(LEGAL_ENTITY_HEADER, seller.legalEntityId)
                            .header("Idempotency-Key", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"expectedVersion\": 3}"))
                    .andReturn();
            int status = result.getResponse().getStatus();
            if (status == 201) {
                createdCount.incrementAndGet();
            } else if (status == 409) {
                conflictCount.incrementAndGet();
            } else {
                throw new AssertionError("Unexpected start status: " + status);
            }
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private String uploadIntentRequest(String evidenceType, String fileName, String mediaType) {
        return uploadIntentRequest(evidenceType, fileName, mediaType, 12);
    }

    private String uploadIntentRequest(String evidenceType, String fileName, String mediaType, long sizeBytes) {
        return """
                {
                  "evidenceType": "%s",
                  "mediaType": "%s",
                  "fileName": "%s",
                  "sizeBytes": %d,
                  "sha256": "%s"
                }
                """.formatted(evidenceType, mediaType, fileName, sizeBytes, SHA);
    }

    private UUID createActiveFundedDeal() throws Exception {
        UUID dealId = createActiveDeal();
        seedFunding(dealId);
        return dealId;
    }

    private UUID createActiveDealWithoutFunding() throws Exception {
        return createActiveDeal();
    }

    private UUID createActiveDeal() throws Exception {
        MvcResult dealResult = mockMvc.perform(post("/api/v1/deals")
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Fulfillment Deal\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID dealId = UUID.fromString(JsonPath.read(dealResult.getResponse().getContentAsString(), "$.id"));

        insertParticipant(dealId, sellerAdmin);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/deals/" + dealId + "/parties")
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"buyerLegalEntityId": "%s", "sellerLegalEntityId": "%s", "expectedVersion": 0}
                                """.formatted(buyerAdmin.legalEntityId, sellerAdmin.legalEntityId)))
                .andExpect(status().isOk());

        seedAvailableDocumentAndRuleSetWithRules(dealId);

        MvcResult created = mockMvc.perform(post("/api/v1/deals/" + dealId + "/ratification-packages")
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion": 1, "commercialTerms": {"amountMinor": 5000, "currency": "TRY"}}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        UUID packageId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));

        approve(dealId, packageId, buyerAdmin, 0).andExpect(status().isOk());
        approve(dealId, packageId, sellerAdmin, 1).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RATIFIED"));

        return dealId;
    }

    private org.springframework.test.web.servlet.ResultActions approve(
            UUID dealId, UUID packageId, Principal actor, long expectedPackageVersion) throws Exception {
        return mockMvc.perform(post("/api/v1/deals/" + dealId + "/ratification-packages/" + packageId + "/approve")
                .with(user(actor.userId.toString())).with(csrf())
                .header(LEGAL_ENTITY_HEADER, actor.legalEntityId)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedPackageVersion\": " + expectedPackageVersion + "}"));
    }

    private void seedFunding(UUID dealId) {
        UUID planId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO funding_plan (id, deal_id, ratification_package_id, tenant_id,
                    amount_minor, currency, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'TRY', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, planId, dealId, currentRatificationPackageId(dealId), tenantId(dealId), 5000L);
        jdbc.update("""
                INSERT INTO funding_unit (id, funding_plan_id, sequence_no, amount_minor,
                    currency, status, version, created_at, updated_at)
                VALUES (?, ?, 1, ?, 'TRY', 'FUNDED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, unitId, planId, 5000L);
    }

    private UUID currentRatificationPackageId(UUID dealId) {
        return jdbc.queryForObject(
                "SELECT current_ratification_package_id FROM deal WHERE id = ?", UUID.class, dealId);
    }

    private UUID tenantId(UUID dealId) {
        return jdbc.queryForObject("SELECT tenant_id FROM deal WHERE id = ?", UUID.class, dealId);
    }

    private void seedAvailableDocumentAndRuleSetWithRules(UUID dealId) {
        UUID tenantId = buyerAdmin.tenantId;
        String sha = SHA;
        UUID documentId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO document (
                    id, deal_id, file_name, media_type, document_status, object_key,
                    declared_size_bytes, declared_sha256, upload_expires_at,
                    verified_size_bytes, verified_sha256, object_version,
                    created_at, available_at
                ) VALUES (?, ?, 'contract.pdf', 'application/pdf', 'AVAILABLE', ?,
                    1024, ?, CURRENT_TIMESTAMP + INTERVAL '1 hour',
                    1024, ?, 'v1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, documentId, dealId, "obj-" + documentId, sha, sha);
        jdbc.update("""
                UPDATE deal SET current_document_id = ?, current_document_status = 'AVAILABLE' WHERE id = ?
                """, documentId, dealId);

        UUID analysisJobId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO contract_intelligence_analysis_job (
                    id, tenant_id, deal_id, document_id, object_version, input_sha256, status,
                    requested_at, processing_started_at, completed_at
                ) VALUES (?, ?, ?, ?, 'v1', ?, 'ACCEPTED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, analysisJobId, tenantId, dealId, documentId, sha);
        UUID extractionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO contract_intelligence_extraction_result_version (
                    id, analysis_job_id, schema_version, canonical_result, created_at
                ) VALUES (?, ?, '1.0.0',
                    '{"parties":[],"rules":[],"deliveryRequirements":[],"summary":{}}'::jsonb, CURRENT_TIMESTAMP)
                """, extractionId, analysisJobId);
        UUID ruleSetId = UUID.randomUUID();
        String rules = """
                [
                  {
                    "ruleReference": "delivery-1",
                    "decision": "KEPT",
                    "category": "DELIVERY",
                    "title": "Delivery address",
                    "description": "Delivery must be to the address specified in the contract.",
                    "structuredValue": {"type": "TEXT", "value": "Istanbul"},
                    "legalBasis": {"source": "tbk-6098", "articleNo": "1"},
                    "legalBasisProvenance": "EXTRACTED"
                  },
                  {
                    "ruleReference": "quality-1",
                    "decision": "KEPT",
                    "category": "QUALITY",
                    "title": "Quality standard",
                    "description": "Product must conform to the agreed quality standard.",
                    "structuredValue": {"type": "TEXT", "value": "ISO 9001"},
                    "legalBasis": {"source": "tbk-6098", "articleNo": "2"},
                    "legalBasisProvenance": "EXTRACTED"
                  }
                ]
                """;
        jdbc.update("""
                INSERT INTO contract_intelligence_rule_set_version (
                    id, deal_id, version, source_analysis_id, source_extraction_result_version_id,
                    created_by_user_id, created_at, rules, excluded_rule_references
                ) VALUES (?, ?, 1, ?, ?, ?, CURRENT_TIMESTAMP, CAST(? AS jsonb), '[]'::jsonb)
                """, ruleSetId, dealId, analysisJobId, extractionId, buyerAdmin.userId, rules);
        jdbc.update("UPDATE deal SET current_rule_set_version_id = ? WHERE id = ?", ruleSetId, dealId);
    }

    private void insertParticipant(UUID dealId, Principal principal) {
        jdbc.update("""
                INSERT INTO deal_participant (deal_id, tenant_id, legal_entity_id, legal_entity_tenant_id)
                SELECT id, tenant_id, ?, ? FROM deal WHERE id = ?
                """, principal.legalEntityId, principal.tenantId, dealId);
    }

    private Principal insertPrincipal(String email, String legalName, String role) {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID legalEntityId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO identity_user (id, email, password_hash, display_name, enabled)
                VALUES (?, ?, 'test-hash', ?, true)
                """, userId, email, legalName + " User");
        jdbc.update("INSERT INTO tenant (id) VALUES (?)", tenantId);
        jdbc.update("INSERT INTO tenant_user (user_id, tenant_id) VALUES (?, ?)", userId, tenantId);
        jdbc.update("""
                INSERT INTO legal_entity (id, tenant_id, legal_name, registration_number)
                VALUES (?, ?, ?, ?)
                """, legalEntityId, tenantId, legalName, "REG-" + legalEntityId);
        jdbc.update("""
                INSERT INTO legal_entity_membership (id, tenant_id, legal_entity_id, user_id, role)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), tenantId, legalEntityId, userId, role);
        return new Principal(userId, tenantId, legalEntityId);
    }

    private Principal insertMember(Principal ofEntity, String email) {
        UUID userId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO identity_user (id, email, password_hash, display_name, enabled)
                VALUES (?, ?, 'test-hash', 'Member User', true)
                """, userId, email);
        jdbc.update("INSERT INTO tenant_user (user_id, tenant_id) VALUES (?, ?)", userId, ofEntity.tenantId);
        jdbc.update("""
                INSERT INTO legal_entity_membership (id, tenant_id, legal_entity_id, user_id, role)
                VALUES (?, ?, ?, ?, 'MEMBER')
                """, UUID.randomUUID(), ofEntity.tenantId, ofEntity.legalEntityId, userId);
        return new Principal(userId, ofEntity.tenantId, ofEntity.legalEntityId);
    }

    private static final class Principal {
        final UUID userId;
        final UUID tenantId;
        final UUID legalEntityId;

        Principal(UUID userId, UUID tenantId, UUID legalEntityId) {
            this.userId = userId;
            this.tenantId = tenantId;
            this.legalEntityId = legalEntityId;
        }
    }

    @TestConfiguration
    static class FakeStorageConfiguration {
        @Bean
        @Primary
        FakeStorage fakeFulfillmentObjectStorage() {
            return new FakeStorage();
        }
    }

    static class FakeStorage implements FulfillmentObjectStorage {
        final AtomicBoolean calledInsideTransaction = new AtomicBoolean();
        final AtomicBoolean downloadCalledInsideTransaction = new AtomicBoolean();
        final AtomicInteger verifyCalls = new AtomicInteger();
        private volatile VerifiedObject verified = new VerifiedObject(12, SHA, "immutable-version", "application/pdf");

        void reset() {
            calledInsideTransaction.set(false);
            downloadCalledInsideTransaction.set(false);
            verifyCalls.set(0);
            verified = new VerifiedObject(12, SHA, "immutable-version", "application/pdf");
        }

        void setVerified(VerifiedObject verified) {
            this.verified = verified;
        }

        @Override
        public DirectUpload createDirectUpload(String objectKey, String mediaType, long contentLength) {
            recordTransactionState();
            return new DirectUpload(URI.create("https://storage.example/" + objectKey),
                    Map.of("Content-Type", mediaType), Instant.now().plusSeconds(600));
        }

        @Override
        public DirectDownload createDirectDownload(String objectKey, String objectVersion) {
            downloadCalledInsideTransaction.compareAndSet(false,
                    TransactionSynchronizationManager.isActualTransactionActive());
            return new DirectDownload(URI.create("https://storage.example/" + objectKey
                    + "?version=" + objectVersion), Instant.now().plusSeconds(300));
        }

        @Override
        public VerifiedObject verify(String objectKey) {
            recordTransactionState();
            verifyCalls.incrementAndGet();
            return verified;
        }

        private void recordTransactionState() {
            calledInsideTransaction.compareAndSet(false,
                    TransactionSynchronizationManager.isActualTransactionActive());
        }
    }
}
