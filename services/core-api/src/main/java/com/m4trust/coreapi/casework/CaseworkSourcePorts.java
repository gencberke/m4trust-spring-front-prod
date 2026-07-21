package com.m4trust.coreapi.casework;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.m4trust.coreapi.organization.OperationContext;

/**
 * Consumer-owned contracts toward {@code deal} and {@code fulfillment}; those
 * modules implement these ports without casework reading foreign repositories
 * (ADR-003 §23, ADR-013 §2.5).
 */
public final class CaseworkSourcePorts {

    private CaseworkSourcePorts() {
    }

    public interface DealTarget {

        Optional<DealTargetSnapshot> findVisible(OperationContext context, UUID dealId);

        Optional<DealTargetSnapshot> lockVisibleForOpen(OperationContext context, UUID dealId);
    }

    /**
     * @param buyerLegalEntityId Current buyer assignment when present.
     * @param sellerLegalEntityId Current seller assignment when present.
     */
    public record DealTargetSnapshot(
            UUID dealId,
            UUID tenantId,
            String status,
            long version,
            UUID buyerLegalEntityId,
            UUID sellerLegalEntityId) {
    }

    public interface FulfillmentTarget {

        Optional<FulfillmentOpeningSnapshot> findVisible(OperationContext context, UUID dealId);

        Optional<FulfillmentOpeningSnapshot> lockVisibleForOpen(OperationContext context, UUID dealId);
    }

    /**
     * Server-owned fulfillment provenance captured at dispute opening.
     */
    public record FulfillmentOpeningSnapshot(
            UUID fulfillmentId,
            UUID milestoneId,
            UUID ratificationPackageId,
            String fulfillmentStatus,
            long fulfillmentVersion,
            long milestoneVersion,
            List<FinalizedEvidenceSnapshot> finalizedEvidence) {
    }

    public record FinalizedEvidenceSnapshot(
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
            String rejectionReason) {
    }

    public interface VideoAnalysisJobs {

        /**
         * Locks the requested evidence jobs in deterministic evidence-id order and
         * returns only successful immutable results already committed at lock time.
         */
        List<PinnedVideoResult> lockSuccessfulResults(UUID dealId, List<UUID> evidenceSubmissionIds);
    }

    public record PinnedVideoResult(
            UUID evidenceSubmissionId,
            UUID jobId,
            UUID resultId) {
    }

    /** Organization-owned legal-name lookup for immutable opening attribution. */
    public interface LegalEntityNames {

        String requireLegalName(UUID legalEntityId);
    }

    /** Identity-owned display-name lookup for immutable comment attribution. */
    public interface UserDisplayNames {

        String requireDisplayName(UUID userId);
    }

    /**
     * Fulfillment-owned safe advisory projection for pinned job/result identity.
     */
    public interface VideoAnalysisProjection {

        Object readPinnedPublicResult(UUID jobId, UUID resultId);
    }
}
