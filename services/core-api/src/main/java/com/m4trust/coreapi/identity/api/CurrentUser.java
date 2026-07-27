package com.m4trust.coreapi.identity.api;

import com.m4trust.coreapi.organization.domain.LegalEntityMembership;
import java.util.List;
import java.util.UUID;

public record CurrentUser(
    UUID id, String email, String displayName, List<LegalEntityMembership> memberships) {

  public CurrentUser {
    memberships = List.copyOf(memberships);
  }
}
