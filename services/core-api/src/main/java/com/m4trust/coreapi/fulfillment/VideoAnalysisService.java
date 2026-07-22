package com.m4trust.coreapi.fulfillment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

import com.m4trust.coreapi.api.ApiErrorCode;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** Coordinates per-evidence video analysis request and read projection. */
@Service
class VideoAnalysisService {

    private static final String IDEMPOTENCY_OPERATION = "VIDEO_ANALYSIS_REQUEST";
    private static final String IDEMPOTENCY_RESULT = "VIDEO_ANALYSIS";
    private static final String AUDIT_SUBJECT = "VIDEO_ANALYSIS_JOB";
    private static final String AUDIT_ACTION = "VIDEO_ANALYSIS_REQUESTED";

    private final VideoAnalysisRepository repository;
    private final FulfillmentSourcePorts.DealTarget deals;
    private final FulfillmentRepository fulfillmentRepository;
    private final MilestoneRepository milestoneRepository;
    private final EvidenceSubmissionRepository evidenceRepository;
    private final VideoAnalysisEvidenceInputPort evidenceInputs;
    private final IdempotencyService idempotency;
    private final VideoAnalysisCommandEnqueuePort commandEnqueue;
    private final AuditAppendPort auditAppender;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final VideoAnalysisRequestedEventFactory eventFactory;

    VideoAnalysisService(VideoAnalysisRepository repository, FulfillmentSourcePorts.DealTarget deals,
            FulfillmentRepository fulfillmentRepository, MilestoneRepository milestoneRepository,
            EvidenceSubmissionRepository evidenceRepository, VideoAnalysisEvidenceInputPort evidenceInputs,
            IdempotencyService idempotency, VideoAnalysisCommandEnqueuePort commandEnqueue,
            AuditAppendPort auditAppender,
            TransactionTemplate transactions, Clock clock, ObjectMapper objectMapper,
            @Value("${app.ai.producer-version:1.0.0}") String producerVersion) {
        this.repository = repository;
        this.deals = deals;
        this.fulfillmentRepository = fulfillmentRepository;
        this.milestoneRepository = milestoneRepository;
        this.evidenceRepository = evidenceRepository;
        this.evidenceInputs = evidenceInputs;
        this.idempotency = idempotency;
        this.commandEnqueue = commandEnqueue;
        this.auditAppender = auditAppender;
        this.transactions = transactions;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.eventFactory = new VideoAnalysisRequestedEventFactory(objectMapper, producerVersion);
    }

    VideoAnalysisDetail get(OperationContext context, UUID dealId, UUID evidenceSubmissionId) {
        requireOperation(context, RequestedOperation.VIDEO_ANALYSIS_READ);
        ResolvedEvidence resolved = resolveEligibleEvidence(context, dealId, evidenceSubmissionId);
        return projection(resolved, context);
    }

    VideoAnalysisDetail request(OperationContext context, UUID dealId, UUID evidenceSubmissionId,
            RequestVideoAnalysisRequest request, UUID idempotencyKey, UUID correlationId) {
        requireOperation(context, RequestedOperation.VIDEO_ANALYSIS_REQUEST);
        IdempotencyRequest idempotencyRequest = idempotencyRequest(context, dealId, evidenceSubmissionId,
                request, idempotencyKey);
        IdempotencyResultReference completed = idempotency.findCompleted(idempotencyRequest).orElse(null);
        if (completed != null) {
            return projectionForJob(context, completed);
        }

        FulfillmentSourcePorts.Target preflightDeal = deals.findVisible(context, dealId)
                .orElseThrow(FulfillmentExceptions.DealNotFound::new);
        requireBuyerAdmin(context, preflightDeal);
        ResolvedEvidence preflightEvidence = resolveEligibleEvidence(context, dealId, evidenceSubmissionId);
        requireRequestEligible(preflightEvidence, request.expectedEvidenceVersion());
        if (repository.hasQueuedJob(evidenceSubmissionId)) {
            throw activeJobConflict();
        }
        if (repository.hasSuccessfulJob(evidenceSubmissionId)) {
            throw alreadyCompletedConflict();
        }
        VideoAnalysisEvidenceInputPort.VerifiedSnapshot preflightSnapshot = preflightEvidence.snapshot();
        FulfillmentObjectStorage.DirectDownload download = evidenceInputs.mintVersionPinnedDownload(preflightSnapshot);

        return required(transactions.execute(status -> requestInTransaction(context, dealId, evidenceSubmissionId,
                idempotencyKey, correlationId, idempotencyRequest, request, preflightSnapshot, download)));
    }

