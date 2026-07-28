package com.m4trust.coreapi.payment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Internal settlement aggregate owned by the payment module. */
public final class Settlement {

  private final UUID id;
  private final UUID dealId;
  private final UUID fundingUnitId;
  private final UUID tenantId;
  private SettlementStatus status;
  private Instant createdAt;
  private Instant updatedAt;
  private long version;

  private Settlement(
      UUID id,
      UUID dealId,
      UUID fundingUnitId,
      UUID tenantId,
      SettlementStatus status,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    this.id = Objects.requireNonNull(id);
    this.dealId = Objects.requireNonNull(dealId);
    this.fundingUnitId = Objects.requireNonNull(fundingUnitId);
    this.tenantId = Objects.requireNonNull(tenantId);
    this.status = Objects.requireNonNull(status);
    this.createdAt = Objects.requireNonNull(createdAt);
    this.updatedAt = Objects.requireNonNull(updatedAt);
    if (version < 0) {
      throw new IllegalArgumentException("version must be non-negative");
    }
    this.version = version;
  }

  public static Settlement create(
      UUID id, UUID dealId, UUID fundingUnitId, UUID tenantId, Instant now) {
    return new Settlement(
        id, dealId, fundingUnitId, tenantId, SettlementStatus.NOT_READY, now, now, 0);
  }

  public static Settlement rehydrate(SettlementRecords.SettlementRecord record) {
    return new Settlement(
        record.id(),
        record.dealId(),
        record.fundingUnitId(),
        record.tenantId(),
        record.status(),
        record.createdAt(),
        record.updatedAt(),
        record.version());
  }

  public void refreshReadiness(SettlementStatus next, Instant changedAt) {
    if (status.terminal()) {
      return;
    }
    if (status == SettlementStatus.PROCESSING || status == SettlementStatus.ON_HOLD) {
      return;
    }
    if (status != next) {
      status = next;
      updatedAt = Objects.requireNonNull(changedAt);
      version++;
    }
  }

  public void beginRelease(long expectedVersion, Instant changedAt) {
    requireVersion(expectedVersion);
    if (status != SettlementStatus.READY) {
      throw new IllegalStateException("settlement must be READY to begin release");
    }
    status = SettlementStatus.PROCESSING;
    updatedAt = Objects.requireNonNull(changedAt);
    version++;
  }

  public void markOnHold(Instant changedAt) {
    if (status.terminal()) {
      return;
    }
    if (status != SettlementStatus.ON_HOLD) {
      status = SettlementStatus.ON_HOLD;
      updatedAt = Objects.requireNonNull(changedAt);
      version++;
    }
  }

  public void markSimulatedSettled(Instant changedAt) {
    if (status == SettlementStatus.SIMULATED_SETTLED) {
      return;
    }
    status = SettlementStatus.SIMULATED_SETTLED;
    updatedAt = Objects.requireNonNull(changedAt);
    version++;
  }

  public void markFailed(Instant changedAt) {
    if (status == SettlementStatus.FAILED) {
      return;
    }
    status = SettlementStatus.FAILED;
    updatedAt = Objects.requireNonNull(changedAt);
    version++;
  }

  public void resumeProcessing(Instant changedAt) {
    if (status == SettlementStatus.ON_HOLD) {
      status = SettlementStatus.PROCESSING;
      updatedAt = Objects.requireNonNull(changedAt);
      version++;
    }
  }

  public SettlementRecords.SettlementRecord toRecord() {
    return new SettlementRecords.SettlementRecord(
        id, dealId, fundingUnitId, tenantId, status, createdAt, updatedAt, version);
  }

  public UUID id() {
    return id;
  }

  public UUID dealId() {
    return dealId;
  }

  public UUID fundingUnitId() {
    return fundingUnitId;
  }

  public UUID tenantId() {
    return tenantId;
  }

  public SettlementStatus status() {
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

  private void requireVersion(long expectedVersion) {
    if (version != expectedVersion) {
      throw new StaleVersion();
    }
  }

  public static final class StaleVersion extends RuntimeException {
    private static final long serialVersionUID = 1L;
  }
}
