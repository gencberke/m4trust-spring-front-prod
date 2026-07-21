package com.m4trust.coreapi.casework;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Internal aggregate for one dispute case per Deal history row. */
final class DisputeCase {

    private final UUID id;
    private final UUID dealId;
    private final UUID tenantId;
    private final UUID fulfillmentId;
    private final UUID milestoneId;
    private final UUID ratificationPackageId;
    private final String fulfillmentStatusAtOpen;
    private final long fulfillmentVersionAtOpen;
    private final long milestoneVersionAtOpen;
    private final DisputeReasonCode reasonCode;
    private final String subject;
    private final String statement;
    private DisputeStatus status;
    private final UUID openingTenantId;
    private final UUID openingLegalEntityId;
    private final UUID openingUserId;
    private final String openingLegalName;
    private final Instant openedAt;
    private Instant acknowledgedAt;
    private Instant withdrawnAt;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    private DisputeCase(DisputeCaseRecord record) {
        this.id = Objects.requireNonNull(record.id());
        this.dealId = Objects.requireNonNull(record.dealId());
        this.tenantId = Objects.requireNonNull(record.tenantId());
        this.fulfillmentId = Objects.requireNonNull(record.fulfillmentId());
        this.milestoneId = Objects.requireNonNull(record.milestoneId());
        this.ratificationPackageId = Objects.requireNonNull(record.ratificationPackageId());
        this.fulfillmentStatusAtOpen = requireNonBlank(record.fulfillmentStatusAtOpen(), "fulfillmentStatusAtOpen");
        this.fulfillmentVersionAtOpen = record.fulfillmentVersionAtOpen();
        this.milestoneVersionAtOpen = record.milestoneVersionAtOpen();
        this.reasonCode = Objects.requireNonNull(record.reasonCode());
        this.subject = requireNonBlank(record.subject(), "subject");
        this.statement = requireNonBlank(record.statement(), "statement");
        this.status = Objects.requireNonNull(record.status());
        this.openingTenantId = Objects.requireNonNull(record.openingTenantId());
        this.openingLegalEntityId = Objects.requireNonNull(record.openingLegalEntityId());
        this.openingUserId = Objects.requireNonNull(record.openingUserId());
        this.openingLegalName = requireNonBlank(record.openingLegalName(), "openingLegalName");
        this.openedAt = Objects.requireNonNull(record.openedAt());
        this.acknowledgedAt = record.acknowledgedAt();
        this.withdrawnAt = record.withdrawnAt();
        this.version = record.version();
        this.createdAt = Objects.requireNonNull(record.createdAt());
        this.updatedAt = Objects.requireNonNull(record.updatedAt());
        validate();
    }

    static DisputeCase rehydrate(DisputeCaseRecord record) {
        return new DisputeCase(record);
    }

    static DisputeCase open(
            UUID id,
            UUID dealId,
            UUID tenantId,
            UUID fulfillmentId,
            UUID milestoneId,
            UUID ratificationPackageId,
            String fulfillmentStatusAtOpen,
            long fulfillmentVersionAtOpen,
            long milestoneVersionAtOpen,
            DisputeReasonCode reasonCode,
            String subject,
            String statement,
            UUID openingTenantId,
            UUID openingLegalEntityId,
            UUID openingUserId,
            String openingLegalName,
            Instant openedAt) {
        return new DisputeCase(new DisputeCaseRecord(
                id,
                dealId,
                tenantId,
                fulfillmentId,
                milestoneId,
                ratificationPackageId,
                fulfillmentStatusAtOpen,
                fulfillmentVersionAtOpen,
                milestoneVersionAtOpen,
                reasonCode,
                subject,
                statement,
                DisputeStatus.OPEN,
                openingTenantId,
                openingLegalEntityId,
                openingUserId,
                openingLegalName,
                openedAt,
                null,
                null,
                0L,
                openedAt,
                openedAt));
    }

    DisputeCaseRecord toRecord() {
        return new DisputeCaseRecord(id, dealId, tenantId, fulfillmentId, milestoneId, ratificationPackageId,
                fulfillmentStatusAtOpen, fulfillmentVersionAtOpen, milestoneVersionAtOpen, reasonCode, subject,
                statement, status, openingTenantId, openingLegalEntityId, openingUserId, openingLegalName, openedAt,
                acknowledgedAt, withdrawnAt, version, createdAt, updatedAt);
    }

    void acknowledge(Instant acknowledgedAt, Instant updatedAt) {
        requireStatus(DisputeStatus.OPEN);
        this.status = DisputeStatus.UNDER_REVIEW;
        this.acknowledgedAt = Objects.requireNonNull(acknowledgedAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.version++;
    }

    void withdraw(Instant withdrawnAt, Instant updatedAt) {
        if (status != DisputeStatus.OPEN && status != DisputeStatus.UNDER_REVIEW) {
            throw new IllegalStateException("only OPEN or UNDER_REVIEW disputes may be withdrawn");
        }
        this.status = DisputeStatus.WITHDRAWN;
        this.withdrawnAt = Objects.requireNonNull(withdrawnAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.version++;
    }

    void recordComment(Instant updatedAt) {
        if (!isActive()) {
            throw new IllegalStateException("comments require an active case");
        }
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.version++;
    }

    boolean isActive() {
        return status == DisputeStatus.OPEN || status == DisputeStatus.UNDER_REVIEW;
    }

    private void requireStatus(DisputeStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("expected status " + expected + " but was " + status);
        }
    }

    private void validate() {
        if (fulfillmentVersionAtOpen < 0 || milestoneVersionAtOpen < 0 || version < 0) {
            throw new IllegalArgumentException("versions must be non-negative");
        }
        if (subject.length() > 200 || statement.length() > 4000 || openingLegalName.length() > 200) {
            throw new IllegalArgumentException("text bounds exceeded");
        }
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    record DisputeCaseRecord(
            UUID id,
            UUID dealId,
            UUID tenantId,
            UUID fulfillmentId,
            UUID milestoneId,
            UUID ratificationPackageId,
            String fulfillmentStatusAtOpen,
            long fulfillmentVersionAtOpen,
            long milestoneVersionAtOpen,
            DisputeReasonCode reasonCode,
            String subject,
            String statement,
            DisputeStatus status,
            UUID openingTenantId,
            UUID openingLegalEntityId,
            UUID openingUserId,
            String openingLegalName,
            Instant openedAt,
            Instant acknowledgedAt,
            Instant withdrawnAt,
            long version,
            Instant createdAt,
            Instant updatedAt) {
    }
}