    private VideoAnalysisDetail requestInTransaction(OperationContext context, UUID dealId,
            UUID evidenceSubmissionId, UUID idempotencyKey, UUID correlationId,
            IdempotencyRequest idempotencyRequest, RequestVideoAnalysisRequest request,
            VideoAnalysisEvidenceInputPort.VerifiedSnapshot preflightSnapshot,
            FulfillmentObjectStorage.DirectDownload download) {
        IdempotencyClaim claim = idempotency.claim(idempotencyRequest);
        if (claim.isReplay()) {
            return projectionForJob(context, claim.resultReference());
        }

        FulfillmentSourcePorts.Target target = deals.lockVisibleForStart(context, dealId)
                .orElseThrow(FulfillmentExceptions.DealNotFound::new);
        requireBuyerAdmin(context, target);
        ResolvedEvidence lockedEvidence = resolveEligibleEvidenceLocked(target, dealId, evidenceSubmissionId);
        requireRequestEligible(lockedEvidence, request.expectedEvidenceVersion());
        VideoAnalysisEvidenceInputPort.VerifiedSnapshot lockedSnapshot = lockedEvidence.snapshot();
        if (!sameImmutableSnapshot(preflightSnapshot, lockedSnapshot)) {
            throw notEligibleConflict();
        }
        if (repository.hasQueuedJob(evidenceSubmissionId)) {
            throw activeJobConflict();
        }
        if (repository.hasSuccessfulJob(evidenceSubmissionId)) {
            throw alreadyCompletedConflict();
        }

        Instant requestedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        UUID jobId = UUID.randomUUID();
        UUID predecessorJobId = repository.findLatestByEvidenceId(evidenceSubmissionId)
                .filter(job -> job.status() == VideoAnalysisJobStatus.FAILED)
                .map(VideoAnalysisRepository.VideoAnalysisJobRecord::id)
                .orElse(null);
        VideoAnalysisRepository.VideoAnalysisJobRecord queuedJob =
                VideoAnalysisRepository.VideoAnalysisJobRecord.queued(jobId, target.tenantId(), dealId,
                        lockedEvidence.fulfillmentId(), lockedEvidence.milestoneId(), evidenceSubmissionId,
                        lockedSnapshot.objectVersion(), lockedSnapshot.verifiedSha256(),
                        lockedSnapshot.verifiedSizeBytes(), lockedSnapshot.mediaType().value(),
                        lockedSnapshot.fileName(), predecessorJobId, requestedAt);
        try {
            repository.insertQueued(queuedJob);
        } catch (DuplicateKeyException exception) {
            throw activeJobConflict();
        }

        String event = eventFactory.create(UUID.randomUUID(), jobId, target.tenantId(), dealId, correlationId,
                idempotencyKey, lockedSnapshot, download, requestedAt);
        commandEnqueue.enqueueRequested(event);
        auditAppender.append(new AuditRecord(UUID.randomUUID(), context.tenantId(),
                context.authenticatedUserId(), context.activeLegalEntityId(), AUDIT_SUBJECT, jobId,
                AUDIT_ACTION, correlationId, null, requestedAt));
        idempotency.recordResult(claim, new IdempotencyResultReference(IDEMPOTENCY_RESULT, jobId));
        return projection(lockedEvidence, context, queuedJob);
    }

    private VideoAnalysisDetail projection(ResolvedEvidence resolved, OperationContext context) {
        return repository.findLatestByEvidenceId(resolved.evidenceSubmissionId())
                .map(job -> projection(resolved, context, job))
                .orElseGet(() -> notRequested(resolved, context));
    }

