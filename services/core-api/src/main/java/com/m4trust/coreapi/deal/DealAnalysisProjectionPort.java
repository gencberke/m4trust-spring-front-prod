package com.m4trust.coreapi.deal;

import java.time.Instant;
import java.util.UUID;

public interface DealAnalysisProjectionPort {
    AnalysisSummary summary(UUID currentDocumentId);
    boolean hasActiveJob(UUID currentDocumentId);
    record AnalysisSummary(UUID currentDocumentId, String status, Instant requestedAt,
            Instant processingStartedAt, Instant completedAt, Instant failedAt,
            Failure failure) { }
    record Failure(String code, boolean retryRecommended) { }
}
