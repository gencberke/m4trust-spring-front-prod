package com.m4trust.coreapi.fulfillment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.m4trust.coreapi.audit.AuditAppendPort;
import com.m4trust.coreapi.audit.AuditRecord;
import com.m4trust.coreapi.idempotency.IdempotencyClaim;
import com.m4trust.coreapi.idempotency.IdempotencyRequest;
import com.m4trust.coreapi.idempotency.IdempotencyResultReference;
import com.m4trust.coreapi.idempotency.IdempotencyService;
import com.m4trust.coreapi.organization.LegalEntityRole;
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Coordinates fulfillment lifecycle, direct storage evidence transfer, and review. */
@Service
class FulfillmentService {

    private static final String START_IDEMPOTENCY_OPERATION = "FULFILLMENT_START";
    private static final String START_IDEMPOTENCY_RESULT = "FULFILLMENT";
    private static final String FINALIZE_IDEMPOTENCY_OPERATION = "EVIDENCE_UPLOAD_FINALIZE";
    private static final String FINALIZE_IDEMPOTENCY_RESULT = "SUBMITTED_EVIDENCE";
    private static final String ACCEPT_IDEMPOTENCY_OPERATION = "EVIDENCE_ACCEPT";
    private static final String ACCEPT_IDEMPOTENCY_RESULT = "ACCEPTED_EVIDENCE";
    private static final String REJECT_IDEMPOTENCY_OPERATION = "EVIDENCE_REJECT";
    private static final String REJECT_IDEMPOTENCY_RESULT = "REJECTED_EVIDENCE";
    private static final String AUDIT_SUBJECT = "FULFILLMENT";

    private final FulfillmentRepository fulfillmentRepository;
    private final MilestoneRepository milestoneRepository;
    private final EvidenceSubmissionRepository evidenceRepository;
    private final FulfillmentSourcePorts.DealTarget deals;
    private final FulfillmentObjectStorage storage;
    private final IdempotencyService idempotency;
    private final AuditAppendPort auditAppender;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final long maxUploadSizeBytes;

    FulfillmentService(FulfillmentRepository fulfillmentRepository,
            MilestoneRepository milestoneRepository,
            EvidenceSubmissionRepository evidenceRepository,
            FulfillmentSourcePorts.DealTarget deals,
            FulfillmentObjectStorage storage,
            IdempotencyService idempotency,
            AuditAppendPort auditAppender,
            TransactionTemplate transactions,
            Clock clock,
            @Value("${app.object-storage.max-upload-size-bytes}") long maxUploadSizeBytes) {
        this.fulfillmentRepository = fulfillmentRepository;
        this.milestoneRepository = milestoneRepository;
        this.evidenceRepository = evidenceRepository;
        this.deals = deals;
        this.storage = storage;
        this.idempotency = idempotency;
        this.auditAppender = auditAppender;
        this.transactions = transactions;
        this.clock = clock;
        this.maxUploadSizeBytes = maxUploadSizeBytes;
    }

    FulfillmentDetail startFulfillment(OperationContext context, UUID dealId,
            StartFulfillmentRequest request, UUID idempotencyKey, UUID correlationId) {
        requireOperation(context, RequestedOperation.FULFILLMENT_START);
        IdempotencyRequest idempotencyRequest = startIdempotencyRequest(context, dealId, request, idempotencyKey);
        IdempotencyResultReference completed = idempotency.findCompleted(idempotencyRequest).orElse(null);
        if (completed != null) {
            return replayFulfillment(context, completed);
        }
        FulfillmentSourcePorts.Target preflight = deals.findVisible(context, dealId)
                .orElseThrow(FulfillmentExceptions.DealNotFound::new);
        requireSeller(context, preflight);
        if (!"ACTIVE".equals(preflight.status()) || !"FUNDED".equals(preflight.fundingStatus())) {
            throw new FulfillmentExceptions.StartConflict();
        }
        if (preflight.version() != request.expectedVersion()) {
            throw new FulfillmentExceptions.StartConflict();
        }
        if (preflight.ratifiedPackageId() == null) {
            throw new FulfillmentExceptions.StartConflict();
        }
        return required(transactions.execute(status -> startInTransaction(context, dealId, request,
                idempotencyRequest, correlationId)));
    }

