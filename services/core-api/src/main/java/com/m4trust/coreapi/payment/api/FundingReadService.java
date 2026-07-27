package com.m4trust.coreapi.payment.api;

import com.m4trust.coreapi.organization.domain.OperationContext;
import com.m4trust.coreapi.organization.domain.RequestedOperation;
import com.m4trust.coreapi.payment.api.dto.*;
import com.m4trust.coreapi.payment.domain.*;
import com.m4trust.coreapi.payment.infra.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Participant-readable funding plan / payment operation projections. */
@Service
class FundingReadService {

  private final FundingSourcePorts.DealTarget deals;
  private final FundingRepository funding;
  private final PaymentProviderModeResolver mode;

  FundingReadService(
      FundingSourcePorts.DealTarget deals,
      FundingRepository funding,
      PaymentProviderModeResolver mode) {
    this.deals = deals;
    this.funding = funding;
    this.mode = mode;
  }

  @Transactional(readOnly = true)
  FundingPlanDetailView getPlan(OperationContext context, UUID dealId) {
    requireOperation(context, RequestedOperation.DEAL_FUNDING_PLAN_READ);
    FundingSourcePorts.Target target =
        deals.findVisible(context, dealId).orElseThrow(PaymentExceptions.FundingPlanNotFound::new);
    FundingRecords.PlanRecord plan =
        funding.findPlanByDeal(dealId).orElseThrow(PaymentExceptions.FundingPlanNotFound::new);
    FundingRecords.UnitRecord unit =
        funding
            .findUnitByPlan(plan.id())
            .orElseThrow(
                () -> new IllegalStateException("Funding plan is missing its funding unit"));
    FundingRecords.OperationRecord currentOperation =
        funding.findCurrentOperation(unit.id()).orElse(null);
    boolean buyerAdmin = FundingProjection.isBuyerAdmin(context, target);
    return FundingProjection.plan(
        plan, unit, currentOperation, buyerAdmin, "ACTIVE".equals(target.status()), mode.resolve());
  }

  @Transactional(readOnly = true)
  PaymentOperationView getOperation(OperationContext context, UUID operationId) {
    requireOperation(context, RequestedOperation.PAYMENT_OPERATION_READ);
    FundingRecords.OperationLookup lookup =
        funding
            .findOperationById(operationId)
            .orElseThrow(PaymentExceptions.PaymentOperationNotFound::new);
    FundingSourcePorts.Target target =
        deals
            .findVisible(context, lookup.dealId())
            .orElseThrow(PaymentExceptions.PaymentOperationNotFound::new);
    boolean buyerAdmin = FundingProjection.isBuyerAdmin(context, target);
    return FundingProjection.operation(lookup.operation(), buyerAdmin, mode.resolve());
  }

  private static void requireOperation(OperationContext context, RequestedOperation expected) {
    if (context.requestedOperation() != expected) {
      throw new IllegalArgumentException("Operation context does not match the requested use case");
    }
  }
}
