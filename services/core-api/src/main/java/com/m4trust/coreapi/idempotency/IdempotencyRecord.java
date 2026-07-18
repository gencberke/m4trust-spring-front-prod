package com.m4trust.coreapi.idempotency;

import java.util.Optional;
import java.util.UUID;

record IdempotencyRecord(
        UUID id,
        String canonicalRequestHash,
        Optional<IdempotencyResultReference> resultReference) {
}
