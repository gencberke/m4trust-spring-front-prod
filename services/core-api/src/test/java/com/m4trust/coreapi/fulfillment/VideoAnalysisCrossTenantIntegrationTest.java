package com.m4trust.coreapi.fulfillment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
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
@Import(VideoAnalysisCrossTenantIntegrationTest.Fakes.class)
class VideoAnalysisCrossTenantIntegrationTest {

    private static final String LEGAL_ENTITY_HEADER = "X-M4Trust-Legal-Entity-Id";
    private static final String SHA = "e".repeat(64);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.5-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingStorage storage;

    private UUID hostTenantId;
    private UUID sellerTenantId;
    private UUID buyerAdminUserId;
    private UUID buyerAdminEntityId;
    private UUID sellerAdminUserId;
    private UUID sellerAdminEntityId;
    private UUID dealId;
    private UUID packageId;

    @BeforeEach
    void setUp() {
        storage.reset();
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

        hostTenantId = UUID.randomUUID();
        sellerTenantId = UUID.randomUUID();
        buyerAdminUserId = UUID.randomUUID();
        buyerAdminEntityId = UUID.randomUUID();
        sellerAdminUserId = UUID.randomUUID();
        sellerAdminEntityId = UUID.randomUUID();
        dealId = UUID.randomUUID();
        packageId = UUID.randomUUID();

        jdbc.update("INSERT INTO tenant (id) VALUES (?)", hostTenantId);
        jdbc.update("INSERT INTO tenant (id) VALUES (?)", sellerTenantId);

        insertUser(buyerAdminUserId, "buyer-admin@example.test");
        insertUser(sellerAdminUserId, "seller-admin@example.test");
        jdbc.update("INSERT INTO tenant_user (user_id, tenant_id) VALUES (?, ?)", buyerAdminUserId, hostTenantId);
        jdbc.update("INSERT INTO tenant_user (user_id, tenant_id) VALUES (?, ?)", sellerAdminUserId, sellerTenantId);

        insertEntity(buyerAdminEntityId, hostTenantId, "Buyer Co");
        insertEntity(sellerAdminEntityId, sellerTenantId, "Seller Co");
        insertMembership(buyerAdminUserId, hostTenantId, buyerAdminEntityId, "ADMIN");
        insertMembership(sellerAdminUserId, sellerTenantId, sellerAdminEntityId, "ADMIN");

        seedDeal();
        seedFunding();
    }

