package com.m4trust.coreapi.casework;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record OpenDisputeRequest(
        @NotBlank(message = "reasonCode is required") String reasonCode,
        @NotBlank(message = "subject is required") @Size(min = 1, max = 200, message = "subject must be 1-200 characters") String subject,
        @NotBlank(message = "statement is required") @Size(min = 1, max = 4000, message = "statement must be 1-4000 characters") String statement,
        @Min(value = 0, message = "expectedDealVersion must be non-negative") long expectedDealVersion,
        @Min(value = 0, message = "expectedFulfillmentVersion must be non-negative") long expectedFulfillmentVersion) {
}

record DisputeOpeningLegalEntity(UUID legalEntityId, String legalName) {
}

record DisputeAvailableActions(boolean canComment, boolean canAcknowledge, boolean canWithdraw) {
}

record DisputeEvidenceSnapshotEntry(
        UUID evidenceSubmissionId,
        String statusAtOpen,
        long versionAtOpen,
        String evidenceType,
        String mediaType,
        String fileName,
        String objectVersion,
        long verifiedSizeBytes,
        String verifiedSha256,
        Instant createdAt,
        Instant submittedAt,
        Instant acceptedAt,
        Instant rejectedAt,
        String rejectionReason) {
}

record DisputeVideoAnalysisSnapshotEntry(
        UUID evidenceSubmissionId,
        UUID jobId,
        UUID resultId,
        Object result) {
}

record DisputeOpeningSnapshot(
        UUID ratificationPackageId,
        UUID fulfillmentId,
        String fulfillmentStatusAtOpen,
        long fulfillmentVersionAtOpen,
        UUID milestoneId,
        long milestoneVersionAtOpen,
        List<DisputeEvidenceSnapshotEntry> evidence,
        List<DisputeVideoAnalysisSnapshotEntry> videoAnalysis) {

    DisputeOpeningSnapshot {
        evidence = List.copyOf(evidence);
        videoAnalysis = List.copyOf(videoAnalysis);
    }
}

record DisputeSummary(
        UUID id,
        UUID dealId,
        DisputeStatus status,
        DisputeReasonCode reasonCode,
        String subject,
        DisputeOpeningLegalEntity openingLegalEntity,
        Instant openedAt,
        Instant acknowledgedAt,
        Instant withdrawnAt,
        long version,
        DisputeAvailableActions availableActions) {
}

record DisputeDetail(
        UUID id,
        UUID dealId,
        DisputeStatus status,
        DisputeReasonCode reasonCode,
        String subject,
        String statement,
        DisputeOpeningLegalEntity openingLegalEntity,
        Instant openedAt,
        Instant acknowledgedAt,
        Instant withdrawnAt,
        DisputeOpeningSnapshot openingSnapshot,
        long version,
        DisputeAvailableActions availableActions) {
}

record DisputePage(
        List<DisputeSummary> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    DisputePage {
        items = List.copyOf(items);
    }
}
