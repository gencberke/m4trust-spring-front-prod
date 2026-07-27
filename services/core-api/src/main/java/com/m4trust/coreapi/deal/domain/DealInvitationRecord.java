package com.m4trust.coreapi.deal.domain;

import java.time.Instant;
import java.util.UUID;

/** Persistence-neutral snapshot used to rehydrate a Deal invitation. */
public record DealInvitationRecord(
    UUID id,
    UUID tenantId,
    UUID dealId,
    String recipientEmail,
    DealInvitationStatus status,
    UUID acceptedLegalEntityId,
    UUID acceptedLegalEntityTenantId,
    Instant createdAt,
    Instant updatedAt,
    long version,
    DealRecord deal) {}
