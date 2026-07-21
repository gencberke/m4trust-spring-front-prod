package com.m4trust.coreapi.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.jayway.jsonpath.JsonPath;
import com.m4trust.coreapi.integration.payment.moka.MokaHttpPaymentProviderAdapter;
import com.m4trust.coreapi.integration.payment.moka.MokaTransportSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end HTTP coverage for the funding surface plus the plan §8 minimum
 * invariants. A test-scoped {@link FakePaymentProviderPort} replaces the
 * profile-gated sandbox adapter so every scenario (success, decline, timeout,
 * crash windows) is driven deterministically instead of depending on a shared
 * scenario sequence across test methods.
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
class PaymentFundingIntegrationTest {

    private static final String LEGAL_ENTITY_HEADER = "X-M4Trust-Legal-Entity-Id";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.5-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentDispatchRelay relay;

    @Autowired
    private FakePaymentProviderPort provider;

    private Principal buyerAdmin;
    private Principal buyerMember;
    private Principal seller;
    private Principal outsider;

    @BeforeEach
    void setUp() {
        provider.reset();
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
        seller = insertPrincipal("seller@example.test", "Seller Co", "ADMIN");
        outsider = insertPrincipal("outsider@example.test", "Outsider Co", "ADMIN");
    }

    @Test
    void happyPathCreatePlanThenSucceedFunds() throws Exception {
        UUID dealId = createActiveDeal(5_000, "TRY");

        MvcResult planResult = createPlan(dealId, 3)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/deals/" + dealId + "/funding-plan"))
                .andExpect(jsonPath("$.amountMinor").value(5000))
                .andExpect(jsonPath("$.currency").value("TRY"))
                .andExpect(jsonPath("$.fundingStatus").value("PLANNED"))
                .andExpect(jsonPath("$.fundingUnit.status").value("PLANNED"))
                .andReturn();
        UUID unitId = UUID.fromString(JsonPath.read(
                planResult.getResponse().getContentAsString(), "$.fundingUnit.id"));
        assertEquals(
                jdbc.queryForObject("SELECT current_ratification_package_id FROM deal WHERE id = ?",
                        UUID.class, dealId),
                jdbc.queryForObject("SELECT ratification_package_id FROM funding_plan WHERE deal_id = ?",
                        UUID.class, dealId));

        MvcResult initiateResult = initiate(unitId, 0)
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(
                        "/api/v1/payment-operations/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andReturn();
        UUID operationId = UUID.fromString(
                JsonPath.read(initiateResult.getResponse().getContentAsString(), "$.id"));
        String providerKey = jdbc.queryForObject(
                "SELECT provider_key FROM payment_operation WHERE id = ?", String.class, operationId);
        provider.scriptSuccess(providerKey);

        relay.relayOnce();

        assertEquals("SUCCEEDED", jdbc.queryForObject(
                "SELECT status FROM payment_operation WHERE id = ?", String.class, operationId));
        assertEquals("FUNDED", jdbc.queryForObject(
                "SELECT status FROM funding_unit WHERE id = ?", String.class, unitId));
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertFalse(provider.sawOpenTransaction());
        assertEquals(1, provider.initiateCallCount(providerKey));

        mockMvc.perform(get("/api/v1/deals/" + dealId + "/funding-plan")
                        .with(user(buyerAdmin.userId.toString()))
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fundingStatus").value("FUNDED"))
                .andExpect(jsonPath("$.fundingUnit.status").value("FUNDED"))
                .andExpect(jsonPath("$.fundingUnit.availableActions.canInitiatePayment").value(false));

        mockMvc.perform(get("/api/v1/deals/" + dealId)
                        .with(user(buyerAdmin.userId.toString()))
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycle").value("FULFILLMENT"))
                .andExpect(jsonPath("$.funding.fundingStatus").value("FUNDED"))
                .andExpect(jsonPath("$.funding.amountMinor").value(5000));

        // FUNDED rejects a new payment attempt, both via the projection and the server.
        initiate(unitId, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FUNDING_UNIT_ALREADY_FUNDED"));
    }

    @Test
    void declineFailsTheUnitAndARetryThenSucceeds() throws Exception {
        UUID dealId = createActiveDeal(7_000, "USD");
        UUID unitId = planUnitId(createPlan(dealId, 3).andExpect(status().isCreated()).andReturn());

        MvcResult first = initiate(unitId, 0).andExpect(status().isAccepted()).andReturn();
        UUID firstOperationId = operationId(first);
        String firstKey = providerKeyFor(firstOperationId);
        provider.scriptDecline(firstKey);
        relay.relayOnce();

        assertEquals("DECLINED", jdbc.queryForObject(
                "SELECT status FROM payment_operation WHERE id = ?", String.class, firstOperationId));
        assertEquals("FAILED", jdbc.queryForObject(
                "SELECT status FROM funding_unit WHERE id = ?", String.class, unitId));

        // The unit's version advanced twice (PLANNED->PENDING, PENDING->FAILED) before this retry.
        MvcResult second = initiate(unitId, 2).andExpect(status().isAccepted()).andReturn();
        UUID secondOperationId = operationId(second);
        String secondKey = providerKeyFor(secondOperationId);
        assertFalse(secondKey.equals(firstKey), "retry must use a new provider key, never the declined one");
        provider.scriptSuccess(secondKey);
        relay.relayOnce();

        assertEquals("SUCCEEDED", jdbc.queryForObject(
                "SELECT status FROM payment_operation WHERE id = ?", String.class, secondOperationId));
        assertEquals("FUNDED", jdbc.queryForObject(
                "SELECT status FROM funding_unit WHERE id = ?", String.class, unitId));
        assertEquals(1, (int) jdbc.queryForObject(
                "SELECT count(*) FROM payment_operation WHERE funding_unit_id = ? AND status = 'SUCCEEDED'",
                Integer.class, unitId), "exactly one successful money-movement record");
    }

    @Test
    void timeoutNeverProducesFailedAndReconciliationResolvesIt() throws Exception {
        UUID dealId = createActiveDeal(2_500, "TRY");
        UUID unitId = planUnitId(createPlan(dealId, 3).andExpect(status().isCreated()).andReturn());

        MvcResult initiated = initiate(unitId, 0).andExpect(status().isAccepted()).andReturn();
        UUID operationId = operationId(initiated);
        String key = providerKeyFor(operationId);
        provider.scriptTimeoutThenSuccess(key);

        relay.relayOnce();
        assertEquals("UNCONFIRMED", jdbc.queryForObject(
                "SELECT status FROM payment_operation WHERE id = ?", String.class, operationId));
        assertEquals("PENDING", jdbc.queryForObject(
                "SELECT status FROM funding_unit WHERE id = ?", String.class, unitId),
                "timeout never produces FAILED and stays PENDING, not a fabricated failure");

        // While UNCONFIRMED, a brand-new payment attempt is blocked (single in-flight operation).
        initiate(unitId, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_OPERATION_IN_FLIGHT"));

        long operationVersion = jdbc.queryForObject(
                "SELECT version FROM payment_operation WHERE id = ?", Long.class, operationId);
        reconcile(operationId, operationVersion)
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/payment-operations/" + operationId));

        relay.relayOnce();
        assertEquals("SUCCEEDED", jdbc.queryForObject(
                "SELECT status FROM payment_operation WHERE id = ?", String.class, operationId));
        assertEquals("FUNDED", jdbc.queryForObject(
                "SELECT status FROM funding_unit WHERE id = ?", String.class, unitId));
        // Exactly one initiate call ever reached the provider for this key.
        assertEquals(1, provider.initiateCallCount(key));
    }

    @Test
    void durableRelayUsesRealExternalMokaHttpQueryFirstWithoutDuplicateInitiate() throws Exception {
        try (ExternalMokaEmulator emulator = ExternalMokaEmulator.start("success,timeout_then_late_success")) {
            provider.delegateTo(new MokaHttpPaymentProviderAdapter(new MokaTransportSettings(
                    emulator.baseUri(), "DEALER-001", "fixture-user", "fixture-password", Duration.ofSeconds(1),
                    Duration.ofMillis(400), 8_192, 16_384)));

            UUID successUnit = planUnitId(createPlan(createActiveDeal(2_500, "TRY"), 3)
                    .andExpect(status().isCreated()).andReturn());
            UUID successOperation = operationId(initiate(successUnit, 0).andExpect(status().isAccepted()).andReturn());
            String successKey = providerKeyFor(successOperation);
            relay.relayOnce();
            assertEquals("SUCCEEDED", jdbc.queryForObject("SELECT status FROM payment_operation WHERE id = ?",
                    String.class, successOperation));
            assertEquals(1, provider.queryCallCount(successKey));
            assertEquals(1, provider.initiateCallCount(successKey));

            UUID timeoutUnit = planUnitId(createPlan(createActiveDeal(2_600, "TRY"), 3)
                    .andExpect(status().isCreated()).andReturn());
            UUID timeoutOperation = operationId(initiate(timeoutUnit, 0).andExpect(status().isAccepted()).andReturn());
            String timeoutKey = providerKeyFor(timeoutOperation);
            relay.relayOnce();
            assertEquals("UNCONFIRMED", jdbc.queryForObject("SELECT status FROM payment_operation WHERE id = ?",
                    String.class, timeoutOperation));
            assertEquals(1, provider.queryCallCount(timeoutKey));
            assertEquals(1, provider.initiateCallCount(timeoutKey));

            reconcile(timeoutOperation, operationVersion(timeoutOperation)).andExpect(status().isAccepted());
            relay.relayOnce();
            assertEquals("UNCONFIRMED", jdbc.queryForObject("SELECT status FROM payment_operation WHERE id = ?",
                    String.class, timeoutOperation));
            reconcile(timeoutOperation, operationVersion(timeoutOperation)).andExpect(status().isAccepted());
            relay.relayOnce();

            assertEquals("SUCCEEDED", jdbc.queryForObject("SELECT status FROM payment_operation WHERE id = ?",
                    String.class, timeoutOperation));
            assertEquals("FUNDED", jdbc.queryForObject("SELECT status FROM funding_unit WHERE id = ?",
                    String.class, timeoutUnit));
            assertEquals(3, provider.queryCallCount(timeoutKey), "initial query plus two reconciliation queries");
            assertEquals(1, provider.initiateCallCount(timeoutKey), "late recovery must not initiate again");
            assertEquals(List.of(timeoutKey, timeoutKey, timeoutKey), provider.queryKeys(timeoutKey));
            assertFalse(provider.sawOpenTransaction(), "external HTTP runs after the durable claim transaction closes");
        }
    }

    @Test
    void createdOperationCannotBeReconciledBeforeItsInitialDispatch() throws Exception {
        UUID dealId = createActiveDeal(2_600, "TRY");
        UUID unitId = planUnitId(createPlan(dealId, 3).andExpect(status().isCreated()).andReturn());
        UUID operationId = operationId(initiate(unitId, 0).andExpect(status().isAccepted()).andReturn());

        reconcile(operationId, 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_OPERATION_STATE_CONFLICT"));

        assertEquals(1, (int) jdbc.queryForObject(
                "SELECT count(*) FROM payment_dispatch WHERE payment_operation_id = ?",
                Integer.class, operationId));
        assertEquals("CREATED", jdbc.queryForObject(
                "SELECT status FROM payment_operation WHERE id = ?", String.class, operationId));
    }

    @Test
    void sameIdempotencyKeyReplaysWithoutASecondProviderDispatchAndDifferentKeyConflicts() throws Exception {
        UUID dealId = createActiveDeal(1_000, "TRY");
        UUID unitId = planUnitId(createPlan(dealId, 3).andExpect(status().isCreated()).andReturn());

        UUID sameKey = UUID.randomUUID();
        MvcResult first = mockMvc.perform(post("/api/v1/funding-units/" + unitId + "/payment-operations")
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .header("Idempotency-Key", sameKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\": 0}"))
                .andExpect(status().isAccepted())
                .andReturn();
        UUID operationId = operationId(first);

        mockMvc.perform(post("/api/v1/funding-units/" + unitId + "/payment-operations")
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .header("Idempotency-Key", sameKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\": 0}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(operationId.toString()));

        assertEquals(1, (int) jdbc.queryForObject(
                "SELECT count(*) FROM payment_operation WHERE funding_unit_id = ?", Integer.class, unitId));

        // A different HTTP key while one is already in flight is a 409, not a second operation.
        initiate(unitId, 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_OPERATION_IN_FLIGHT"));
    }

    @Test
    void concurrentIdempotentFundingPlanCreateProducesExactlyOnePlanAndUnit() throws Exception {
        UUID dealId = createActiveDeal(9_900, "TRY");
        long dealVersion = 3;

        // Two different Idempotency-Keys targeting the same canonical create race
        // under the DB unique invariant; both must resolve to the single plan.
        UUID keyA = UUID.randomUUID();
        UUID keyB = UUID.randomUUID();
        MvcResult first = mockMvc.perform(post("/api/v1/deals/" + dealId + "/funding-plan")
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .header("Idempotency-Key", keyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\": " + dealVersion + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID planId = UUID.fromString(JsonPath.read(first.getResponse().getContentAsString(), "$.id"));

        mockMvc.perform(post("/api/v1/deals/" + dealId + "/funding-plan")
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .header("Idempotency-Key", keyB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\": " + dealVersion + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FUNDING_PLAN_ALREADY_EXISTS"));

        assertEquals(1, (int) jdbc.queryForObject("SELECT count(*) FROM funding_plan WHERE deal_id = ?",
                Integer.class, dealId));
        assertEquals(1, (int) jdbc.queryForObject("SELECT count(*) FROM funding_unit WHERE funding_plan_id = ?",
                Integer.class, planId));
    }

    @Test
    void nonActiveDealAndWrongActorsAreRejected() throws Exception {
        // A DRAFT Deal (never ratified) has no ACTIVE state to create a plan from.
        MvcResult draftDeal = mockMvc.perform(post("/api/v1/deals")
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Draft Deal\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID draftDealId = UUID.fromString(JsonPath.read(draftDeal.getResponse().getContentAsString(), "$.id"));
        // Assign buyerAdmin as the buyer so the DEAL_STATE_CONFLICT check (not
        // FUNDING_MUTATION_FORBIDDEN) is what actually rejects the DRAFT plan create.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/deals/" + draftDealId + "/parties")
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"buyerLegalEntityId": "%s", "sellerLegalEntityId": null, "expectedVersion": 0}
                                """.formatted(buyerAdmin.legalEntityId)))
                .andExpect(status().isOk());
        createPlan(draftDealId, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEAL_STATE_CONFLICT"));

        UUID dealId = createActiveDeal(4_200, "TRY");

        // Buyer MEMBER cannot create the plan even though it can see the Deal.
        mockMvc.perform(post("/api/v1/deals/" + dealId + "/funding-plan")
                        .with(user(buyerMember.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerMember.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\": 3}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FUNDING_MUTATION_FORBIDDEN"));

        UUID unitId = planUnitId(createPlan(dealId, 3).andExpect(status().isCreated()).andReturn());

        // Seller cannot initiate a payment.
        mockMvc.perform(post("/api/v1/funding-units/" + unitId + "/payment-operations")
                        .with(user(seller.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, seller.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\": 0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FUNDING_MUTATION_FORBIDDEN"));
    }

    @Test
    void negativeAmountIsRejectedAtTheDatabaseLevel() throws Exception {
        UUID dealId = createActiveDeal(100, "TRY");
        UUID packageId = jdbc.queryForObject(
                "SELECT current_ratification_package_id FROM deal WHERE id = ?", UUID.class, dealId);
        assertThrowsDataIntegrityViolation(() -> jdbc.update("""
                INSERT INTO funding_plan (
                    id, deal_id, ratification_package_id, tenant_id, amount_minor, currency,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'TRY', 0, now(), now())
                """, UUID.randomUUID(), dealId, packageId, buyerAdmin.tenantId, -100));
    }

    @Test
    void crashBeforeAnyProviderCallIsRecoveredByANormalRelayPass() throws Exception {
        UUID dealId = createActiveDeal(3_300, "TRY");
        UUID unitId = planUnitId(createPlan(dealId, 3).andExpect(status().isCreated()).andReturn());
        MvcResult initiated = initiate(unitId, 0).andExpect(status().isAccepted()).andReturn();
        UUID operationId = operationId(initiated);
        String key = providerKeyFor(operationId);
        provider.scriptSuccess(key);

        // The dispatch row is durable and CREATED already committed before any
        // provider call happened; a normal relay pass is exactly the recovery path.
        assertEquals("CREATED", jdbc.queryForObject(
                "SELECT status FROM payment_operation WHERE id = ?", String.class, operationId));
        relay.relayOnce();

        assertEquals("SUCCEEDED", jdbc.queryForObject(
                "SELECT status FROM payment_operation WHERE id = ?", String.class, operationId));
        assertEquals(1, provider.initiateCallCount(key));
    }

    @Test
    void crashAfterProviderCallBeforeLocalCommitNeverRepeatsInitiate() throws Exception {
        UUID dealId = createActiveDeal(6_600, "TRY");
        UUID unitId = planUnitId(createPlan(dealId, 3).andExpect(status().isCreated()).andReturn());
        MvcResult initiated = initiate(unitId, 0).andExpect(status().isAccepted()).andReturn();
        UUID operationId = operationId(initiated);
        String key = providerKeyFor(operationId);
        provider.scriptSuccess(key);

        // Simulate the crash window by hand: claim + call the provider exactly as
        // the relay would, but never apply the result or mark the dispatch
        // completed. The process "dies" right here.
        PaymentDispatchStore.DispatchClaim claim = store.claimAvailable(10, Duration.ZERO).get(0);
        PaymentProviderPort.ProviderRequest request = new PaymentProviderPort.ProviderRequest(
                claim.providerKey().toString(), claim.amountMinor(), claim.currency());
        provider.queryStatus(request); // NOT_FOUND (never dispatched at the provider yet)
        provider.initiate(request); // provider now durably knows the key and its outcome
        assertEquals("CREATED", jdbc.queryForObject(
                "SELECT status FROM payment_operation WHERE id = ?", String.class, operationId),
                "the crash window: provider succeeded but the local result was never committed");

        // Recovery: the still-claimed-but-uncompleted dispatch is reclaimable
        // (claim-timeout is configured to 0 for this test) and resolves query-first.
        relay.relayOnce();

        assertEquals("SUCCEEDED", jdbc.queryForObject(
                "SELECT status FROM payment_operation WHERE id = ?", String.class, operationId));
        assertEquals("FUNDED", jdbc.queryForObject(
                "SELECT status FROM funding_unit WHERE id = ?", String.class, unitId));
        assertEquals(1, provider.initiateCallCount(key), "initiate is never blindly repeated after a crash");
    }

    @Autowired
    private PaymentDispatchStore store;

    private org.springframework.test.web.servlet.ResultActions createPlan(UUID dealId, long expectedVersion)
            throws Exception {
        return mockMvc.perform(post("/api/v1/deals/" + dealId + "/funding-plan")
                .with(user(buyerAdmin.userId.toString())).with(csrf())
                .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\": " + expectedVersion + "}"));
    }

    private org.springframework.test.web.servlet.ResultActions initiate(UUID unitId, long expectedVersion)
            throws Exception {
        return mockMvc.perform(post("/api/v1/funding-units/" + unitId + "/payment-operations")
                .with(user(buyerAdmin.userId.toString())).with(csrf())
                .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\": " + expectedVersion + "}"));
    }

    private org.springframework.test.web.servlet.ResultActions reconcile(UUID operationId, long expectedVersion)
            throws Exception {
        return mockMvc.perform(post("/api/v1/payment-operations/" + operationId + "/reconcile")
                .with(user(buyerAdmin.userId.toString())).with(csrf())
                .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\": " + expectedVersion + "}"));
    }

    private long operationVersion(UUID operationId) {
        return jdbc.queryForObject("SELECT version FROM payment_operation WHERE id = ?", Long.class, operationId);
    }

    private UUID planUnitId(MvcResult planResult) throws Exception {
        return UUID.fromString(JsonPath.read(planResult.getResponse().getContentAsString(), "$.fundingUnit.id"));
    }

    private UUID operationId(MvcResult result) throws Exception {
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private String providerKeyFor(UUID operationId) {
        return jdbc.queryForObject(
                "SELECT provider_key FROM payment_operation WHERE id = ?", String.class, operationId);
    }

    private void assertThrowsDataIntegrityViolation(Runnable runnable) {
        try {
            runnable.run();
        } catch (DataIntegrityViolationException expected) {
            return;
        }
        throw new AssertionError("Expected a DataIntegrityViolationException for a negative amount");
    }

    /** ACTIVE, buyer/seller-assigned Deal with a RATIFIED package for the given commercial terms. */
    private UUID createActiveDeal(long amountMinor, String currency) throws Exception {
        MvcResult dealResult = mockMvc.perform(post("/api/v1/deals")
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Funding Deal\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID dealId = UUID.fromString(JsonPath.read(dealResult.getResponse().getContentAsString(), "$.id"));

        insertParticipant(dealId, seller);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/deals/" + dealId + "/parties")
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"buyerLegalEntityId": "%s", "sellerLegalEntityId": "%s", "expectedVersion": 0}
                                """.formatted(buyerAdmin.legalEntityId, seller.legalEntityId)))
                .andExpect(status().isOk());

        seedAvailableDocumentAndAcceptedRuleSet(dealId);

        MvcResult created = mockMvc.perform(post("/api/v1/deals/" + dealId + "/ratification-packages")
                        .with(user(buyerAdmin.userId.toString())).with(csrf())
                        .header(LEGAL_ENTITY_HEADER, buyerAdmin.legalEntityId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion": 1, "commercialTerms": {"amountMinor": %d, "currency": "%s"}}
                                """.formatted(amountMinor, currency)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID packageId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));

        approve(dealId, packageId, buyerAdmin, 0).andExpect(status().isOk());
        approve(dealId, packageId, seller, 1).andExpect(status().isOk())
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

    private void seedAvailableDocumentAndAcceptedRuleSet(UUID dealId) {
        UUID tenantId = buyerAdmin.tenantId;
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
                """, ruleSetId, dealId, analysisJobId, extractionId, buyerAdmin.userId);
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
    static class FakeProviderConfiguration {
        @Bean
        FakePaymentProviderPort fakePaymentProviderPort() {
            return new FakePaymentProviderPort();
        }
    }

    /** Test-controlled provider double: deterministic per-key scripts, no shared global scenario order. */
    static final class FakePaymentProviderPort implements PaymentProviderPort {
        private final Map<String, ProviderResult> initiateOutcome = new ConcurrentHashMap<>();
        private final Map<String, java.util.Deque<ProviderResult>> queryQueue = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> initiateCalls = new ConcurrentHashMap<>();
        private final java.util.Set<String> known = ConcurrentHashMap.newKeySet();
        private final AtomicBoolean openTransactionObserved = new AtomicBoolean(false);
        private final Map<String, AtomicInteger> queryCalls = new ConcurrentHashMap<>();
        private final Map<String, List<String>> queryKeys = new ConcurrentHashMap<>();
        private volatile PaymentProviderPort delegate;

        void reset() {
            initiateOutcome.clear();
            queryQueue.clear();
            initiateCalls.clear();
            known.clear();
            openTransactionObserved.set(false);
            queryCalls.clear();
            queryKeys.clear();
            delegate = null;
        }

        void delegateTo(PaymentProviderPort delegate) { this.delegate = delegate; }

        void scriptSuccess(String key) {
            initiateOutcome.put(key, new ProviderResult(Outcome.SUCCEEDED, "sandbox-" + key));
        }

        void scriptDecline(String key) {
            initiateOutcome.put(key, new ProviderResult(Outcome.DECLINED, "sandbox-" + key));
        }

        void scriptTimeoutThenSuccess(String key) {
            initiateOutcome.put(key, new ProviderResult(Outcome.UNCONFIRMED, null));
            queryQueue.put(key, new java.util.ArrayDeque<>(
                    List.of(new ProviderResult(Outcome.SUCCEEDED, "sandbox-" + key))));
        }

        int initiateCallCount(String key) {
            return initiateCalls.getOrDefault(key, new AtomicInteger(0)).get();
        }

        int queryCallCount(String key) { return queryCalls.getOrDefault(key, new AtomicInteger()).get(); }
        List<String> queryKeys(String key) { return queryKeys.getOrDefault(key, List.of()); }

        boolean sawOpenTransaction() {
            return openTransactionObserved.get();
        }

        @Override
        public ProviderResult initiate(ProviderRequest request) {
            observeTransaction();
            known.add(request.providerKey());
            initiateCalls.computeIfAbsent(request.providerKey(), k -> new AtomicInteger()).incrementAndGet();
            if (delegate != null) return delegate.initiate(request);
            return initiateOutcome.getOrDefault(request.providerKey(),
                    new ProviderResult(Outcome.SUCCEEDED, "sandbox-" + request.providerKey()));
        }

        @Override
        public ProviderResult queryStatus(ProviderRequest request) {
            observeTransaction();
            queryCalls.computeIfAbsent(request.providerKey(), k -> new AtomicInteger()).incrementAndGet();
            queryKeys.computeIfAbsent(request.providerKey(), k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                    .add(request.providerKey());
            if (delegate != null) return delegate.queryStatus(request);
            if (!known.contains(request.providerKey())) {
                return new ProviderResult(Outcome.NOT_FOUND, null);
            }
            java.util.Deque<ProviderResult> queue = queryQueue.get(request.providerKey());
            if (queue != null && !queue.isEmpty()) {
                return queue.pollFirst();
            }
            return initiateOutcome.getOrDefault(request.providerKey(),
                    new ProviderResult(Outcome.SUCCEEDED, "sandbox-" + request.providerKey()));
        }

        private void observeTransaction() {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                openTransactionObserved.set(true);
            }
        }
    }

    private static final class ExternalMokaEmulator implements AutoCloseable {
        private final Process process;
        private final URI baseUri;
        private ExternalMokaEmulator(Process process, URI baseUri) { this.process = process; this.baseUri = baseUri; }
        static ExternalMokaEmulator start(String scenarios) throws Exception {
            int port;
            try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
            ProcessBuilder builder = new ProcessBuilder("python3", "-m", "m4trust_moka_emulator");
            builder.directory(java.nio.file.Path.of("..", "..", "tools", "moka-emulator").toFile());
            builder.environment().put("PYTHONPATH", "src");
            builder.environment().put("M4TRUST_MOKA_EMULATOR_ENABLED", "true");
            builder.environment().put("M4TRUST_MOKA_EMULATOR_PORT", Integer.toString(port));
            builder.environment().put("M4TRUST_MOKA_EMULATOR_SCENARIOS", scenarios);
            Process process = builder.start();
            URI baseUri = URI.create("http://127.0.0.1:" + port);
            Instant deadline = Instant.now().plusSeconds(5);
            while (Instant.now().isBefore(deadline)) {
                try {
                    if (java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(
                            baseUri.resolve("/health")).GET().build(), java.net.http.HttpResponse.BodyHandlers.discarding())
                            .statusCode() == 200) return new ExternalMokaEmulator(process, baseUri);
                } catch (java.io.IOException ignored) { }
                Thread.sleep(50);
            }
            process.destroyForcibly();
            throw new IllegalStateException("Moka emulator did not become healthy");
        }
        URI baseUri() { return baseUri; }
        @Override public void close() { process.destroyForcibly(); }
    }
}
