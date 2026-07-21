package com.m4trust.coreapi.fulfillment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.m4trust.coreapi.casework.CaseworkSourcePorts;
import com.m4trust.coreapi.organization.OperationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fulfillment-owned casework opening snapshot boundary; casework never reads
 * fulfillment repositories directly (ADR-013 §2.5).
 */
@Service
class CaseworkFulfillmentSourceAdapter implements CaseworkSourcePorts.FulfillmentTarget {

    private final FulfillmentRepository fulfillments;
    private final MilestoneRepository milestones;
    private final EvidenceSubmissionRepository evidenceSubmissions;

    CaseworkFulfillmentSourceAdapter(
            FulfillmentRepository fulfillments,
            MilestoneRepository milestones,
            EvidenceSubmissionRepository evidenceSubmissions) {
        this.fulfillments = fulfillments;
        this.milestones = milestones;
        this.evidenceSubmissions = evidenceSubmissions;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CaseworkSourcePorts.FulfillmentOpeningSnapshot> findVisible(
            OperationContext context,
            UUID dealId) {
        return fulfillments.findByDealId(dealId).flatMap(this::snapshot);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<CaseworkSourcePorts.FulfillmentOpeningSnapshot> lockVisibleForOpen(
            OperationContext context,
            UUID dealId) {
        return fulfillments.findByDealIdForUpdate(dealId).flatMap(fulfillment -> {
            milestones.findByFulfillmentIdForUpdate(fulfillment.id());
            evidenceSubmissions.findFinalizedByDealIdOrderByIdForUpdate(dealId);
            return snapshot(fulfillment);
        });
    }

    private Optional<CaseworkSourcePorts.FulfillmentOpeningSnapshot> snapshot(
            Fulfillment.FulfillmentRecord fulfillment) {
        return milestones.findByFulfillmentId(fulfillment.id()).map(milestone -> {
            List<CaseworkSourcePorts.FinalizedEvidenceSnapshot> evidence = evidenceSubmissions
                    .findFinalizedByDealIdOrderById(fulfillment.dealId())
                    .stream()
                    .map(this::toEvidenceSnapshot)
                    .toList();
            return new CaseworkSourcePorts.FulfillmentOpeningSnapshot(
                    fulfillment.id(),
                    milestone.id(),
                    fulfillment.sourcePackageId(),
                    fulfillment.status().name(),
                    fulfillment.version(),
                    milestone.version(),
                    evidence);
        });
    }

    private CaseworkSourcePorts.FinalizedEvidenceSnapshot toEvidenceSnapshot(
            EvidenceSubmission.EvidenceSubmissionRecord submission) {
        return new CaseworkSourcePorts.FinalizedEvidenceSnapshot(
                submission.id(),
                submission.status().name(),
                submission.version(),
                submission.evidenceType().name(),
                submission.mediaType().value(),
                submission.fileName(),
                submission.objectVersion(),
                submission.verifiedSizeBytes() == null ? 0L : submission.verifiedSizeBytes(),
                submission.verifiedSha256(),
                submission.createdAt(),
                submission.submittedAt(),
                submission.acceptedAt(),
                submission.rejectedAt(),
                submission.rejectionReason());
    }
}
