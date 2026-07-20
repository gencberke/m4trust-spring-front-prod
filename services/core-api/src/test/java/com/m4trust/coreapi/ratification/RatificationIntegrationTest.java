package com.m4trust.coreapi.ratification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end HTTP coverage for the ratification surface (create/history/detail/
 * approve/reject), plus the §8 minimum invariants that need a real Deal +
 * document + accepted rule-set: the withdrawal-vs-last-approval race in both
 * orderings, and the approve-vs-supersede race. Each "race" here is proven by
 * ordering two real, fully-committed HTTP calls rather than true thread
 * concurrency: DealCancelRaceIntegrationTest already proves the shared
 * Deal-row lock (used by every one of these mutations) actually blocks a
 * second transaction until the first commits, so whichever call completes
 * first is exactly what a real concurrent race would serialize to.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Testcontainers
class RatificationIntegrationTest {

    private static final String LEGAL_ENTITY_HEADER = "X-M4Trust-Legal-Entity-Id";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.5-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mockMvc;

    private Principal buyer;
    private Principal seller;
    private Principal sellerMember;
    private Principal outsider;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM spring_session_attributes");
        jdbc.update("DELETE FROM spring_session");
        jdbc.execute("""
                TRUNCATE TABLE
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
        buyer = insertPrincipal("buyer@example.test", "Buyer Co");
        seller = insertPrincipal("seller@example.test", "Seller Co");
        sellerMember = insertMember(seller, "seller-member@example.test");
        outsider = insertPrincipal("outsider@example.test", "Outsider Co");
    }

    @Test
    void happyPathCreateApproveApproveActivatesTheDealAndPackage() throws Exception {
        UUID dealId = createReadyDraftDeal();

        MvcResult created = createPackage(dealId, buyer, 1, 5_000, "TRY")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(
                        "/api/v1/deals/" + dealId + "/ratification-packages/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.snapshot.commercialTerms.amountMinor").value(5000))
                .andExpect(jsonPath("$.snapshot.commercialTerms.currency").value("TRY"))
                .andExpect(jsonPath("$.approvals", org.hamcrest.Matchers.hasSize(2)))
                // The creator (buyer, the initiator) is itself an assigned
                // ADMIN party that has not approved yet.
                .andExpect(jsonPath("$.availableActions.canApprove").value(true))
                .andReturn();
        UUID packageId = packageId(created);

        mockMvc.perform(get(packagePath(dealId, packageId))
                        .with(user(buyer.userId.toString()))
                        .header(LEGAL_ENTITY_HEADER, buyer.legalEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(packageId.toString()));

        mockMvc.perform(get(historyPath(dealId))
                        .with(user(buyer.userId.toString()))
                        .header(LEGAL_ENTITY_HEADER, buyer.legalEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", org.hamcrest.Matchers.hasSize(1)));

        approve(dealId, packageId, buyer, 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.approvals[?(@.legalEntityId=='" + buyer.legalEntityId + "')].status")
                        .value("APPROVED"));

        approve(dealId, packageId, seller, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RATIFIED"));

        mockMvc.perform(get("/api/v1/deals/" + dealId)
                        .with(user(buyer.userId.toString()))
                        .header(LEGAL_ENTITY_HEADER, buyer.legalEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.lifecycle").value("FUNDING"))
                .andExpect(jsonPath("$.ratification.currentPackage.status").value("RATIFIED"))
                .andExpect(jsonPath("$.availableActions.canUpdate").value(false))
                .andExpect(jsonPath("$.availableActions.canCancel").value(false))
                .andExpect(jsonPath("$.availableActions.canManageParties").value(false))
                .andExpect(jsonPath("$.availableActions.canCreateRatificationPackage").value(false))
                .andExpect(jsonPath("$.availableActions.canApproveRatification").value(false))
                .andExpect(jsonPath("$.availableActions.canRejectRatification").value(false));

        // ACTIVE closes every DRAFT mutation surface server-side, not just in
        // the availableActions projection.
        mockMvc.perform(patch("/api/v1/deals/" + dealId)
                        .with(user(buyer.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyer.legalEntityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Post-activation edit\", \"description\": null, \"expectedVersion\": 3}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEAL_STATE_CONFLICT"));
        mockMvc.perform(patch("/api/v1/deals/" + dealId + "/parties")
                        .with(user(buyer.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyer.legalEntityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"buyerLegalEntityId": "%s", "sellerLegalEntityId": "%s", "expectedVersion": 3}
                                """.formatted(buyer.legalEntityId, seller.legalEntityId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEAL_STATE_CONFLICT"));
        mockMvc.perform(post("/api/v1/deals/" + dealId + "/cancel")
                        .with(user(buyer.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyer.legalEntityId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEAL_STATE_CONFLICT"));
    }

    @Test
    void detailIsNonDisclosingForANonparticipantAndForbiddenForAMemberApprove() throws Exception {
        UUID dealId = createReadyDraftDeal();
        UUID packageId = packageId(createPackage(dealId, buyer, 1, 5_000, "TRY")
                .andExpect(status().isCreated()).andReturn());

        mockMvc.perform(get(packagePath(dealId, packageId))
                        .with(user(outsider.userId.toString()))
                        .header(LEGAL_ENTITY_HEADER, outsider.legalEntityId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RATIFICATION_PACKAGE_NOT_FOUND"));

        approve(dealId, packageId, sellerMember, 0)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RATIFICATION_APPROVAL_FORBIDDEN"));
    }

    @Test
    void staleAndTerminalPackageVersionsConflict() throws Exception {
        UUID dealId = createReadyDraftDeal();
        UUID packageId = packageId(createPackage(dealId, buyer, 1, 5_000, "TRY")
                .andExpect(status().isCreated()).andReturn());

        approve(dealId, packageId, buyer, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RATIFICATION_STALE_PACKAGE"));

        reject(dealId, packageId, seller, 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        approve(dealId, packageId, buyer, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RATIFICATION_PACKAGE_STATE_CONFLICT"));
    }

    @Test
    void invalidCommercialTermsAndMissingIdempotencyKeyAreRejected() throws Exception {
        UUID dealId = createReadyDraftDeal();
        long dealVersion = currentDealVersion(dealId);

        mockMvc.perform(post(createPath(dealId))
                        .with(user(buyer.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyer.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion": %d, "commercialTerms": {"amountMinor": 0, "currency": "TRY"}}
                                """.formatted(dealVersion)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("commercialTerms.amountMinor"));

        mockMvc.perform(post(createPath(dealId))
                        .with(user(buyer.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyer.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion": %d, "commercialTerms": {"amountMinor": 100, "currency": "try"}}
                                """.formatted(dealVersion)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post(createPath(dealId))
                        .with(user(buyer.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyer.legalEntityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion": %d, "commercialTerms": {"amountMinor": 100, "currency": "TRY"}}
                                """.formatted(dealVersion)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM ratification_package", Integer.class));
    }

    @Test
    void partiesChangeSupersedesThePendingPackageAndAnEarlierApprovalBecomesStale() throws Exception {
        UUID dealId = createReadyDraftDeal();
        UUID packageId = packageId(createPackage(dealId, buyer, 1, 5_000, "TRY")
                .andExpect(status().isCreated()).andReturn());

        // Buyer approves first: package advances PENDING v0 -> v1.
        approve(dealId, packageId, buyer, 0).andExpect(status().isOk());

        // The initiator reassigns parties (a no-op reassignment still counts
        // as "changed" only when the assignment differs; here we swap and
        // swap back is avoided - reassigning to the same buyer/seller with a
        // participant addition forces a genuine assignment change instead).
        Principal thirdParty = insertPrincipal("third@example.test", "Third Co");
        insertParticipant(dealId, thirdParty);
        long dealVersion = currentDealVersion(dealId);
        mockMvc.perform(patch("/api/v1/deals/" + dealId + "/parties")
                        .with(user(buyer.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyer.legalEntityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"buyerLegalEntityId": "%s", "sellerLegalEntityId": "%s", "expectedVersion": %d}
                                """.formatted(buyer.legalEntityId, thirdParty.legalEntityId, dealVersion)))
                .andExpect(status().isOk());

        assertEquals("SUPERSEDED", jdbc.queryForObject(
                "SELECT status FROM ratification_package WHERE id = ?", String.class, packageId));

        // Seller's approve, still targeting the pre-supersede version, loses
        // the race and is told its target package is stale.
        approve(dealId, packageId, seller, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RATIFICATION_STALE_PACKAGE"));
    }

    @Test
    void secondRequiredApprovalWinningTheRaceLeavesWithdrawalConflicting() throws Exception {
        UUID dealId = createReadyDraftDeal();
        UUID packageId = packageId(createPackage(dealId, buyer, 1, 5_000, "TRY")
                .andExpect(status().isCreated()).andReturn());
        approve(dealId, packageId, buyer, 0).andExpect(status().isOk());

        // The last required approval completes first: package RATIFIED, Deal ACTIVE.
        approve(dealId, packageId, seller, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RATIFIED"));

        // The withdrawal (cancel), arriving after, now conflicts: the Deal it
        // targeted is no longer DRAFT.
        mockMvc.perform(post("/api/v1/deals/" + dealId + "/cancel")
                        .with(user(buyer.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyer.legalEntityId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEAL_STATE_CONFLICT"));
        assertEquals("ACTIVE", jdbc.queryForObject(
                "SELECT deal_status FROM deal WHERE id = ?", String.class, dealId));
    }

    @Test
    void withdrawalWinningTheRaceLeavesTheLastApprovalConflicting() throws Exception {
        UUID dealId = createReadyDraftDeal();
        UUID packageId = packageId(createPackage(dealId, buyer, 1, 5_000, "TRY")
                .andExpect(status().isCreated()).andReturn());
        approve(dealId, packageId, buyer, 0).andExpect(status().isOk());

        // The withdrawal completes first: it supersedes the PENDING package
        // under the same Deal lock, then cancels the Deal.
        mockMvc.perform(post("/api/v1/deals/" + dealId + "/cancel")
                        .with(user(buyer.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyer.legalEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertEquals("SUPERSEDED", jdbc.queryForObject(
                "SELECT status FROM ratification_package WHERE id = ?", String.class, packageId));

        // Seller's would-be last approval, arriving after, now conflicts: the
        // Deal lock is the cross-module race gate and is checked first.
        approve(dealId, packageId, seller, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEAL_STATE_CONFLICT"));
    }

    private org.springframework.test.web.servlet.ResultActions createPackage(
            UUID dealId, Principal actor, long expectedVersion, long amountMinor, String currency) throws Exception {
        return mockMvc.perform(post(createPath(dealId))
                .with(user(actor.userId.toString())).with(csrf())
                .header(LEGAL_ENTITY_HEADER, actor.legalEntityId)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"expectedVersion": %d, "commercialTerms": {"amountMinor": %d, "currency": "%s"}}
                        """.formatted(expectedVersion, amountMinor, currency)));
    }

    private org.springframework.test.web.servlet.ResultActions approve(
            UUID dealId, UUID packageId, Principal actor, long expectedPackageVersion) throws Exception {
        return mockMvc.perform(post(packagePath(dealId, packageId) + "/approve")
                .with(user(actor.userId.toString())).with(csrf())
                .header(LEGAL_ENTITY_HEADER, actor.legalEntityId)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedPackageVersion\": " + expectedPackageVersion + "}"));
    }

    private org.springframework.test.web.servlet.ResultActions reject(
            UUID dealId, UUID packageId, Principal actor, long expectedPackageVersion) throws Exception {
        return mockMvc.perform(post(packagePath(dealId, packageId) + "/reject")
                .with(user(actor.userId.toString())).with(csrf())
                .header(LEGAL_ENTITY_HEADER, actor.legalEntityId)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedPackageVersion\": " + expectedPackageVersion + "}"));
    }

    private String createPath(UUID dealId) {
        return "/api/v1/deals/" + dealId + "/ratification-packages";
    }

    private String packagePath(UUID dealId, UUID packageId) {
        return createPath(dealId) + "/" + packageId;
    }

    private String historyPath(UUID dealId) {
        return createPath(dealId);
    }

    private UUID packageId(MvcResult result) throws Exception {
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private long currentDealVersion(UUID dealId) {
        return jdbc.queryForObject("SELECT version FROM deal WHERE id = ?", Long.class, dealId);
    }

    /** Buyer-initiated DRAFT Deal with parties, an AVAILABLE document, and an ACCEPTED rule-set: READY. */
    private UUID createReadyDraftDeal() throws Exception {
        MvcResult dealResult = mockMvc.perform(post("/api/v1/deals")
                        .with(user(buyer.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyer.legalEntityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Ratification Deal\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID dealId = UUID.fromString(JsonPath.read(dealResult.getResponse().getContentAsString(), "$.id"));

        insertParticipant(dealId, seller);
        mockMvc.perform(patch("/api/v1/deals/" + dealId + "/parties")
                        .with(user(buyer.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyer.legalEntityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"buyerLegalEntityId": "%s", "sellerLegalEntityId": "%s", "expectedVersion": 0}
                                """.formatted(buyer.legalEntityId, seller.legalEntityId)))
                .andExpect(status().isOk());

        seedAvailableDocumentAndAcceptedRuleSet(dealId);
        return dealId;
    }

    private void seedAvailableDocumentAndAcceptedRuleSet(UUID dealId) {
        UUID tenantId = buyer.tenantId;
        String sha = "a".repeat(64);
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
        jdbc.update("""
                INSERT INTO contract_intelligence_rule_set_version (
                    id, deal_id, version, source_analysis_id, source_extraction_result_version_id,
                    created_by_user_id, created_at, rules, excluded_rule_references
                ) VALUES (?, ?, 1, ?, ?, ?, CURRENT_TIMESTAMP, '[]'::jsonb, '[]'::jsonb)
                """, ruleSetId, dealId, analysisJobId, extractionId, buyer.userId);
        jdbc.update("UPDATE deal SET current_rule_set_version_id = ? WHERE id = ?", ruleSetId, dealId);
    }

    private void insertParticipant(UUID dealId, Principal principal) {
        jdbc.update("""
                INSERT INTO deal_participant (deal_id, tenant_id, legal_entity_id, legal_entity_tenant_id)
                SELECT id, tenant_id, ?, ? FROM deal WHERE id = ?
                """, principal.legalEntityId, principal.tenantId, dealId);
    }

    private Principal insertPrincipal(String email, String legalName) {
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
                VALUES (?, ?, ?, ?, 'ADMIN')
                """, UUID.randomUUID(), tenantId, legalEntityId, userId);
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
}
