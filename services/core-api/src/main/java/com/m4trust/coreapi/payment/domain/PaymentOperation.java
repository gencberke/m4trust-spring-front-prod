package com.m4trust.coreapi.payment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One PaymentOperation attempt on a FundingUnit. {@code providerKey} is fixed for the lifetime of
 * the operation (ADR-010 §2.4): every dispatch/retry uses the same key, never a new one.
 */
public final class PaymentOperation {
  static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;

  private final UUID id;
  private final UUID fundingUnitId;
  private final UUID providerKey;
  private final Instant createdAt;
  private PaymentOperationStatus status;
  private String providerReference;
  private Instant updatedAt;
  private long version;

  private PaymentOperation(
      UUID id,
      UUID fundingUnitId,
      UUID providerKey,
      PaymentOperationStatus status,
      String providerReference,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    this.id = Objects.requireNonNull(id);
    this.fundingUnitId = Objects.requireNonNull(fundingUnitId);
    this.providerKey = Objects.requireNonNull(providerKey);
    this.status = Objects.requireNonNull(status);
    this.providerReference = providerReference;
    this.createdAt = Objects.requireNonNull(createdAt);
    this.updatedAt = Objects.requireNonNull(updatedAt);
    if (version < 0) {
      throw new IllegalArgumentException("version must not be negative");
    }
    this.version = version;
  }

  public static PaymentOperation create(
      UUID id, UUID fundingUnitId, UUID providerKey, Instant now) {
    return new PaymentOperation(
        id, fundingUnitId, providerKey, PaymentOperationStatus.CREATED, null, now, now, 0);
  }

  public static PaymentOperation rehydrate(FundingRecords.OperationRecord record) {
    return new PaymentOperation(
        record.id(),
        record.fundingUnitId(),
        record.providerKey(),
        record.status(),
        record.providerReference(),
        record.createdAt(),
        record.updatedAt(),
        record.version());
  }

  public boolean inFlight() {
    return status == PaymentOperationStatus.CREATED || status == PaymentOperationStatus.UNCONFIRMED;
  }

  public boolean terminal() {
    return status == PaymentOperationStatus.SUCCEEDED || status == PaymentOperationStatus.DECLINED;
  }

  /** CREATED|UNCONFIRMED -> SUCCEEDED, on a verified provider SUCCEEDED result. */
  public void applySucceeded(String providerReference, Instant now) {
    requireNonTerminal();
    apply(PaymentOperationStatus.SUCCEEDED, providerReference, now);
  }

  /** CREATED|UNCONFIRMED -> DECLINED, on a definitive provider DECLINED result. */
  public void applyDeclined(String providerReference, Instant now) {
    requireNonTerminal();
    apply(PaymentOperationStatus.DECLINED, providerReference, now);
  }

  /** CREATED -> UNCONFIRMED, on timeout/crash/ambiguous response. */
  public void markUnconfirmed(Instant now) {
    if (status != PaymentOperationStatus.CREATED) {
      throw new StateConflict();
    }
    apply(PaymentOperationStatus.UNCONFIRMED, providerReference, now);
  }

  private void requireNonTerminal() {
    if (terminal()) {
      throw new StateConflict();
    }
  }

  private void apply(PaymentOperationStatus next, String nextProviderReference, Instant now) {
    if (version == MAX_SAFE_INTEGER) {
      throw new IllegalStateException("Payment operation version exceeds the safe integer range");
    }
    status = next;
    providerReference = nextProviderReference;
    updatedAt = Objects.requireNonNull(now);
    version++;
  }

  public FundingRecords.OperationRecord toRecord() {
    return new FundingRecords.OperationRecord(
        id, fundingUnitId, providerKey, status, providerReference, createdAt, updatedAt, version);
  }

  public UUID id() {
    return id;
  }

  public UUID fundingUnitId() {
    return fundingUnitId;
  }

  public UUID providerKey() {
    return providerKey;
  }

  public PaymentOperationStatus status() {
    return status;
  }

  public String providerReference() {
    return providerReference;
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
