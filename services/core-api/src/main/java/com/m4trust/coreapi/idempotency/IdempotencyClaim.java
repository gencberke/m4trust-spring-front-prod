package com.m4trust.coreapi.idempotency;

import java.util.Objects;
import java.util.UUID;

/**
 * The result of atomically claiming an idempotency key in the caller's active
 * business transaction.
 */
public record IdempotencyClaim(
        UUID recordId,
        IdempotencyClaimStatus status,
        IdempotencyResultReference resultReference) {

    public IdempotencyClaim {
        Objects.requireNonNull(recordId, "recordId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if ((status == IdempotencyClaimStatus.CLAIMED)
                != (resultReference == null)) {
            throw new IllegalArgumentException(
                    "Only a claimed key may omit a result reference");
        }
    }

    public boolean isReplay() {
        return status == IdempotencyClaimStatus.REPLAY;
    }
}
