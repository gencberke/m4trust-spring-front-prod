package com.m4trust.coreapi.fulfillment;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Internal aggregate for one V1 fulfillment record per Deal. */
final class Fulfillment {

    private final UUID id;
    private final UUID dealId;
    private final UUID tenantId;
    private final UUID sourcePackageId;
    private FulfillmentStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private long version;

    private Fulfillment(UUID id, UUID dealId, UUID tenantId, UUID sourcePackageId,
            FulfillmentStatus status, Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id);
        this.dealId = Objects.requireNonNull(dealId);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.sourcePackageId = Objects.requireNonNull(sourcePackageId);
        this.status = Objects.requireNonNull(status);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.version = version;
        validate();
    }

    static Fulfillment create(UUID id, UUID dealId, UUID tenantId, UUID sourcePackageId, Instant createdAt) {
        return new Fulfillment(id, dealId, tenantId, sourcePackageId,
                FulfillmentStatus.IN_PROGRESS, createdAt, createdAt, 0);
    }

    static Fulfillment rehydrate(FulfillmentRecord record) {
        return new Fulfillment(record.id(), record.dealId(), record.tenantId(),
                record.sourcePackageId(), record.status(), record.createdAt(),
                record.updatedAt(), record.version());
    }

    void moveToEvidenceRequired(Instant changedAt) {
        requireMutable();
        requireTransition(FulfillmentStatus.EVIDENCE_REQUIRED);
        status = FulfillmentStatus.EVIDENCE_REQUIRED;
        updatedAt = Objects.requireNonNull(changedAt);
        version++;
    }

    void moveToReviewRequired(Instant changedAt) {
        requireMutable();
        requireTransition(FulfillmentStatus.REVIEW_REQUIRED);
        status = FulfillmentStatus.REVIEW_REQUIRED;
        updatedAt = Objects.requireNonNull(changedAt);
        version++;
    }

    void moveToCompleted(Instant changedAt) {
        requireMutable();
        requireTransition(FulfillmentStatus.COMPLETED);
        status = FulfillmentStatus.COMPLETED;
        updatedAt = Objects.requireNonNull(changedAt);
        version++;
    }

    void returnToEvidenceRequired(Instant changedAt) {
        requireMutable();
        if (status != FulfillmentStatus.REVIEW_REQUIRED) {
            throw new IllegalStateException("fulfillment must be in REVIEW_REQUIRED to return to EVIDENCE_REQUIRED");
        }
        status = FulfillmentStatus.EVIDENCE_REQUIRED;
        updatedAt = Objects.requireNonNull(changedAt);
        version++;
    }

    FulfillmentRecord toRecord() {
        return new FulfillmentRecord(id, dealId, tenantId, sourcePackageId,
                status, createdAt, updatedAt, version);
    }

    UUID id() { return id; }
    UUID dealId() { return dealId; }
    UUID tenantId() { return tenantId; }
    UUID sourcePackageId() { return sourcePackageId; }
    FulfillmentStatus status() { return status; }
    Instant createdAt() { return createdAt; }
    Instant updatedAt() { return updatedAt; }
    long version() { return version; }

    private void requireMutable() {
        if (status == FulfillmentStatus.COMPLETED || status == FulfillmentStatus.CANCELLED) {
            throw new IllegalStateException("fulfillment is terminal");
        }
    }

    private void requireTransition(FulfillmentStatus next) {
        boolean allowed = switch (status) {
            case IN_PROGRESS, EVIDENCE_REQUIRED -> next == FulfillmentStatus.EVIDENCE_REQUIRED
                    || next == FulfillmentStatus.REVIEW_REQUIRED;
            case REVIEW_REQUIRED -> next == FulfillmentStatus.COMPLETED;
            default -> false;
        };
        if (!allowed) {
            throw new IllegalStateException("fulfillment status transition from " + status + " to " + next + " is not allowed");
        }
    }

    private void validate() {
        if (version < 0) {
            throw new IllegalArgumentException("version must be non-negative");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    public record FulfillmentRecord(UUID id, UUID dealId, UUID tenantId, UUID sourcePackageId,
            FulfillmentStatus status, Instant createdAt, Instant updatedAt, long version) {
    }
}
