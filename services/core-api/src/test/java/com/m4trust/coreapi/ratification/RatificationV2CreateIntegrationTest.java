package com.m4trust.coreapi.ratification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Testcontainers
class RatificationV2CreateIntegrationTest {

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

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM spring_session_attributes");
        jdbc.update("DELETE FROM spring_session");
        jdbc.execute("""
                TRUNCATE TABLE
                    dispute_comment, dispute_evidence_snapshot, dispute_case, fulfillment_video_analysis_result,
                    fulfillment_video_analysis_job, fulfillment_evidence_submission, fulfillment_milestone_rule_reference,
                    fulfillment_milestone, fulfillment, release_dispatch, release_operation, settlement,
                    payment_dispatch, payment_operation, funding_unit, funding_plan,
                    contract_intelligence_rule_set_version, contract_intelligence_extraction_result_version,
                    contract_intelligence_analysis_job, http_idempotency_record, deal_invitation, deal_participant,
                    document, ratification_package_approval, ratification_package, ratification_package_snapshot,
                    deal, audit_record, legal_entity_membership, legal_entity, tenant_user, tenant, identity_user
                """);
        buyer = insertPrincipal("buyer@example.test", "Buyer Co");
        seller = insertPrincipal("seller@example.test", "Seller Co");
    }

    @Test
    void createWithoutDisputeWindowProducesV1Snapshot() throws Exception {
        UUID dealId = createReadyDraftDeal();
        MvcResult created = createPackage(dealId, buyer, currentDealVersion(dealId), 5_000, "TRY", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.snapshot.schemaVersion").value(1))
                .andExpect(jsonPath("$.snapshot.disputeWindowDays").doesNotExist())
                .andReturn();
        UUID packageId = packageId(created);
        assertEquals(1, jdbc.queryForObject("""
                SELECT s.schema_version FROM ratification_package_snapshot s
                JOIN ratification_package p ON p.snapshot_id = s.id WHERE p.id = ?
                """, Integer.class, packageId));
    }

    @Test
    void createWithDisputeWindowProducesV2SnapshotAndDistinctHashes() throws Exception {
        UUID dealId = createReadyDraftDeal();
        long dealVersion = currentDealVersion(dealId);

        MvcResult zero = createPackage(dealId, buyer, dealVersion, 5_000, "TRY", 0)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.snapshot.schemaVersion").value(2))
                .andExpect(jsonPath("$.snapshot.disputeWindowDays").value(0))
                .andReturn();
        String zeroHash = JsonPath.read(zero.getResponse().getContentAsString(), "$.contentHash");

        MvcResult one = createPackage(dealId, buyer, currentDealVersion(dealId), 5_000, "TRY", 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.snapshot.schemaVersion").value(2))
                .andExpect(jsonPath("$.snapshot.disputeWindowDays").value(1))
                .andReturn();
        String oneHash = JsonPath.read(one.getResponse().getContentAsString(), "$.contentHash");

        assertNotEquals(zeroHash, oneHash);
        assertEquals(2, jdbc.queryForObject("""
                SELECT s.schema_version FROM ratification_package_snapshot s
                JOIN ratification_package p ON p.snapshot_id = s.id WHERE p.id = ?
                """, Integer.class, packageId(one)));
    }

    @Test
    void rejectsOutOfRangeDisputeWindowDays() throws Exception {
        UUID dealId = createReadyDraftDeal();
        long dealVersion = currentDealVersion(dealId);

        createPackage(dealId, buyer, dealVersion, 5_000, "TRY", -1)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("disputeWindowDays"));

        createPackage(dealId, buyer, dealVersion, 5_000, "TRY", 366)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("disputeWindowDays"));
    }

    private org.springframework.test.web.servlet.ResultActions createPackage(
            UUID dealId, Principal actor, long expectedVersion, long amountMinor, String currency,
            Integer disputeWindowDays) throws Exception {
        String disputeWindow = disputeWindowDays == null ? "" : ", \"disputeWindowDays\": " + disputeWindowDays;
        return mockMvc.perform(post("/api/v1/deals/" + dealId + "/ratification-packages")
                .with(user(actor.userId.toString())).with(csrf())
                .header(LEGAL_ENTITY_HEADER, actor.legalEntityId)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"expectedVersion": %d, "commercialTerms": {"amountMinor": %d, "currency": "%s"}%s}
                        """.formatted(expectedVersion, amountMinor, currency, disputeWindow)));
    }

    private long currentDealVersion(UUID dealId) {
        return jdbc.queryForObject("SELECT version FROM deal WHERE id = ?", Long.class, dealId);
    }

    private UUID packageId(MvcResult result) throws Exception {
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private UUID createReadyDraftDeal() throws Exception {
        MvcResult dealResult = mockMvc.perform(post("/api/v1/deals")
                        .with(user(buyer.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyer.legalEntityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Ratification Deal\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID dealId = UUID.fromString(JsonPath.read(dealResult.getResponse().getContentAsString(), "$.id"));
        jdbc.update("""
                INSERT INTO deal_participant (deal_id, tenant_id, legal_entity_id, legal_entity_tenant_id)
                SELECT id, tenant_id, ?, ? FROM deal WHERE id = ?
                """, seller.legalEntityId, seller.tenantId, dealId);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                                "/api/v1/deals/" + dealId + "/parties")
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
        jdbc.update("UPDATE deal SET current_document_id = ?, current_document_status = 'AVAILABLE' WHERE id = ?",
                documentId, dealId);
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
