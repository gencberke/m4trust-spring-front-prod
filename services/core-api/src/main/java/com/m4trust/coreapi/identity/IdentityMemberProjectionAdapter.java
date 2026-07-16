package com.m4trust.coreapi.identity;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.m4trust.coreapi.organization.IdentityMemberProjectionPort;
import org.springframework.stereotype.Component;

@Component
class IdentityMemberProjectionAdapter
        implements IdentityMemberProjectionPort {

    private final IdentityRepository repository;

    IdentityMemberProjectionAdapter(IdentityRepository repository) {
        this.repository = repository;
    }

    @Override
    public Map<UUID, IdentityMemberProjection> findByIds(
            Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return repository.findPublicProjections(userIds).stream()
                .map(account -> new IdentityMemberProjection(
                        account.id(), account.email(), account.displayName()))
                .collect(Collectors.toUnmodifiableMap(
                        IdentityMemberProjection::userId,
                        Function.identity()));
    }
}