    private VideoAnalysisDetail projection(ResolvedEvidence resolved, OperationContext context,
            VideoAnalysisRepository.VideoAnalysisJobRecord job) {
        VideoAnalysisFailureSummary failure = job.failureCode() == null ? null
                : new VideoAnalysisFailureSummary(job.failureCode(),
                        Boolean.TRUE.equals(job.retryRecommended()));
        Object result = repository.findResultByJobId(job.id()).map(this::readPublicResult).orElse(null);
        return new VideoAnalysisDetail(resolved.evidenceSubmissionId(), job.id(), publicStatus(job),
                job.requestedAt(), job.completedAt(), job.failedAt(), failure, result,
                new VideoAnalysisAvailableActions(canRequest(resolved, context, job)));
    }

    private Object readPublicResult(String serialized) {
        try {
            var canonical = objectMapper.readTree(serialized);
            var result = canonical.has("result") ? canonical.get("result") : canonical;
            var warnings = canonical.has("warnings")
                    ? canonical.get("warnings")
                    : canonical.path("warnings");
            if (!warnings.isArray()) {
                warnings = objectMapper.createArrayNode();
            }
            return java.util.Map.of(
                    "durationMs", result.get("durationMs").numberValue(),
                    "observations", objectMapper.treeToValue(result.get("observations"), Object.class),
                    "anomalies", objectMapper.treeToValue(result.get("anomalies"), Object.class),
                    "summary", objectMapper.treeToValue(result.get("summary"), Object.class),
                    "warnings", objectMapper.treeToValue(warnings, Object.class));
        } catch (Exception exception) {
            throw new IllegalStateException("Stored canonical result is invalid", exception);
        }
    }

    private VideoAnalysisDetail notRequested(ResolvedEvidence resolved, OperationContext context) {
        return new VideoAnalysisDetail(resolved.evidenceSubmissionId(), null,
                VideoAnalysisPublicStatus.NOT_REQUESTED, null, null, null, null, null,
                new VideoAnalysisAvailableActions(canRequest(resolved, context, null)));
    }

    private VideoAnalysisDetail projectionForJob(OperationContext context,
            IdempotencyResultReference reference) {
        if (!IDEMPOTENCY_RESULT.equals(reference.type())) {
            throw new IllegalStateException("Unexpected video analysis idempotency result type");
        }
        VideoAnalysisRepository.VideoAnalysisJobRecord job = repository.findById(reference.id())
                .orElseThrow(() -> new IllegalStateException("Idempotency result job is unavailable"));
        ResolvedEvidence resolved = resolveEligibleEvidence(context, job.dealId(), job.evidenceSubmissionId());
        return projection(resolved, context, job);
    }

    private VideoAnalysisPublicStatus publicStatus(VideoAnalysisRepository.VideoAnalysisJobRecord job) {
        return switch (job.status()) {
            case QUEUED -> VideoAnalysisPublicStatus.QUEUED;
            case RESULT_AVAILABLE -> VideoAnalysisPublicStatus.RESULT_AVAILABLE;
            case FAILED -> VideoAnalysisPublicStatus.FAILED;
        };
    }

    private boolean canRequest(ResolvedEvidence resolved, OperationContext context,
            VideoAnalysisRepository.VideoAnalysisJobRecord latestJob) {
        if (context.activeLegalEntityRole() != LegalEntityRole.ADMIN
                || !context.activeLegalEntityId().equals(resolved.buyerLegalEntityId())) {
            return false;
        }
        if (latestJob != null) {
            if (latestJob.status() == VideoAnalysisJobStatus.QUEUED
                    || latestJob.status() == VideoAnalysisJobStatus.RESULT_AVAILABLE) {
                return false;
            }
        }
        if (repository.hasQueuedJob(resolved.evidenceSubmissionId())
                || repository.hasSuccessfulJob(resolved.evidenceSubmissionId())) {
            return false;
        }
        return isRequestEligible(resolved);
    }

    private ResolvedEvidence resolveEligibleEvidence(OperationContext context, UUID dealId,
            UUID evidenceSubmissionId) {
        FulfillmentSourcePorts.Target target = deals.findVisible(context, dealId)
                .orElseThrow(FulfillmentExceptions.DealNotFound::new);
        return resolveEligibleEvidenceRecord(target, evidenceSubmissionId);
    }

