package com.m4trust.coreapi.organization;

import java.util.Objects;
import java.util.UUID;

/**
 * Application-layer authorization context resolved for one organization-scoped
 * operation. The active legal entity is request-scoped and is never stored in
 * the authenticated session.
 */
public record OperationContext(
        UUID authenticatedUserId,
        UUID tenantId,
        UUID activeLegalEntityId,
        LegalEntityRole activeLegalEntityRole,
        RequestedOperation requestedOperation) {

    public OperationContext {
        Objects.requireNonNull(authenticatedUserId);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(activeLegalEntityId);
        Objects.requireNonNull(activeLegalEntityRole);
        Objects.requireNonNull(requestedOperation);
    }
}
