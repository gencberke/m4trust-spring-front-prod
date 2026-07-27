package com.m4trust.coreapi.ratification.domain;

import java.time.Instant;
import java.util.UUID;

/** Persistence-neutral snapshot used to rehydrate and store a ratification package. */
public record RatificationPackageRecord(
    UUID id,
    UUID dealId,
    UUID snapshotId,
    RatificationPackageStatus status,
    UUID buyerLegalEntityId,
    UUID sellerLegalEntityId,
    long amountMinor,
    String currency,
    Instant createdAt,
    long version,
    Integer snapshotSchemaVersion,
    String canonicalSnapshot,
    String contentHash) {

  public RatificationPackageRecord(
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
    this(
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
}
