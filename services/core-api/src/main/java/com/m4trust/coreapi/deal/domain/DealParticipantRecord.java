package com.m4trust.coreapi.deal.domain;

import java.time.Instant;
import java.util.UUID;

/** Persistence-neutral participant snapshot. */
public record DealParticipantRecord(
    UUID legalEntityId, UUID legalEntityTenantId, Instant createdAt) {}
