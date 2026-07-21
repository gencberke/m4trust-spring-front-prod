package com.m4trust.coreapi.fulfillment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.m4trust.coreapi.casework.CaseworkSourcePorts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Fulfillment-owned video-analysis job lock boundary for dispute opening. */
@Service
class CaseworkVideoAnalysisLockAdapter implements CaseworkSourcePorts.VideoAnalysisJobs {

    private final VideoAnalysisRepository videoAnalysisJobs;

    CaseworkVideoAnalysisLockAdapter(VideoAnalysisRepository videoAnalysisJobs) {
        this.videoAnalysisJobs = videoAnalysisJobs;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<CaseworkSourcePorts.PinnedVideoResult> lockSuccessfulResults(
            UUID dealId,
            List<UUID> evidenceSubmissionIds) {
        List<UUID> orderedEvidenceIds = evidenceSubmissionIds.stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        List<CaseworkSourcePorts.PinnedVideoResult> pinned = new ArrayList<>();
        for (UUID evidenceSubmissionId : orderedEvidenceIds) {
            List<VideoAnalysisRepository.VideoAnalysisJobRecord> lockedJobs =
                    videoAnalysisJobs.findByEvidenceIdOrderByIdForUpdate(evidenceSubmissionId);
            lockedJobs.stream()
                    .filter(job -> dealId.equals(job.dealId()))
                    .filter(job -> job.status() == VideoAnalysisJobStatus.RESULT_AVAILABLE)
                    .findFirst()
                    .flatMap(job -> videoAnalysisJobs.findResultIdByJobId(job.id())
                            .map(resultId -> new CaseworkSourcePorts.PinnedVideoResult(
                                    evidenceSubmissionId, job.id(), resultId)))
                    .ifPresent(pinned::add);
        }
        return List.copyOf(pinned);
    }
}
