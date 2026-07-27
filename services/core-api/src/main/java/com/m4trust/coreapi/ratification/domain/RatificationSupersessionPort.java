package com.m4trust.coreapi.ratification.domain;

import com.m4trust.coreapi.organization.domain.OperationContext;
import java.time.Instant;
import java.util.UUID;

/**
 * Ratification-owned boundary for invalidating a Deal's current pending package. The caller must
 * already hold the Deal row lock in its transaction.
 */
public interface RatificationSupersessionPort {

  void supersedePending(
      OperationContext context,
      UUID dealId,
      UUID currentPackageId,
      UUID correlationId,
      Instant occurredAt);

  final class Stale extends RuntimeException {}

  final class InvariantViolation extends RuntimeException {}
}
