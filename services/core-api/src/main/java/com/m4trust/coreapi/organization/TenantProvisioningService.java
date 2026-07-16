package com.m4trust.coreapi.organization;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class TenantProvisioningService implements TenantProvisioningPort {

    private final TenantRepository repository;

    TenantProvisioningService(TenantRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID provisionForNewUser(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        UUID tenantId = UUID.randomUUID();
        repository.insertTenant(tenantId);
        repository.linkUser(tenantId, userId);
        return tenantId;
    }
}
