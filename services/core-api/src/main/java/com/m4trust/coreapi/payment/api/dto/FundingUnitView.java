package com.m4trust.coreapi.payment.api.dto;

import com.m4trust.coreapi.payment.domain.FundingUnitStatus;
import java.time.Instant;
import java.util.UUID;

public record FundingUnitView(
    UUID id,
    int sequenceNo,
    long amountMinor,
    String currency,
    FundingUnitStatus status,
    long version,
    PaymentOperationView currentOperation,
    FundingUnitAvailableActions availableActions,
    Instant createdAt,
    Instant updatedAt) {}
