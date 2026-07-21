package com.m4trust.coreapi.casework;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.concurrent.atomic.AtomicInteger;

import com.jayway.jsonpath.JsonPath;
import com.m4trust.coreapi.fulfillment.FulfillmentObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("local")
@Testcontainers
@AutoConfigureMockMvc
@Import(DisputeIntegrationTest.FakeStorageConfiguration.class)
class DisputeIntegrationTest {

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
                    dispute_comment,
                    dispute_evidence_snapshot,
                    dispute_case,
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
    void buyerAdminCanOpenListAndReadDisputeWithSnapshot() throws Exception {
        PreparedDeal prepared = prepareReviewRequiredDeal();
        UUID idempotencyKey = UUID.randomUUID();

        MvcResult created = mockMvc.perform(post("/api/v1/deals/" + prepared.dealId() + "/disputes")
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openRequest("NON_DELIVERY", "Late delivery", "Goods never arrived.",
                                prepared.dealVersion(), prepared.fulfillmentVersion())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.reasonCode").value("NON_DELIVERY"))
                .andExpect(jsonPath("$.openingSnapshot.evidence.length()").value(1))
                .andExpect(jsonPath("$.openingSnapshot.fulfillmentStatusAtOpen").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.availableActions.canAcknowledge").value(false))
                .andExpect(jsonPath("$.availableActions.canWithdraw").value(true))
                .andReturn();
        String disputeId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/v1/deals/" + prepared.dealId() + "/disputes")
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openRequest("NON_DELIVERY", "Late delivery", "Goods never arrived.",
                                prepared.dealVersion(), prepared.fulfillmentVersion())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(disputeId));

        mockMvc.perform(get("/api/v1/deals/" + prepared.dealId() + "/disputes")
                        .with(user(buyerMember.userId.toString()))
                        .header(LEGAL_ENTITY_HEADER, buyerMember.legalEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(disputeId));

        mockMvc.perform(get("/api/v1/deals/" + prepared.dealId() + "/disputes/" + disputeId)
                        .with(user(sellerMember.userId.toString()))
                        .header(LEGAL_ENTITY_HEADER, sellerMember.legalEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statement").value("Goods never arrived."))
                .andExpect(jsonPath("$.availableActions.canAcknowledge").value(false));
    }

    @Test
    void sellerAdminCanOpenDispute() throws Exception {
        PreparedDeal prepared = prepareReviewRequiredDeal();

        mockMvc.perform(post("/api/v1/deals/" + prepared.dealId() + "/disputes")
                        .with(user(sellerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, sellerAdmin.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openRequest("EVIDENCE_QUALITY", "Poor quality", "Evidence is unusable.",
                                prepared.dealVersion(), prepared.fulfillmentVersion())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.openingLegalEntity.legalEntityId").value(sellerAdmin.legalEntityId.toString()));
    }

    @Test
    void memberCannotOpenDispute() throws Exception {
        PreparedDeal prepared = prepareReviewRequiredDeal();

        mockMvc.perform(post("/api/v1/deals/" + prepared.dealId() + "/disputes")
                        .with(user(buyerMember.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerMember.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openRequest("OTHER", "Issue", "Details here.",
                                prepared.dealVersion(), prepared.fulfillmentVersion())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DISPUTE_OPEN_FORBIDDEN"));
    }

    @Test
    void hiddenActorsReceiveNonDisclosing404() throws Exception {
        PreparedDeal prepared = prepareReviewRequiredDeal();
        insertParticipant(prepared.dealId(), outsider);

        mockMvc.perform(get("/api/v1/deals/" + prepared.dealId() + "/disputes")
                        .with(user(outsider.userId.toString()))
                        .header(LEGAL_ENTITY_HEADER, outsider.legalEntityId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CASEWORK_NOT_FOUND_OR_HIDDEN"));

        UUID initiatorUserId = UUID.randomUUID();
        UUID initiatorEntityId = UUID.randomUUID();
        insertUser(initiatorUserId, "initiator@example.test");
        jdbc.update("INSERT INTO tenant_user (user_id, tenant_id) VALUES (?, ?)",
                initiatorUserId, buyerAdmin.tenantId);
        insertEntity(initiatorEntityId, buyerAdmin.tenantId, "Initiator Agency");
        insertMembership(initiatorUserId, initiatorEntityId, "ADMIN");
        jdbc.update("UPDATE deal SET initiator_legal_entity_id = ? WHERE id = ?",
                initiatorEntityId, prepared.dealId());
        jdbc.update("""
                INSERT INTO deal_participant (deal_id, tenant_id, legal_entity_id, legal_entity_tenant_id)
                VALUES (?, ?, ?, ?)
                """, prepared.dealId(), buyerAdmin.tenantId, initiatorEntityId, buyerAdmin.tenantId);

        mockMvc.perform(post("/api/v1/deals/" + prepared.dealId() + "/disputes")
                        .with(user(initiatorUserId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, initiatorEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openRequest("OTHER", "Issue", "Details here.",
                                prepared.dealVersion(), prepared.fulfillmentVersion())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CASEWORK_NOT_FOUND_OR_HIDDEN"));
    }

    @Test
    void nonParticipantCannotAccessCasework() throws Exception {
        PreparedDeal prepared = prepareReviewRequiredDeal();

        mockMvc.perform(get("/api/v1/deals/" + prepared.dealId() + "/disputes")
                        .with(user(outsider.userId.toString()))
                        .header(LEGAL_ENTITY_HEADER, outsider.legalEntityId))
                .andExpect(status().isNotFound());
    }

    @Test
    void openRejectsStaleVersionsAndActiveCaseConflicts() throws Exception {
        PreparedDeal prepared = prepareReviewRequiredDeal();

        mockMvc.perform(post("/api/v1/deals/" + prepared.dealId() + "/disputes")
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openRequest("OTHER", "Issue", "Details here.",
                                prepared.dealVersion() + 1, prepared.fulfillmentVersion())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEAL_STALE_VERSION"));

        mockMvc.perform(post("/api/v1/deals/" + prepared.dealId() + "/disputes")
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openRequest("OTHER", "Issue", "Details here.",
                                prepared.dealVersion(), prepared.fulfillmentVersion() + 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FULFILLMENT_STALE_VERSION"));

        mockMvc.perform(post("/api/v1/deals/" + prepared.dealId() + "/disputes")
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openRequest("OTHER", "Issue", "Details here.",
                                prepared.dealVersion(), prepared.fulfillmentVersion())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/deals/" + prepared.dealId() + "/disputes")
                        .with(user(sellerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, sellerAdmin.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openRequest("OTHER", "Another", "Second case.",
                                prepared.dealVersion(), prepared.fulfillmentVersion())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISPUTE_ACTIVE_CASE_EXISTS"));
    }

    @Test
    void openRequiresStartedFulfillment() throws Exception {
        UUID dealId = createActiveFundedDeal();
        long dealVersion = dealVersion(dealId);

        mockMvc.perform(post("/api/v1/deals/" + dealId + "/disputes")
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openRequest("OTHER", "Issue", "Details here.", dealVersion, 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FULFILLMENT_STATE_CONFLICT"));
    }

    @Test
    void validationRejectsUnknownReasonCode() throws Exception {
        PreparedDeal prepared = prepareReviewRequiredDeal();

        mockMvc.perform(post("/api/v1/deals/" + prepared.dealId() + "/disputes")
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openRequest("NOT_A_REASON", "Issue", "Details here.",
                                prepared.dealVersion(), prepared.fulfillmentVersion())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("reasonCode"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_ENUM"));
    }

    @Test
    void buyerAdminCanOpenDisputeAfterEvidenceRejection() throws Exception {
        UUID dealId = createActiveFundedDeal();
        startFulfillment(dealId);
        String submissionId = submitEvidence(dealId);
        mockMvc.perform(post("/api/v1/deals/" + dealId
                        + "/fulfillment/evidence/" + submissionId + "/reject")
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\": 0, \"expectedEvidenceVersion\": 1, \"reason\": \"Missing tax number\"}"))
                .andExpect(status().isOk());
        long dealVersion = dealVersion(dealId);
        long fulfillmentVersion = fulfillmentVersion(dealId);

        mockMvc.perform(post("/api/v1/deals/" + dealId + "/disputes")
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openRequest("EVIDENCE_REJECTION", "Rejected evidence", "Evidence was rejected.",
                                dealVersion, fulfillmentVersion)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.openingSnapshot.evidence[0].statusAtOpen").value("REJECTED"))
                .andExpect(jsonPath("$.openingSnapshot.evidence[0].rejectedAt").exists())
                .andExpect(jsonPath("$.openingSnapshot.evidence[0].rejectionReason").value("Missing tax number"));
    }

    @Test
    void validationRejectsBlankSubject() throws Exception {
        PreparedDeal prepared = prepareReviewRequiredDeal();

        mockMvc.perform(post("/api/v1/deals/" + prepared.dealId() + "/disputes")
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openRequest("OTHER", "   ", "Details here.",
                                prepared.dealVersion(), prepared.fulfillmentVersion())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void concurrentDistinctKeyOpensProduceOneActiveCase() throws Exception {
        PreparedDeal prepared = prepareReviewRequiredDeal();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger createdCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        Future<?> first = executor.submit(() -> recordOpenOutcome(
                prepared, buyerAdmin, createdCount, conflictCount, start));
        Future<?> second = executor.submit(() -> recordOpenOutcome(
                prepared, sellerAdmin, createdCount, conflictCount, start));

        start.countDown();
        first.get(15, TimeUnit.SECONDS);
        second.get(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(1, createdCount.get());
        assertEquals(1, conflictCount.get());
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM dispute_case WHERE deal_id = ? AND status IN ('OPEN', 'UNDER_REVIEW')",
                Long.class, prepared.dealId()));
    }

    @Test
    void crossDealDisputeIdIsHidden() throws Exception {
        PreparedDeal first = prepareReviewRequiredDeal();
        PreparedDeal second = prepareReviewRequiredDeal();

        MvcResult created = mockMvc.perform(post("/api/v1/deals/" + first.dealId() + "/disputes")
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openRequest("OTHER", "Issue", "Details here.",
                                first.dealVersion(), first.fulfillmentVersion())))
                .andExpect(status().isCreated())
                .andReturn();
        String disputeId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/deals/" + second.dealId() + "/disputes/" + disputeId)
                        .with(user(buyerAdmin.userId.toString()))
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DISPUTE_NOT_FOUND_OR_HIDDEN"));
    }

    private void recordOpenOutcome(
            PreparedDeal prepared,
            Principal actor,
            AtomicInteger createdCount,
            AtomicInteger conflictCount,
            CountDownLatch start) {
        try {
            start.await();
            MvcResult result = mockMvc.perform(post("/api/v1/deals/" + prepared.dealId() + "/disputes")
                            .with(user(actor.userId.toString())).with(csrf())
                            .header(LEGAL_ENTITY_HEADER, actor.legalEntityId)
                            .header("Idempotency-Key", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(openRequest("OTHER", "Issue", "Details here.",
                                    prepared.dealVersion(), prepared.fulfillmentVersion())))
                    .andReturn();
            if (result.getResponse().getStatus() == 201) {
                createdCount.incrementAndGet();
            } else if (result.getResponse().getStatus() == 409) {
                conflictCount.incrementAndGet();
            } else {
                throw new AssertionError("Unexpected open status: " + result.getResponse().getStatus());
            }
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    @Test
    void collaborationLifecycleCoversCommentsAcknowledgeAndWithdraw() throws Exception {
        PreparedDeal prepared = prepareReviewRequiredDeal();
        OpenedDispute opened = openDispute(prepared);

        mockMvc.perform(post(commentPath(opened), opened.version())
                        .with(user(sellerMember.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, sellerMember.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentRequest("Seller follow-up", opened.version())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorAttribution.legalName").value("Seller Co"))
                .andExpect(jsonPath("$.body").value("Seller follow-up"));

        mockMvc.perform(get("/api/v1/deals/" + opened.dealId() + "/disputes/" + opened.disputeId() + "/comments")
                        .with(user(buyerMember.userId.toString()))
                        .header(LEGAL_ENTITY_HEADER, buyerMember.legalEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].body").value("Seller follow-up"));

        MvcResult acknowledged = mockMvc.perform(post(acknowledgePath(opened), opened.version())
                        .with(user(sellerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, sellerAdmin.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionRequest(opened.version())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.availableActions.canAcknowledge").value(false))
                .andReturn();
        long acknowledgedVersion = ((Number) JsonPath.read(
                acknowledged.getResponse().getContentAsString(), "$.version")).longValue();

        mockMvc.perform(post(commentPath(opened), acknowledgedVersion)
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentRequest("Buyer update", acknowledgedVersion)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(withdrawPath(opened), acknowledgedVersion + 1)
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionRequest(acknowledgedVersion + 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.availableActions.canComment").value(false));

        mockMvc.perform(get("/api/v1/deals/" + opened.dealId() + "/disputes/" + opened.disputeId())
                        .with(user(sellerMember.userId.toString()))
                        .header(LEGAL_ENTITY_HEADER, sellerMember.legalEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.withdrawnAt").exists());
    }

    @Test
    void memberCanCommentButCannotAcknowledgeOrWithdraw() throws Exception {
        PreparedDeal prepared = prepareReviewRequiredDeal();
        OpenedDispute opened = openDispute(prepared);

        mockMvc.perform(post(commentPath(opened), opened.version())
                        .with(user(buyerMember.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerMember.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentRequest("Member note", opened.version())))
                .andExpect(status().isCreated());

        mockMvc.perform(post(acknowledgePath(opened), opened.version())
                        .with(user(sellerMember.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, sellerMember.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionRequest(opened.version())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DISPUTE_ACKNOWLEDGE_FORBIDDEN"));

        mockMvc.perform(post(withdrawPath(opened), opened.version())
                        .with(user(buyerMember.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerMember.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionRequest(opened.version())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DISPUTE_WITHDRAW_FORBIDDEN"));
    }

    @Test
    void commentOnWithdrawnDisputeReturnsStateConflict() throws Exception {
        PreparedDeal prepared = prepareReviewRequiredDeal();
        OpenedDispute opened = openDispute(prepared);

        mockMvc.perform(post(withdrawPath(opened), opened.version())
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionRequest(opened.version())))
                .andExpect(status().isOk());

        mockMvc.perform(post(commentPath(opened), opened.version() + 1)
                        .with(user(sellerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, sellerAdmin.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentRequest("Too late", opened.version() + 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISPUTE_STATE_CONFLICT"));
    }

    @Test
    void staleCommentVersionReturnsConflict() throws Exception {
        PreparedDeal prepared = prepareReviewRequiredDeal();
        OpenedDispute opened = openDispute(prepared);

        mockMvc.perform(post(commentPath(opened), opened.version())
                        .with(user(sellerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, sellerAdmin.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentRequest("First", opened.version())))
                .andExpect(status().isCreated());

        mockMvc.perform(post(commentPath(opened), opened.version())
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentRequest("Stale", opened.version())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISPUTE_STALE_VERSION"));
    }

    @Test
    void commentIdempotencyReplaysOriginal() throws Exception {
        PreparedDeal prepared = prepareReviewRequiredDeal();
        OpenedDispute opened = openDispute(prepared);
        UUID idempotencyKey = UUID.randomUUID();

        MvcResult first = mockMvc.perform(post(commentPath(opened), opened.version())
                        .with(user(sellerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, sellerAdmin.legalEntityId)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentRequest("Replay me", opened.version())))
                .andExpect(status().isCreated())
                .andReturn();
        String commentId = JsonPath.read(first.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post(commentPath(opened), opened.version())
                        .with(user(sellerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, sellerAdmin.legalEntityId)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentRequest("Replay me", opened.version())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(commentId));
    }

    @Test
    void concurrentCommentAttemptsHaveSingleVersionWinner() throws Exception {
        PreparedDeal prepared = prepareReviewRequiredDeal();
        OpenedDispute opened = openDispute(prepared);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger stale = new AtomicInteger();

        Future<?> buyer = executor.submit(() -> postCommentAsync(
                opened, buyerAdmin, opened.version(), "Buyer race", created, stale));
        Future<?> seller = executor.submit(() -> postCommentAsync(
                opened, sellerAdmin, opened.version(), "Seller race", created, stale));

        buyer.get(15, TimeUnit.SECONDS);
        seller.get(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(1, created.get());
        assertEquals(1, stale.get());
    }

    @Test
    void terminalWriterWinsWhenResultCommitsBeforeOpen() throws Exception {
        PreparedVideoDeal prepared = prepareVideoReviewRequiredDeal();
        UUID resultId = completeVideoJob(prepared.videoJobId());

        mockMvc.perform(post("/api/v1/deals/" + prepared.dealId() + "/disputes")
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openRequest("EVIDENCE_QUALITY", "Video issue", "Analysis mismatch.",
                                prepared.dealVersion(), prepared.fulfillmentVersion())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.openingSnapshot.videoAnalysis.length()").value(1))
                .andExpect(jsonPath("$.openingSnapshot.videoAnalysis[0].jobId").value(prepared.videoJobId().toString()))
                .andExpect(jsonPath("$.openingSnapshot.videoAnalysis[0].resultId").value(resultId.toString()));
    }

    @Test
    void openingWinsConcurrentRaceAgainstTerminalWriter() throws Exception {
        PreparedVideoDeal prepared = prepareVideoReviewRequiredDeal();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch externalLockHeld = new CountDownLatch(1);
        CountDownLatch releaseExternalLock = new CountDownLatch(1);

        Future<?> locker = executor.submit(() -> jdbc.execute(
                (org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
                    connection.setAutoCommit(false);
                    try (var statement = connection.prepareStatement("""
                            SELECT id FROM fulfillment_video_analysis_job
                            WHERE id = ? FOR UPDATE
                            """)) {
                        statement.setObject(1, prepared.videoJobId());
                        statement.executeQuery();
                        externalLockHeld.countDown();
                        releaseExternalLock.await(10, TimeUnit.SECONDS);
                        connection.commit();
                    } catch (Exception exception) {
                        connection.rollback();
                        throw new RuntimeException(exception);
                    }
                    return null;
                }));

        assertTrue(externalLockHeld.await(10, TimeUnit.SECONDS));

        Future<MvcResult> openFuture = executor.submit(() -> mockMvc.perform(
                        post("/api/v1/deals/" + prepared.dealId() + "/disputes")
                                .with(user(buyerAdmin.userId.toString())).with(csrf())
                                .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                                .header("Idempotency-Key", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(openRequest("EVIDENCE_QUALITY", "Video issue", "Race test.",
                                        prepared.dealVersion(), prepared.fulfillmentVersion())))
                .andExpect(status().isCreated())
                .andReturn());

        Thread.sleep(200);
        Future<?> completeFuture = executor.submit(() -> completeVideoJob(prepared.videoJobId()));

        releaseExternalLock.countDown();

        MvcResult openResult = openFuture.get(15, TimeUnit.SECONDS);
        locker.get(15, TimeUnit.SECONDS);
        completeFuture.get(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(0, ((Number) JsonPath.read(
                openResult.getResponse().getContentAsString(),
                "$.openingSnapshot.videoAnalysis.length()")).intValue());
    }

    private UUID completeVideoJob(UUID jobId) {
        UUID resultId = UUID.randomUUID();
        jdbc.update("""
                UPDATE fulfillment_video_analysis_job
                SET status = 'RESULT_AVAILABLE', completed_at = CURRENT_TIMESTAMP, version = version + 1
                WHERE id = ? AND status = 'QUEUED'
                """, jobId);
        jdbc.update("""
                INSERT INTO fulfillment_video_analysis_result (id, job_id, schema_version, canonical_result, created_at)
                VALUES (?, ?, '1.0.0',
                    '{"result":{"durationMs":1,"observations":[],"anomalies":[],"summary":{}},"warnings":[]}'::jsonb,
                    CURRENT_TIMESTAMP)
                """, resultId, jobId);
        return resultId;
    }

    private OpenedDispute openDispute(PreparedDeal prepared) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/deals/" + prepared.dealId() + "/disputes")
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openRequest("NON_DELIVERY", "Late delivery", "Goods never arrived.",
                                prepared.dealVersion(), prepared.fulfillmentVersion())))
                .andExpect(status().isCreated())
                .andReturn();
        String body = created.getResponse().getContentAsString();
        return new OpenedDispute(
                prepared.dealId(),
                UUID.fromString(JsonPath.read(body, "$.id")),
                ((Number) JsonPath.read(body, "$.version")).longValue());
    }

    private String commentPath(OpenedDispute opened) {
        return "/api/v1/deals/" + opened.dealId() + "/disputes/" + opened.disputeId() + "/comments";
    }

    private String acknowledgePath(OpenedDispute opened) {
        return "/api/v1/deals/" + opened.dealId() + "/disputes/" + opened.disputeId() + "/acknowledge";
    }

    private String withdrawPath(OpenedDispute opened) {
        return "/api/v1/deals/" + opened.dealId() + "/disputes/" + opened.disputeId() + "/withdraw";
    }

    private String commentRequest(String body, long expectedVersion) {
        return """
                {"body": "%s", "expectedVersion": %d}
                """.formatted(body, expectedVersion);
    }

    private String versionRequest(long expectedVersion) {
        return "{\"expectedVersion\": " + expectedVersion + "}";
    }

    private void postCommentAsync(
            OpenedDispute opened,
            Principal actor,
            long expectedVersion,
            String body,
            AtomicInteger created,
            AtomicInteger stale) {
        try {
            int status = mockMvc.perform(post(commentPath(opened))
                            .with(user(actor.userId.toString())).with(csrf())
                            .header(LEGAL_ENTITY_HEADER, actor.legalEntityId)
                            .header("Idempotency-Key", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(commentRequest(body, expectedVersion)))
                    .andReturn()
                    .getResponse()
                    .getStatus();
            if (status == 201) {
                created.incrementAndGet();
            } else if (status == 409) {
                stale.incrementAndGet();
            } else {
                throw new IllegalStateException("Unexpected status: " + status);
            }
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private PreparedDeal prepareReviewRequiredDeal() throws Exception {
        UUID dealId = createActiveFundedDeal();
        startFulfillment(dealId);
        submitEvidence(dealId);
        return new PreparedDeal(dealId, dealVersion(dealId), fulfillmentVersion(dealId));
    }

    private PreparedVideoDeal prepareVideoReviewRequiredDeal() throws Exception {
        UUID dealId = createActiveFundedDeal();
        startFulfillment(dealId);
        String submissionId = submitVideoEvidence(dealId);
        long dealVersion = dealVersion(dealId);
        long fulfillmentVersion = fulfillmentVersion(dealId);
        MvcResult requested = mockMvc.perform(post("/api/v1/deals/" + dealId
                        + "/fulfillment/evidence/" + submissionId + "/video-analysis")
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedEvidenceVersion\": 1}"))
                .andExpect(status().isAccepted())
                .andReturn();
        UUID jobId = UUID.fromString(JsonPath.read(requested.getResponse().getContentAsString(), "$.id"));
        return new PreparedVideoDeal(dealId, dealVersion, fulfillmentVersion, jobId);
    }

    private String submitVideoEvidence(UUID dealId) throws Exception {
        storage.setVerified(new FulfillmentObjectStorage.VerifiedObject(
                2048, SHA, "immutable-version", "video/mp4"));
        MvcResult intent = mockMvc.perform(post("/api/v1/deals/" + dealId + "/fulfillment/evidence/upload-intents")
                        .with(user(sellerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, sellerAdmin.legalEntityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "evidenceType": "VIDEO",
                                  "mediaType": "video/mp4",
                                  "fileName": "delivery.mp4",
                                  "sizeBytes": 2048,
                                  "sha256": "%s"
                                }
                                """.formatted(SHA)))
                .andExpect(status().isCreated())
                .andReturn();
        String submissionId = JsonPath.read(intent.getResponse().getContentAsString(), "$.evidence.id");
        mockMvc.perform(post("/api/v1/deals/" + dealId + "/fulfillment/evidence/" + submissionId + "/finalize")
                        .with(user(sellerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, sellerAdmin.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sizeBytes\": 2048, \"sha256\": \"" + SHA + "\"}"))
                .andExpect(status().isOk());
        return submissionId;
    }

    private String openRequest(
            String reasonCode, String subject, String statement,
            long expectedDealVersion, long expectedFulfillmentVersion) {
        return """
                {
                  "reasonCode": "%s",
                  "subject": "%s",
                  "statement": "%s",
                  "expectedDealVersion": %d,
                  "expectedFulfillmentVersion": %d
                }
                """.formatted(reasonCode, subject, statement, expectedDealVersion, expectedFulfillmentVersion);
    }

    private long dealVersion(UUID dealId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/deals/" + dealId)
                        .with(user(buyerAdmin.userId.toString()))
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId))
                .andExpect(status().isOk())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.version")).longValue();
    }

    private long fulfillmentVersion(UUID dealId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/deals/" + dealId + "/fulfillment")
                        .with(user(buyerAdmin.userId.toString()))
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId))
                .andExpect(status().isOk())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.version")).longValue();
    }

    private void startFulfillment(UUID dealId) throws Exception {
        mockMvc.perform(post("/api/v1/deals/" + dealId + "/fulfillment")
                        .with(user(sellerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, sellerAdmin.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\": " + dealVersion(dealId) + "}"))
                .andExpect(status().isCreated());
    }

    private String submitEvidence(UUID dealId) throws Exception {
        MvcResult intent = mockMvc.perform(post("/api/v1/deals/" + dealId + "/fulfillment/evidence/upload-intents")
                        .with(user(sellerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, sellerAdmin.legalEntityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "evidenceType": "DELIVERY_NOTE",
                                  "mediaType": "application/pdf",
                                  "fileName": "note.pdf",
                                  "sizeBytes": 12,
                                  "sha256": "%s"
                                }
                                """.formatted(SHA)))
                .andExpect(status().isCreated())
                .andReturn();
        String submissionId = JsonPath.read(intent.getResponse().getContentAsString(), "$.evidence.id");
        mockMvc.perform(post("/api/v1/deals/" + dealId + "/fulfillment/evidence/" + submissionId + "/finalize")
                        .with(user(sellerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, sellerAdmin.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sizeBytes\": 12, \"sha256\": \"" + SHA + "\"}"))
                .andExpect(status().isOk());
        return submissionId;
    }

    private UUID createActiveFundedDeal() throws Exception {
        UUID dealId = createActiveDeal();
        seedFunding(dealId);
        return dealId;
    }

    private UUID createActiveDeal() throws Exception {
        MvcResult dealResult = mockMvc.perform(post("/api/v1/deals")
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Dispute Deal\"}"))
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
        approve(dealId, packageId, buyerAdmin, 0);
        approve(dealId, packageId, sellerAdmin, 1);
        return dealId;
    }

    private void approve(UUID dealId, UUID packageId, Principal actor, long expectedPackageVersion) throws Exception {
        mockMvc.perform(post("/api/v1/deals/" + dealId + "/ratification-packages/" + packageId + "/approve")
                        .with(user(actor.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, actor.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedPackageVersion\": " + expectedPackageVersion + "}"))
                .andExpect(status().isOk());
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
                """, documentId, dealId, "obj-" + documentId, SHA, SHA);
        jdbc.update("""
                UPDATE deal SET current_document_id = ?, current_document_status = 'AVAILABLE' WHERE id = ?
                """, documentId, dealId);
        UUID analysisJobId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO contract_intelligence_analysis_job (
                    id, tenant_id, deal_id, document_id, object_version, input_sha256, status,
                    requested_at, processing_started_at, completed_at
                ) VALUES (?, ?, ?, ?, 'v1', ?, 'ACCEPTED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, analysisJobId, tenantId, dealId, documentId, SHA);
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
        insertUser(userId, email);
        jdbc.update("INSERT INTO tenant (id) VALUES (?)", tenantId);
        jdbc.update("INSERT INTO tenant_user (user_id, tenant_id) VALUES (?, ?)", userId, tenantId);
        insertEntity(legalEntityId, tenantId, legalName);
        insertMembership(userId, legalEntityId, role);
        return new Principal(userId, tenantId, legalEntityId);
    }

    private Principal insertMember(Principal ofEntity, String email) {
        UUID userId = UUID.randomUUID();
        insertUser(userId, email);
        jdbc.update("INSERT INTO tenant_user (user_id, tenant_id) VALUES (?, ?)", userId, ofEntity.tenantId);
        insertMembership(userId, ofEntity.legalEntityId, "MEMBER");
        return new Principal(userId, ofEntity.tenantId, ofEntity.legalEntityId);
    }

    private void insertUser(UUID userId, String email) {
        jdbc.update("""
                INSERT INTO identity_user (id, email, password_hash, display_name, enabled)
                VALUES (?, ?, 'test-hash', 'Test User', true)
                """, userId, email);
    }

    private void insertEntity(UUID legalEntityId, UUID tenantId, String legalName) {
        jdbc.update("""
                INSERT INTO legal_entity (id, tenant_id, legal_name, registration_number)
                VALUES (?, ?, ?, ?)
                """, legalEntityId, tenantId, legalName, "REG-" + legalEntityId);
    }

    private void insertMembership(UUID userId, UUID legalEntityId, String role) {
        UUID tenantId = jdbc.queryForObject(
                "SELECT tenant_id FROM legal_entity WHERE id = ?", UUID.class, legalEntityId);
        jdbc.update("""
                INSERT INTO legal_entity_membership (id, tenant_id, legal_entity_id, user_id, role)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), tenantId, legalEntityId, userId, role);
    }

    private record PreparedDeal(UUID dealId, long dealVersion, long fulfillmentVersion) {
    }

    private record PreparedVideoDeal(UUID dealId, long dealVersion, long fulfillmentVersion, UUID videoJobId) {
    }

    private record OpenedDispute(UUID dealId, UUID disputeId, long version) {
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
        private volatile VerifiedObject verified =
                new VerifiedObject(12, SHA, "immutable-version", "application/pdf");

        void reset() {
            verified = new VerifiedObject(12, SHA, "immutable-version", "application/pdf");
        }

        void setVerified(VerifiedObject verified) {
            this.verified = verified;
        }

        @Override
        public DirectUpload createDirectUpload(String objectKey, String mediaType, long contentLength) {
            return new DirectUpload(URI.create("https://storage.example/" + objectKey),
                    Map.of("Content-Type", mediaType), Instant.now().plusSeconds(600));
        }

        @Override
        public DirectDownload createDirectDownload(String objectKey, String objectVersion) {
            return new DirectDownload(URI.create("https://storage.example/" + objectKey
                    + "?version=" + objectVersion), Instant.now().plusSeconds(300));
        }

        @Override
        public VerifiedObject verify(String objectKey) {
            return verified;
        }
    }
}
