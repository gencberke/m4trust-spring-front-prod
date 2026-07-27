package com.m4trust.coreapi.payment.api;

import com.m4trust.coreapi.organization.domain.LegalEntityRole;
import com.m4trust.coreapi.organization.domain.OperationContext;
import com.m4trust.coreapi.payment.api.dto.*;
import com.m4trust.coreapi.payment.domain.*;
import com.m4trust.coreapi.payment.infra.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Shared eligibility and projection helpers for settlement/release (ADR-014 §2.4). */
final class SettlementProjection {
  private SettlementProjection() {}

  static boolean isBuyerAdmin(OperationContext context, SettlementSourcePorts.DealSnapshot deal) {
    return context.activeLegalEntityId().equals(deal.buyerLegalEntityId())
        && context.activeLegalEntityRole() == LegalEntityRole.ADMIN;
  }

  static Instant releaseEligibleAt(Instant completedAt, int disputeWindowDays) {
    return completedAt.plus(Duration.ofDays(disputeWindowDays));
  }

  static boolean windowElapsed(Instant completedAt, Integer disputeWindowDays, Instant now) {
    if (completedAt == null || disputeWindowDays == null) {
      return false;
    }
    return !now.isBefore(releaseEligibleAt(completedAt, disputeWindowDays));
  }

  static ReleaseOperationView operation(
      SettlementRecords.ReleaseOperationRecord operation,
      boolean buyerAdmin,
      PaymentProviderMode mode) {
    boolean reconciliationRequired =
        operation.status() == ReleaseOperationStatus.RECONCILIATION_REQUIRED;
    boolean canReconcile = buyerAdmin && reconciliationRequired;
    return new ReleaseOperationView(
        operation.id(),
        operation.settlementId(),
        operation.status(),
        mode,
        reconciliationRequired,
        operation.version(),
        new ReleaseOperationAvailableActions(canReconcile),
        operation.createdAt(),
        operation.updatedAt());
  }

  static SettlementDetailView detail(
      SettlementRecords.SettlementRecord settlement,
      Integer disputeWindowDays,
      Instant releaseEligibleAt,
      SettlementRecords.ReleaseOperationRecord currentOperation,
      boolean buyerAdmin,
      boolean canRequestRelease,
      boolean canReconcileRelease,
      PaymentProviderMode mode) {
    ReleaseOperationSummaryView operationSummary =
        currentOperation == null
            ? null
            : new ReleaseOperationSummaryView(currentOperation.id(), currentOperation.status());
    return new SettlementDetailView(
        settlement.id(),
        settlement.dealId(),
        settlement.status(),
        mode,
        disputeWindowDays,
        releaseEligibleAt,
        operationSummary,
        new SettlementAvailableActions(canRequestRelease, canReconcileRelease),
        settlement.version(),
        settlement.createdAt(),
        settlement.updatedAt());
  }

  static SettlementProjectionPort.Summary dealSummary(
      SettlementRecords.SettlementRecord settlement,
      SettlementRecords.ReleaseOperationRecord operation) {
    return new SettlementProjectionPort.Summary(
        settlement.id(),
        settlement.status().name(),
        operation == null ? null : operation.id(),
        settlement.status() == SettlementStatus.READY && operation == null,
        operation != null && operation.status() == ReleaseOperationStatus.RECONCILIATION_REQUIRED);
  }

  static void requireNonNull(PaymentProviderMode mode) {
    Objects.requireNonNull(mode, "settlement mode must be present when settlement exists");
  }
}
