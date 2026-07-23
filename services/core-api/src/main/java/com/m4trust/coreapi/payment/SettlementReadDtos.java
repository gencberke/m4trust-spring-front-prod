package com.m4trust.coreapi.payment;

import java.time.Instant;
import java.util.UUID;

/** Package-private OpenAPI-shaped read projections for the settlement surface. */
final class SettlementReadDtos {
    private SettlementReadDtos() { }

    record SettlementAvailableActions(boolean canRequestRelease, boolean canReconcileRelease) { }

    record ReleaseOperationAvailableActions(boolean canReconcile) { }

    record ReleaseOperationSummaryView(UUID id, ReleaseOperationStatus status) { }

    record SettlementDetailView(
            UUID id,
            UUID dealId,
            SettlementStatus status,
            PaymentProviderMode mode,
            Integer disputeWindowDays,
            Instant releaseEligibleAt,
            ReleaseOperationSummaryView currentReleaseOperation,
            SettlementAvailableActions availableActions,
            long version,
            Instant createdAt,
            Instant updatedAt) { }

    record ReleaseOperationView(
            UUID id,
            UUID settlementId,
            ReleaseOperationStatus status,
            PaymentProviderMode mode,
            boolean reconciliationRequired,
            long version,
            ReleaseOperationAvailableActions availableActions,
            Instant createdAt,
            Instant updatedAt) { }
}
