package com.m4trust.coreapi.deal.api;

import com.m4trust.coreapi.deal.api.port.*;
import com.m4trust.coreapi.deal.domain.*;
import com.m4trust.coreapi.deal.infra.repository.*;
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
    DealAvailableActions availableActions) {}
