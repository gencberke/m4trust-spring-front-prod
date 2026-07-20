package com.m4trust.coreapi.payment;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One PaymentOperation attempt on a FundingUnit. {@code providerKey} is fixed
 * for the lifetime of the operation (ADR-010 §2.4): every dispatch/retry uses
 * the same key, never a new one.
 */
final class PaymentOperation {
    static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;

    private final UUID id;
    private final UUID fundingUnitId;
    private final UUID providerKey;
    private final Instant createdAt;
    private PaymentOperationStatus status;
    private String providerReference;
    private Instant updatedAt;
    private long version;

    private PaymentOperation(UUID id, UUID fundingUnitId, UUID providerKey, PaymentOperationStatus status,
            String providerReference, Instant createdAt, Instant updatedAt, long version) {
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

    static PaymentOperation create(UUID id, UUID fundingUnitId, UUID providerKey, Instant now) {
        return new PaymentOperation(id, fundingUnitId, providerKey, PaymentOperationStatus.CREATED,
                null, now, now, 0);
    }

    static PaymentOperation rehydrate(FundingRepository.OperationRecord record) {
        return new PaymentOperation(record.id(), record.fundingUnitId(), record.providerKey(), record.status(),
                record.providerReference(), record.createdAt(), record.updatedAt(), record.version());
    }

    boolean inFlight() {
        return status == PaymentOperationStatus.CREATED || status == PaymentOperationStatus.UNCONFIRMED;
    }

    boolean terminal() {
        return status == PaymentOperationStatus.SUCCEEDED || status == PaymentOperationStatus.DECLINED;
    }

    /** CREATED|UNCONFIRMED -> SUCCEEDED, on a verified provider SUCCEEDED result. */
    void applySucceeded(String providerReference, Instant now) {
        requireNonTerminal();
        apply(PaymentOperationStatus.SUCCEEDED, providerReference, now);
    }

    /** CREATED|UNCONFIRMED -> DECLINED, on a definitive provider DECLINED result. */
    void applyDeclined(String providerReference, Instant now) {
        requireNonTerminal();
        apply(PaymentOperationStatus.DECLINED, providerReference, now);
    }

    /** CREATED -> UNCONFIRMED, on timeout/crash/ambiguous response. */
    void markUnconfirmed(Instant now) {
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

    FundingRepository.OperationRecord toRecord() {
        return new FundingRepository.OperationRecord(id, fundingUnitId, providerKey, status, providerReference,
                createdAt, updatedAt, version);
    }

    UUID id() { return id; }
    UUID fundingUnitId() { return fundingUnitId; }
    UUID providerKey() { return providerKey; }
    PaymentOperationStatus status() { return status; }
    String providerReference() { return providerReference; }
    Instant createdAt() { return createdAt; }
    Instant updatedAt() { return updatedAt; }
    long version() { return version; }

    static final class StaleVersion extends RuntimeException { }
    static final class StateConflict extends RuntimeException { }
}
