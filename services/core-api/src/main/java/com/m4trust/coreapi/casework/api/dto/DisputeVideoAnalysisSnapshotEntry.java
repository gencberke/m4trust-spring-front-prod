package com.m4trust.coreapi.casework.api.dto;

import com.m4trust.coreapi.casework.domain.*;
import java.util.UUID;

public record DisputeVideoAnalysisSnapshotEntry(
    UUID evidenceSubmissionId, UUID jobId, UUID resultId, Object result) {}
