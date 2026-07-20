package com.m4trust.coreapi.ratification;

import java.time.Instant;
import java.util.UUID;

import com.m4trust.coreapi.organization.OperationContext;

/**
 * Ratification-owned boundary for invalidating a Deal's current pending package.
 * The caller must already hold the Deal row lock in its transaction.
 */
public interface RatificationSupersessionPort {

    void supersedePending(
            OperationContext context,
            UUID dealId,
            UUID currentPackageId,
            UUID correlationId,
            Instant occurredAt);

    final class Stale extends RuntimeException { }

    final class InvariantViolation extends RuntimeException { }
}
