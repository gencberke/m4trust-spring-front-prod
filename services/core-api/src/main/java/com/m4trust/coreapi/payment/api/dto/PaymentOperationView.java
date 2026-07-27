package com.m4trust.coreapi.payment.api.dto;

import com.m4trust.coreapi.payment.domain.PaymentOperationStatus;
import com.m4trust.coreapi.payment.domain.PaymentProviderMode;
import java.time.Instant;
import java.util.UUID;

public record PaymentOperationView(
    UUID id,
    UUID fundingUnitId,
    PaymentOperationStatus status,
    boolean reconciliationRequired,
    String providerReference,
    long version,
    PaymentOperationAvailableActions availableActions,
    Instant createdAt,
    Instant updatedAt,
    PaymentProviderMode mode) {}
