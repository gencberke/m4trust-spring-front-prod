package com.m4trust.coreapi.idempotency;

import java.util.Objects;
import java.util.UUID;

/**
 * The server-side identity of one idempotent HTTP operation.
 *
 * <p>The caller supplies a SHA-256 hash of its canonical request representation;
 * canonicalization itself remains owned by the use case that defines the request.
 */
public record IdempotencyRequest(
        UUID actorUserId,
        UUID actorTenantId,
        String operation,
        UUID key,
        String canonicalRequestHash) {

    public IdempotencyRequest {
        Objects.requireNonNull(actorUserId, "actorUserId must not be null");
        Objects.requireNonNull(actorTenantId, "actorTenantId must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(canonicalRequestHash,
                "canonicalRequestHash must not be null");
        if (!operation.equals(operation.trim())
                || operation.isBlank() || operation.length() > 100) {
            throw new IllegalArgumentException(
                    "operation must be trimmed and between 1 and 100 characters");
        }
        if (!canonicalRequestHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "canonicalRequestHash must be a lowercase SHA-256 hex digest");
        }
    }
}