    private ResolvedEvidence resolveEligibleEvidenceLocked(FulfillmentSourcePorts.Target target, UUID dealId,
            UUID evidenceSubmissionId) {
        Fulfillment.FulfillmentRecord fulfillmentRecord = fulfillmentRepository.findByDealIdForUpdate(dealId)
                .orElseThrow(FulfillmentExceptions.FulfillmentNotFound::new);
        Milestone.MilestoneRecord milestoneRecord = milestoneRepository
                .findByFulfillmentIdForUpdate(fulfillmentRecord.id())
                .orElseThrow(FulfillmentExceptions.FulfillmentNotFound::new);
        EvidenceSubmission.EvidenceSubmissionRecord evidenceRecord = evidenceRepository
                .findByIdForUpdate(evidenceSubmissionId)
                .orElseThrow(FulfillmentExceptions.EvidenceNotFound::new);
        return resolveEligibleEvidenceRecord(fulfillmentRecord, milestoneRecord, evidenceRecord, target);
    }

    private ResolvedEvidence resolveEligibleEvidenceRecord(FulfillmentSourcePorts.Target target,
            UUID evidenceSubmissionId) {
        Fulfillment.FulfillmentRecord fulfillmentRecord = fulfillmentRepository.findByDealId(target.dealId())
                .orElseThrow(FulfillmentExceptions.FulfillmentNotFound::new);
        Milestone.MilestoneRecord milestoneRecord = milestoneRepository.findByFulfillmentId(fulfillmentRecord.id())
                .orElseThrow(FulfillmentExceptions.FulfillmentNotFound::new);
        EvidenceSubmission.EvidenceSubmissionRecord evidenceRecord = evidenceRepository.findById(evidenceSubmissionId)
                .orElseThrow(FulfillmentExceptions.EvidenceNotFound::new);
        return resolveEligibleEvidenceRecord(fulfillmentRecord, milestoneRecord, evidenceRecord, target);
    }

    private ResolvedEvidence resolveEligibleEvidenceRecord(Fulfillment.FulfillmentRecord fulfillmentRecord,
            Milestone.MilestoneRecord milestoneRecord,
            EvidenceSubmission.EvidenceSubmissionRecord evidenceRecord,
            FulfillmentSourcePorts.Target target) {
        if (!evidenceRecord.dealId().equals(fulfillmentRecord.dealId())
                || !evidenceRecord.milestoneId().equals(milestoneRecord.id())) {
            throw new FulfillmentExceptions.EvidenceNotFound();
        }
        VideoAnalysisEvidenceInputPort.VerifiedSnapshot snapshot = evidenceInputs
                .findVerifiedSnapshot(evidenceRecord.id())
                .orElseThrow(FulfillmentExceptions.EvidenceNotFound::new);
        return new ResolvedEvidence(snapshot, fulfillmentRecord.id(), milestoneRecord.id(),
                target.buyerLegalEntityId(), target);
    }

    private void requireRequestEligible(ResolvedEvidence resolved, long expectedEvidenceVersion) {
        if (!isRequestEligible(resolved)) {
            throw notEligibleConflict();
        }
        if (resolved.snapshot().evidenceVersion() != expectedEvidenceVersion) {
            throw staleVersionConflict();
        }
    }

    private boolean isRequestEligible(ResolvedEvidence resolved) {
        if (resolved.target() == null) {
            return false;
        }
        FulfillmentSourcePorts.Target target = resolved.target();
        if (!"ACTIVE".equals(target.status()) || !"FUNDED".equals(target.fundingStatus())) {
            return false;
        }
        Fulfillment.FulfillmentRecord fulfillmentRecord = fulfillmentRepository.findByDealId(target.dealId())
                .orElse(null);
        if (fulfillmentRecord == null || fulfillmentRecord.status() != FulfillmentStatus.REVIEW_REQUIRED) {
            return false;
        }
        Milestone.MilestoneRecord milestoneRecord = milestoneRepository.findByFulfillmentId(fulfillmentRecord.id())
                .orElse(null);
        if (milestoneRecord == null || milestoneRecord.status() != FulfillmentStatus.REVIEW_REQUIRED) {
            return false;
        }
        EvidenceSubmission.EvidenceSubmissionRecord evidenceRecord = evidenceRepository
                .findById(resolved.evidenceSubmissionId())
                .orElse(null);
        if (evidenceRecord == null
                || EvidenceSubmission.rehydrate(evidenceRecord).status() != EvidenceSubmissionStatus.SUBMITTED) {
            return false;
        }
        return evidenceRepository.findCurrentSubmittedByMilestoneId(milestoneRecord.id())
                .map(current -> current.id().equals(resolved.evidenceSubmissionId()))
                .orElse(false);
    }

