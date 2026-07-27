package com.m4trust.coreapi.payment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** V1 always sequence 1: exactly one FundingUnit exists per FundingPlan (ADR-010 §2.2). */
public final class FundingUnit {
  static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;

  private final UUID id;
  private final UUID fundingPlanId;
  private final int sequenceNo;
  private final long amountMinor;
  private final String currency;
  private final Instant createdAt;
  private FundingUnitStatus status;
  private Instant updatedAt;
  private long version;

  private FundingUnit(
      UUID id,
      UUID fundingPlanId,
      int sequenceNo,
      long amountMinor,
      String currency,
      FundingUnitStatus status,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    this.id = Objects.requireNonNull(id);
    this.fundingPlanId = Objects.requireNonNull(fundingPlanId);
    this.sequenceNo = sequenceNo;
    this.amountMinor = amountMinor;
    this.currency = Objects.requireNonNull(currency);
    this.status = Objects.requireNonNull(status);
    this.createdAt = Objects.requireNonNull(createdAt);
    this.updatedAt = Objects.requireNonNull(updatedAt);
    this.version = version;
    if (sequenceNo != 1
        || amountMinor < 1
        || amountMinor > MAX_SAFE_INTEGER
        || !currency.matches("[A-Z]{3}")
        || version < 0) {
      throw new IllegalArgumentException("Invalid funding unit stable fields");
    }
  }

  public static FundingUnit create(
      UUID id, UUID fundingPlanId, long amountMinor, String currency, Instant now) {
    return new FundingUnit(
        id, fundingPlanId, 1, amountMinor, currency, FundingUnitStatus.PLANNED, now, now, 0);
  }

  public static FundingUnit rehydrate(FundingRecords.UnitRecord record) {
    return new FundingUnit(
        record.id(),
        record.fundingPlanId(),
        record.sequenceNo(),
        record.amountMinor(),
        record.currency(),
        record.status(),
        record.createdAt(),
        record.updatedAt(),
        record.version());
  }

  /** PLANNED -> PENDING, or FAILED -> PENDING when a new PaymentOperation starts. */
  public void beginPayment(long expectedVersion, Instant now) {
    if (status != FundingUnitStatus.PLANNED && status != FundingUnitStatus.FAILED) {
      throw new StateConflict();
    }
    transition(expectedVersion, FundingUnitStatus.PENDING, now);
  }

  /** PENDING -> FUNDED, only after a verified provider SUCCEEDED result. */
  public void markFunded(long expectedVersion, Instant now) {
    if (status != FundingUnitStatus.PENDING) {
      throw new StateConflict();
    }
    transition(expectedVersion, FundingUnitStatus.FUNDED, now);
  }

  /** PENDING -> FAILED, only after a definitive provider DECLINED result. */
  public void markFailed(long expectedVersion, Instant now) {
    if (status != FundingUnitStatus.PENDING) {
      throw new StateConflict();
    }
    transition(expectedVersion, FundingUnitStatus.FAILED, now);
  }

  private void transition(long expectedVersion, FundingUnitStatus next, Instant now) {
    if (version != expectedVersion) {
      throw new StaleVersion();
    }
    if (version == MAX_SAFE_INTEGER) {
      throw new IllegalStateException("Funding unit version exceeds the safe integer range");
    }
    status = next;
    updatedAt = Objects.requireNonNull(now);
    version++;
  }

  public FundingRecords.UnitRecord toRecord() {
    return new FundingRecords.UnitRecord(
        id,
        fundingPlanId,
        sequenceNo,
        amountMinor,
        currency,
        status,
        createdAt,
        updatedAt,
        version);
  }

  public UUID id() {
    return id;
  }

  public UUID fundingPlanId() {
    return fundingPlanId;
  }

  public int sequenceNo() {
    return sequenceNo;
  }

  public long amountMinor() {
    return amountMinor;
  }

  public String currency() {
    return currency;
  }

  public FundingUnitStatus status() {
    return status;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public long version() {
    return version;
  }

  public static final class StaleVersion extends RuntimeException {}

  public static final class StateConflict extends RuntimeException {}
}
