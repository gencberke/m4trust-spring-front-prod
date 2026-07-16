package com.m4trust.coreapi.deal;

import java.time.Instant;
import java.util.UUID;

record DealSummary(
        UUID id,
        String reference,
        String title,
        DealStatus status,
        DealLifecycleProjection lifecycle,
        long version,
        Instant createdAt,
        Instant updatedAt,
        DealAvailableActions availableActions) {
}
