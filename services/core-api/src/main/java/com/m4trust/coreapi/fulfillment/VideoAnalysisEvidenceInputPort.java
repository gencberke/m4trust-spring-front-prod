package com.m4trust.coreapi.fulfillment;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Verified evidence snapshot and version-pinned AI download for video analysis input. */
public interface VideoAnalysisEvidenceInputPort {

    Optional<VerifiedSnapshot> findVerifiedSnapshot(UUID evidenceSubmissionId);

    FulfillmentObjectStorage.DirectDownload mintVersionPinnedDownload(VerifiedSnapshot snapshot);

    record VerifiedSnapshot(UUID evidenceSubmissionId, UUID dealId, UUID fulfillmentId,
            UUID milestoneId, EvidenceType evidenceType, EvidenceMediaType mediaType,
            String fileName, long verifiedSizeBytes, String verifiedSha256, String objectKey,
            String objectVersion, long evidenceVersion, Instant submittedAt) {
    }
}