    private static boolean sameImmutableSnapshot(VideoAnalysisEvidenceInputPort.VerifiedSnapshot preflight,
            VideoAnalysisEvidenceInputPort.VerifiedSnapshot locked) {
        return preflight.evidenceSubmissionId().equals(locked.evidenceSubmissionId())
                && preflight.objectVersion().equals(locked.objectVersion())
                && preflight.verifiedSha256().equals(locked.verifiedSha256())
                && preflight.verifiedSizeBytes() == locked.verifiedSizeBytes()
                && preflight.mediaType() == locked.mediaType();
    }

    private static void requireBuyerAdmin(OperationContext context, FulfillmentSourcePorts.Target target) {
        if (context.activeLegalEntityRole() != LegalEntityRole.ADMIN
                || !context.activeLegalEntityId().equals(target.buyerLegalEntityId())) {
            throw new FulfillmentExceptions.RequestForbidden();
        }
    }

    private IdempotencyRequest idempotencyRequest(OperationContext context, UUID dealId, UUID evidenceSubmissionId,
            RequestVideoAnalysisRequest request, UUID key) {
        return new IdempotencyRequest(context.authenticatedUserId(), context.tenantId(), IDEMPOTENCY_OPERATION, key,
                canonicalHash(context.activeLegalEntityId(), dealId, evidenceSubmissionId,
                        request.expectedEvidenceVersion()));
    }

    private static String canonicalHash(UUID activeLegalEntityId, UUID dealId, UUID evidenceSubmissionId,
            long expectedEvidenceVersion) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] canonical = ("activeLegalEntityId=" + activeLegalEntityId + "\n"
                    + "dealId=" + dealId + "\n"
                    + "evidenceSubmissionId=" + evidenceSubmissionId + "\n"
                    + "expectedEvidenceVersion=" + expectedEvidenceVersion + "\n")
                    .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(digest.digest(canonical));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static FulfillmentExceptions.Conflict activeJobConflict() {
        return new FulfillmentExceptions.Conflict(ApiErrorCode.VIDEO_ANALYSIS_ACTIVE_JOB_EXISTS);
    }

    private static FulfillmentExceptions.Conflict alreadyCompletedConflict() {
        return new FulfillmentExceptions.Conflict(ApiErrorCode.VIDEO_ANALYSIS_ALREADY_COMPLETED);
    }

    private static FulfillmentExceptions.Conflict notEligibleConflict() {
        return new FulfillmentExceptions.Conflict(ApiErrorCode.VIDEO_ANALYSIS_EVIDENCE_NOT_ELIGIBLE);
    }

    private static FulfillmentExceptions.Conflict staleVersionConflict() {
        return new FulfillmentExceptions.Conflict(ApiErrorCode.EVIDENCE_STALE_VERSION);
    }

    private static void requireOperation(OperationContext context, RequestedOperation expectedOperation) {
        if (context.requestedOperation() != expectedOperation) {
            throw new IllegalArgumentException("Operation context does not match video analysis use case");
        }
    }

    private static <T> T required(T result) {
        if (result == null) {
            throw new IllegalStateException("Transaction returned no result");
        }
        return result;
    }

    private record ResolvedEvidence(
            VideoAnalysisEvidenceInputPort.VerifiedSnapshot snapshot,
            UUID fulfillmentId,
            UUID milestoneId,
            UUID buyerLegalEntityId,
            FulfillmentSourcePorts.Target target) {

        UUID evidenceSubmissionId() {
            return snapshot.evidenceSubmissionId();
        }
    }
}
