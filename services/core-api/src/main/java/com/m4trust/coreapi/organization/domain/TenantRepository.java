package com.m4trust.coreapi.organization.domain;

import java.util.UUID;

/** Organization-owned persistence port for technical tenant provisioning. */
public interface TenantRepository {

  void insertTenant(UUID tenantId);

  void linkUser(UUID tenantId, UUID userId);
}
