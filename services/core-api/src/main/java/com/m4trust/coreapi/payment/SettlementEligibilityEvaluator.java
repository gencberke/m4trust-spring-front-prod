package com.m4trust.coreapi.payment;

import java.time.Clock;
import java.time.Instant;

import com.m4trust.coreapi.organization.OperationContext;
import org.springframework.stereotype.Component;

/**
 * Re-evaluates settlement readiness under ADR-014 §2.4 eligibility without
 * creating automatic release intents.
 */
@Component
class SettlementEligibilityEvaluator {

    private final FundingRepository funding;
    private final PaymentProviderModeResolver modeResolver;
    private final Clock clock;

    SettlementEligibilityEvaluator(FundingRepository funding, PaymentProviderModeResolver modeResolver, Clock clock) {
        this.funding = funding;
        this.modeResolver = modeResolver;
        this.clock = clock;
    }

    record Context(
            SettlementSourcePorts.DealSnapshot deal,
            SettlementSourcePorts.FulfillmentSnapshot fulfillment,
            SettlementSourcePorts.RatificationSnapshot ratification,
            FundingRepository.UnitRecord fundingUnit,
            boolean activeDispute,
            SettlementRepository.SettlementRecord settlement,
            SettlementRepository.ReleaseOperationRecord operation) { }

    record Evaluation(
            SettlementStatus projectedStatus,
            Integer disputeWindowDays,
            Instant releaseEligibleAt,
            boolean releaseEligible,
            boolean canRequestRelease,
            boolean canReconcileRelease) { }

    Evaluation evaluate(OperationContext context, Context input) {
        return evaluate(input, SettlementProjection.isBuyerAdmin(context, input.deal()));
    }

    Evaluation evaluate(Context input, boolean buyerAdmin) {
        PaymentProviderMode mode = modeResolver.resolve();
        if (mode != PaymentProviderMode.DEMO_SIMULATED) {
            return new Evaluation(SettlementStatus.NOT_READY, null, null, false, false, false);
        }
        Integer disputeWindowDays = ratifiedWindow(input.ratification());
        Instant completedAt = input.fulfillment() == null ? null : input.fulfillment().completedAt();
        Instant eligibleAt = disputeWindowDays != null && completedAt != null
                ? SettlementProjection.releaseEligibleAt(completedAt, disputeWindowDays)
                : null;
        boolean funded = input.fundingUnit() != null && input.fundingUnit().status() == FundingUnitStatus.FUNDED;
        boolean fulfillmentCompleted = input.fulfillment() != null
                && "COMPLETED".equals(input.fulfillment().status())
                && completedAt != null;
        boolean dealActive = "ACTIVE".equals(input.deal().status());
        boolean ratifiedV2 = disputeWindowDays != null
                && input.ratification() != null
                && "RATIFIED".equals(input.ratification().status());
        boolean windowElapsed = eligibleAt != null && !clock.instant().isBefore(eligibleAt);
        boolean noActiveDispute = !input.activeDispute();
        boolean noOperation = input.operation() == null;
        boolean settlementReady = dealActive && funded && fulfillmentCompleted && ratifiedV2
                && windowElapsed && noActiveDispute && noOperation;
        SettlementStatus projected = projectedStatus(input.settlement(), settlementReady, input.operation());
        boolean canRequest = buyerAdmin && settlementReady && input.settlement() != null;
        boolean canReconcile = buyerAdmin && input.operation() != null
                && input.operation().status() == ReleaseOperationStatus.RECONCILIATION_REQUIRED
                && input.settlement() != null
                && !input.settlement().status().terminal();
        return new Evaluation(projected, disputeWindowDays, eligibleAt, settlementReady,
                canRequest, canReconcile);
    }

    private static Integer ratifiedWindow(SettlementSourcePorts.RatificationSnapshot ratification) {
        if (ratification == null || !"RATIFIED".equals(ratification.status())) {
            return null;
        }
        if ((ratification.schemaVersion() != 2 && ratification.schemaVersion() != 3)
                || ratification.disputeWindowDays() == null) {
            return null;
        }
        return ratification.disputeWindowDays();
    }

    private static SettlementStatus projectedStatus(
            SettlementRepository.SettlementRecord settlement,
            boolean ready,
            SettlementRepository.ReleaseOperationRecord operation) {
        if (settlement == null) {
            return ready ? SettlementStatus.READY : SettlementStatus.NOT_READY;
        }
        if (settlement.status().terminal()) {
            return settlement.status();
        }
        if (operation != null) {
            return switch (operation.status()) {
                case RECONCILIATION_REQUIRED -> SettlementStatus.ON_HOLD;
                case QUEUED, PROCESSING -> SettlementStatus.PROCESSING;
                case SIMULATED_SETTLED -> SettlementStatus.SIMULATED_SETTLED;
                case SIMULATED_DECLINED, FAILED_BEFORE_DISPATCH -> SettlementStatus.FAILED;
            };
        }
        return ready ? SettlementStatus.READY : SettlementStatus.NOT_READY;
    }
}
