package com.m4trust.coreapi.fulfillment;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Internal aggregate for one immutable evidence submission record. */
final class EvidenceSubmission {

    private final UUID id;
    private final UUID dealId;
    private final UUID milestoneId;
    private final UUID fulfillmentId;
    private final EvidenceType evidenceType;
    private final EvidenceMediaType mediaType;
    private final String fileName;
    private EvidenceSubmissionStatus status;
    private final String objectKey;
    private String objectVersion;
    private final long clientSizeBytes;
    private final String clientSha256;
    private Long verifiedSizeBytes;
    private String verifiedSha256;
    private final Instant uploadExpiresAt;
    private final Instant createdAt;
    private Instant submittedAt;
    private Instant acceptedAt;
    private Instant rejectedAt;
    private String rejectionReason;
    private Instant cancelledAt;
    private long version;

    private EvidenceSubmission(UUID id, UUID dealId, UUID milestoneId, UUID fulfillmentId,
            EvidenceType evidenceType, EvidenceMediaType mediaType, String fileName,
            EvidenceSubmissionStatus status, String objectKey, String objectVersion,
            long clientSizeBytes, String clientSha256, Long verifiedSizeBytes,
            String verifiedSha256, Instant uploadExpiresAt, Instant createdAt,
            Instant submittedAt, Instant acceptedAt, Instant rejectedAt,
            String rejectionReason, Instant cancelledAt, long version) {
        this.id = Objects.requireNonNull(id);
        this.dealId = Objects.requireNonNull(dealId);
        this.milestoneId = Objects.requireNonNull(milestoneId);
        this.fulfillmentId = Objects.requireNonNull(fulfillmentId);
        this.evidenceType = Objects.requireNonNull(evidenceType);
        this.mediaType = Objects.requireNonNull(mediaType);
        this.fileName = requireNonBlank(fileName, "fileName");
        this.status = Objects.requireNonNull(status);
        this.objectKey = requireNonBlank(objectKey, "objectKey");
        this.objectVersion = objectVersion;
        this.clientSizeBytes = clientSizeBytes;
        this.clientSha256 = requireSha256(clientSha256);
        this.verifiedSizeBytes = verifiedSizeBytes;
        this.verifiedSha256 = verifiedSha256;
        this.uploadExpiresAt = Objects.requireNonNull(uploadExpiresAt);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.submittedAt = submittedAt;
        this.acceptedAt = acceptedAt;
        this.rejectedAt = rejectedAt;
        this.rejectionReason = rejectionReason;
        this.cancelledAt = cancelledAt;
        this.version = version;
        validate();
    }

    static EvidenceSubmission createPending(UUID id, UUID dealId, UUID milestoneId, UUID fulfillmentId,
            EvidenceType evidenceType, EvidenceMediaType mediaType, String fileName,
            String objectKey, long clientSizeBytes, String clientSha256,
            Instant uploadExpiresAt, Instant createdAt) {
        return new EvidenceSubmission(id, dealId, milestoneId, fulfillmentId,
                evidenceType, mediaType, fileName, EvidenceSubmissionStatus.PENDING_UPLOAD,
                objectKey, null, clientSizeBytes, clientSha256, null, null,
                uploadExpiresAt, createdAt, null, null, null, null, null, 0);
    }

    static EvidenceSubmission rehydrate(EvidenceSubmissionRecord record) {
        return new EvidenceSubmission(record.id(), record.dealId(), record.milestoneId(),
                record.fulfillmentId(), record.evidenceType(), record.mediaType(),
                record.fileName(), record.status(), record.objectKey(), record.objectVersion(),
                record.clientSizeBytes(), record.clientSha256(), record.verifiedSizeBytes(),
                record.verifiedSha256(), record.uploadExpiresAt(), record.createdAt(),
                record.submittedAt(), record.acceptedAt(), record.rejectedAt(),
                record.rejectionReason(), record.cancelledAt(), record.version());
    }

    void markCancelled(Instant changedAt) {
        if (status != EvidenceSubmissionStatus.PENDING_UPLOAD) {
            throw new IllegalStateException("only PENDING_UPLOAD evidence can be cancelled");
        }
        if (cancelledAt != null) {
            throw new IllegalStateException("evidence upload is already cancelled");
        }
        Instant nextChangedAt = Objects.requireNonNull(changedAt);
        if (!nextChangedAt.isBefore(uploadExpiresAt)) {
            throw new IllegalStateException("evidence upload has expired");
        }
        cancelledAt = nextChangedAt;
        version++;
    }

