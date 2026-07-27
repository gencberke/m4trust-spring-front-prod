package com.m4trust.coreapi.fulfillment.api.dto;

import com.m4trust.coreapi.fulfillment.domain.EvidencePolicy;
import com.m4trust.coreapi.fulfillment.domain.FulfillmentStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FulfillmentDetail(
    UUID id,
    UUID dealId,
    FulfillmentStatus status,
    UUID sourcePackageId,
    EvidencePolicy evidencePolicy,
    FulfillmentMilestoneProjection milestone,
    EvidenceSubmissionProjection currentEvidence,
    List<EvidenceSubmissionProjection> history,
    FulfillmentAvailableActions availableActions,
    long version,
    Instant createdAt,
    Instant updatedAt) {}
