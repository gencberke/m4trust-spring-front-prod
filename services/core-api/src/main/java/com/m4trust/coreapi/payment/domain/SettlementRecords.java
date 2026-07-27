package com.m4trust.coreapi.payment.domain;

import java.time.Instant;
import java.util.UUID;

/** Persistence-neutral snapshots owned by the settlement domain. */
public final class SettlementRecords {
  private SettlementRecords() {}

  public record SettlementRecord(
      UUID id,
      UUID dealId,
      UUID fundingUnitId,
      UUID tenantId,
      SettlementStatus status,
      Instant createdAt,
      Instant updatedAt,
      long version) {}

  public record ReleaseOperationRecord(
      UUID id,
      UUID settlementId,
      UUID providerKey,
      ReleaseOperationStatus status,
      String providerReference,
      Instant createdAt,
      Instant updatedAt,
      long version) {}

  public record ReleaseOperationLookup(
      ReleaseOperationRecord operation,
      UUID dealId,
      UUID tenantId,
      UUID fundingUnitId,
      long amountMinor,
      String currency,
      long dealVersion) {}

  public record DispatchRecord(
      UUID id,
      UUID releaseOperationId,
      DispatchType dispatchType,
      UUID providerKey,
      long amountMinor,
      String currency,
      Instant createdAt) {}

  public enum DispatchType {
    INITIATE,
    RECONCILE
  }
}
