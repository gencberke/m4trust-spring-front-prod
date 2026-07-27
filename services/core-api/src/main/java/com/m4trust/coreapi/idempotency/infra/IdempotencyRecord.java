package com.m4trust.coreapi.idempotency.infra;

import com.m4trust.coreapi.idempotency.domain.*;
import java.util.Optional;
import java.util.UUID;

record IdempotencyRecord(
    UUID id, String canonicalRequestHash, Optional<IdempotencyResultReference> resultReference) {}
