package com.m4trust.coreapi.organization;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Narrow organization-owned query surface for Deal invitation membership
 * validation and legal-name projections.
 */
public interface InvitationLegalEntityQueryPort {

    Optional<InvitationLegalEntityMembership> findCurrentMembership(
            UUID userId, UUID legalEntityId);

    Optional<UUID> findTenantIdForUser(UUID userId);

    Map<UUID, String> findLegalNames(Set<UUID> legalEntityIds);

    record InvitationLegalEntityMembership(UUID legalEntityId, UUID tenantId) {
    }
}
