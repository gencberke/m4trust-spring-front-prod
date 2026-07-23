package com.m4trust.coreapi.payment;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class SettlementReadService {

    private final SettlementSourcePorts.DealTarget deals;
    private final SettlementSourcePorts.FulfillmentTarget fulfillments;
    private final SettlementSourcePorts.RatificationTarget ratifications;
    private final SettlementSourcePorts.CaseworkTarget casework;
    private final FundingRepository funding;
    private final SettlementRepository settlements;
    private final SettlementEligibilityEvaluator eligibility;
    private final PaymentProviderModeResolver mode;
    private final TransactionTemplate transactions;
    private final Clock clock;

    SettlementReadService(SettlementSourcePorts.DealTarget deals,
            SettlementSourcePorts.FulfillmentTarget fulfillments,
            SettlementSourcePorts.RatificationTarget ratifications,
            SettlementSourcePorts.CaseworkTarget casework, FundingRepository funding,
            SettlementRepository settlements, SettlementEligibilityEvaluator eligibility,
            PaymentProviderModeResolver mode, TransactionTemplate transactions, Clock clock) {
        this.deals = deals;
        this.fulfillments = fulfillments;
        this.ratifications = ratifications;
        this.casework = casework;
        this.funding = funding;
        this.settlements = settlements;
        this.eligibility = eligibility;
        this.mode = mode;
        this.transactions = transactions;
        this.clock = clock;
    }

    SettlementReadDtos.SettlementDetailView get(OperationContext context, UUID dealId) {
        requireOperation(context, RequestedOperation.DEAL_SETTLEMENT_READ);
        return transactions.execute(status -> getInTransaction(context, dealId));
    }

    SettlementReadDtos.ReleaseOperationView getOperation(OperationContext context, UUID operationId) {
        requireOperation(context, RequestedOperation.RELEASE_OPERATION_READ);
        SettlementRepository.ReleaseOperationLookup lookup = settlements.findOperationById(operationId)
                .orElseThrow(SettlementExceptions.ReleaseOperationNotFound::new);
        SettlementSourcePorts.DealSnapshot deal = deals.findVisible(context, lookup.dealId())
                .orElseThrow(SettlementExceptions.ReleaseOperationNotFound::new);
        boolean buyerAdmin = SettlementProjection.isBuyerAdmin(context, deal);
        PaymentProviderMode providerMode = mode.resolve();
        SettlementProjection.requireNonNull(providerMode);
        return SettlementProjection.operation(lookup.operation(), buyerAdmin, providerMode);
    }

    private SettlementReadDtos.SettlementDetailView getInTransaction(OperationContext context, UUID dealId) {
        SettlementSourcePorts.DealSnapshot deal = deals.lockVisible(context, dealId)
                .orElseThrow(SettlementExceptions.DealNotFound::new);
        SettlementEligibilityEvaluator.Context input = loadContext(context, deal);
        SettlementRepository.SettlementRecord settlement = ensureSettlement(input);
        input = new SettlementEligibilityEvaluator.Context(input.deal(), input.fulfillment(), input.ratification(),
                input.fundingUnit(), input.activeDispute(), settlement, input.operation());
        SettlementEligibilityEvaluator.Evaluation evaluation = eligibility.evaluate(context, input);
        settlement = refreshSettlementStatus(settlement, evaluation.projectedStatus());
        PaymentProviderMode providerMode = mode.resolve();
        SettlementProjection.requireNonNull(providerMode);
        boolean buyerAdmin = SettlementProjection.isBuyerAdmin(context, deal);
        SettlementRepository.ReleaseOperationRecord operation = settlements
                .findOperationBySettlement(settlement.id()).orElse(null);
        return SettlementProjection.detail(settlement, evaluation.disputeWindowDays(),
                evaluation.releaseEligibleAt(), operation, buyerAdmin,
                evaluation.canRequestRelease(), evaluation.canReconcileRelease(), providerMode);
    }

    private SettlementRepository.SettlementRecord ensureSettlement(SettlementEligibilityEvaluator.Context input) {
        SettlementRepository.SettlementRecord existing = settlements.findByDealIdForUpdate(input.deal().dealId())
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        if (!preconditionsForCreation(input)) {
            throw new SettlementExceptions.SettlementNotFound();
        }
        Instant now = clock.instant();
        UUID settlementId = UUID.randomUUID();
        Settlement settlement = Settlement.create(settlementId, input.deal().dealId(),
                input.fundingUnit().id(), input.deal().tenantId(), now);
        if (!settlements.insertSettlementIfAbsent(settlement.toRecord())) {
            return settlements.findByDealIdForUpdate(input.deal().dealId())
                    .orElseThrow(() -> new IllegalStateException("Settlement row is unavailable after race"));
        }
        return settlement.toRecord();
    }

    private boolean preconditionsForCreation(SettlementEligibilityEvaluator.Context input) {
        return "ACTIVE".equals(input.deal().status())
                && input.fundingUnit() != null
                && input.fundingUnit().status() == FundingUnitStatus.FUNDED
                && input.fulfillment() != null
                && "COMPLETED".equals(input.fulfillment().status())
                && mode.resolve() == PaymentProviderMode.DEMO_SIMULATED;
    }

    private SettlementRepository.SettlementRecord refreshSettlementStatus(
            SettlementRepository.SettlementRecord record, SettlementStatus next) {
        Settlement settlement = Settlement.rehydrate(record);
        long previousVersion = settlement.version();
        settlement.refreshReadiness(next, clock.instant());
        if (settlement.version() != previousVersion) {
            if (!settlements.updateSettlement(settlement.toRecord(), previousVersion)) {
                throw new SettlementExceptions.SettlementStaleVersion();
            }
            return settlement.toRecord();
        }
        return record;
    }

    private SettlementEligibilityEvaluator.Context loadContext(
            OperationContext context, SettlementSourcePorts.DealSnapshot deal) {
        SettlementSourcePorts.FulfillmentSnapshot fulfillment = fulfillments.lockVisible(context, deal.dealId())
                .orElse(null);
        SettlementSourcePorts.RatificationSnapshot ratification = deal.ratifiedPackageId() == null
                ? null
                : ratifications.findRatifiedPackage(context, deal.dealId(), deal.ratifiedPackageId()).orElse(null);
        FundingRepository.PlanRecord plan = funding.findPlanByDeal(deal.dealId()).orElse(null);
        FundingRepository.UnitRecord unit = plan == null ? null : funding.findUnitByPlan(plan.id()).orElse(null);
        boolean activeDispute = casework.hasActiveDispute(deal.dealId());
        SettlementRepository.SettlementRecord settlement = settlements.findByDealId(deal.dealId()).orElse(null);
        SettlementRepository.ReleaseOperationRecord operation = settlement == null
                ? null : settlements.findOperationBySettlement(settlement.id()).orElse(null);
        return new SettlementEligibilityEvaluator.Context(deal, fulfillment, ratification, unit, activeDispute,
                settlement, operation);
    }

    private static void requireOperation(OperationContext context, RequestedOperation operation) {
        if (context.requestedOperation() != operation) {
            throw new IllegalArgumentException("Operation context mismatch");
        }
    }
}
