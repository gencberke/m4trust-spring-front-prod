package com.m4trust.coreapi.fulfillment;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Internal aggregate for one V1 primary milestone per fulfillment. */
final class Milestone {

    private final UUID id;
    private final UUID fulfillmentId;
    private final UUID dealId;
    private String title;
    private String description;
    private FulfillmentStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private long version;

    private Milestone(UUID id, UUID fulfillmentId, UUID dealId, String title,
            String description, FulfillmentStatus status, Instant createdAt,
            Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id);
        this.fulfillmentId = Objects.requireNonNull(fulfillmentId);
        this.dealId = Objects.requireNonNull(dealId);
        this.title = requireNonBlank(title, "title");
        this.description = description;
        this.status = Objects.requireNonNull(status);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.version = version;
        validate();
    }

    static Milestone create(UUID id, UUID fulfillmentId, UUID dealId, String title,
            String description, Instant createdAt) {
        return new Milestone(id, fulfillmentId, dealId, title, description,
                FulfillmentStatus.IN_PROGRESS, createdAt, createdAt, 0);
    }

    static Milestone rehydrate(MilestoneRecord record) {
        return new Milestone(record.id(), record.fulfillmentId(), record.dealId(),
                record.title(), record.description(), record.status(),
                record.createdAt(), record.updatedAt(), record.version());
    }

    void moveToEvidenceRequired(Instant changedAt) {
        requireTransition(FulfillmentStatus.EVIDENCE_REQUIRED);
        status = FulfillmentStatus.EVIDENCE_REQUIRED;
        updatedAt = Objects.requireNonNull(changedAt);
        version++;
    }

    void moveToReviewRequired(Instant changedAt) {
        requireTransition(FulfillmentStatus.REVIEW_REQUIRED);
        status = FulfillmentStatus.REVIEW_REQUIRED;
        updatedAt = Objects.requireNonNull(changedAt);
        version++;
    }

    void moveToCompleted(Instant changedAt) {
        requireTransition(FulfillmentStatus.COMPLETED);
        status = FulfillmentStatus.COMPLETED;
        updatedAt = Objects.requireNonNull(changedAt);
        version++;
    }

    void returnToEvidenceRequired(Instant changedAt) {
        if (status != FulfillmentStatus.REVIEW_REQUIRED) {
            throw new IllegalStateException("milestone must be in REVIEW_REQUIRED to return to EVIDENCE_REQUIRED");
        }
        status = FulfillmentStatus.EVIDENCE_REQUIRED;
        updatedAt = Objects.requireNonNull(changedAt);
        version++;
    }

    MilestoneRecord toRecord() {
        return new MilestoneRecord(id, fulfillmentId, dealId, title, description,
                status, createdAt, updatedAt, version);
    }

    UUID id() { return id; }
    UUID fulfillmentId() { return fulfillmentId; }
    UUID dealId() { return dealId; }
    String title() { return title; }
    String description() { return description; }
    FulfillmentStatus status() { return status; }
    Instant createdAt() { return createdAt; }
    Instant updatedAt() { return updatedAt; }
    long version() { return version; }

    private void requireTransition(FulfillmentStatus next) {
        boolean allowed = switch (status) {
            case IN_PROGRESS, EVIDENCE_REQUIRED -> next == FulfillmentStatus.EVIDENCE_REQUIRED
                    || next == FulfillmentStatus.REVIEW_REQUIRED;
            case REVIEW_REQUIRED -> next == FulfillmentStatus.COMPLETED;
            default -> false;
        };
        if (!allowed) {
            throw new IllegalStateException("milestone status transition from " + status + " to " + next + " is not allowed");
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

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public record MilestoneRecord(UUID id, UUID fulfillmentId, UUID dealId, String title,
            String description, FulfillmentStatus status, Instant createdAt,
            Instant updatedAt, long version) {
    }

    public record MilestoneRuleReferenceRecord(UUID milestoneId, String ruleReference, String category) {
    }
}
