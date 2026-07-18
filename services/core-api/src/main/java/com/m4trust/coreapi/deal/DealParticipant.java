package com.m4trust.coreapi.deal;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

record DealParticipant(UUID legalEntityId, String legalName, Instant joinedAt,
        List<DealPartyRole> partyRoles) {

    DealParticipant {
        partyRoles = List.copyOf(partyRoles);
    }
}
