package com.m4trust.coreapi.casework.api.dto;

import com.m4trust.coreapi.casework.domain.*;
import java.time.Instant;
import java.util.UUID;

public record DisputeDetail(
    UUID id,
    UUID dealId,
    DisputeStatus status,
    DisputeReasonCode reasonCode,
    String subject,
    String statement,
    DisputeOpeningLegalEntity openingLegalEntity,
    Instant openedAt,
    Instant acknowledgedAt,
    Instant withdrawnAt,
    DisputeOpeningSnapshot openingSnapshot,
    long version,
    DisputeAvailableActions availableActions) {}
