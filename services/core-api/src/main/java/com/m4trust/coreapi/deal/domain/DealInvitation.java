package com.m4trust.coreapi.deal.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Invitation aggregate with the closed Slice 4 workflow. */
public final class DealInvitation {

  private final UUID id;
  private final UUID tenantId;
  private final UUID dealId;
  private final String recipientEmail;
  private DealInvitationStatus status;
  private UUID acceptedLegalEntityId;
  private UUID acceptedLegalEntityTenantId;
  private final Instant createdAt;
  private Instant updatedAt;
  private long version;

  private DealInvitation(
      UUID id,
      UUID tenantId,
      UUID dealId,
      String recipientEmail,
      DealInvitationStatus status,
      UUID acceptedLegalEntityId,
      UUID acceptedLegalEntityTenantId,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    this.id = Objects.requireNonNull(id);
    this.tenantId = Objects.requireNonNull(tenantId);
    this.dealId = Objects.requireNonNull(dealId);
    this.recipientEmail = Objects.requireNonNull(recipientEmail);
    this.status = Objects.requireNonNull(status);
    this.acceptedLegalEntityId = acceptedLegalEntityId;
    this.acceptedLegalEntityTenantId = acceptedLegalEntityTenantId;
    this.createdAt = Objects.requireNonNull(createdAt);
    this.updatedAt = Objects.requireNonNull(updatedAt);
    this.version = version;
  }

  public static DealInvitation create(
      UUID id, UUID tenantId, UUID dealId, String recipientEmail, Instant now) {
    return new DealInvitation(
        id,
        tenantId,
        dealId,
        recipientEmail,
        DealInvitationStatus.PENDING,
        null,
        null,
        now,
        now,
        0);
  }

  public static DealInvitation rehydrate(DealInvitationRecord record) {
    return new DealInvitation(
        record.id(),
        record.tenantId(),
        record.dealId(),
        record.recipientEmail(),
        record.status(),
        record.acceptedLegalEntityId(),
        record.acceptedLegalEntityTenantId(),
        record.createdAt(),
        record.updatedAt(),
        record.version());
  }

  public void accept(
      UUID legalEntityId, UUID legalEntityTenantId, long expectedVersion, Instant now) {
    requirePendingVersion(expectedVersion);
    status = DealInvitationStatus.ACCEPTED;
    acceptedLegalEntityId = Objects.requireNonNull(legalEntityId);
    acceptedLegalEntityTenantId = Objects.requireNonNull(legalEntityTenantId);
    updatedAt = Objects.requireNonNull(now);
    version++;
  }

  public void reject(long expectedVersion, Instant now) {
    requirePendingVersion(expectedVersion);
    status = DealInvitationStatus.REJECTED;
    updatedAt = Objects.requireNonNull(now);
    version++;
  }

  public void revoke(long expectedVersion, Instant now) {
    requirePendingVersion(expectedVersion);
    status = DealInvitationStatus.REVOKED;
    updatedAt = Objects.requireNonNull(now);
    version++;
  }

  private void requirePendingVersion(long expectedVersion) {
    if (version != expectedVersion) {
      throw new DealInvitationStaleVersionException();
    }
    if (status != DealInvitationStatus.PENDING) {
      throw new DealInvitationStateConflictException();
    }
  }

  public DealInvitationRecord toRecord() {
    return new DealInvitationRecord(
        id,
        tenantId,
        dealId,
        recipientEmail,
        status,
        acceptedLegalEntityId,
        acceptedLegalEntityTenantId,
        createdAt,
        updatedAt,
        version,
        null);
  }

  public UUID id() {
    return id;
  }

  public UUID dealId() {
    return dealId;
  }

  public String recipientEmail() {
    return recipientEmail;
  }

  public DealInvitationStatus status() {
    return status;
  }

  public UUID acceptedLegalEntityId() {
    return acceptedLegalEntityId;
  }

  public UUID acceptedLegalEntityTenantId() {
    return acceptedLegalEntityTenantId;
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
}