    void markSubmitted(long verifiedSizeBytes, String verifiedSha256, String objectVersion, Instant changedAt) {
        if (status != EvidenceSubmissionStatus.PENDING_UPLOAD) {
            throw new IllegalStateException("evidence submission is no longer pending upload");
        }
        if (cancelledAt != null) {
            throw new IllegalStateException("cancelled evidence upload cannot be finalized");
        }
        Instant nextChangedAt = Objects.requireNonNull(changedAt);
        if (!nextChangedAt.isBefore(uploadExpiresAt)) {
            throw new IllegalStateException("evidence upload has expired");
        }
        if (verifiedSizeBytes != clientSizeBytes || !clientSha256.equals(verifiedSha256)) {
            throw new IllegalArgumentException("verified evidence metadata does not match declaration");
        }
        status = EvidenceSubmissionStatus.SUBMITTED;
        this.verifiedSizeBytes = verifiedSizeBytes;
        this.verifiedSha256 = requireSha256(verifiedSha256);
        this.objectVersion = requireNonBlank(objectVersion, "object version");
        submittedAt = nextChangedAt;
        version++;
    }

    void markAccepted(Instant changedAt) {
        if (status != EvidenceSubmissionStatus.SUBMITTED) {
            throw new IllegalStateException("only SUBMITTED evidence can be accepted");
        }
        status = EvidenceSubmissionStatus.ACCEPTED;
        acceptedAt = Objects.requireNonNull(changedAt);
        version++;
    }

    void markRejected(String reason, Instant changedAt) {
        if (status != EvidenceSubmissionStatus.SUBMITTED) {
            throw new IllegalStateException("only SUBMITTED evidence can be rejected");
        }
        status = EvidenceSubmissionStatus.REJECTED;
        rejectionReason = requireNonBlank(reason, "rejection reason");
        rejectedAt = Objects.requireNonNull(changedAt);
        version++;
    }

    EvidenceSubmissionRecord toRecord() {
        return new EvidenceSubmissionRecord(id, dealId, milestoneId, fulfillmentId,
                evidenceType, mediaType, fileName, status, objectKey, objectVersion,
                clientSizeBytes, clientSha256, verifiedSizeBytes, verifiedSha256,
                uploadExpiresAt, createdAt, submittedAt, acceptedAt, rejectedAt,
                rejectionReason, cancelledAt, version);
    }

    UUID id() { return id; }
    UUID dealId() { return dealId; }
    UUID milestoneId() { return milestoneId; }
    UUID fulfillmentId() { return fulfillmentId; }
    EvidenceType evidenceType() { return evidenceType; }
    EvidenceMediaType mediaType() { return mediaType; }
    String fileName() { return fileName; }
    EvidenceSubmissionStatus status() { return status; }
    String objectKey() { return objectKey; }
    String objectVersion() { return objectVersion; }
    long clientSizeBytes() { return clientSizeBytes; }
    String clientSha256() { return clientSha256; }
    Long verifiedSizeBytes() { return verifiedSizeBytes; }
    String verifiedSha256() { return verifiedSha256; }
    Instant uploadExpiresAt() { return uploadExpiresAt; }
    Instant createdAt() { return createdAt; }
    Instant submittedAt() { return submittedAt; }
    Instant acceptedAt() { return acceptedAt; }
    Instant rejectedAt() { return rejectedAt; }
    String rejectionReason() { return rejectionReason; }
    Instant cancelledAt() { return cancelledAt; }
    long version() { return version; }

    private void validate() {
        if (clientSizeBytes <= 0 || version < 0
                || !uploadExpiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("invalid evidence submission lifecycle data");
        }
        boolean finalized = status != EvidenceSubmissionStatus.PENDING_UPLOAD;
        if (finalized != (verifiedSizeBytes != null && verifiedSha256 != null
                && objectVersion != null && submittedAt != null)
                || (status == EvidenceSubmissionStatus.ACCEPTED) != (acceptedAt != null)
                || (status == EvidenceSubmissionStatus.REJECTED) != (rejectedAt != null && rejectionReason != null)
                || (cancelledAt != null && status != EvidenceSubmissionStatus.PENDING_UPLOAD)
                || (cancelledAt != null && !cancelledAt.isBefore(uploadExpiresAt))) {
            throw new IllegalArgumentException("invalid evidence submission state data");
        }
    }

    private static String requireSha256(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("SHA-256 must be lowercase hexadecimal");
        }
        return value;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public record EvidenceSubmissionRecord(UUID id, UUID dealId, UUID milestoneId, UUID fulfillmentId,
            EvidenceType evidenceType, EvidenceMediaType mediaType, String fileName,
            EvidenceSubmissionStatus status, String objectKey, String objectVersion,
            long clientSizeBytes, String clientSha256, Long verifiedSizeBytes,
            String verifiedSha256, Instant uploadExpiresAt, Instant createdAt,
            Instant submittedAt, Instant acceptedAt, Instant rejectedAt,
            String rejectionReason, Instant cancelledAt, long version) {
    }
}
