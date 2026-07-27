package com.m4trust.coreapi.payment.api.dto;

import com.m4trust.coreapi.payment.domain.PaymentProviderMode;
import java.time.Instant;
import java.util.UUID;

public record FundingPlanDetailView(
    UUID id,
    UUID dealId,
    long amountMinor,
    String currency,
    String fundingStatus,
    long version,
    FundingUnitView fundingUnit,
    Instant createdAt,
    Instant updatedAt,
    PaymentProviderMode mode) {}
