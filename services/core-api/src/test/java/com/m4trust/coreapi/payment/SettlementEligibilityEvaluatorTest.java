package com.m4trust.coreapi.payment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class SettlementEligibilityEvaluatorTest {

    private static final Instant COMPLETED = Instant.parse("2026-07-23T00:00:00Z");
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-23T01:00:00Z"), ZoneOffset.UTC);
    private SettlementEligibilityEvaluator evaluator;

    @BeforeEach
    void setUp() {
        PaymentProviderPort port = mock(PaymentProviderPort.class);
        when(port.mode()).thenReturn(PaymentProviderMode.DEMO_SIMULATED);
        @SuppressWarnings("unchecked")
        ObjectProvider<PaymentProviderPort> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(port);
        evaluator = new SettlementEligibilityEvaluator(
                mock(FundingRepository.class), new PaymentProviderModeResolver(provider), clock);
    }

    @Test
    void windowZeroAllowsImmediateEligibility() {
        assertTrue(evaluate(0).releaseEligible());
    }

    @Test
    void windowOneBlocksBeforeDeadline() {
        assertFalse(evaluate(1).releaseEligible());
    }

    private SettlementEligibilityEvaluator.Evaluation evaluate(int disputeWindowDays) {
        SettlementSourcePorts.DealSnapshot deal = new SettlementSourcePorts.DealSnapshot(
                UUID.randomUUID(), UUID.randomUUID(), "ACTIVE", 1,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        SettlementSourcePorts.FulfillmentSnapshot fulfillment = new SettlementSourcePorts.FulfillmentSnapshot(
                UUID.randomUUID(), "COMPLETED", 1, COMPLETED);
        SettlementSourcePorts.RatificationSnapshot ratification = new SettlementSourcePorts.RatificationSnapshot(
                2, "RATIFIED", disputeWindowDays);
        FundingRepository.UnitRecord unit = new FundingRepository.UnitRecord(
                UUID.randomUUID(), UUID.randomUUID(), 1, 1000, "TRY",
                FundingUnitStatus.FUNDED, COMPLETED, COMPLETED, 0);
        Settlement settlement = Settlement.create(UUID.randomUUID(), deal.dealId(), unit.id(), deal.tenantId(),
                COMPLETED);
        settlement.refreshReadiness(SettlementStatus.READY, COMPLETED);
        return evaluator.evaluate(new SettlementEligibilityEvaluator.Context(
                deal, fulfillment, ratification, unit, false, settlement.toRecord(), null), true);
    }
}
