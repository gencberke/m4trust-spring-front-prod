package com.m4trust.coreapi.payment;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SettlementProjectionService implements SettlementProjectionPort {

    private final SettlementRepository settlements;
    private final SettlementSourcePorts.DealTarget deals;
    private final SettlementSourcePorts.FulfillmentTarget fulfillments;
    private final SettlementSourcePorts.RatificationTarget ratifications;
    private final SettlementSourcePorts.CaseworkTarget casework;
    private final FundingRepository funding;
    private final SettlementEligibilityEvaluator eligibility;
    private final PaymentProviderModeResolver mode;

    SettlementProjectionService(SettlementRepository settlements, SettlementSourcePorts.DealTarget deals,
            SettlementSourcePorts.FulfillmentTarget fulfillments, SettlementSourcePorts.RatificationTarget ratifications,
            SettlementSourcePorts.CaseworkTarget casework, FundingRepository funding,
            SettlementEligibilityEvaluator eligibility, PaymentProviderModeResolver mode) {
        this.settlements = settlements;
        this.deals = deals;
        this.fulfillments = fulfillments;
        this.ratifications = ratifications;
        this.casework = casework;
        this.funding = funding;
        this.eligibility = eligibility;
        this.mode = mode;
    }

    @Override
    @Transactional(readOnly = true)
    public Summary summarize(UUID dealId, boolean dealActive, boolean callerIsBuyerAdmin) {
        if (!dealActive || mode.resolve() != PaymentProviderMode.DEMO_SIMULATED) {
            return empty();
        }
        SettlementRepository.SettlementRecord settlement = settlements.findByDealId(dealId).orElse(null);
        if (settlement == null) {
            return empty();
        }
        SettlementRepository.ReleaseOperationRecord operation = settlements.findOperationBySettlement(settlement.id())
                .orElse(null);
        SettlementEligibilityEvaluator.Context context = loadContext(dealId, settlement, operation);
        if (context == null) {
            return empty();
        }
        SettlementEligibilityEvaluator.Evaluation evaluation = eligibility.evaluate(context, callerIsBuyerAdmin);
        return new Summary(settlement.id(), settlement.status().name(),
                operation == null ? null : operation.id(),
                evaluation.canRequestRelease(), evaluation.canReconcileRelease());
    }

    private SettlementEligibilityEvaluator.Context loadContext(UUID dealId,
            SettlementRepository.SettlementRecord settlement,
            SettlementRepository.ReleaseOperationRecord operation) {
        return deals.findForProjection(dealId).map(deal -> {
            SettlementSourcePorts.FulfillmentSnapshot fulfillment = fulfillments.findForProjection(dealId)
                    .orElse(null);
            SettlementSourcePorts.RatificationSnapshot ratification = deal.ratifiedPackageId() == null
                    ? null
                    : ratifications.findForProjection(dealId, deal.ratifiedPackageId()).orElse(null);
            FundingRepository.PlanRecord plan = funding.findPlanByDeal(dealId).orElse(null);
            FundingRepository.UnitRecord unit = plan == null ? null : funding.findUnitByPlan(plan.id()).orElse(null);
            boolean activeDispute = casework.hasActiveDispute(dealId);
            return new SettlementEligibilityEvaluator.Context(deal, fulfillment, ratification, unit, activeDispute,
                    settlement, operation);
        }).orElse(null);
    }

    private static Summary empty() {
        return new Summary(null, null, null, false, false);
    }
}
