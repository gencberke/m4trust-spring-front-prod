package com.m4trust.coreapi.identity.infra.security;

import com.m4trust.coreapi.identity.domain.PublicUser;
import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

public record IdentityPrincipal(UUID id, String email, String displayName)
    implements Principal, Serializable {

  public static IdentityPrincipal from(PublicUser user) {
    return new IdentityPrincipal(user.id(), user.email(), user.displayName());
  }

  public PublicUser toPublicUser() {
    return new PublicUser(id, email, displayName);
  }

  @Override
  public String getName() {
    return id.toString();
  }
}
