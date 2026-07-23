package com.m4trust.coreapi.payment;

import java.time.Instant;
import java.util.UUID;

/** Package-private OpenAPI-shaped read projections for the funding surface. */
final class FundingReadDtos {
    private FundingReadDtos() { }

    record PaymentOperationAvailableActions(boolean canReconcile) { }

    record PaymentOperationView(
            UUID id,
            UUID fundingUnitId,
            PaymentOperationStatus status,
            boolean reconciliationRequired,
            String providerReference,
            long version,
            PaymentOperationAvailableActions availableActions,
            Instant createdAt,
            Instant updatedAt,
            PaymentProviderMode mode) { }

    record FundingUnitAvailableActions(boolean canInitiatePayment) { }

    record FundingUnitView(
            UUID id,
            int sequenceNo,
            long amountMinor,
            String currency,
            FundingUnitStatus status,
            long version,
            PaymentOperationView currentOperation,
            FundingUnitAvailableActions availableActions,
            Instant createdAt,
            Instant updatedAt) { }

    record FundingPlanDetailView(
            UUID id,
            UUID dealId,
            long amountMinor,
            String currency,
            String fundingStatus,
            long version,
            FundingUnitView fundingUnit,
            Instant createdAt,
            Instant updatedAt,
            PaymentProviderMode mode) { }
}
