package com.m4trust.coreapi.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end HTTP coverage for settlement release plus Plan 17 B2 gate scenarios.
 * A test-scoped {@link FakePaymentProviderPort} drives deterministic release outcomes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "app.payment.dispatch.relay.enabled=true",
        "app.payment.dispatch.relay.claim-timeout=0s",
        "app.payment.dispatch.relay.batch-size=20"
})
@Testcontainers
class PaymentSettlementIntegrationTest {

    private static final String LEGAL_ENTITY_HEADER = "X-M4Trust-Legal-Entity-Id";
    private static final AtomicLong NEXT_DEAL_REFERENCE = new AtomicLong(1);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.5-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReleaseDispatchRelay releaseRelay;

    @Autowired
    private FakePaymentProviderPort provider;

    private Principal buyerAdmin;
    private Principal seller;

    @BeforeEach
    void setUp() {
        provider.reset();
        jdbc.update("DELETE FROM spring_session_attributes");
        jdbc.update("DELETE FROM spring_session");
        jdbc.execute("""
                TRUNCATE TABLE
                    dispute_comment, dispute_evidence_snapshot, dispute_case,
                    fulfillment_video_analysis_result, fulfillment_video_analysis_job,
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
        seller = insertPrincipal("seller@example.test", "Seller Co", "ADMIN");
    }

    @Test
    void happyPathReleaseCompletesDeal() throws Exception {
        SeedContext seed = seedReleaseReadyDeal(0, Instant.parse("2026-07-20T12:00:00Z"));

        mockMvc.perform(get("/api/v1/deals/" + seed.dealId() + "/settlement")
                        .with(user(buyerAdmin.userId.toString()))
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));

        Versions versions = ensureSettlementReady(seed.dealId());

        MvcResult release = requestRelease(seed.dealId(), versions)
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(
                        "/api/v1/release-operations/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.mode").value("DEMO_SIMULATED"))
                .andReturn();
        UUID operationId = UUID.fromString(JsonPath.read(
                release.getResponse().getContentAsString(), "$.id"));
        String providerKey = providerKeyFor(operationId);
        provider.scriptReleaseSuccess(providerKey);

        releaseRelay.relayOnce();

        assertEquals("SIMULATED_SETTLED", jdbc.queryForObject(
                "SELECT status FROM release_operation WHERE id = ?", String.class, operationId));
        assertEquals("SIMULATED_SETTLED", jdbc.queryForObject(
                "SELECT status FROM settlement WHERE deal_id = ?", String.class, seed.dealId()));
        assertEquals("COMPLETED", jdbc.queryForObject(
                "SELECT deal_status FROM deal WHERE id = ?", String.class, seed.dealId()));

        mockMvc.perform(get("/api/v1/deals/" + seed.dealId() + "/settlement")
                        .with(user(buyerAdmin.userId.toString()))
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIMULATED_SETTLED"))
                .andExpect(jsonPath("$.mode").value("DEMO_SIMULATED"));
    }

    @Test
    void windowOneBlocksUntilElapsed() throws Exception {
        SeedContext seed = seedReleaseReadyDeal(1, Instant.now());
        Versions versions = fetchSettlementVersions(seed.dealId());

        mockMvc.perform(get("/api/v1/deals/" + seed.dealId() + "/settlement")
                        .with(user(buyerAdmin.userId.toString()))
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_READY"));

        requestRelease(seed.dealId(), versions)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_DISPUTE_WINDOW_NOT_ELAPSED"));
    }

    @Test
    void disputeFirstBlocksRelease() throws Exception {
        SeedContext seed = seedReleaseReadyDeal(0, Instant.parse("2026-07-20T12:00:00Z"));
        Versions versions = ensureSettlementReady(seed.dealId());
        insertOpenDispute(seed);

        requestRelease(seed.dealId(), versions)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_ACTIVE_DISPUTE"));
    }

    @Test
    void releaseFirstThenDisputeDefersCompletion() throws Exception {
        SeedContext seed = seedReleaseReadyDeal(0, Instant.parse("2026-07-20T12:00:00Z"));
        Versions versions = ensureSettlementReady(seed.dealId());

        MvcResult release = requestRelease(seed.dealId(), versions)
                .andExpect(status().isAccepted())
                .andReturn();
        UUID operationId = UUID.fromString(JsonPath.read(
                release.getResponse().getContentAsString(), "$.id"));
        provider.scriptReleaseSuccess(providerKeyFor(operationId));
        insertOpenDispute(seed);

        releaseRelay.relayOnce();

        assertEquals("ACTIVE", jdbc.queryForObject(
                "SELECT deal_status FROM deal WHERE id = ?", String.class, seed.dealId()));
        String operationStatus = jdbc.queryForObject(
                "SELECT status FROM release_operation WHERE id = ?", String.class, operationId);
        String settlementStatus = jdbc.queryForObject(
                "SELECT status FROM settlement WHERE deal_id = ?", String.class, seed.dealId());
        assertTrue(
                ("RECONCILIATION_REQUIRED".equals(operationStatus) && "ON_HOLD".equals(settlementStatus))
                        || ("SIMULATED_SETTLED".equals(operationStatus)
                                && "SIMULATED_SETTLED".equals(settlementStatus)),
                "release must defer deal completion while an active dispute exists");
    }

    @Test
    void duplicateReleaseRejected() throws Exception {
        SeedContext seed = seedReleaseReadyDeal(0, Instant.parse("2026-07-20T12:00:00Z"));
        Versions versions = ensureSettlementReady(seed.dealId());

        requestRelease(seed.dealId(), versions).andExpect(status().isAccepted());

        Versions afterInitiate = fetchSettlementVersions(seed.dealId());
        requestRelease(seed.dealId(), afterInitiate)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RELEASE_OPERATION_ALREADY_EXISTS"));
    }

    @Test
    void nonBuyerReadOnly() throws Exception {
        SeedContext seed = seedReleaseReadyDeal(0, Instant.parse("2026-07-20T12:00:00Z"));
        Versions versions = ensureSettlementReady(seed.dealId());

        mockMvc.perform(get("/api/v1/deals/" + seed.dealId() + "/settlement")
                        .with(user(seller.userId.toString()))
                        .header(LEGAL_ENTITY_HEADER, seller.legalEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));

        mockMvc.perform(post("/api/v1/deals/" + seed.dealId() + "/settlement/release")
                        .with(user(seller.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, seller.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(releaseBody(versions)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_MUTATION_FORBIDDEN"));
    }

    @Test
    void financialSettledUnreachable() {
        for (SettlementStatus status : SettlementStatus.values()) {
            assertNotEquals("SETTLED", status.name());
        }
        for (ReleaseOperationStatus status : ReleaseOperationStatus.values()) {
            assertNotEquals("SETTLED", status.name());
        }
        for (PaymentProviderPort.ReleaseOutcome outcome : PaymentProviderPort.ReleaseOutcome.values()) {
            assertNotEquals("SETTLED", outcome.name());
        }
    }

    private Versions ensureSettlementReady(UUID dealId) throws Exception {
        return fetchSettlementVersions(dealId, "READY");
    }

    private Versions fetchSettlementVersions(UUID dealId) throws Exception {
        return fetchSettlementVersions(dealId, null);
    }

    private Versions fetchSettlementVersions(UUID dealId, String expectedStatus) throws Exception {
        var request = mockMvc.perform(get("/api/v1/deals/" + dealId + "/settlement")
                        .with(user(buyerAdmin.userId.toString()))
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("DEMO_SIMULATED"));
        if (expectedStatus != null) {
            request.andExpect(jsonPath("$.status").value(expectedStatus));
        }
        MvcResult result = request.andReturn();
        String body = result.getResponse().getContentAsString();
        return new Versions(
                jdbc.queryForObject("SELECT version FROM deal WHERE id = ?", Long.class, dealId),
                ((Number) JsonPath.read(body, "$.version")).longValue(),
                jdbc.queryForObject("SELECT version FROM fulfillment WHERE deal_id = ?", Long.class, dealId),
                jdbc.queryForObject("""
                        SELECT fu.version FROM funding_unit fu
                        JOIN funding_plan fp ON fp.id = fu.funding_plan_id
                        WHERE fp.deal_id = ?
                        """, Long.class, dealId));
    }

    private org.springframework.test.web.servlet.ResultActions requestRelease(UUID dealId, Versions versions)
            throws Exception {
        return mockMvc.perform(post("/api/v1/deals/" + dealId + "/settlement/release")
                .with(user(buyerAdmin.userId.toString())).with(csrf())
                .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(releaseBody(versions)));
    }

    private static String releaseBody(Versions versions) {
        return """
                {"expectedDealVersion": %d, "expectedSettlementVersion": %d,
                 "expectedFulfillmentVersion": %d, "expectedFundingUnitVersion": %d}
                """.formatted(versions.dealVersion(), versions.settlementVersion(),
                versions.fulfillmentVersion(), versions.fundingUnitVersion());
    }

    private String providerKeyFor(UUID operationId) {
        return jdbc.queryForObject(
                "SELECT provider_key FROM release_operation WHERE id = ?", String.class, operationId);
    }

    private SeedContext seedReleaseReadyDeal(int disputeWindowDays, Instant completedAt) {
        UUID dealId = UUID.randomUUID();
        long dealVersion = 3L;
        jdbc.update("""
                INSERT INTO deal (
                    id, tenant_id, reference, title, deal_status,
                    initiator_legal_entity_id, created_by, created_at, updated_at, version
                ) VALUES (?, ?, ?, 'Settlement Deal', 'DRAFT', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, dealId, buyerAdmin.tenantId, nextDealReference(), buyerAdmin.legalEntityId, buyerAdmin.userId);
        insertParticipant(dealId, buyerAdmin);
        insertParticipant(dealId, seller);
        jdbc.update("""
                UPDATE deal
                SET deal_status = 'ACTIVE',
                    buyer_legal_entity_id = ?,
                    seller_legal_entity_id = ?,
                    version = ?
                WHERE id = ?
                """, buyerAdmin.legalEntityId, seller.legalEntityId, dealVersion, dealId);

        UUID snapshotId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        String snapshotJson = """
                {"schemaVersion":2,"disputeWindowDays":%d}
                """.formatted(disputeWindowDays);
        jdbc.update("""
                INSERT INTO ratification_package_snapshot (id, schema_version, canonical_snapshot, content_hash, created_at)
                VALUES (?, 2, CAST(? AS jsonb), ?, CURRENT_TIMESTAMP)
                """, snapshotId, snapshotJson, SHA);
        jdbc.update("""
                INSERT INTO ratification_package (
                    id, deal_id, snapshot_id, buyer_legal_entity_id, seller_legal_entity_id,
                    amount_minor, currency, status, version, created_at
                ) VALUES (?, ?, ?, ?, ?, 5000, 'TRY', 'RATIFIED', 0, CURRENT_TIMESTAMP)
                """, packageId, dealId, snapshotId, buyerAdmin.legalEntityId, seller.legalEntityId);
        jdbc.update("UPDATE deal SET current_ratification_package_id = ? WHERE id = ?", packageId, dealId);

        UUID planId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO funding_plan (
                    id, deal_id, ratification_package_id, tenant_id, amount_minor, currency,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 5000, 'TRY', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, planId, dealId, packageId, buyerAdmin.tenantId);
        jdbc.update("""
                INSERT INTO funding_unit (
                    id, funding_plan_id, sequence_no, amount_minor, currency, status,
                    version, created_at, updated_at
                ) VALUES (?, ?, 1, 5000, 'TRY', 'FUNDED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, unitId, planId);

        UUID fulfillmentId = UUID.randomUUID();
        UUID milestoneId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO fulfillment (
                    id, deal_id, tenant_id, source_package_id, status, version, created_at, updated_at, completed_at
                ) VALUES (?, ?, ?, ?, 'COMPLETED', 0, ?, ?, ?)
                """, fulfillmentId, dealId, buyerAdmin.tenantId, packageId,
                java.sql.Timestamp.from(completedAt), java.sql.Timestamp.from(completedAt),
                java.sql.Timestamp.from(completedAt));
        jdbc.update("""
                INSERT INTO fulfillment_milestone (
                    id, fulfillment_id, deal_id, title, status, version, created_at, updated_at
                ) VALUES (?, ?, ?, 'Primary', 'COMPLETED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, milestoneId, fulfillmentId, dealId);

        return new SeedContext(dealId, packageId, fulfillmentId, milestoneId, unitId);
    }

    private void insertOpenDispute(SeedContext seed) {
        jdbc.update("""
                INSERT INTO dispute_case (
                    id, deal_id, tenant_id, fulfillment_id, milestone_id, ratification_package_id,
                    fulfillment_status_at_open, fulfillment_version_at_open, milestone_version_at_open,
                    reason_code, subject, statement, status, opening_tenant_id, opening_legal_entity_id,
                    opening_user_id, opening_legal_name, opened_at, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'COMPLETED', 0, 0, 'NON_DELIVERY', 'subject', 'statement', 'OPEN',
                    ?, ?, ?, 'Buyer', CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), seed.dealId(), buyerAdmin.tenantId, seed.fulfillmentId(),
                seed.milestoneId(), seed.packageId(), buyerAdmin.tenantId, buyerAdmin.legalEntityId,
                buyerAdmin.userId);
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

    private static final String SHA = "a".repeat(64);

    private static String nextDealReference() {
        return "DL-" + String.format("%010d", NEXT_DEAL_REFERENCE.getAndIncrement());
    }

    private record SeedContext(UUID dealId, UUID packageId, UUID fulfillmentId, UUID milestoneId, UUID unitId) { }

    private record Versions(long dealVersion, long settlementVersion, long fulfillmentVersion, long fundingUnitVersion) { }

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
    static class FakeProviderConfiguration {
        @Bean
        FakePaymentProviderPort fakePaymentProviderPort() {
            return new FakePaymentProviderPort();
        }
    }

    static final class FakePaymentProviderPort implements PaymentProviderPort {
        private final Map<String, ReleaseProviderResult> releaseInitiateOutcome = new ConcurrentHashMap<>();
        private final Map<String, java.util.Deque<ReleaseProviderResult>> releaseQueryQueue = new ConcurrentHashMap<>();
        private final java.util.Set<String> releaseKnown = ConcurrentHashMap.newKeySet();

        void reset() {
            releaseInitiateOutcome.clear();
            releaseQueryQueue.clear();
            releaseKnown.clear();
        }

        void scriptReleaseSuccess(String key) {
            releaseInitiateOutcome.put(key,
                    new ReleaseProviderResult(ReleaseOutcome.SIMULATED_SETTLED, "sandbox-release-" + key));
        }

        void scriptReleaseUnconfirmed(String key) {
            releaseInitiateOutcome.put(key, new ReleaseProviderResult(ReleaseOutcome.UNCONFIRMED, null));
            releaseQueryQueue.put(key, new java.util.ArrayDeque<>(
                    List.of(new ReleaseProviderResult(ReleaseOutcome.SIMULATED_SETTLED, "sandbox-release-" + key))));
        }

        @Override
        public PaymentProviderMode mode() {
            return PaymentProviderMode.DEMO_SIMULATED;
        }

        @Override
        public ProviderResult initiate(ProviderRequest request) {
            return new ProviderResult(Outcome.SUCCEEDED, "sandbox-" + request.providerKey());
        }

        @Override
        public ProviderResult queryStatus(ProviderRequest request) {
            return new ProviderResult(Outcome.SUCCEEDED, "sandbox-" + request.providerKey());
        }

        @Override
        public ReleaseProviderResult initiateRelease(ProviderRequest request) {
            releaseKnown.add(request.providerKey());
            return releaseInitiateOutcome.getOrDefault(request.providerKey(),
                    new ReleaseProviderResult(ReleaseOutcome.SIMULATED_SETTLED, "sandbox-release-" + request.providerKey()));
        }

        @Override
        public ReleaseProviderResult queryReleaseStatus(ProviderRequest request) {
            if (!releaseKnown.contains(request.providerKey())) {
                return new ReleaseProviderResult(ReleaseOutcome.NOT_FOUND, null);
            }
            java.util.Deque<ReleaseProviderResult> queue = releaseQueryQueue.get(request.providerKey());
            if (queue != null && !queue.isEmpty()) {
                return queue.pollFirst();
            }
            return releaseInitiateOutcome.getOrDefault(request.providerKey(),
                    new ReleaseProviderResult(ReleaseOutcome.SIMULATED_SETTLED, "sandbox-release-" + request.providerKey()));
        }
    }
}
