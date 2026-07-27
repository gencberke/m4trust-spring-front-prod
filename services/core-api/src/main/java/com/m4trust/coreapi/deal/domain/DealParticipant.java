package com.m4trust.coreapi.deal.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DealParticipant(
    UUID legalEntityId, String legalName, Instant joinedAt, List<DealPartyRole> partyRoles) {

  public DealParticipant {
    partyRoles = List.copyOf(partyRoles);
  }
}
