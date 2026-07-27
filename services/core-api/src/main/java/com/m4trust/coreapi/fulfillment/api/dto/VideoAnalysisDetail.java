package com.m4trust.coreapi.fulfillment.api.dto;

import java.time.Instant;
import java.util.UUID;

public record VideoAnalysisDetail(
    UUID evidenceSubmissionId,
    UUID jobId,
    VideoAnalysisPublicStatus status,
    Instant requestedAt,
    Instant completedAt,
    Instant failedAt,
    VideoAnalysisFailureSummary failure,
    Object result,
    VideoAnalysisAvailableActions availableActions) {}
