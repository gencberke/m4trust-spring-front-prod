package com.m4trust.coreapi.payment.api;

import com.m4trust.coreapi.organization.domain.OperationContext;
import com.m4trust.coreapi.organization.domain.RequestedOperation;
import com.m4trust.coreapi.payment.api.dto.*;
import com.m4trust.coreapi.payment.domain.*;
import com.m4trust.coreapi.payment.infra.*;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
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

  SettlementReadService(
      SettlementSourcePorts.DealTarget deals,
      SettlementSourcePorts.FulfillmentTarget fulfillments,
      SettlementSourcePorts.RatificationTarget ratifications,
      SettlementSourcePorts.CaseworkTarget casework,
      FundingRepository funding,
      SettlementRepository settlements,
      SettlementEligibilityEvaluator eligibility,
      PaymentProviderModeResolver mode,
      TransactionTemplate transactions,
      Clock clock) {
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

  SettlementDetailView get(OperationContext context, UUID dealId) {
    requireOperation(context, RequestedOperation.DEAL_SETTLEMENT_READ);
    return transactions.execute(status -> getInTransaction(context, dealId));
  }

  ReleaseOperationView getOperation(OperationContext context, UUID operationId) {
    requireOperation(context, RequestedOperation.RELEASE_OPERATION_READ);
    SettlementRecords.ReleaseOperationLookup lookup =
        settlements
            .findOperationById(operationId)
            .orElseThrow(SettlementExceptions.ReleaseOperationNotFound::new);
    SettlementSourcePorts.DealSnapshot deal =
        deals
            .findVisible(context, lookup.dealId())
            .orElseThrow(SettlementExceptions.ReleaseOperationNotFound::new);
    boolean buyerAdmin = SettlementProjection.isBuyerAdmin(context, deal);
    PaymentProviderMode providerMode = mode.resolve();
    SettlementProjection.requireNonNull(providerMode);
    return SettlementProjection.operation(lookup.operation(), buyerAdmin, providerMode);
  }

  private SettlementDetailView getInTransaction(OperationContext context, UUID dealId) {
    SettlementSourcePorts.DealSnapshot deal =
        deals.lockVisible(context, dealId).orElseThrow(SettlementExceptions.DealNotFound::new);
    SettlementEligibilityEvaluator.Context input = loadContext(context, deal);
    SettlementRecords.SettlementRecord settlement = ensureSettlement(input);
    input =
        new SettlementEligibilityEvaluator.Context(
            input.deal(),
            input.fulfillment(),
            input.ratification(),
            input.fundingUnit(),
            input.activeDispute(),
            settlement,
            input.operation());
    SettlementEligibilityEvaluator.Evaluation evaluation = eligibility.evaluate(context, input);
    settlement = refreshSettlementStatus(settlement, evaluation.projectedStatus());
    PaymentProviderMode providerMode = mode.resolve();
    SettlementProjection.requireNonNull(providerMode);
    boolean buyerAdmin = SettlementProjection.isBuyerAdmin(context, deal);
    SettlementRecords.ReleaseOperationRecord operation =
        settlements.findOperationBySettlement(settlement.id()).orElse(null);
    return SettlementProjection.detail(
        settlement,
        evaluation.disputeWindowDays(),
        evaluation.releaseEligibleAt(),
        operation,
        buyerAdmin,
        evaluation.canRequestRelease(),
        evaluation.canReconcileRelease(),
        providerMode);
  }

  private SettlementRecords.SettlementRecord ensureSettlement(
      SettlementEligibilityEvaluator.Context input) {
    SettlementRecords.SettlementRecord existing =
        settlements.findByDealIdForUpdate(input.deal().dealId()).orElse(null);
    if (existing != null) {
      return existing;
    }
    if (!preconditionsForCreation(input)) {
      throw new SettlementExceptions.SettlementNotFound();
    }
    Instant now = clock.instant();
    UUID settlementId = UUID.randomUUID();
    Settlement settlement =
        Settlement.create(
            settlementId,
            input.deal().dealId(),
            input.fundingUnit().id(),
            input.deal().tenantId(),
            now);
    if (!settlements.insertSettlementIfAbsent(settlement.toRecord())) {
      return settlements
          .findByDealIdForUpdate(input.deal().dealId())
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

  private SettlementRecords.SettlementRecord refreshSettlementStatus(
      SettlementRecords.SettlementRecord record, SettlementStatus next) {
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
    SettlementSourcePorts.FulfillmentSnapshot fulfillment =
        fulfillments.lockVisible(context, deal.dealId()).orElse(null);
    SettlementSourcePorts.RatificationSnapshot ratification =
        deal.ratifiedPackageId() == null
            ? null
            : ratifications
                .findRatifiedPackage(context, deal.dealId(), deal.ratifiedPackageId())
                .orElse(null);
    FundingRecords.PlanRecord plan = funding.findPlanByDeal(deal.dealId()).orElse(null);
    FundingRecords.UnitRecord unit =
        plan == null ? null : funding.findUnitByPlan(plan.id()).orElse(null);
    boolean activeDispute = casework.hasActiveDispute(deal.dealId());
    SettlementRecords.SettlementRecord settlement =
        settlements.findByDealId(deal.dealId()).orElse(null);
    SettlementRecords.ReleaseOperationRecord operation =
        settlement == null
            ? null
            : settlements.findOperationBySettlement(settlement.id()).orElse(null);
    return new SettlementEligibilityEvaluator.Context(
        deal, fulfillment, ratification, unit, activeDispute, settlement, operation);
  }

  private static void requireOperation(OperationContext context, RequestedOperation operation) {
    if (context.requestedOperation() != operation) {
      throw new IllegalArgumentException("Operation context mismatch");
    }
  }
}
