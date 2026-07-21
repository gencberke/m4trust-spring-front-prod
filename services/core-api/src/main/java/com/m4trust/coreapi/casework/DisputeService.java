package com.m4trust.coreapi.casework;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.m4trust.coreapi.audit.AuditAppendPort;
import com.m4trust.coreapi.audit.AuditRecord;
import com.m4trust.coreapi.casework.CaseworkSourcePorts.FinalizedEvidenceSnapshot;
import com.m4trust.coreapi.casework.CaseworkSourcePorts.FulfillmentOpeningSnapshot;
import com.m4trust.coreapi.casework.CaseworkSourcePorts.PinnedVideoResult;
import com.m4trust.coreapi.casework.DisputeEvidenceSnapshotRepository.DisputeEvidenceSnapshotRecord;
import com.m4trust.coreapi.idempotency.IdempotencyClaim;
import com.m4trust.coreapi.idempotency.IdempotencyRequest;
import com.m4trust.coreapi.idempotency.IdempotencyResultReference;
import com.m4trust.coreapi.idempotency.IdempotencyService;
import com.m4trust.coreapi.organization.LegalEntityRole;
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Coordinates dispute open, list, and detail reads for buyer/seller actors. */
@Service
class DisputeService {

    private static final String OPEN_IDEMPOTENCY_OPERATION = "DISPUTE_OPEN";
    private static final String OPEN_IDEMPOTENCY_RESULT = "DISPUTE_CASE";
    private static final String AUDIT_SUBJECT = "DISPUTE_CASE";
    private static final String AUDIT_ACTION = "DISPUTE_OPENED";
    private static final Set<String> ELIGIBLE_FULFILLMENT_STATUSES = Set.of(
            "IN_PROGRESS", "EVIDENCE_REQUIRED", "REVIEW_REQUIRED", "COMPLETED");

    private final CaseworkSourcePorts.DealTarget deals;
    private final CaseworkSourcePorts.FulfillmentTarget fulfillments;
    private final CaseworkSourcePorts.VideoAnalysisJobs videoAnalysisJobs;
    private final CaseworkSourcePorts.LegalEntityNames legalEntityNames;
    private final CaseworkSourcePorts.VideoAnalysisProjection videoAnalysisProjection;
    private final DisputeCaseRepository disputeCases;
    private final DisputeEvidenceSnapshotRepository evidenceSnapshots;
    private final IdempotencyService idempotency;
    private final AuditAppendPort auditAppender;
    private final TransactionTemplate transactions;
    private final Clock clock;

    DisputeService(
            CaseworkSourcePorts.DealTarget deals,
            CaseworkSourcePorts.FulfillmentTarget fulfillments,
            CaseworkSourcePorts.VideoAnalysisJobs videoAnalysisJobs,
            CaseworkSourcePorts.LegalEntityNames legalEntityNames,
            CaseworkSourcePorts.VideoAnalysisProjection videoAnalysisProjection,
            DisputeCaseRepository disputeCases,
            DisputeEvidenceSnapshotRepository evidenceSnapshots,
            IdempotencyService idempotency,
            AuditAppendPort auditAppender,
            TransactionTemplate transactions,
            Clock clock) {
        this.deals = deals;
        this.fulfillments = fulfillments;
        this.videoAnalysisJobs = videoAnalysisJobs;
        this.legalEntityNames = legalEntityNames;
        this.videoAnalysisProjection = videoAnalysisProjection;
        this.disputeCases = disputeCases;
        this.evidenceSnapshots = evidenceSnapshots;
        this.idempotency = idempotency;
        this.auditAppender = auditAppender;
        this.transactions = transactions;
        this.clock = clock;
    }