    private FulfillmentDetail startInTransaction(OperationContext context, UUID dealId,
            StartFulfillmentRequest request, IdempotencyRequest idempotencyRequest,
            UUID correlationId) {
        FulfillmentSourcePorts.Target target = deals.lockVisibleForStart(context, dealId)
                .orElseThrow(FulfillmentExceptions.DealNotFound::new);
        requireSeller(context, target);
        if (!"ACTIVE".equals(target.status()) || !"FUNDED".equals(target.fundingStatus())) {
            throw new FulfillmentExceptions.StartConflict();
        }
        if (target.version() != request.expectedVersion()) {
            throw new FulfillmentExceptions.StartConflict();
        }
        IdempotencyClaim claim = idempotency.claim(idempotencyRequest);
        if (claim.isReplay()) {
            return replayFulfillment(context, claim.resultReference());
        }
        if (fulfillmentRepository.findByDealId(dealId).isPresent()) {
            throw new FulfillmentExceptions.StartConflict();
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        UUID fulfillmentId = UUID.randomUUID();
        UUID milestoneId = UUID.randomUUID();
        Fulfillment fulfillment = Fulfillment.create(fulfillmentId, dealId, context.tenantId(),
                target.ratifiedPackageId(), now);
        Milestone milestone = Milestone.create(milestoneId, fulfillmentId, dealId,
                milestoneTitle(target), milestoneDescription(target), now);
        List<Milestone.MilestoneRuleReferenceRecord> ruleReferenceRecords = target.ruleReferences().stream()
                .map(ref -> new Milestone.MilestoneRuleReferenceRecord(milestoneId,
                        ref.ruleReference(), ref.category()))
                .toList();
        fulfillmentRepository.insert(fulfillment.toRecord());
        milestoneRepository.insert(milestone.toRecord(), ruleReferenceRecords);
        auditAppender.append(new AuditRecord(UUID.randomUUID(), context.tenantId(),
                context.authenticatedUserId(), context.activeLegalEntityId(),
                AUDIT_SUBJECT, fulfillmentId, "FULFILLMENT_STARTED", correlationId, null, now));
        idempotency.recordResult(claim, new IdempotencyResultReference(START_IDEMPOTENCY_RESULT, fulfillmentId));
        return toDetail(fulfillment, milestone, ruleReferenceRecords, target, context, now);
    }

    FulfillmentDetail getFulfillment(OperationContext context, UUID dealId) {
        requireOperation(context, RequestedOperation.FULFILLMENT_READ);
        FulfillmentSourcePorts.Target target = deals.findVisible(context, dealId)
                .orElseThrow(FulfillmentExceptions.DealNotFound::new);
        requireParticipant(context, target);
        Fulfillment.FulfillmentRecord record = fulfillmentRepository.findByDealId(dealId)
                .orElseThrow(FulfillmentExceptions.NotFound::new);
        return toDetail(record, target, context);
    }

    EvidenceUploadIntent createEvidenceUploadIntent(OperationContext context, UUID dealId,
            CreateEvidenceUploadIntentRequest request, UUID correlationId) {
        requireOperation(context, RequestedOperation.EVIDENCE_UPLOAD_INTENT_CREATE);
        requireUploadSize(request.sizeBytes());
        EvidenceType evidenceType = evidenceType(request.evidenceType());
        EvidenceMediaType mediaType = mediaType(request.mediaType());
        FulfillmentSourcePorts.Target preflight = deals.findVisible(context, dealId)
                .orElseThrow(FulfillmentExceptions.DealNotFound::new);
        requireSellerForUpload(context, preflight);
        requireUploadState(preflight);
        Fulfillment.FulfillmentRecord fulfillment = fulfillmentRepository.findByDealId(dealId)
                .orElseThrow(FulfillmentExceptions.UploadConflict::new);
        Milestone.MilestoneRecord milestone = milestoneRepository.findByFulfillmentId(fulfillment.id())
                .orElseThrow(FulfillmentExceptions.UploadConflict::new);
        if (milestone.status() != FulfillmentStatus.IN_PROGRESS
                && milestone.status() != FulfillmentStatus.EVIDENCE_REQUIRED) {
            throw new FulfillmentExceptions.UploadConflict();
        }
        String objectKey = FulfillmentObjectKeys.newKey();
        FulfillmentObjectStorage.DirectUpload directUpload = storage.createDirectUpload(
                objectKey, mediaType.value(), request.sizeBytes());
        return required(transactions.execute(status -> {
            Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            Milestone.MilestoneRecord lockedMilestone = milestoneRepository.findByFulfillmentIdForUpdate(fulfillment.id())
                    .orElseThrow(FulfillmentExceptions.UploadConflict::new);
            if (lockedMilestone.status() != FulfillmentStatus.IN_PROGRESS
                    && lockedMilestone.status() != FulfillmentStatus.EVIDENCE_REQUIRED) {
                throw new FulfillmentExceptions.UploadConflict();
            }
            Fulfillment.FulfillmentRecord lockedFulfillment = fulfillmentRepository.findByIdForUpdate(fulfillment.id())
                    .orElseThrow(FulfillmentExceptions.UploadConflict::new);
            Milestone milestoneAggregate = Milestone.rehydrate(lockedMilestone);
            Fulfillment fulfillmentAggregate = Fulfillment.rehydrate(lockedFulfillment);
            if (milestoneAggregate.status() == FulfillmentStatus.IN_PROGRESS) {
                milestoneAggregate.moveToEvidenceRequired(now);
            }
            if (fulfillmentAggregate.status() == FulfillmentStatus.IN_PROGRESS) {
                fulfillmentAggregate.moveToEvidenceRequired(now);
            }
            long previousMilestoneVersion = lockedMilestone.version();
            long previousFulfillmentVersion = lockedFulfillment.version();
            if (!milestoneRepository.update(milestoneAggregate.toRecord(), previousMilestoneVersion)
                    || !fulfillmentRepository.update(fulfillmentAggregate.toRecord(), previousFulfillmentVersion)) {
                throw new FulfillmentExceptions.UploadConflict();
            }
            EvidenceSubmission submission = EvidenceSubmission.createPending(UUID.randomUUID(), dealId,
                    lockedMilestone.id(), fulfillment.id(), evidenceType, mediaType, request.fileName(),
                    objectKey, request.sizeBytes(), request.sha256().toLowerCase(),
                    directUpload.expiresAt(), now);
            evidenceRepository.insert(submission.toRecord());
            auditAppender.append(new AuditRecord(UUID.randomUUID(), context.tenantId(),
                    context.authenticatedUserId(), context.activeLegalEntityId(),
                    AUDIT_SUBJECT, submission.id(), "EVIDENCE_UPLOAD_INTENT_CREATED",
                    correlationId, null, now));
            return new EvidenceUploadIntent(toProjection(submission, false, now), directUpload.url().toString(),
                    directUpload.headers(), directUpload.expiresAt());
        }));
    }

    EvidenceSubmissionProjection finalizeEvidenceUpload(OperationContext context, UUID dealId,
            UUID submissionId, FinalizeEvidenceUploadRequest request, UUID idempotencyKey,
            UUID correlationId) {
        requireOperation(context, RequestedOperation.EVIDENCE_UPLOAD_FINALIZE);
        requireUploadSize(request.sizeBytes());
        IdempotencyRequest idempotencyRequest = finalizeIdempotencyRequest(context, submissionId, request, idempotencyKey);
        IdempotencyResultReference completed = idempotency.findCompleted(idempotencyRequest).orElse(null);
        if (completed != null) {
            return replaySubmitted(completed);
        }
        EvidenceSubmission.EvidenceSubmissionRecord preflight = evidenceRepository.findById(submissionId)
                .orElseThrow(FulfillmentExceptions.NotFound::new);
        if (!preflight.dealId().equals(dealId)) {
            throw new FulfillmentExceptions.NotFound();
        }
        FulfillmentSourcePorts.Target preflightDeal = deals.findVisible(context, preflight.dealId())
                .orElseThrow(FulfillmentExceptions.NotFound::new);
        requireSellerForUpload(context, preflightDeal);
        requireUploadState(preflightDeal);
        FulfillmentObjectStorage.VerifiedObject verified = storage.verify(preflight.objectKey());
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        return required(transactions.execute(status -> finalizeInTransaction(context, dealId, submissionId,
                request, idempotencyRequest, correlationId, verified, now)));
    }

    private EvidenceSubmissionProjection finalizeInTransaction(OperationContext context, UUID dealId,
            UUID submissionId, FinalizeEvidenceUploadRequest request,
            IdempotencyRequest idempotencyRequest, UUID correlationId,
            FulfillmentObjectStorage.VerifiedObject verified, Instant now) {
        FulfillmentSourcePorts.Target target = deals.lockVisibleForStart(context, dealId)
                .orElseThrow(FulfillmentExceptions.DealNotFound::new);
        requireSellerForUpload(context, target);
        requireUploadState(target);
        Fulfillment.FulfillmentRecord fulfillmentRecord = fulfillmentRepository.findByDealIdForUpdate(dealId)
                .orElseThrow(FulfillmentExceptions.FinalizeConflict::new);
        Milestone.MilestoneRecord milestoneRecord = milestoneRepository.findByFulfillmentIdForUpdate(fulfillmentRecord.id())
                .orElseThrow(FulfillmentExceptions.FinalizeConflict::new);
        EvidenceSubmission.EvidenceSubmissionRecord current = evidenceRepository.findByIdForUpdate(submissionId)
                .orElseThrow(FulfillmentExceptions.NotFound::new);
        if (!current.dealId().equals(dealId) || !current.milestoneId().equals(milestoneRecord.id())) {
            throw new FulfillmentExceptions.NotFound();
        }
        IdempotencyClaim claim = idempotency.claim(idempotencyRequest);
        if (claim.isReplay()) {
            return replaySubmitted(claim.resultReference());
        }
        EvidenceSubmission submission = EvidenceSubmission.rehydrate(current);
        if (submission.status() != EvidenceSubmissionStatus.PENDING_UPLOAD) {
            throw new FulfillmentExceptions.FinalizeConflict();
        }
        if (!now.isBefore(submission.uploadExpiresAt())) {
            throw new FulfillmentExceptions.FinalizeConflict();
        }
        if (verified.sizeBytes() != request.sizeBytes()
                || !verified.sha256().equals(request.sha256().toLowerCase())) {
            throw new FulfillmentExceptions.FinalizeConflict();
        }
        if (!submission.mediaType().value().equalsIgnoreCase(verified.mediaType())) {
            throw new FulfillmentExceptions.FinalizeConflict();
        }
        if (evidenceRepository.findCurrentSubmittedByMilestoneId(milestoneRecord.id()).isPresent()) {
            throw new FulfillmentExceptions.FinalizeConflict();
        }
        long previousSubmissionVersion = submission.version();
        try {
            submission.markSubmitted(verified.sizeBytes(), verified.sha256(), verified.objectVersion(), now);
        } catch (IllegalArgumentException exception) {
            throw new FulfillmentExceptions.FinalizeConflict();
        }
        if (!evidenceRepository.update(submission.toRecord(), previousSubmissionVersion)) {
            throw new FulfillmentExceptions.FinalizeConflict();
        }
        Milestone milestone = Milestone.rehydrate(milestoneRecord);
        long previousMilestoneVersion = milestoneRecord.version();
        milestone.moveToReviewRequired(now);
        if (!milestoneRepository.update(milestone.toRecord(), previousMilestoneVersion)) {
            throw new FulfillmentExceptions.FinalizeConflict();
        }
        Fulfillment fulfillment = Fulfillment.rehydrate(fulfillmentRecord);
        long previousFulfillmentVersion = fulfillmentRecord.version();
        fulfillment.moveToReviewRequired(now);
        if (!fulfillmentRepository.update(fulfillment.toRecord(), previousFulfillmentVersion)) {
            throw new FulfillmentExceptions.FinalizeConflict();
        }
        auditAppender.append(new AuditRecord(UUID.randomUUID(), context.tenantId(),
                context.authenticatedUserId(), context.activeLegalEntityId(),
                AUDIT_SUBJECT, submissionId, "EVIDENCE_SUBMITTED", correlationId, null, now));
        idempotency.recordResult(claim, new IdempotencyResultReference(FINALIZE_IDEMPOTENCY_RESULT, submissionId));
        return toProjection(submission, true, now);
    }

    EvidenceDownloadLink createDownloadLink(OperationContext context, UUID dealId, UUID submissionId) {
        requireOperation(context, RequestedOperation.EVIDENCE_DOWNLOAD_LINK_CREATE);
        EvidenceSubmission.EvidenceSubmissionRecord record = evidenceRepository.findById(submissionId)
                .orElseThrow(FulfillmentExceptions.NotFound::new);
        if (!record.dealId().equals(dealId)) {
            throw new FulfillmentExceptions.NotFound();
        }
        FulfillmentSourcePorts.Target target = deals.findVisible(context, record.dealId())
                .orElseThrow(FulfillmentExceptions.NotFound::new);
        requireParticipant(context, target);
        EvidenceSubmission submission = EvidenceSubmission.rehydrate(record);
        if (submission.status() == EvidenceSubmissionStatus.PENDING_UPLOAD
                || submission.objectVersion() == null) {
            throw new FulfillmentExceptions.DownloadNotAvailable();
        }
        FulfillmentObjectStorage.DirectDownload download = storage.createDirectDownload(
                submission.objectKey(), submission.objectVersion());
        return new EvidenceDownloadLink(submission.id(), submission.objectVersion(),
                download.url().toString(), download.expiresAt());
    }

    EvidenceSubmissionProjection acceptEvidence(OperationContext context, UUID dealId, UUID submissionId,
            AcceptEvidenceRequest request, UUID idempotencyKey, UUID correlationId) {
        requireOperation(context, RequestedOperation.EVIDENCE_ACCEPT);
        IdempotencyRequest idempotencyRequest = acceptIdempotencyRequest(context, submissionId, request, idempotencyKey);
        IdempotencyResultReference completed = idempotency.findCompleted(idempotencyRequest).orElse(null);
        if (completed != null) {
            return replayAccepted(completed);
        }
        return required(transactions.execute(status -> reviewInTransaction(context, dealId, submissionId,
                request.expectedVersion(), request.expectedEvidenceVersion(), idempotencyRequest,
                correlationId, true, null)));
    }

    EvidenceSubmissionProjection rejectEvidence(OperationContext context, UUID dealId, UUID submissionId,
            RejectEvidenceRequest request, UUID idempotencyKey, UUID correlationId) {
        requireOperation(context, RequestedOperation.EVIDENCE_REJECT);
        IdempotencyRequest idempotencyRequest = rejectIdempotencyRequest(context, submissionId, request, idempotencyKey);
        IdempotencyResultReference completed = idempotency.findCompleted(idempotencyRequest).orElse(null);
        if (completed != null) {
            return replayRejected(completed);
        }
        return required(transactions.execute(status -> reviewInTransaction(context, dealId, submissionId,
                request.expectedVersion(), request.expectedEvidenceVersion(), idempotencyRequest,
                correlationId, false, request.reason())));
    }

    private EvidenceSubmissionProjection reviewInTransaction(OperationContext context, UUID dealId,
            UUID submissionId, long expectedDealVersion, long expectedEvidenceVersion,
            IdempotencyRequest idempotencyRequest, UUID correlationId, boolean accept, String reason) {
        FulfillmentSourcePorts.Target target = deals.lockVisibleForStart(context, dealId)
                .orElseThrow(FulfillmentExceptions.DealNotFound::new);
        requireBuyerAdmin(context, target);
        if (!"ACTIVE".equals(target.status()) || !"FUNDED".equals(target.fundingStatus())) {
            throw new FulfillmentExceptions.ReviewConflict();
        }
        if (target.version() != expectedDealVersion) {
            throw new FulfillmentExceptions.ReviewConflict();
        }
        Fulfillment.FulfillmentRecord fulfillmentRecord = fulfillmentRepository.findByDealIdForUpdate(dealId)
                .orElseThrow(FulfillmentExceptions.ReviewConflict::new);
        if (fulfillmentRecord.status() == FulfillmentStatus.COMPLETED) {
            throw new FulfillmentExceptions.ReviewConflict();
        }
        Milestone.MilestoneRecord milestoneRecord = milestoneRepository.findByFulfillmentIdForUpdate(fulfillmentRecord.id())
                .orElseThrow(FulfillmentExceptions.ReviewConflict::new);
        EvidenceSubmission.EvidenceSubmissionRecord currentRecord = evidenceRepository.findByIdForUpdate(submissionId)
                .orElseThrow(FulfillmentExceptions.NotFound::new);
        if (!currentRecord.dealId().equals(dealId)
                || !currentRecord.milestoneId().equals(milestoneRecord.id())) {
            throw new FulfillmentExceptions.NotFound();
        }
        EvidenceSubmission currentSubmitted = evidenceRepository.findCurrentSubmittedByMilestoneId(milestoneRecord.id())
                .map(EvidenceSubmission::rehydrate)
                .orElseThrow(FulfillmentExceptions.ReviewConflict::new);
        if (!currentSubmitted.id().equals(submissionId)) {
            throw new FulfillmentExceptions.ReviewConflict();
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        IdempotencyClaim claim = idempotency.claim(idempotencyRequest);
        if (claim.isReplay()) {
            return accept ? replayAccepted(claim.resultReference()) : replayRejected(claim.resultReference());
        }
        EvidenceSubmission submission = EvidenceSubmission.rehydrate(currentRecord);
        if (submission.status() != EvidenceSubmissionStatus.SUBMITTED) {
            throw new FulfillmentExceptions.ReviewConflict();
        }
        if (submission.version() != expectedEvidenceVersion) {
            throw new FulfillmentExceptions.ReviewConflict();
        }
        long previousSubmissionVersion = submission.version();
        if (accept) {
            submission.markAccepted(now);
        } else {
            submission.markRejected(reason, now);
        }
        if (!evidenceRepository.update(submission.toRecord(), previousSubmissionVersion)) {
            throw new FulfillmentExceptions.ReviewConflict();
        }
        Milestone milestone = Milestone.rehydrate(milestoneRecord);
        long previousMilestoneVersion = milestoneRecord.version();
        if (accept) {
            milestone.moveToCompleted(now);
        } else {
            milestone.returnToEvidenceRequired(now);
        }
        if (!milestoneRepository.update(milestone.toRecord(), previousMilestoneVersion)) {
            throw new FulfillmentExceptions.ReviewConflict();
        }
        Fulfillment fulfillment = Fulfillment.rehydrate(fulfillmentRecord);
        long previousFulfillmentVersion = fulfillmentRecord.version();
        if (accept) {
            fulfillment.moveToCompleted(now);
        } else {
            fulfillment.returnToEvidenceRequired(now);
        }
        if (!fulfillmentRepository.update(fulfillment.toRecord(), previousFulfillmentVersion)) {
            throw new FulfillmentExceptions.ReviewConflict();
        }
        String action = accept ? "EVIDENCE_ACCEPTED" : "EVIDENCE_REJECTED";
        String resultType = accept ? ACCEPT_IDEMPOTENCY_RESULT : REJECT_IDEMPOTENCY_RESULT;
        auditAppender.append(new AuditRecord(UUID.randomUUID(), context.tenantId(),
                context.authenticatedUserId(), context.activeLegalEntityId(),
                AUDIT_SUBJECT, submissionId, action, correlationId, null, now));
        idempotency.recordResult(claim, new IdempotencyResultReference(resultType, submissionId));
        return toProjection(submission, true, now);
    }

    private FulfillmentDetail replayFulfillment(OperationContext context, IdempotencyResultReference reference) {
        if (!START_IDEMPOTENCY_RESULT.equals(reference.type())) {
            throw new IllegalStateException("Unexpected idempotency result type");
        }
        Fulfillment.FulfillmentRecord record = fulfillmentRepository.findById(reference.id())
                .orElseThrow(FulfillmentExceptions.StartConflict::new);
        FulfillmentSourcePorts.Target target = deals.findVisible(context, record.dealId())
                .orElseThrow(FulfillmentExceptions.DealNotFound::new);
        return toDetail(record, target, context);
    }

    private EvidenceSubmissionProjection replaySubmitted(IdempotencyResultReference reference) {
        if (!FINALIZE_IDEMPOTENCY_RESULT.equals(reference.type())) {
            throw new IllegalStateException("Unexpected idempotency result type");
        }
        EvidenceSubmission submission = evidenceRepository.findById(reference.id())
                .map(EvidenceSubmission::rehydrate)
                .filter(result -> result.status() == EvidenceSubmissionStatus.SUBMITTED)
                .orElseThrow(FulfillmentExceptions.FinalizeConflict::new);
        return toProjection(submission, true, clock.instant());
    }

    private EvidenceSubmissionProjection replayAccepted(IdempotencyResultReference reference) {
        if (!ACCEPT_IDEMPOTENCY_RESULT.equals(reference.type())) {
            throw new IllegalStateException("Unexpected idempotency result type");
        }
        EvidenceSubmission submission = evidenceRepository.findById(reference.id())
                .map(EvidenceSubmission::rehydrate)
                .filter(result -> result.status() == EvidenceSubmissionStatus.ACCEPTED)
                .orElseThrow(FulfillmentExceptions.ReviewConflict::new);
        return toProjection(submission, true, clock.instant());
    }

    private EvidenceSubmissionProjection replayRejected(IdempotencyResultReference reference) {
        if (!REJECT_IDEMPOTENCY_RESULT.equals(reference.type())) {
            throw new IllegalStateException("Unexpected idempotency result type");
        }
        EvidenceSubmission submission = evidenceRepository.findById(reference.id())
                .map(EvidenceSubmission::rehydrate)
                .filter(result -> result.status() == EvidenceSubmissionStatus.REJECTED)
                .orElseThrow(FulfillmentExceptions.ReviewConflict::new);
        return toProjection(submission, true, clock.instant());
    }

    private FulfillmentDetail toDetail(Fulfillment.FulfillmentRecord record,
            FulfillmentSourcePorts.Target target, OperationContext context) {
        Fulfillment fulfillment = Fulfillment.rehydrate(record);
        Milestone.MilestoneRecord milestoneRecord = milestoneRepository.findByFulfillmentId(record.id())
                .orElseThrow(() -> new IllegalStateException("Milestone is unavailable"));
        Milestone milestone = Milestone.rehydrate(milestoneRecord);
        List<Milestone.MilestoneRuleReferenceRecord> ruleReferenceRecords =
                milestoneRepository.findRuleReferencesByMilestoneId(milestone.id());
        Instant now = clock.instant();
        return toDetail(fulfillment, milestone, ruleReferenceRecords, target, context, now);
    }

    private FulfillmentDetail toDetail(Fulfillment fulfillment, Milestone milestone,
            List<Milestone.MilestoneRuleReferenceRecord> ruleReferences,
            FulfillmentSourcePorts.Target target, OperationContext context, Instant now) {
        List<EvidenceSubmission.EvidenceSubmissionRecord> submissionRecords =
                evidenceRepository.findByMilestoneId(milestone.id());
        List<EvidenceSubmissionProjection> history = submissionRecords.stream()
                .map(EvidenceSubmission::rehydrate)
                .map(submission -> toProjection(submission, canDownload(context, submission), now))
                .toList();
        EvidenceSubmissionProjection current = submissionRecords.stream()
                .map(EvidenceSubmission::rehydrate)
                .filter(submission -> isCurrentEvidence(submission, now))
                .findFirst()
                .map(submission -> toProjection(submission, canDownload(context, submission), now))
                .orElse(null);
        boolean seller = context.activeLegalEntityId().equals(target.sellerLegalEntityId());
        boolean buyerAdmin = context.activeLegalEntityRole() == LegalEntityRole.ADMIN
                && context.activeLegalEntityId().equals(target.buyerLegalEntityId());
        boolean canStart = false;
        boolean canAccept = buyerAdmin && current != null
                && current.status() == EvidenceSubmissionStatus.SUBMITTED;
        boolean canReject = canAccept;
        boolean canUpload = seller && (milestone.status() == FulfillmentStatus.IN_PROGRESS
                || milestone.status() == FulfillmentStatus.EVIDENCE_REQUIRED)
                && current == null;
        FulfillmentAvailableActions fulfillmentActions = new FulfillmentAvailableActions(canStart, canAccept, canReject);
        MilestoneAvailableActions milestoneActions = new MilestoneAvailableActions(canUpload);
        FulfillmentMilestoneProjection milestoneProjection = new FulfillmentMilestoneProjection(
                milestone.id(), milestone.title(), milestone.description(),
                ruleReferences.stream()
                        .map(ref -> new MilestoneRuleReference(ref.ruleReference(), ref.category()))
                        .toList(),
                milestoneActions, milestone.version());
        return new FulfillmentDetail(fulfillment.id(), fulfillment.dealId(), fulfillment.status(),
                fulfillment.sourcePackageId(), milestoneProjection, current, history,
                fulfillmentActions, fulfillment.version(), fulfillment.createdAt(), fulfillment.updatedAt());
    }

    private boolean isCurrentEvidence(EvidenceSubmission submission, Instant now) {
        if (submission.status() == EvidenceSubmissionStatus.SUBMITTED) {
            return true;
        }
        if (submission.status() == EvidenceSubmissionStatus.PENDING_UPLOAD) {
            return now.isBefore(submission.uploadExpiresAt());
        }
        return false;
    }

    private EvidenceSubmissionProjection toProjection(EvidenceSubmission submission, boolean canDownload, Instant now) {
        return new EvidenceSubmissionProjection(
                submission.id(), submission.dealId(), submission.milestoneId(),
                submission.evidenceType(), submission.mediaType(), submission.fileName(),
                submission.status(), submission.clientSizeBytes(), submission.clientSha256(),
                submission.verifiedSizeBytes(), submission.verifiedSha256(), submission.objectVersion(),
                submission.createdAt(), submission.submittedAt(), submission.acceptedAt(),
                submission.rejectedAt(), submission.rejectionReason(),
                new EvidenceAvailableActions(canDownload), submission.version());
    }

    private boolean canDownload(OperationContext context, EvidenceSubmission submission) {
        return submission.status() != EvidenceSubmissionStatus.PENDING_UPLOAD
                && submission.objectVersion() != null;
    }

    private String milestoneTitle(FulfillmentSourcePorts.Target target) {
        // V1 default: the ratified package does not expose a canonical milestone title.
        return "Primary milestone";
    }

    private String milestoneDescription(FulfillmentSourcePorts.Target target) {
        return null;
    }

    private void requireSeller(OperationContext context, FulfillmentSourcePorts.Target target) {
        if (!context.activeLegalEntityId().equals(target.sellerLegalEntityId())) {
            throw new FulfillmentExceptions.StartForbidden();
        }
    }

    private void requireSellerForUpload(OperationContext context, FulfillmentSourcePorts.Target target) {
        if (!context.activeLegalEntityId().equals(target.sellerLegalEntityId())) {
            throw new FulfillmentExceptions.UploadForbidden();
        }
    }

    private void requireBuyerAdmin(OperationContext context, FulfillmentSourcePorts.Target target) {
        if (context.activeLegalEntityRole() != LegalEntityRole.ADMIN
                || !context.activeLegalEntityId().equals(target.buyerLegalEntityId())) {
            throw new FulfillmentExceptions.ReviewForbidden();
        }
    }

    private void requireParticipant(OperationContext context, FulfillmentSourcePorts.Target target) {
        UUID active = context.activeLegalEntityId();
        if (!active.equals(target.buyerLegalEntityId()) && !active.equals(target.sellerLegalEntityId())) {
            throw new FulfillmentExceptions.DealNotFound();
        }
    }

    private void requireUploadState(FulfillmentSourcePorts.Target target) {
        if (!"ACTIVE".equals(target.status()) || !"FUNDED".equals(target.fundingStatus())) {
            throw new FulfillmentExceptions.UploadConflict();
        }
    }

    private void requireUploadSize(long sizeBytes) {
        if (sizeBytes <= 0 || sizeBytes > maxUploadSizeBytes) {
            throw new FulfillmentExceptions.Validation("sizeBytes");
        }
    }

    private EvidenceType evidenceType(String value) {
        try {
            return EvidenceType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new FulfillmentExceptions.MalformedRequest();
        }
    }

    private EvidenceMediaType mediaType(String value) {
        try {
            return EvidenceMediaType.fromValue(value);
        } catch (IllegalArgumentException exception) {
            throw new FulfillmentExceptions.MalformedRequest();
        }
    }

    private void requireOperation(OperationContext context, RequestedOperation expected) {
        if (context.requestedOperation() != expected) {
            throw new IllegalArgumentException("Operation context does not match fulfillment use case");
        }
    }

    private IdempotencyRequest startIdempotencyRequest(OperationContext context, UUID dealId,
            StartFulfillmentRequest request, UUID key) {
        Map<String, Object> canonicalRequest = new LinkedHashMap<>();
        canonicalRequest.put("actorEntity", context.activeLegalEntityId().toString());
        canonicalRequest.put("dealId", dealId.toString());
        canonicalRequest.put("expectedVersion", request.expectedVersion());
        return new IdempotencyRequest(context.authenticatedUserId(), context.tenantId(),
                START_IDEMPOTENCY_OPERATION, key, canonicalHash(canonicalRequest));
    }

    private IdempotencyRequest finalizeIdempotencyRequest(OperationContext context, UUID submissionId,
            FinalizeEvidenceUploadRequest request, UUID key) {
        Map<String, Object> canonicalRequest = new LinkedHashMap<>();
        canonicalRequest.put("actorEntity", context.activeLegalEntityId().toString());
        canonicalRequest.put("submissionId", submissionId.toString());
        canonicalRequest.put("sizeBytes", request.sizeBytes());
        canonicalRequest.put("sha256", request.sha256().toLowerCase());
        return new IdempotencyRequest(context.authenticatedUserId(), context.tenantId(),
                FINALIZE_IDEMPOTENCY_OPERATION, key, canonicalHash(canonicalRequest));
    }

    private IdempotencyRequest acceptIdempotencyRequest(OperationContext context, UUID submissionId,
            AcceptEvidenceRequest request, UUID key) {
        Map<String, Object> canonicalRequest = new LinkedHashMap<>();
        canonicalRequest.put("actorEntity", context.activeLegalEntityId().toString());
        canonicalRequest.put("submissionId", submissionId.toString());
        canonicalRequest.put("expectedVersion", request.expectedVersion());
        canonicalRequest.put("expectedEvidenceVersion", request.expectedEvidenceVersion());
        return new IdempotencyRequest(context.authenticatedUserId(), context.tenantId(),
                ACCEPT_IDEMPOTENCY_OPERATION, key, canonicalHash(canonicalRequest));
    }

    private IdempotencyRequest rejectIdempotencyRequest(OperationContext context, UUID submissionId,
            RejectEvidenceRequest request, UUID key) {
        Map<String, Object> canonicalRequest = new LinkedHashMap<>();
        canonicalRequest.put("actorEntity", context.activeLegalEntityId().toString());
        canonicalRequest.put("submissionId", submissionId.toString());
        canonicalRequest.put("expectedVersion", request.expectedVersion());
        canonicalRequest.put("expectedEvidenceVersion", request.expectedEvidenceVersion());
        canonicalRequest.put("reason", request.reason());
        return new IdempotencyRequest(context.authenticatedUserId(), context.tenantId(),
                REJECT_IDEMPOTENCY_OPERATION, key, canonicalHash(canonicalRequest));
    }

    private String canonicalHash(Map<String, Object> canonicalRequest) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder builder = new StringBuilder();
            canonicalRequest.forEach((k, v) -> builder.append(k).append("=").append(v).append("\n"));
            return HexFormat.of().formatHex(digest.digest(builder.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static <T> T required(T value) {
        if (value == null) {
            throw new IllegalStateException("Transaction returned no result");
        }
        return value;
    }
}
