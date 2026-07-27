package com.m4trust.coreapi.payment.domain;

import java.time.Instant;
import java.util.UUID;

/** Persistence-neutral snapshots owned by the payment domain. */
public final class FundingRecords {
  private FundingRecords() {}

  public record PlanRecord(
      UUID id,
      UUID dealId,
      UUID ratificationPackageId,
      UUID tenantId,
      long amountMinor,
      String currency,
      Instant createdAt,
      Instant updatedAt,
      long version) {}

  public record UnitRecord(
      UUID id,
      UUID fundingPlanId,
      int sequenceNo,
      long amountMinor,
      String currency,
      FundingUnitStatus status,
      Instant createdAt,
      Instant updatedAt,
      long version) {}

  public record UnitLookup(UnitRecord unit, UUID dealId, UUID tenantId) {}

  public record OperationRecord(
      UUID id,
      UUID fundingUnitId,
      UUID providerKey,
      PaymentOperationStatus status,
      String providerReference,
      Instant createdAt,
      Instant updatedAt,
      long version) {}

  public record OperationLookup(
      OperationRecord operation,
      UUID dealId,
      UUID tenantId,
      long unitAmountMinor,
      String unitCurrency) {}

  public record DispatchRecord(
      UUID id,
      UUID paymentOperationId,
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
