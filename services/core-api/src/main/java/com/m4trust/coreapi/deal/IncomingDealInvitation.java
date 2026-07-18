package com.m4trust.coreapi.deal;

import java.time.Instant;
import java.util.UUID;

record IncomingDealInvitation(UUID id, DealInvitationDeal deal,
        DealInvitationStatus status, long version, Instant createdAt,
        Instant updatedAt, DealInvitationAvailableActions availableActions) {
}
