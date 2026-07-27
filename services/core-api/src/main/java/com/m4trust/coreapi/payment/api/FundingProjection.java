package com.m4trust.coreapi.payment.api;

import com.m4trust.coreapi.organization.domain.LegalEntityRole;
import com.m4trust.coreapi.organization.domain.OperationContext;
import com.m4trust.coreapi.payment.api.dto.*;
import com.m4trust.coreapi.payment.domain.*;
import com.m4trust.coreapi.payment.infra.*;

/**
 * Shared, backend-derived projection logic used by every funding read path so FundingStatus and the
 * actor-aware availableActions never drift between the dedicated funding endpoints, the Deal detail
 * summary, and command responses.
 */
final class FundingProjection {
  private FundingProjection() {}

  static boolean isBuyerAdmin(OperationContext context, FundingSourcePorts.Target target) {
    return isBuyerAdmin(
        context.activeLegalEntityId().equals(target.buyerLegalEntityId()),
        context.activeLegalEntityRole());
  }

  static boolean isBuyerAdmin(boolean isBuyerEntity, LegalEntityRole role) {
    return isBuyerEntity && role == LegalEntityRole.ADMIN;
  }

  /**
   * The closed Deal-level FundingStatus axis has no FAILED member: a FAILED unit is retryable, so
   * it projects back to PLANNED at the Deal level. This mapping resolves a gap left open by the
   * contract (see report).
   */
  static String dealFundingStatus(FundingUnitStatus unitStatus) {
    return switch (unitStatus) {
      case PLANNED, FAILED -> "PLANNED";
      case PENDING -> "PENDING";
      case FUNDED -> "FUNDED";
    };
  }

  static PaymentOperationView operation(
      FundingRecords.OperationRecord op, boolean buyerAdmin, PaymentProviderMode mode) {
    boolean reconciliationRequired = op.status() == PaymentOperationStatus.UNCONFIRMED;
    boolean canReconcile = buyerAdmin && op.status() == PaymentOperationStatus.UNCONFIRMED;
    return new PaymentOperationView(
        op.id(),
        op.fundingUnitId(),
        op.status(),
        reconciliationRequired,
        op.providerReference(),
        op.version(),
        new PaymentOperationAvailableActions(canReconcile),
        op.createdAt(),
        op.updatedAt(),
        mode);
  }

  static FundingUnitView unit(
      FundingRecords.UnitRecord unit,
      FundingRecords.OperationRecord currentOperation,
      boolean buyerAdmin,
      boolean dealActive,
      PaymentProviderMode mode) {
    boolean inFlight =
        currentOperation != null
            && (currentOperation.status() == PaymentOperationStatus.CREATED
                || currentOperation.status() == PaymentOperationStatus.UNCONFIRMED);
    boolean canInitiatePayment =
        buyerAdmin && dealActive && unit.status() != FundingUnitStatus.FUNDED && !inFlight;
    PaymentOperationView operationView =
        currentOperation == null ? null : operation(currentOperation, buyerAdmin, mode);
    return new FundingUnitView(
        unit.id(),
        unit.sequenceNo(),
        unit.amountMinor(),
        unit.currency(),
        unit.status(),
        unit.version(),
        operationView,
        new FundingUnitAvailableActions(canInitiatePayment),
        unit.createdAt(),
        unit.updatedAt());
  }

  static FundingPlanDetailView plan(
      FundingRecords.PlanRecord plan,
      FundingRecords.UnitRecord unit,
      FundingRecords.OperationRecord currentOperation,
      boolean buyerAdmin,
      boolean dealActive,
      PaymentProviderMode mode) {
    return new FundingPlanDetailView(
        plan.id(),
        plan.dealId(),
        plan.amountMinor(),
        plan.currency(),
        dealFundingStatus(unit.status()),
        plan.version(),
        unit(unit, currentOperation, buyerAdmin, dealActive, mode),
        plan.createdAt(),
        plan.updatedAt(),
        mode);
  }
}
