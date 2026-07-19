package com.m4trust.coreapi.contractintelligence;

import java.time.Instant;
import java.util.UUID;

/** Public read projection for the current Deal document. Result population starts in 3B. */
record DealDocumentAnalysis(UUID currentDocumentId, AnalysisJobStatus status,
        Instant requestedAt, Instant processingStartedAt, Instant completedAt,
        Instant failedAt, Failure failure, Object result) {

    record Failure(String code, boolean retryRecommended) {
    }
}