    DisputeDetail openDispute(
            OperationContext context,
            UUID dealId,
            OpenDisputeRequest request,
            UUID idempotencyKey,
            UUID correlationId) {
        requireOperation(context, RequestedOperation.DISPUTE_OPEN);
        String subject = trimRequired(request.subject(), "subject");
        String statement = trimRequired(request.statement(), "statement");
        DisputeReasonCode reasonCode = reasonCode(request.reasonCode());
        IdempotencyRequest idempotencyRequest = openIdempotencyRequest(
                context, dealId, reasonCode, subject, statement,
                request.expectedDealVersion(), request.expectedFulfillmentVersion(), idempotencyKey);
        IdempotencyResultReference completed = idempotency.findCompleted(idempotencyRequest).orElse(null);
        if (completed != null) {
            return replayDispute(context, dealId, completed);
        }

        CaseworkSourcePorts.DealTargetSnapshot preflightDeal = deals.findVisible(context, dealId)
                .orElseThrow(CaseworkExceptions.NotFound::new);
        requirePartyAdmin(context, preflightDeal);
        validateOpenPreflight(preflightDeal, request.expectedDealVersion());
        FulfillmentOpeningSnapshot preflightFulfillment = fulfillments.findVisible(context, dealId)
                .orElseThrow(() -> conflict("FULFILLMENT_STATE_CONFLICT"));
        validateFulfillmentOpenPreflight(preflightFulfillment, request.expectedFulfillmentVersion());

        return required(transactions.execute(status -> openInTransaction(
                context, dealId, reasonCode, subject, statement,
                request.expectedDealVersion(), request.expectedFulfillmentVersion(),
                idempotencyRequest, correlationId)));
    }