    @Test
    void crossTenantSellerFulfillmentVideoAnalysisRequestUsesDealHostingTenantForJob() throws Exception {
        mockMvc.perform(post("/api/v1/deals/" + dealId + "/fulfillment")
                        .with(user(sellerAdminUserId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, sellerAdminEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\": 3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        UUID fulfillmentId = jdbc.queryForObject("SELECT id FROM fulfillment WHERE deal_id = ?", UUID.class, dealId);
        assertEquals(sellerTenantId, jdbc.queryForObject(
                "SELECT tenant_id FROM fulfillment WHERE id = ?", UUID.class, fulfillmentId));

        storage.configureVerify(2048, SHA, "version-1", "video/mp4");

        MvcResult intentResult = mockMvc.perform(post("/api/v1/deals/" + dealId
                        + "/fulfillment/evidence/upload-intents")
                        .with(user(sellerAdminUserId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, sellerAdminEntityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadIntentRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evidence.mediaType").value("video/mp4"))
                .andReturn();
        String submissionId = JsonPath.read(intentResult.getResponse().getContentAsString(), "$.evidence.id");

        mockMvc.perform(post("/api/v1/deals/" + dealId
                        + "/fulfillment/evidence/" + submissionId + "/finalize")
                        .with(user(sellerAdminUserId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, sellerAdminEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sizeBytes\": 2048, \"sha256\": \"" + SHA + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        long evidenceVersion = jdbc.queryForObject(
                "SELECT version FROM fulfillment_evidence_submission WHERE id = ?", Long.class,
                UUID.fromString(submissionId));

        int jobsBefore = count("fulfillment_video_analysis_job");
        int outboxBefore = count("integration_outbox_event");
        int videoAuditBefore = countAudit("VIDEO_ANALYSIS_REQUESTED");
        int idempotencyBefore = count("http_idempotency_record");

        mockMvc.perform(post("/api/v1/deals/" + dealId + "/fulfillment/evidence/" + submissionId + "/video-analysis")
                        .with(user(buyerAdminUserId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdminEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedEvidenceVersion\": " + evidenceVersion + "}"))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("QUEUED"));

        assertEquals(sellerTenantId, jdbc.queryForObject(
                "SELECT tenant_id FROM fulfillment WHERE id = ?", UUID.class, fulfillmentId));
        assertEquals(hostTenantId, jdbc.queryForObject(
                "SELECT tenant_id FROM fulfillment_video_analysis_job LIMIT 1", UUID.class));
        assertEquals(hostTenantId, jdbc.queryForObject("SELECT tenant_id FROM deal WHERE id = ?", UUID.class, dealId));
        assertEquals(hostTenantId.toString(), jdbc.queryForObject(
                "SELECT payload->>'tenantId' FROM integration_outbox_event WHERE payload->>'jobType' = 'VIDEO_ANALYSIS' LIMIT 1",
                String.class));
        assertEquals(hostTenantId, jdbc.queryForObject(
                "SELECT tenant_id FROM audit_record WHERE action = 'VIDEO_ANALYSIS_REQUESTED' LIMIT 1", UUID.class));
        assertEquals(jobsBefore + 1, count("fulfillment_video_analysis_job"));
        assertEquals(outboxBefore + 1, count("integration_outbox_event"));
        assertEquals(videoAuditBefore + 1, countAudit("VIDEO_ANALYSIS_REQUESTED"));
        assertEquals(idempotencyBefore + 1, count("http_idempotency_record"));
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

    private void seedDeal() {
        jdbc.update("""
                INSERT INTO deal (id, tenant_id, reference, title, deal_status, initiator_legal_entity_id,
                    created_by, version)
                VALUES (?, ?, 'DL-0000000099', 'Cross Tenant Deal', 'ACTIVE', ?, ?, 3)
                """, dealId, hostTenantId, buyerAdminEntityId, buyerAdminUserId);
        jdbc.update("""
                INSERT INTO deal_participant (deal_id, tenant_id, legal_entity_id, legal_entity_tenant_id)
                VALUES (?, ?, ?, ?)
                """, dealId, hostTenantId, buyerAdminEntityId, hostTenantId);
        jdbc.update("""
                INSERT INTO deal_participant (deal_id, tenant_id, legal_entity_id, legal_entity_tenant_id)
                VALUES (?, ?, ?, ?)
                """, dealId, hostTenantId, sellerAdminEntityId, sellerTenantId);

        RatificationPackageSeedSupport.SeededSnapshot snapshot = RatificationPackageSeedSupport.seedSnapshot(
                objectMapper, dealId, hostTenantId, buyerAdminEntityId, sellerAdminEntityId, packageId,
                "DL-0000000099", SHA);
        jdbc.update("""
                INSERT INTO ratification_package_snapshot (id, schema_version, canonical_snapshot, content_hash, created_at)
                VALUES (?, 1, CAST(? AS jsonb), ?, CURRENT_TIMESTAMP)
                """, snapshot.snapshotId(), snapshot.serializedSnapshot(), snapshot.contentHash());
        jdbc.update("""
                INSERT INTO ratification_package (id, deal_id, snapshot_id, version, status,
                    buyer_legal_entity_id, seller_legal_entity_id, amount_minor, currency, created_at)
                VALUES (?, ?, ?, 1, 'RATIFIED', ?, ?, 5000, 'TRY', CURRENT_TIMESTAMP)
                """, packageId, dealId, snapshot.snapshotId(), buyerAdminEntityId, sellerAdminEntityId);
        jdbc.update("""
                UPDATE deal
                SET buyer_legal_entity_id = ?, seller_legal_entity_id = ?, current_ratification_package_id = ?
                WHERE id = ?
                """, buyerAdminEntityId, sellerAdminEntityId, packageId, dealId);
    }

    private void seedFunding() {
        UUID planId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO funding_plan (id, deal_id, ratification_package_id, tenant_id,
                    amount_minor, currency, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'TRY', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, planId, dealId, packageId, hostTenantId, 5000L);
        jdbc.update("""
                INSERT INTO funding_unit (id, funding_plan_id, sequence_no, amount_minor,
                    currency, status, version, created_at, updated_at)
                VALUES (?, ?, 1, ?, 'TRY', 'FUNDED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), planId, 5000L);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private int countAudit(String action) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_record WHERE action = ?", Integer.class, action);
    }

    private void insertUser(UUID userId, String email) {
        jdbc.update("""
                INSERT INTO identity_user (id, email, password_hash, display_name, enabled)
                VALUES (?, ?, 'test-hash', 'Test User', true)
                """, userId, email);
    }

    private void insertEntity(UUID entityId, UUID entityTenantId, String legalName) {
        jdbc.update("""
                INSERT INTO legal_entity (id, tenant_id, legal_name, registration_number)
                VALUES (?, ?, ?, ?)
                """, entityId, entityTenantId, legalName, "REG-" + entityId);
    }

    private void insertMembership(UUID userId, UUID entityTenantId, UUID entityId, String role) {
        jdbc.update("""
                INSERT INTO legal_entity_membership (id, tenant_id, legal_entity_id, user_id, role)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), entityTenantId, entityId, userId, role);
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

        void reset() {
            configureVerify(2048, SHA, "version-1", "video/mp4");
        }

        @Override
        public DirectUpload createDirectUpload(String objectKey, String mediaType, long contentLength) {
            Instant expiresAt = Instant.now().plusSeconds(600);
            return new DirectUpload(URI.create("https://storage.example/" + objectKey),
                    Map.of("Content-Type", mediaType), expiresAt);
        }

        @Override
        public DirectDownload createDirectDownload(String objectKey, String objectVersion) {
            return new DirectDownload(URI.create("https://storage.example/" + objectKey
                    + "?version=" + objectVersion), Instant.now().plusSeconds(300));
        }

        @Override
        public VerifiedObject verify(String objectKey) {
            return new VerifiedObject(verifiedSize, verifiedSha, verifiedVersion, verifiedMediaType);
        }
    }
}
