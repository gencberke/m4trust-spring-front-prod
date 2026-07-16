package com.m4trust.coreapi.audit;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AuditRecord(
        UUID id,
        UUID tenantId,
        UUID actorUserId,
        UUID legalEntityId,
        String subjectType,
        UUID subjectId,
        String action,
        UUID correlationId,
        UUID causationId,
        Instant occurredAt) {

    public AuditRecord {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(subjectType, "subjectType must not be null");
        Objects.requireNonNull(subjectId, "subjectId must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
