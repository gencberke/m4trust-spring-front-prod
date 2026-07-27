package com.m4trust.coreapi.payment.api.dto;

import com.m4trust.coreapi.payment.domain.PaymentProviderMode;
import com.m4trust.coreapi.payment.domain.SettlementStatus;
import java.time.Instant;
import java.util.UUID;

public record SettlementDetailView(
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
    Instant updatedAt) {}
