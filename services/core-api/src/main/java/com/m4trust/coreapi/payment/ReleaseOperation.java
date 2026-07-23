package com.m4trust.coreapi.payment;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Lifetime-fixed release operation aggregate (ADR-014 §2.3). */
final class ReleaseOperation {

    private final UUID id;
    private final UUID settlementId;
    private final UUID providerKey;
    private ReleaseOperationStatus status;
    private String providerReference;
    private Instant createdAt;
    private Instant updatedAt;
    private long version;

    private ReleaseOperation(UUID id, UUID settlementId, UUID providerKey, ReleaseOperationStatus status,
            String providerReference, Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id);
        this.settlementId = Objects.requireNonNull(settlementId);
        this.providerKey = Objects.requireNonNull(providerKey);
        this.status = Objects.requireNonNull(status);
        this.providerReference = providerReference;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        if (version < 0) {
            throw new IllegalArgumentException("version must be non-negative");
        }
        this.version = version;
    }

    static ReleaseOperation create(UUID id, UUID settlementId, UUID providerKey, Instant now) {
        return new ReleaseOperation(id, settlementId, providerKey, ReleaseOperationStatus.QUEUED,
                null, now, now, 0);
    }

    static ReleaseOperation rehydrate(SettlementRepository.ReleaseOperationRecord record) {
        return new ReleaseOperation(record.id(), record.settlementId(), record.providerKey(), record.status(),
                record.providerReference(), record.createdAt(), record.updatedAt(), record.version());
    }

    void markProcessing(Instant changedAt) {
        if (status == ReleaseOperationStatus.QUEUED) {
            status = ReleaseOperationStatus.PROCESSING;
            updatedAt = Objects.requireNonNull(changedAt);
            version++;
        }
    }

    void markReconciliationRequired(Instant changedAt) {
        if (!status.terminal()) {
            status = ReleaseOperationStatus.RECONCILIATION_REQUIRED;
            updatedAt = Objects.requireNonNull(changedAt);
            version++;
        }
    }

    void applySimulatedSettled(String reference, Instant changedAt) {
        if (status == ReleaseOperationStatus.SIMULATED_SETTLED) {
            return;
        }
        status = ReleaseOperationStatus.SIMULATED_SETTLED;
        providerReference = reference;
        updatedAt = Objects.requireNonNull(changedAt);
        version++;
    }

    void applySimulatedDeclined(String reference, Instant changedAt) {
        if (status == ReleaseOperationStatus.SIMULATED_DECLINED) {
            return;
        }
        status = ReleaseOperationStatus.SIMULATED_DECLINED;
        providerReference = reference;
        updatedAt = Objects.requireNonNull(changedAt);
        version++;
    }

    void requireVersion(long expectedVersion) {
        if (version != expectedVersion) {
            throw new StaleVersion();
        }
    }

    SettlementRepository.ReleaseOperationRecord toRecord() {
        return new SettlementRepository.ReleaseOperationRecord(id, settlementId, providerKey, status,
                providerReference, createdAt, updatedAt, version);
    }

    UUID id() { return id; }
    UUID settlementId() { return settlementId; }
    UUID providerKey() { return providerKey; }
    ReleaseOperationStatus status() { return status; }
    String providerReference() { return providerReference; }
    Instant createdAt() { return createdAt; }
    Instant updatedAt() { return updatedAt; }
    long version() { return version; }
    boolean terminal() { return status.terminal(); }

    static final class StaleVersion extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
