package com.m4trust.coreapi.document.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Internal aggregate for retained contractual document metadata. */
public final class Document {

  private final UUID id;
  private final UUID dealId;
  private final String fileName;
  private final DocumentMediaType mediaType;
  private final String objectKey;
  private final long declaredSizeBytes;
  private final String declaredSha256;
  private final Instant uploadExpiresAt;
  private final Instant createdAt;
  private DocumentStatus status;
  private Long verifiedSizeBytes;
  private String verifiedSha256;
  private String objectVersion;
  private Instant availableAt;
  private Instant supersededAt;
  private Instant updatedAt;
  private long version;

  private Document(
      UUID id,
      UUID dealId,
      String fileName,
      DocumentMediaType mediaType,
      String objectKey,
      long declaredSizeBytes,
      String declaredSha256,
      Instant uploadExpiresAt,
      DocumentStatus status,
      Long verifiedSizeBytes,
      String verifiedSha256,
      String objectVersion,
      Instant createdAt,
      Instant availableAt,
      Instant supersededAt,
      Instant updatedAt,
      long version) {
    this.id = Objects.requireNonNull(id);
    this.dealId = Objects.requireNonNull(dealId);
    this.fileName = Objects.requireNonNull(fileName);
    this.mediaType = Objects.requireNonNull(mediaType);
    this.objectKey = Objects.requireNonNull(objectKey);
    this.declaredSizeBytes = declaredSizeBytes;
    this.declaredSha256 = Objects.requireNonNull(declaredSha256);
    this.uploadExpiresAt = Objects.requireNonNull(uploadExpiresAt);
    this.status = Objects.requireNonNull(status);
    this.verifiedSizeBytes = verifiedSizeBytes;
    this.verifiedSha256 = verifiedSha256;
    this.objectVersion = objectVersion;
    this.createdAt = Objects.requireNonNull(createdAt);
    this.availableAt = availableAt;
    this.supersededAt = supersededAt;
    this.updatedAt = Objects.requireNonNull(updatedAt);
    this.version = version;
    validate();
  }

  public static Document createPending(
      UUID id,
      UUID dealId,
      String fileName,
      DocumentMediaType mediaType,
      String objectKey,
      long declaredSizeBytes,
      String declaredSha256,
      Instant uploadExpiresAt,
      Instant createdAt) {
    return new Document(
        id,
        dealId,
        fileName,
        mediaType,
        objectKey,
        declaredSizeBytes,
        declaredSha256,
        uploadExpiresAt,
        DocumentStatus.PENDING_UPLOAD,
        null,
        null,
        null,
        createdAt,
        null,
        null,
        createdAt,
        0);
  }

  public static Document rehydrate(DocumentRecord record) {
    return new Document(
        record.id(),
        record.dealId(),
        record.fileName(),
        DocumentMediaType.fromValue(record.mediaType()),
        record.objectKey(),
        record.declaredSizeBytes(),
        record.declaredSha256(),
        record.uploadExpiresAt(),
        record.status(),
        record.verifiedSizeBytes(),
        record.verifiedSha256(),
        record.objectVersion(),
        record.createdAt(),
        record.availableAt(),
        record.supersededAt(),
        record.updatedAt(),
        record.version());
  }

  public void markAvailable(
      long nextVerifiedSizeBytes,
      String nextVerifiedSha256,
      String nextObjectVersion,
      Instant changedAt) {
    if (status != DocumentStatus.PENDING_UPLOAD) {
      throw new IllegalStateException("document is no longer pending upload");
    }
    Instant nextChangedAt = Objects.requireNonNull(changedAt);
    if (!nextChangedAt.isBefore(uploadExpiresAt)) {
      throw new IllegalStateException("document upload has expired");
    }
    if (nextVerifiedSizeBytes != declaredSizeBytes || !declaredSha256.equals(nextVerifiedSha256)) {
      throw new IllegalArgumentException("verified document metadata does not match declaration");
    }
    status = DocumentStatus.AVAILABLE;
    verifiedSizeBytes = nextVerifiedSizeBytes;
    verifiedSha256 = Objects.requireNonNull(nextVerifiedSha256);
    objectVersion = requireNonBlank(nextObjectVersion, "object version");
    availableAt = nextChangedAt;
    updatedAt = nextChangedAt;
    version++;
  }

  public void supersede(Instant changedAt) {
    if (status != DocumentStatus.AVAILABLE) {
      throw new IllegalStateException("only available documents can be superseded");
    }
    supersededAt = Objects.requireNonNull(changedAt);
    updatedAt = supersededAt;
    status = DocumentStatus.SUPERSEDED;
    version++;
  }

  public DocumentRecord toRecord() {
    return new DocumentRecord(
        id,
        dealId,
        fileName,
        mediaType.value(),
        status,
        objectKey,
        declaredSizeBytes,
        declaredSha256,
        uploadExpiresAt,
        verifiedSizeBytes,
        verifiedSha256,
        objectVersion,
        createdAt,
        availableAt,
        supersededAt,
        updatedAt,
        version);
  }

  public DocumentStatus status() {
    return status;
  }

  public UUID id() {
    return id;
  }

  public UUID dealId() {
    return dealId;
  }

  public String fileName() {
    return fileName;
  }

  public DocumentMediaType mediaType() {
    return mediaType;
  }

  public long declaredSizeBytes() {
    return declaredSizeBytes;
  }

  public String declaredSha256() {
    return declaredSha256;
  }

  public Instant uploadExpiresAt() {
    return uploadExpiresAt;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Long verifiedSizeBytes() {
    return verifiedSizeBytes;
  }

  public String verifiedSha256() {
    return verifiedSha256;
  }

  public String objectVersion() {
    return objectVersion;
  }

  public String objectKey() {
    return objectKey;
  }

  public Instant availableAt() {
    return availableAt;
  }

  public Instant supersededAt() {
    return supersededAt;
  }

  private void validate() {
    if (declaredSizeBytes <= 0
        || version < 0
        || !uploadExpiresAt.isAfter(createdAt)
        || updatedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("invalid document lifecycle data");
    }
    if (!declaredSha256.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("declared SHA-256 must be lowercase hexadecimal");
    }
    boolean finalized = status != DocumentStatus.PENDING_UPLOAD;
    if (finalized
            != (verifiedSizeBytes != null
                && verifiedSha256 != null
                && objectVersion != null
                && availableAt != null)
        || (status == DocumentStatus.SUPERSEDED) != (supersededAt != null)) {
      throw new IllegalArgumentException("invalid document state data");
    }
  }

  private static String requireNonBlank(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }
}
