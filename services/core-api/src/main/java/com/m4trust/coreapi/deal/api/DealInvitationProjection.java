package com.m4trust.coreapi.deal.api;

import com.m4trust.coreapi.deal.api.port.*;
import com.m4trust.coreapi.deal.domain.*;
import com.m4trust.coreapi.deal.infra.repository.*;
import java.time.Instant;
import java.util.UUID;

record DealInvitationProjection(
    UUID id,
    UUID dealId,
    String recipientEmail,
    DealInvitationStatus status,
    long version,
    Instant createdAt,
    Instant updatedAt,
    DealInvitationAvailableActions availableActions) {}