    DisputePage listDisputes(OperationContext context, UUID dealId, DisputeQuery query) {
        requireOperation(context, RequestedOperation.DISPUTE_LIST_READ);
        CaseworkSourcePorts.DealTargetSnapshot deal = deals.findVisible(context, dealId)
                .orElseThrow(CaseworkExceptions.NotFound::new);
        requirePartyReader(context, deal);
        long totalElements = disputeCases.countByDealId(dealId);
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / query.size());
        List<DisputeSummary> items = disputeCases.findByDealIdPage(
                        dealId, query.offset(), query.size(), query.sort())
                .stream()
                .map(record -> toSummary(record, deal, context))
                .toList();
        return new DisputePage(items, query.page(), query.size(), totalElements, totalPages);
    }

    DisputeDetail getDispute(OperationContext context, UUID dealId, UUID disputeId) {
        requireOperation(context, RequestedOperation.DISPUTE_DETAIL_READ);
        CaseworkSourcePorts.DealTargetSnapshot deal = deals.findVisible(context, dealId)
                .orElseThrow(CaseworkExceptions.NotFound::new);
        requirePartyReader(context, deal);
        DisputeCase.DisputeCaseRecord record = disputeCases.findByIdAndDealId(disputeId, dealId)
                .orElseThrow(CaseworkExceptions.DisputeNotFound::new);
        return toDetail(record, deal, context);
    }

    private DisputeDetail openInTransaction(
            OperationContext context,
            UUID dealId,
            DisputeReasonCode reasonCode,
            String subject,
            String statement,
            long expectedDealVersion,
            long expectedFulfillmentVersion,
            IdempotencyRequest idempotencyRequest,
            UUID correlationId) {
        CaseworkSourcePorts.DealTargetSnapshot deal = deals.lockVisibleForOpen(context, dealId)
                .orElseThrow(CaseworkExceptions.NotFound::new);
        requirePartyAdmin(context, deal);
        validateOpenPreflight(deal, expectedDealVersion);
        FulfillmentOpeningSnapshot fulfillment = fulfillments.lockVisibleForOpen(context, dealId)
                .orElseThrow(() -> conflict("FULFILLMENT_STATE_CONFLICT"));
        validateFulfillmentOpenPreflight(fulfillment, expectedFulfillmentVersion);
        if (disputeCases.findActiveByDealIdForUpdate(dealId).isPresent()) {
            throw conflict("DISPUTE_ACTIVE_CASE_EXISTS");
        }

        List<UUID> evidenceIds = fulfillment.finalizedEvidence().stream()
                .map(FinalizedEvidenceSnapshot::evidenceSubmissionId)
                .toList();
        List<PinnedVideoResult> pinnedVideos = videoAnalysisJobs.lockSuccessfulResults(dealId, evidenceIds);
        Map<UUID, PinnedVideoResult> pinnedByEvidence = new HashMap<>();
        for (PinnedVideoResult pinned : pinnedVideos) {
            pinnedByEvidence.put(pinned.evidenceSubmissionId(), pinned);
        }

        IdempotencyClaim claim = idempotency.claim(idempotencyRequest);
        if (claim.isReplay()) {
            return replayDispute(context, dealId, claim.resultReference());
        }

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        UUID disputeId = UUID.randomUUID();
        String openingLegalName = legalEntityNames.requireLegalName(context.activeLegalEntityId());
        DisputeCase dispute = DisputeCase.open(
                disputeId,
                dealId,
                deal.tenantId(),
                fulfillment.fulfillmentId(),
                fulfillment.milestoneId(),
                fulfillment.ratificationPackageId(),
                fulfillment.fulfillmentStatus(),
                fulfillment.fulfillmentVersion(),
                fulfillment.milestoneVersion(),
                reasonCode,
                subject,
                statement,
                context.tenantId(),
                context.activeLegalEntityId(),
                context.authenticatedUserId(),
                openingLegalName,
                now);

        List<DisputeEvidenceSnapshotRecord> snapshotRows = new ArrayList<>();
        for (FinalizedEvidenceSnapshot evidence : fulfillment.finalizedEvidence()) {
            PinnedVideoResult pinned = pinnedByEvidence.get(evidence.evidenceSubmissionId());
            snapshotRows.add(new DisputeEvidenceSnapshotRecord(
                    UUID.randomUUID(),
                    disputeId,
                    dealId,
                    evidence.evidenceSubmissionId(),
                    evidence.statusAtOpen(),
                    evidence.versionAtOpen(),
                    evidence.evidenceType(),
                    evidence.mediaType(),
                    evidence.fileName(),
                    evidence.objectVersion(),
                    evidence.verifiedSizeBytes(),
                    evidence.verifiedSha256(),
                    evidence.createdAt(),
                    evidence.submittedAt(),
                    evidence.acceptedAt(),
                    evidence.rejectedAt(),
                    evidence.rejectionReason(),
                    pinned == null ? null : pinned.jobId(),
                    pinned == null ? null : pinned.resultId()));
        }

        try {
            disputeCases.insert(dispute.toRecord());
            evidenceSnapshots.insertAll(snapshotRows);
        } catch (DuplicateKeyException exception) {
            if (disputeCases.findActiveByDealId(dealId).isPresent()) {
                throw conflict("DISPUTE_ACTIVE_CASE_EXISTS");
            }
            throw exception;
        }

        auditAppender.append(new AuditRecord(
                UUID.randomUUID(),
                context.tenantId(),
                context.authenticatedUserId(),
                context.activeLegalEntityId(),
                AUDIT_SUBJECT,
                disputeId,
                AUDIT_ACTION,
                correlationId,
                null,
                now));
        idempotency.recordResult(claim, new IdempotencyResultReference(OPEN_IDEMPOTENCY_RESULT, disputeId));
        return toDetail(dispute.toRecord(), deal, context);
    }

    private DisputeDetail replayDispute(
            OperationContext context, UUID dealId, IdempotencyResultReference reference) {
        if (!OPEN_IDEMPOTENCY_RESULT.equals(reference.type())) {
            throw new IllegalStateException("Unexpected dispute idempotency result type");
        }
        CaseworkSourcePorts.DealTargetSnapshot deal = deals.findVisible(context, dealId)
                .orElseThrow(CaseworkExceptions.NotFound::new);
        requirePartyReader(context, deal);
        DisputeCase.DisputeCaseRecord record = disputeCases.findByIdAndDealId(reference.id(), dealId)
                .orElseThrow(CaseworkExceptions.DisputeNotFound::new);
        return toDetail(record, deal, context);
    }

    private DisputeDetail toDetail(
            DisputeCase.DisputeCaseRecord record,
            CaseworkSourcePorts.DealTargetSnapshot deal,
            OperationContext context) {
        List<DisputeEvidenceSnapshotRecord> snapshots = evidenceSnapshots.findByDisputeCaseId(record.id());
        List<DisputeEvidenceSnapshotEntry> evidence = snapshots.stream()
                .map(this::toEvidenceEntry)
                .toList();
        List<DisputeVideoAnalysisSnapshotEntry> videoAnalysis = snapshots.stream()
                .filter(snapshot -> snapshot.videoJobId() != null)
                .map(snapshot -> new DisputeVideoAnalysisSnapshotEntry(
                        snapshot.evidenceSubmissionId(),
                        snapshot.videoJobId(),
                        snapshot.videoResultId(),
                        videoAnalysisProjection.readPinnedPublicResult(
                                snapshot.videoJobId(), snapshot.videoResultId())))
                .toList();
        DisputeOpeningSnapshot openingSnapshot = new DisputeOpeningSnapshot(
                record.ratificationPackageId(),
                record.fulfillmentId(),
                record.fulfillmentStatusAtOpen(),
                record.fulfillmentVersionAtOpen(),
                record.milestoneId(),
                record.milestoneVersionAtOpen(),
                evidence,
                videoAnalysis);
        return new DisputeDetail(
                record.id(),
                record.dealId(),
                record.status(),
                record.reasonCode(),
                record.subject(),
                record.statement(),
                openingLegalEntity(record),
                record.openedAt(),
                record.acknowledgedAt(),
                record.withdrawnAt(),
                openingSnapshot,
                record.version(),
                availableActions(record, deal, context));
    }

    private DisputeSummary toSummary(
            DisputeCase.DisputeCaseRecord record,
            CaseworkSourcePorts.DealTargetSnapshot deal,
            OperationContext context) {
        return new DisputeSummary(
                record.id(),
                record.dealId(),
                record.status(),
                record.reasonCode(),
                record.subject(),
                openingLegalEntity(record),
                record.openedAt(),
                record.acknowledgedAt(),
                record.withdrawnAt(),
                record.version(),
                availableActions(record, deal, context));
    }

    private DisputeEvidenceSnapshotEntry toEvidenceEntry(DisputeEvidenceSnapshotRecord snapshot) {
        return new DisputeEvidenceSnapshotEntry(
                snapshot.evidenceSubmissionId(),
                snapshot.statusAtOpen(),
                snapshot.versionAtOpen(),
                snapshot.evidenceType(),
                snapshot.mediaType(),
                snapshot.fileName(),
                snapshot.objectVersion(),
                snapshot.verifiedSizeBytes(),
                snapshot.verifiedSha256(),
                snapshot.createdAt(),
                snapshot.submittedAt(),
                snapshot.acceptedAt(),
                snapshot.rejectedAt(),
                snapshot.rejectionReason());
    }

    private DisputeOpeningLegalEntity openingLegalEntity(DisputeCase.DisputeCaseRecord record) {
        return new DisputeOpeningLegalEntity(record.openingLegalEntityId(), record.openingLegalName());
    }

    private DisputeAvailableActions availableActions(
            DisputeCase.DisputeCaseRecord record,
            CaseworkSourcePorts.DealTargetSnapshot deal,
            OperationContext context) {
        PartyRole role = partyRole(deal, context.activeLegalEntityId());
        boolean active = record.status() == DisputeStatus.OPEN || record.status() == DisputeStatus.UNDER_REVIEW;
        boolean partyReader = role != PartyRole.NONE
                && (context.activeLegalEntityRole() == LegalEntityRole.ADMIN
                || context.activeLegalEntityRole() == LegalEntityRole.MEMBER);
        boolean partyAdmin = role != PartyRole.NONE && context.activeLegalEntityRole() == LegalEntityRole.ADMIN;
        boolean counterpartyAdmin = partyAdmin && !context.activeLegalEntityId().equals(record.openingLegalEntityId());
        boolean openingAdmin = partyAdmin && context.activeLegalEntityId().equals(record.openingLegalEntityId());
        return new DisputeAvailableActions(
                partyReader && active,
                counterpartyAdmin && record.status() == DisputeStatus.OPEN,
                openingAdmin && active);
    }

    private void validateOpenPreflight(CaseworkSourcePorts.DealTargetSnapshot deal, long expectedDealVersion) {
        if (!"ACTIVE".equals(deal.status())) {
            throw conflict("DEAL_STATE_CONFLICT");
        }
        if (deal.version() != expectedDealVersion) {
            throw conflict("DEAL_STALE_VERSION");
        }
    }

    private void validateFulfillmentOpenPreflight(
            FulfillmentOpeningSnapshot fulfillment, long expectedFulfillmentVersion) {
        if (!ELIGIBLE_FULFILLMENT_STATUSES.contains(fulfillment.fulfillmentStatus())) {
            throw conflict("FULFILLMENT_STATE_CONFLICT");
        }
        if (fulfillment.fulfillmentVersion() != expectedFulfillmentVersion) {
            throw conflict("FULFILLMENT_STALE_VERSION");
        }
    }

    private void requirePartyAdmin(OperationContext context, CaseworkSourcePorts.DealTargetSnapshot deal) {
        PartyRole role = partyRole(deal, context.activeLegalEntityId());
        if (role == PartyRole.NONE) {
            throw new CaseworkExceptions.NotFound();
        }
        if (context.activeLegalEntityRole() != LegalEntityRole.ADMIN) {
            throw new CaseworkExceptions.OpenForbidden();
        }
    }

    private void requirePartyReader(OperationContext context, CaseworkSourcePorts.DealTargetSnapshot deal) {
        if (partyRole(deal, context.activeLegalEntityId()) == PartyRole.NONE
                || (context.activeLegalEntityRole() != LegalEntityRole.ADMIN
                && context.activeLegalEntityRole() != LegalEntityRole.MEMBER)) {
            throw new CaseworkExceptions.NotFound();
        }
    }

    private PartyRole partyRole(CaseworkSourcePorts.DealTargetSnapshot deal, UUID legalEntityId) {
        if (legalEntityId.equals(deal.buyerLegalEntityId())) {
            return PartyRole.BUYER;
        }
        if (legalEntityId.equals(deal.sellerLegalEntityId())) {
            return PartyRole.SELLER;
        }
        return PartyRole.NONE;
    }

    private DisputeReasonCode reasonCode(String value) {
        if (value == null || value.isBlank()) {
            throw new CaseworkExceptions.Validation("reasonCode", "REQUIRED", "reasonCode is required.");
        }
        try {
            return DisputeReasonCode.valueOf(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new CaseworkExceptions.Validation(
                    "reasonCode", "INVALID_ENUM", "reasonCode is not supported.");
        }
    }

    private String trimRequired(String value, String field) {
        if (value == null) {
            throw new CaseworkExceptions.Validation(field, "REQUIRED", field + " is required.");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new CaseworkExceptions.Validation(field, "REQUIRED", field + " is required.");
        }
        int max = "subject".equals(field) ? 200 : 4000;
        if (trimmed.length() > max) {
            throw new CaseworkExceptions.Validation(field, "OUT_OF_RANGE", field + " exceeds the allowed length.");
        }
        return trimmed;
    }

    private IdempotencyRequest openIdempotencyRequest(
            OperationContext context,
            UUID dealId,
            DisputeReasonCode reasonCode,
            String subject,
            String statement,
            long expectedDealVersion,
            long expectedFulfillmentVersion,
            UUID key) {
        Map<String, Object> canonicalRequest = new LinkedHashMap<>();
        canonicalRequest.put("actorEntity", context.activeLegalEntityId().toString());
        canonicalRequest.put("dealId", dealId.toString());
        canonicalRequest.put("reasonCode", reasonCode.name());
        canonicalRequest.put("subject", subject);
        canonicalRequest.put("statement", statement);
        canonicalRequest.put("expectedDealVersion", expectedDealVersion);
        canonicalRequest.put("expectedFulfillmentVersion", expectedFulfillmentVersion);
        return new IdempotencyRequest(
                context.authenticatedUserId(),
                context.tenantId(),
                OPEN_IDEMPOTENCY_OPERATION,
                key,
                canonicalHash(canonicalRequest));
    }

    private String canonicalHash(Map<String, Object> canonicalRequest) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder builder = new StringBuilder();
            canonicalRequest.forEach((key, value) -> builder.append(key).append("=").append(value).append("\n"));
            return HexFormat.of().formatHex(digest.digest(builder.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void requireOperation(OperationContext context, RequestedOperation expected) {
        if (context.requestedOperation() != expected) {
            throw new IllegalArgumentException("Operation context does not match casework use case");
        }
    }

    private static <T> T required(T value) {
        if (value == null) {
            throw new IllegalStateException("Transaction returned no result");
        }
        return value;
    }

    private static CaseworkExceptions.Conflict conflict(String code) {
        return new CaseworkExceptions.Conflict(code);
    }

    private enum PartyRole {
        BUYER,
        SELLER,
        NONE
    }
}
