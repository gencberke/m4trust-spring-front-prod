package com.m4trust.coreapi.idempotency;

import java.util.Objects;
import java.util.UUID;

/**
 * A stable reference that lets an owning use case load and return an equivalent
 * result when an idempotent request is replayed.
 */
public record IdempotencyResultReference(String type, UUID id) {

    public IdempotencyResultReference {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(id, "id must not be null");
        if (!type.equals(type.trim()) || type.isBlank() || type.length() > 100) {
            throw new IllegalArgumentException(
                    "type must be trimmed and between 1 and 100 characters");
        }
    }
}
