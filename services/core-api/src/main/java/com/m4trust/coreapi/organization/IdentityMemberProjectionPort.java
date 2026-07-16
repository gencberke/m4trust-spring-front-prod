package com.m4trust.coreapi.organization;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Narrow read-only projection boundary for organization member views.
 */
public interface IdentityMemberProjectionPort {

    Map<UUID, IdentityMemberProjection> findByIds(Collection<UUID> userIds);

    record IdentityMemberProjection(UUID userId, String email, String displayName) {
    }
}
