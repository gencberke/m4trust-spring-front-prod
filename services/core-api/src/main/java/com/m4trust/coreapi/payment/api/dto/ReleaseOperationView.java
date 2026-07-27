package com.m4trust.coreapi.payment.api.dto;

import com.m4trust.coreapi.payment.domain.PaymentProviderMode;
import com.m4trust.coreapi.payment.domain.ReleaseOperationStatus;
import java.time.Instant;
import java.util.UUID;

public record ReleaseOperationView(
    UUID id,
    UUID settlementId,
    ReleaseOperationStatus status,
    PaymentProviderMode mode,
    boolean reconciliationRequired,
    long version,
    ReleaseOperationAvailableActions availableActions,
    Instant createdAt,
    Instant updatedAt) {}
