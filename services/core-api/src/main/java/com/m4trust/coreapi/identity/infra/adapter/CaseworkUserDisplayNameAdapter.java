package com.m4trust.coreapi.identity.infra.adapter;

import com.m4trust.coreapi.casework.domain.CaseworkSourcePorts;
import com.m4trust.coreapi.identity.infra.persistence.IdentityRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Identity-owned display-name projection for casework comment attribution. */
@Service
class CaseworkUserDisplayNameAdapter implements CaseworkSourcePorts.UserDisplayNames {

  private final IdentityRepository identities;

  CaseworkUserDisplayNameAdapter(IdentityRepository identities) {
    this.identities = identities;
  }

  @Override
  public String requireDisplayName(UUID userId) {
    return identities.findPublicProjections(List.of(userId)).stream()
        .findFirst()
        .map(IdentityRepository.PublicIdentityProjection::displayName)
        .filter(name -> name != null && !name.isBlank())
        .orElseThrow(() -> new IllegalStateException("display name unavailable"));
  }
}
