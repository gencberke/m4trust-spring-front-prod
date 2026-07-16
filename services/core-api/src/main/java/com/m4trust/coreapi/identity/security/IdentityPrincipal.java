package com.m4trust.coreapi.identity.security;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

import com.m4trust.coreapi.identity.PublicUser;

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
