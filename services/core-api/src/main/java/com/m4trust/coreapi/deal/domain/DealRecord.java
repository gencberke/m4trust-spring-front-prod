package com.m4trust.coreapi.deal.domain;

import java.time.Instant;
import java.util.UUID;

/** Persistence-neutral snapshot used to rehydrate the Deal aggregate. */
public record DealRecord(
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

  public DealRecord(
      UUID id,
      UUID tenantId,
      String reference,
      String title,
      String description,
      DealStatus status,
      UUID buyerLegalEntityId,
      UUID sellerLegalEntityId,
      UUID currentDocumentId,
      UUID initiatorLegalEntityId,
      UUID createdBy,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    this(
        id,
        tenantId,
        reference,
        title,
        description,
        status,
        buyerLegalEntityId,
        sellerLegalEntityId,
        currentDocumentId,
        null,
        null,
        initiatorLegalEntityId,
        createdBy,
        createdAt,
        updatedAt,
        version);
  }

  public DealRecord(
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
      UUID initiatorLegalEntityId,
      UUID createdBy,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    this(
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
        null,
        initiatorLegalEntityId,
        createdBy,
        createdAt,
        updatedAt,
        version);
  }
}
