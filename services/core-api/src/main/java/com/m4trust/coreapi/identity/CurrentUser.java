package com.m4trust.coreapi.identity;

import java.util.List;
import java.util.UUID;

import com.m4trust.coreapi.organization.LegalEntityMembership;

public record CurrentUser(
        UUID id,
        String email,
        String displayName,
        List<LegalEntityMembership> memberships) {

    public CurrentUser {
        memberships = List.copyOf(memberships);
    }
}
