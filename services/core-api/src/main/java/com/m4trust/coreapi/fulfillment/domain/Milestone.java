package com.m4trust.coreapi.fulfillment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Internal aggregate for one V1 primary milestone per fulfillment. */
public final class Milestone {

  private final UUID id;
  private final UUID fulfillmentId;
  private final UUID dealId;
  private String title;
  private String description;
  private FulfillmentStatus status;
  private Instant createdAt;
  private Instant updatedAt;
  private long version;

  private Milestone(
      UUID id,
      UUID fulfillmentId,
      UUID dealId,
      String title,
      String description,
      FulfillmentStatus status,
      Instant createdAt,
      Instant updatedAt,
      long version) {
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

  public static Milestone create(
      UUID id,
      UUID fulfillmentId,
      UUID dealId,
      String title,
      String description,
      Instant createdAt) {
    return new Milestone(
        id,
        fulfillmentId,
        dealId,
        title,
        description,
        FulfillmentStatus.IN_PROGRESS,
        createdAt,
        createdAt,
        0);
  }

  public static Milestone rehydrate(MilestoneRecord record) {
    return new Milestone(
        record.id(),
        record.fulfillmentId(),
        record.dealId(),
        record.title(),
        record.description(),
        record.status(),
        record.createdAt(),
        record.updatedAt(),
        record.version());
  }

  public void moveToEvidenceRequired(Instant changedAt) {
    requireTransition(FulfillmentStatus.EVIDENCE_REQUIRED);
    status = FulfillmentStatus.EVIDENCE_REQUIRED;
    updatedAt = Objects.requireNonNull(changedAt);
    version++;
  }

  public void moveToReviewRequired(Instant changedAt) {
    requireTransition(FulfillmentStatus.REVIEW_REQUIRED);
    status = FulfillmentStatus.REVIEW_REQUIRED;
    updatedAt = Objects.requireNonNull(changedAt);
    version++;
  }

  public void moveToCompleted(Instant changedAt) {
    requireTransition(FulfillmentStatus.COMPLETED);
    status = FulfillmentStatus.COMPLETED;
    updatedAt = Objects.requireNonNull(changedAt);
    version++;
  }

  public void returnToEvidenceRequired(Instant changedAt) {
    if (status != FulfillmentStatus.REVIEW_REQUIRED) {
      throw new IllegalStateException(
          "milestone must be in REVIEW_REQUIRED to return to EVIDENCE_REQUIRED");
    }
    status = FulfillmentStatus.EVIDENCE_REQUIRED;
    updatedAt = Objects.requireNonNull(changedAt);
    version++;
  }

  public MilestoneRecord toRecord() {
    return new MilestoneRecord(
        id, fulfillmentId, dealId, title, description, status, createdAt, updatedAt, version);
  }

  public UUID id() {
    return id;
  }

  public UUID fulfillmentId() {
    return fulfillmentId;
  }

  public UUID dealId() {
    return dealId;
  }

  public String title() {
    return title;
  }

  public String description() {
    return description;
  }

  public FulfillmentStatus status() {
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

  private void requireTransition(FulfillmentStatus next) {
    boolean allowed =
        switch (status) {
          case IN_PROGRESS ->
              next == FulfillmentStatus.EVIDENCE_REQUIRED
                  || next == FulfillmentStatus.REVIEW_REQUIRED
                  || next == FulfillmentStatus.COMPLETED;
          case EVIDENCE_REQUIRED ->
              next == FulfillmentStatus.EVIDENCE_REQUIRED
                  || next == FulfillmentStatus.REVIEW_REQUIRED;
          case REVIEW_REQUIRED -> next == FulfillmentStatus.COMPLETED;
          default -> false;
        };
    if (!allowed) {
      throw new IllegalStateException(
          "milestone status transition from " + status + " to " + next + " is not allowed");
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

  public record MilestoneRecord(
      UUID id,
      UUID fulfillmentId,
      UUID dealId,
      String title,
      String description,
      FulfillmentStatus status,
      Instant createdAt,
      Instant updatedAt,
      long version) {}

  public record MilestoneRuleReferenceRecord(
      UUID milestoneId, String ruleReference, String category) {}
}
