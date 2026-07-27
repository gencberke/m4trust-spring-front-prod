package com.m4trust.coreapi.deal.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Internal Deal aggregate. Public API DTOs and JDBC records stay separate from this behavior-owning
 * domain object.
 */
public final class Deal {

  private final UUID id;
  private final UUID tenantId;
  private final String reference;
  private String title;
  private String description;
  private DealStatus status;
  private UUID buyerLegalEntityId;
  private UUID sellerLegalEntityId;
  private UUID currentDocumentId;
  private UUID currentRuleSetVersionId;
  private UUID currentRatificationPackageId;
  private final UUID initiatorLegalEntityId;
  private final UUID createdBy;
  private final Instant createdAt;
  private Instant updatedAt;
  private long version;

  private Deal(
      UUID id,
      UUID tenantId,
      String reference,
      String title,
      String description,
      DealStatus status,
      UUID buyerLegalEntityId,
      UUID sellerLegalEntityId,
      UUID currentDocumentId,
      UUID currentRuleSetVersionId,
      UUID currentRatificationPackageId,
      UUID initiatorLegalEntityId,
      UUID createdBy,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    this.id = Objects.requireNonNull(id);
    this.tenantId = Objects.requireNonNull(tenantId);
    this.reference = Objects.requireNonNull(reference);
    this.title = Objects.requireNonNull(title);
    this.description = description;
    this.status = Objects.requireNonNull(status);
    validatePartyAssignments(buyerLegalEntityId, sellerLegalEntityId);
    this.buyerLegalEntityId = buyerLegalEntityId;
    this.sellerLegalEntityId = sellerLegalEntityId;
    this.currentDocumentId = currentDocumentId;
    this.currentRuleSetVersionId = currentRuleSetVersionId;
    this.currentRatificationPackageId = currentRatificationPackageId;
    this.initiatorLegalEntityId = Objects.requireNonNull(initiatorLegalEntityId);
    this.createdBy = Objects.requireNonNull(createdBy);
    this.createdAt = Objects.requireNonNull(createdAt);
    this.updatedAt = Objects.requireNonNull(updatedAt);
    if (version < 0) {
      throw new IllegalArgumentException("version must not be negative");
    }
    this.version = version;
  }

  public static Deal create(
      UUID id,
      UUID tenantId,
      String reference,
      String title,
      String description,
      UUID initiatorLegalEntityId,
      UUID createdBy,
      Instant createdAt) {
    return new Deal(
        id,
        tenantId,
        reference,
        title,
        description,
        DealStatus.DRAFT,
        null,
        null,
        null,
        null,
        null,
        initiatorLegalEntityId,
        createdBy,
        createdAt,
        createdAt,
        0);
  }

  public static Deal rehydrate(DealRecord record) {
    return new Deal(
        record.id(),
        record.tenantId(),
        record.reference(),
        record.title(),
        record.description(),
        record.status(),
        record.buyerLegalEntityId(),
        record.sellerLegalEntityId(),
        record.currentDocumentId(),
        record.currentRuleSetVersionId(),
        record.currentRatificationPackageId(),
        record.initiatorLegalEntityId(),
        record.createdBy(),
        record.createdAt(),
        record.updatedAt(),
        record.version());
  }

  public void updateBasicFields(
      String nextTitle, String nextDescription, long expectedVersion, Instant changedAt) {
    status.requireBasicFieldEditingAllowed();
    if (version != expectedVersion) {
      throw new DealStaleVersionException();
    }
    title = Objects.requireNonNull(nextTitle);
    description = nextDescription;
    updatedAt = Objects.requireNonNull(changedAt);
    version++;
  }

  public void cancel(Instant changedAt) {
    status = status.cancel();
    updatedAt = Objects.requireNonNull(changedAt);
    version++;
  }

  public void assignParties(
      UUID nextBuyerLegalEntityId,
      UUID nextSellerLegalEntityId,
      long expectedVersion,
      Instant changedAt) {
    status.requirePartyManagementAllowed();
    if (version != expectedVersion) {
      throw new DealStaleVersionException();
    }
    validatePartyAssignments(nextBuyerLegalEntityId, nextSellerLegalEntityId);
    Instant nextUpdatedAt = Objects.requireNonNull(changedAt);
    buyerLegalEntityId = nextBuyerLegalEntityId;
    sellerLegalEntityId = nextSellerLegalEntityId;
    updatedAt = nextUpdatedAt;
    version++;
  }

  public DealRecord toRecord() {
    return new DealRecord(
        id,
        tenantId,
        reference,
        title,
        description,
        status,
        buyerLegalEntityId,
        sellerLegalEntityId,
        currentDocumentId,
        currentRuleSetVersionId,
        currentRatificationPackageId,
        initiatorLegalEntityId,
        createdBy,
        createdAt,
        updatedAt,
        version);
  }

  public UUID id() {
    return id;
  }

  public String reference() {
    return reference;
  }

  public String title() {
    return title;
  }

  public String description() {
    return description;
  }

  public DealStatus status() {
    return status;
  }

  public UUID buyerLegalEntityId() {
    return buyerLegalEntityId;
  }

  public UUID sellerLegalEntityId() {
    return sellerLegalEntityId;
  }

  public UUID currentDocumentId() {
    return currentDocumentId;
  }

  public void activateCurrentRatificationPackage(UUID packageId, Instant changedAt) {
    if (status != DealStatus.DRAFT || !Objects.equals(currentRatificationPackageId, packageId)) {
      throw new DealStateConflictException("Ratification package is not current on a DRAFT Deal");
    }
    status = status.activate();
    updatedAt = Objects.requireNonNull(changedAt);
    version++;
  }

  public UUID currentRuleSetVersionId() {
    return currentRuleSetVersionId;
  }

  public UUID currentRatificationPackageId() {
    return currentRatificationPackageId;
  }

  public boolean isInitiatedBy(UUID legalEntityId) {
    return initiatorLegalEntityId.equals(legalEntityId);
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

  private static void validatePartyAssignments(UUID buyerLegalEntityId, UUID sellerLegalEntityId) {
    if (buyerLegalEntityId != null && buyerLegalEntityId.equals(sellerLegalEntityId)) {
      throw new IllegalArgumentException("buyer and seller must be different legal entities");
    }
  }
}
