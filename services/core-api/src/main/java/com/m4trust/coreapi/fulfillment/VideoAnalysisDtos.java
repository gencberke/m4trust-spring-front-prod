package com.m4trust.coreapi.fulfillment;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Min;

record RequestVideoAnalysisRequest(
        @Min(value = 0, message = "expectedEvidenceVersion must be non-negative") long expectedEvidenceVersion) {
}

enum VideoAnalysisPublicStatus {
    NOT_REQUESTED,
    QUEUED,
    RESULT_AVAILABLE,
    FAILED
}

record VideoAnalysisFailureSummary(String code, boolean retryRecommended) {
}

record VideoAnalysisAvailableActions(boolean canRequest) {
}

record VideoAnalysisDetail(UUID evidenceSubmissionId, UUID jobId, VideoAnalysisPublicStatus status,
        Instant requestedAt, Instant completedAt, Instant failedAt,
        VideoAnalysisFailureSummary failure, Object result, VideoAnalysisAvailableActions availableActions) {
}
