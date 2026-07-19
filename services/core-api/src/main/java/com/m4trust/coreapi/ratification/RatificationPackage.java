package com.m4trust.coreapi.ratification;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Mutable wrapper over an immutable package snapshot and immutable commercial terms. */
final class RatificationPackage {
    static final long MAX_SAFE_INTEGER = 9007199254740991L;

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

    static RatificationPackage create(
            UUID id,
            UUID dealId,
            UUID snapshotId,
            UUID buyerLegalEntityId,
            UUID sellerLegalEntityId,
            long amountMinor,
            String currency,
            Instant createdAt) {
        return new RatificationPackage(id, dealId, snapshotId, RatificationPackageStatus.PENDING,
                buyerLegalEntityId, sellerLegalEntityId, amountMinor, currency, createdAt, 0);
    }

    static RatificationPackage rehydrate(RatificationRepository.PackageRecord record) {
        return new RatificationPackage(record.id(), record.dealId(), record.snapshotId(), record.status(),
                record.buyerLegalEntityId(), record.sellerLegalEntityId(), record.amountMinor(),
                record.currency(), record.createdAt(), record.version());
    }

    void supersede(long expectedVersion) {
        transition(expectedVersion, RatificationPackageStatus.SUPERSEDED);
    }

    void reject(long expectedVersion) {
        transition(expectedVersion, RatificationPackageStatus.REJECTED);
    }

    void ratify(long expectedVersion) {
        transition(expectedVersion, RatificationPackageStatus.RATIFIED);
    }

    void approve(long expectedVersion) {
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
            throw new IllegalStateException("Ratification package version exceeds the safe integer range");
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

    RatificationRepository.PackageRecord toRecord() {
        return new RatificationRepository.PackageRecord(id, dealId, snapshotId, status,
                buyerLegalEntityId, sellerLegalEntityId, amountMinor, currency, createdAt, version,
                null, null, null);
    }

    UUID id() { return id; }
    UUID dealId() { return dealId; }
    UUID snapshotId() { return snapshotId; }
    RatificationPackageStatus status() { return status; }
    UUID buyerLegalEntityId() { return buyerLegalEntityId; }
    UUID sellerLegalEntityId() { return sellerLegalEntityId; }
    long amountMinor() { return amountMinor; }
    String currency() { return currency; }
    Instant createdAt() { return createdAt; }
    long version() { return version; }

    static final class StaleVersion extends RuntimeException { }
    static final class StateConflict extends RuntimeException { }
}
