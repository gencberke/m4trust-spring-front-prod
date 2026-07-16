package com.m4trust.coreapi.organization;

import java.util.UUID;

/**
 * Organization-owned entry point for provisioning the technical tenant
 * associated with a newly registered identity.
 */
public interface TenantProvisioningPort {

    UUID provisionForNewUser(UUID userId);
}
