package com.m4trust.coreapi.deal.api;

import com.m4trust.coreapi.deal.api.port.*;
import com.m4trust.coreapi.deal.domain.*;
import com.m4trust.coreapi.deal.infra.repository.*;
import java.time.Instant;
import java.util.UUID;

/** Actor-aware active dispute summary exposed on Deal detail for buyer/seller only. */
record DealCaseworkSummary(
    UUID disputeId,
    String status,
    String reasonCode,
    String subject,
    DealCaseworkOpeningLegalEntity openingLegalEntity,
    Instant openedAt,
    Instant acknowledgedAt,
    long version) {}

record DealCaseworkOpeningLegalEntity(UUID legalEntityId, String legalName) {}
