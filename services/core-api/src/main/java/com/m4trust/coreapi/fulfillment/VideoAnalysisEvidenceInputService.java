package com.m4trust.coreapi.fulfillment;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class VideoAnalysisEvidenceInputService implements VideoAnalysisEvidenceInputPort {

    private final EvidenceSubmissionRepository evidenceRepository;
    private final FulfillmentObjectStorage storage;

    VideoAnalysisEvidenceInputService(EvidenceSubmissionRepository evidenceRepository,
            FulfillmentObjectStorage storage) {
        this.evidenceRepository = evidenceRepository;
        this.storage = storage;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VerifiedSnapshot> findVerifiedSnapshot(UUID evidenceSubmissionId) {
        return evidenceRepository.findById(evidenceSubmissionId)
                .filter(this::hasVerifiedMetadata)
                .filter(this::isSupportedAnalysisInput)
                .map(this::toSnapshot);
    }

    @Override
    public FulfillmentObjectStorage.DirectDownload mintVersionPinnedDownload(VerifiedSnapshot snapshot) {
        return storage.createDirectDownload(snapshot.objectKey(), snapshot.objectVersion());
    }

    private boolean hasVerifiedMetadata(EvidenceSubmission.EvidenceSubmissionRecord submission) {
        return switch (submission.status()) {
            case SUBMITTED, ACCEPTED, REJECTED -> submission.objectVersion() != null
                    && submission.verifiedSizeBytes() != null
                    && submission.verifiedSha256() != null;
            case PENDING_UPLOAD -> false;
        };
    }

    private boolean isSupportedAnalysisInput(EvidenceSubmission.EvidenceSubmissionRecord submission) {
        return switch (submission.evidenceType()) {
            case VIDEO -> submission.mediaType() == EvidenceMediaType.VIDEO_MP4;
            case PHOTO -> submission.mediaType() == EvidenceMediaType.IMAGE_JPEG
                    || submission.mediaType() == EvidenceMediaType.IMAGE_PNG;
            default -> false;
        };
    }

    private VerifiedSnapshot toSnapshot(EvidenceSubmission.EvidenceSubmissionRecord submission) {
        return new VerifiedSnapshot(submission.id(), submission.dealId(), submission.fulfillmentId(),
                submission.milestoneId(), submission.evidenceType(), submission.mediaType(),
                submission.fileName(), submission.verifiedSizeBytes(), submission.verifiedSha256(),
                submission.objectKey(), submission.objectVersion(), submission.version(),
                submission.submittedAt());
    }
}
