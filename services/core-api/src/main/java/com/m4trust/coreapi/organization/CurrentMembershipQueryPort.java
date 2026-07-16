package com.m4trust.coreapi.organization;

import java.util.List;
import java.util.UUID;

/**
 * Organization-owned bootstrap projection used by authentication surfaces.
 */
public interface CurrentMembershipQueryPort {

    List<LegalEntityMembership> findMemberships(UUID authenticatedUserId);
}
