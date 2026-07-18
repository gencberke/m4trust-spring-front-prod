package com.m4trust.coreapi.deal;

import java.time.Instant;
import java.util.UUID;

record DealInvitationProjection(UUID id, UUID dealId, String recipientEmail,
        DealInvitationStatus status, long version, Instant createdAt,
        Instant updatedAt, DealInvitationAvailableActions availableActions) {
}
