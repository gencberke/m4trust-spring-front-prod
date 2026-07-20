package com.m4trust.coreapi.fulfillment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record StartFulfillmentRequest(
        @Min(value = 0, message = "expectedVersion must be non-negative") long expectedVersion) {
}

record CreateEvidenceUploadIntentRequest(
        @NotBlank(message = "evidenceType is required") @Size(max = 32, message = "evidenceType is too long") String evidenceType,
        @NotBlank(message = "mediaType is required") @Size(max = 128, message = "mediaType is too long") String mediaType,
        @NotBlank(message = "fileName is required") @Size(min = 1, max = 255, message = "fileName must be 1-255 characters") String fileName,
        @Min(value = 1, message = "sizeBytes must be positive") long sizeBytes,
        @NotBlank(message = "sha256 is required") @Pattern(regexp = "^[a-f0-9]{64}$", message = "sha256 must be 64 lowercase hex characters") String sha256) {
}

record FinalizeEvidenceUploadRequest(
        @Min(value = 1, message = "sizeBytes must be positive") long sizeBytes,
        @NotBlank(message = "sha256 is required") @Pattern(regexp = "^[a-f0-9]{64}$", message = "sha256 must be 64 lowercase hex characters") String sha256) {
}

record AcceptEvidenceRequest(
        @Min(value = 0, message = "expectedVersion must be non-negative") long expectedVersion,
        @Min(value = 0, message = "expectedEvidenceVersion must be non-negative") long expectedEvidenceVersion) {
}

record RejectEvidenceRequest(
        @Min(value = 0, message = "expectedVersion must be non-negative") long expectedVersion,
        @Min(value = 0, message = "expectedEvidenceVersion must be non-negative") long expectedEvidenceVersion,
        @NotBlank(message = "reason is required") @Size(min = 1, max = 1000, message = "reason must be 1-1000 characters") String reason) {
}

record FulfillmentDetail(UUID id, UUID dealId, FulfillmentStatus status, UUID sourcePackageId,
        FulfillmentMilestoneProjection milestone, EvidenceSubmissionProjection currentEvidence,
        List<EvidenceSubmissionProjection> history, FulfillmentAvailableActions availableActions,
        long version, Instant createdAt, Instant updatedAt) {
}

record FulfillmentMilestoneProjection(UUID id, String title, String description,
        List<MilestoneRuleReference> ruleReferences, MilestoneAvailableActions availableActions,
        long version) {
}

record EvidenceSubmissionProjection(UUID id, UUID dealId, UUID milestoneId,
        EvidenceType evidenceType, EvidenceMediaType mediaType, String fileName,
        EvidenceSubmissionStatus status, Long clientSizeBytes, String clientSha256,
        Long verifiedSizeBytes, String verifiedSha256, String objectVersion,
        Instant createdAt, Instant submittedAt, Instant acceptedAt, Instant rejectedAt,
        String rejectionReason, EvidenceAvailableActions availableActions, long version) {
}

record EvidenceUploadIntent(EvidenceSubmissionProjection evidence, String uploadUrl,
        java.util.Map<String, String> uploadHeaders, Instant expiresAt) {
}

record EvidenceDownloadLink(UUID evidenceSubmissionId, String objectVersion,
        String downloadUrl, Instant expiresAt) {
}

record FulfillmentAvailableActions(boolean canStart, boolean canAccept, boolean canReject) {
}

record MilestoneAvailableActions(boolean canUpload) {
}

record EvidenceAvailableActions(boolean canDownload) {
}

