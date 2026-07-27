package com.m4trust.coreapi.contractintelligence.domain;

import java.time.Instant;
import java.util.UUID;

/** Public read projection for the current Deal document. Result population starts in 3B. */
public record DealDocumentAnalysis(
    UUID currentDocumentId,
    AnalysisJobStatus status,
    Instant requestedAt,
    Instant processingStartedAt,
    Instant completedAt,
    Instant failedAt,
    Failure failure,
    Object result) {

  public record Failure(String code, boolean retryRecommended) {}
}
