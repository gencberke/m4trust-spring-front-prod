package com.m4trust.coreapi.casework.api.dto;

import com.m4trust.coreapi.casework.domain.*;
import java.time.Instant;
import java.util.UUID;

public record DisputeEvidenceSnapshotEntry(
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
    String rejectionReason) {}
