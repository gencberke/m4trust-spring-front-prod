package com.m4trust.coreapi.payment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import com.m4trust.coreapi.organization.LegalEntityRole;
import com.m4trust.coreapi.organization.OperationContext;

/** Shared eligibility and projection helpers for settlement/release (ADR-014 §2.4). */
final class SettlementProjection {
    private SettlementProjection() { }

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

    static SettlementReadDtos.ReleaseOperationView operation(
            SettlementRepository.ReleaseOperationRecord operation,
            boolean buyerAdmin,
            PaymentProviderMode mode) {
        boolean reconciliationRequired = operation.status() == ReleaseOperationStatus.RECONCILIATION_REQUIRED;
        boolean canReconcile = buyerAdmin && reconciliationRequired;
        return new SettlementReadDtos.ReleaseOperationView(
                operation.id(),
                operation.settlementId(),
                operation.status(),
                mode,
                reconciliationRequired,
                operation.version(),
                new SettlementReadDtos.ReleaseOperationAvailableActions(canReconcile),
                operation.createdAt(),
                operation.updatedAt());
    }

    static SettlementReadDtos.SettlementDetailView detail(
            SettlementRepository.SettlementRecord settlement,
            Integer disputeWindowDays,
            Instant releaseEligibleAt,
            SettlementRepository.ReleaseOperationRecord currentOperation,
            boolean buyerAdmin,
            boolean canRequestRelease,
            boolean canReconcileRelease,
            PaymentProviderMode mode) {
        SettlementReadDtos.ReleaseOperationSummaryView operationSummary = currentOperation == null
                ? null
                : new SettlementReadDtos.ReleaseOperationSummaryView(
                        currentOperation.id(), currentOperation.status());
        return new SettlementReadDtos.SettlementDetailView(
                settlement.id(),
                settlement.dealId(),
                settlement.status(),
                mode,
                disputeWindowDays,
                releaseEligibleAt,
                operationSummary,
                new SettlementReadDtos.SettlementAvailableActions(canRequestRelease, canReconcileRelease),
                settlement.version(),
                settlement.createdAt(),
                settlement.updatedAt());
    }

    static SettlementProjectionPort.Summary dealSummary(
            SettlementRepository.SettlementRecord settlement,
            SettlementRepository.ReleaseOperationRecord operation) {
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
