package com.m4trust.coreapi.ratification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Mutable wrapper over an immutable package snapshot and immutable commercial terms. */
public final class RatificationPackage {
  public static final long MAX_SAFE_INTEGER = 9007199254740991L;

  private final UUID id;
  private final UUID dealId;
  private final UUID snapshotId;
  private final UUID buyerLegalEntityId;
  private final UUID sellerLegalEntityId;
  private final long amountMinor;
  private final String currency;
  private final Instant createdAt;
  private RatificationPackageStatus status;
  private long version;

  private RatificationPackage(
      UUID id,
      UUID dealId,
      UUID snapshotId,
      RatificationPackageStatus status,
      UUID buyerLegalEntityId,
      UUID sellerLegalEntityId,
      long amountMinor,
      String currency,
      Instant createdAt,
      long version) {
    this.id = Objects.requireNonNull(id);
    this.dealId = Objects.requireNonNull(dealId);
    this.snapshotId = Objects.requireNonNull(snapshotId);
    this.status = Objects.requireNonNull(status);
    this.buyerLegalEntityId = Objects.requireNonNull(buyerLegalEntityId);
    this.sellerLegalEntityId = Objects.requireNonNull(sellerLegalEntityId);
    this.amountMinor = amountMinor;
    this.currency = Objects.requireNonNull(currency);
    this.createdAt = Objects.requireNonNull(createdAt);
    this.version = version;
    validateStableFields();
  }

  public static RatificationPackage create(
      UUID id,
      UUID dealId,
      UUID snapshotId,
      UUID buyerLegalEntityId,
      UUID sellerLegalEntityId,
      long amountMinor,
      String currency,
      Instant createdAt) {
    return new RatificationPackage(
        id,
        dealId,
        snapshotId,
        RatificationPackageStatus.PENDING,
        buyerLegalEntityId,
        sellerLegalEntityId,
        amountMinor,
        currency,
        createdAt,
        0);
  }

  public static RatificationPackage rehydrate(RatificationPackageRecord record) {
    return new RatificationPackage(
        record.id(),
        record.dealId(),
        record.snapshotId(),
        record.status(),
        record.buyerLegalEntityId(),
        record.sellerLegalEntityId(),
        record.amountMinor(),
        record.currency(),
        record.createdAt(),
        record.version());
  }

  public void supersede(long expectedVersion) {
    transition(expectedVersion, RatificationPackageStatus.SUPERSEDED);
  }

  public void reject(long expectedVersion) {
    transition(expectedVersion, RatificationPackageStatus.REJECTED);
  }

  public void ratify(long expectedVersion) {
    transition(expectedVersion, RatificationPackageStatus.RATIFIED);
  }

  public void approve(long expectedVersion) {
    if (version != expectedVersion) {
      throw new StaleVersion();
    }
    if (status != RatificationPackageStatus.PENDING) {
      throw new StateConflict();
    }
    if (version == MAX_SAFE_INTEGER) {
      throw new IllegalStateException(
          "Ratification package version exceeds the safe integer range");
    }
    version++;
  }

  private void transition(long expectedVersion, RatificationPackageStatus nextStatus) {
    if (version != expectedVersion) {
      throw new StaleVersion();
    }
    if (status != RatificationPackageStatus.PENDING) {
      throw new StateConflict();
    }
    if (version == MAX_SAFE_INTEGER) {
      throw new IllegalStateException(
          "Ratification package version exceeds the safe integer range");
    }
    status = nextStatus;
    version++;
  }

  private void validateStableFields() {
    if (buyerLegalEntityId.equals(sellerLegalEntityId)
        || amountMinor < 1
        || amountMinor > MAX_SAFE_INTEGER
        || !currency.matches("[A-Z]{3}")
        || version < 0
        || version > MAX_SAFE_INTEGER) {
      throw new IllegalArgumentException("Invalid ratification package stable fields");
    }
  }

  public RatificationPackageRecord toRecord() {
    return new RatificationPackageRecord(
        id,
        dealId,
        snapshotId,
        status,
        buyerLegalEntityId,
        sellerLegalEntityId,
        amountMinor,
        currency,
        createdAt,
        version,
        null,
        null,
        null);
  }

  public UUID id() {
    return id;
  }

  public UUID dealId() {
    return dealId;
  }

  public UUID snapshotId() {
    return snapshotId;
  }

  public RatificationPackageStatus status() {
    return status;
  }

  public UUID buyerLegalEntityId() {
    return buyerLegalEntityId;
  }

  public UUID sellerLegalEntityId() {
    return sellerLegalEntityId;
  }

  public long amountMinor() {
    return amountMinor;
  }

  public String currency() {
    return currency;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public long version() {
    return version;
  }

  public static final class StaleVersion extends RuntimeException {}

  public static final class StateConflict extends RuntimeException {}
}
