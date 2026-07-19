package com.m4trust.coreapi.contractintelligence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

import com.m4trust.coreapi.audit.AuditAppendPort;
import com.m4trust.coreapi.audit.AuditRecord;
import com.m4trust.coreapi.deal.DealAnalysisMutationPort;
import com.m4trust.coreapi.deal.DealAnalysisProjectionPort;
import com.m4trust.coreapi.deal.DealAnalysisReadPort;
import com.m4trust.coreapi.document.DocumentAnalysisInputPort;
import com.m4trust.coreapi.document.DocumentAnalysisSupersedePort;
import com.m4trust.coreapi.document.DocumentObjectStorage;
import com.m4trust.coreapi.idempotency.IdempotencyClaim;
import com.m4trust.coreapi.idempotency.IdempotencyRequest;
import com.m4trust.coreapi.idempotency.IdempotencyResultReference;
import com.m4trust.coreapi.idempotency.IdempotencyService;
import com.m4trust.coreapi.integration.messaging.TransactionalOutbox;
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** Coordinates the 3A request and read projection; result consumption is intentionally absent. */
@Service
class AnalysisService implements DealAnalysisProjectionPort, DocumentAnalysisSupersedePort {

    private static final String IDEMPOTENCY_OPERATION = "DEAL_DOCUMENT_ANALYSIS_REQUEST";
    private static final String IDEMPOTENCY_RESULT = "DEAL_DOCUMENT_ANALYSIS";
    private static final String AUDIT_SUBJECT = "ANALYSIS_JOB";
    private static final String AUDIT_ACTION = "DOCUMENT_ANALYSIS_REQUESTED";

    private final AnalysisRepository repository;
    private final DealAnalysisReadPort dealReads;
    private final DealAnalysisMutationPort dealMutations;
    private final DocumentAnalysisInputPort documentInputs;
    private final DocumentObjectStorage storage;
    private final IdempotencyService idempotency;
    private final TransactionalOutbox outbox;
    private final AuditAppendPort auditAppender;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final DocumentExtractionRequestedEventFactory eventFactory;
    private final ObjectMapper objectMapper;

    AnalysisService(AnalysisRepository repository, DealAnalysisReadPort dealReads,
            DealAnalysisMutationPort dealMutations, DocumentAnalysisInputPort documentInputs,
            DocumentObjectStorage storage, IdempotencyService idempotency,
            TransactionalOutbox outbox, AuditAppendPort auditAppender,
            TransactionTemplate transactions, Clock clock, ObjectMapper objectMapper,
            @Value("${app.ai.producer-version:1.0.0}") String producerVersion) {
        this.repository = repository;
        this.dealReads = dealReads;
        this.dealMutations = dealMutations;
        this.documentInputs = documentInputs;
        this.storage = storage;
        this.idempotency = idempotency;
        this.outbox = outbox;
        this.auditAppender = auditAppender;
        this.transactions = transactions;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.eventFactory = new DocumentExtractionRequestedEventFactory(objectMapper, producerVersion);
    }

    DealDocumentAnalysis get(OperationContext context, UUID dealId) {
        requireOperation(context, RequestedOperation.DEAL_DOCUMENT_ANALYSIS_READ);
        DealAnalysisReadPort.AnalysisVisibility visibility = dealReads
                .findAnalysisVisibility(context, dealId)
                .orElseThrow(AnalysisExceptions.DealNotFound::new);
        return currentProjection(visibility.currentDocumentId());
    }

    DealDocumentAnalysis request(OperationContext context, UUID dealId, UUID idempotencyKey,
            UUID correlationId) {
        requireOperation(context, RequestedOperation.DEAL_DOCUMENT_ANALYSIS_REQUEST);
        IdempotencyRequest request = new IdempotencyRequest(context.authenticatedUserId(),
                context.tenantId(), IDEMPOTENCY_OPERATION, idempotencyKey,
                canonicalHash(context.activeLegalEntityId(), dealId));
        IdempotencyResultReference completed = idempotency.findCompleted(request).orElse(null);
        if (completed != null) {
            return projectionForJob(completed);
        }

        // This read is intentionally not a mutation lock. Presigning is external I/O,
        // so it happens after a read-only preflight and before the atomic write.
        DealAnalysisReadPort.AnalysisVisibility preflight = dealReads
                .findAnalysisVisibility(context, dealId)
                .orElseThrow(AnalysisExceptions.DealNotFound::new);
        requireEligible(preflight.initiator(), preflight.acceptsAnalysis(),
                preflight.currentDocumentId());
        DocumentAnalysisInputPort.Input input = availableInput(preflight.currentDocumentId());
        if (repository.hasActiveJob(input.id())) {
            throw activeJobConflict();
        }
        DocumentObjectStorage.DirectDownload download = storage.createAiDirectDownload(
                input.objectKey(), input.objectVersion());

        return required(transactions.execute(status -> requestInTransaction(context, dealId,
                idempotencyKey, correlationId, request, input, download)));
    }

    private DealDocumentAnalysis requestInTransaction(OperationContext context, UUID dealId,
            UUID idempotencyKey, UUID correlationId, IdempotencyRequest request,
            DocumentAnalysisInputPort.Input preflightInput,
            DocumentObjectStorage.DirectDownload download) {
        IdempotencyClaim claim = idempotency.claim(request);
        if (claim.isReplay()) {
            return projectionForJob(claim.resultReference());
        }

        DealAnalysisMutationPort.AnalysisTarget target = dealMutations
                .lockForAnalysisRequest(context, dealId)
                .orElseThrow(AnalysisExceptions.DealNotFound::new);
        requireEligible(target.initiator(), target.acceptsAnalysis(), target.currentDocumentId());
        DocumentAnalysisInputPort.Input lockedInput = availableInput(target.currentDocumentId());
        if (!sameImmutableInput(preflightInput, lockedInput)) {
            throw new AnalysisExceptions.Conflict(
                    "DEAL_DOCUMENT_ANALYSIS_DOCUMENT_NOT_AVAILABLE");
        }
        if (repository.hasActiveJob(lockedInput.id())) {
            throw activeJobConflict();
        }

        Instant requestedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        UUID jobId = UUID.randomUUID();
        try {
            repository.insertQueued(AnalysisRepository.AnalysisJob.queued(jobId,
                    target.owningTenantId(), target.dealId(), lockedInput.id(),
                    lockedInput.objectVersion(), lockedInput.sha256(), requestedAt));
        } catch (DuplicateKeyException exception) {
            // The partial unique index is the final authority under concurrent writers.
            throw activeJobConflict();
        }

        String event = eventFactory.create(UUID.randomUUID(), jobId, target.owningTenantId(),
                target.dealId(), correlationId, idempotencyKey, lockedInput, download, requestedAt);
        outbox.enqueue(DocumentExtractionRequestedEventFactory.EVENT_TYPE, "m4trust.ai.commands",
                "ai.document-extraction.requested.v1", event);
        auditAppender.append(new AuditRecord(UUID.randomUUID(), target.owningTenantId(),
                context.authenticatedUserId(), context.activeLegalEntityId(), AUDIT_SUBJECT,
                jobId, AUDIT_ACTION, correlationId, null, requestedAt));
        idempotency.recordResult(claim, new IdempotencyResultReference(IDEMPOTENCY_RESULT, jobId));
        return projection(AnalysisRepository.AnalysisJob.queued(jobId, target.owningTenantId(),
                target.dealId(), lockedInput.id(), lockedInput.objectVersion(),
                lockedInput.sha256(), requestedAt));
    }

    @Override
    public AnalysisSummary summary(UUID currentDocumentId) {
        DealDocumentAnalysis projection = currentProjection(currentDocumentId);
        return new AnalysisSummary(projection.currentDocumentId(), projection.status().name(),
                projection.requestedAt(), projection.processingStartedAt(),
                projection.completedAt(), projection.failedAt(), projection.failure() == null ? null
                        : new Failure(projection.failure().code(),
                                projection.failure().retryRecommended()));
    }

    @Override
    public boolean hasActiveJob(UUID currentDocumentId) {
        return repository.hasActiveJob(currentDocumentId);
    }

    @Override
    public void supersedeForDocument(UUID dealId, UUID documentId, UUID tenantId, UUID actorUserId,
            UUID legalEntityId, UUID correlationId, Instant occurredAt) {
        repository.lockAndSupersedeDocument(documentId).forEach(job -> auditAppender.append(
                new AuditRecord(UUID.randomUUID(), tenantId, actorUserId, legalEntityId,
                        AUDIT_SUBJECT, job.id(), "DOCUMENT_ANALYSIS_SUPERSEDED",
                        correlationId, null, occurredAt)));
        // Document finalization owns the Deal lock. Clearing here keeps the stale
        // chain and its historical rule versions readable without presenting it current.
        dealMutations.clearCurrentRuleSet(dealId);
    }

    private DealDocumentAnalysis currentProjection(UUID currentDocumentId) {
        if (currentDocumentId == null || documentInputs.findAvailable(currentDocumentId).isEmpty()) {
            return notRequested(null);
        }
        return repository.findLatestForDocument(currentDocumentId).map(this::projection)
                .orElseGet(() -> notRequested(currentDocumentId));
    }

    private DealDocumentAnalysis projectionForJob(IdempotencyResultReference reference) {
        if (!IDEMPOTENCY_RESULT.equals(reference.type())) {
            throw new IllegalStateException("Unexpected analysis idempotency result type");
        }
        return repository.findById(reference.id()).map(this::projection)
                .orElseThrow(() -> new IllegalStateException("Idempotency result job is unavailable"));
    }

    private DealDocumentAnalysis projection(AnalysisRepository.AnalysisJob job) {
        DealDocumentAnalysis.Failure failure = job.failureCode() == null ? null
                : new DealDocumentAnalysis.Failure(job.failureCode(),
                        Boolean.TRUE.equals(job.retryRecommended()));
        Object result = repository.findResult(job.id()).map(this::readPublicResult).orElse(null);
        return new DealDocumentAnalysis(job.documentId(), job.status(), job.requestedAt(),
                job.processingStartedAt(), job.completedAt(), job.failedAt(), failure, result);
    }

    private Object readPublicResult(String serialized) {
        try {
            var complete = objectMapper.readTree(serialized);
            return java.util.Map.of("parties", objectMapper.treeToValue(complete.get("parties"), Object.class),
                    "rules", objectMapper.treeToValue(complete.get("rules"), Object.class),
                    "deliveryRequirements", objectMapper.treeToValue(complete.get("deliveryRequirements"), Object.class),
                    "summary", objectMapper.treeToValue(complete.get("summary"), Object.class));
        }
        catch (Exception exception) { throw new IllegalStateException("Stored canonical result is invalid", exception); }
    }

    private static DealDocumentAnalysis notRequested(UUID currentDocumentId) {
        return new DealDocumentAnalysis(currentDocumentId, AnalysisJobStatus.NOT_REQUESTED,
                null, null, null, null, null, null);
    }

    private DocumentAnalysisInputPort.Input availableInput(UUID documentId) {
        if (documentId == null) {
            throw new AnalysisExceptions.Conflict("DEAL_DOCUMENT_ANALYSIS_DOCUMENT_NOT_AVAILABLE");
        }
        return documentInputs.findAvailable(documentId).orElseThrow(
                () -> new AnalysisExceptions.Conflict("DEAL_DOCUMENT_ANALYSIS_DOCUMENT_NOT_AVAILABLE"));
    }

    private static boolean sameImmutableInput(DocumentAnalysisInputPort.Input preflight,
            DocumentAnalysisInputPort.Input locked) {
        return preflight.id().equals(locked.id())
                && preflight.objectVersion().equals(locked.objectVersion())
                && preflight.sha256().equals(locked.sha256());
    }

    private static void requireEligible(boolean initiator, boolean acceptsAnalysis,
            UUID currentDocumentId) {
        if (!initiator) {
            throw new AnalysisExceptions.RequestForbidden();
        }
        if (!acceptsAnalysis) {
            throw new AnalysisExceptions.Conflict("DEAL_STATE_CONFLICT");
        }
        if (currentDocumentId == null) {
            throw new AnalysisExceptions.Conflict("DEAL_DOCUMENT_ANALYSIS_DOCUMENT_NOT_AVAILABLE");
        }
    }

    private static AnalysisExceptions.Conflict activeJobConflict() {
        return new AnalysisExceptions.Conflict("DEAL_DOCUMENT_ANALYSIS_ACTIVE_JOB_EXISTS");
    }

    private static String canonicalHash(UUID activeLegalEntityId, UUID dealId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] canonical = ("activeLegalEntityId=" + activeLegalEntityId + "\n"
                    + "dealId=" + dealId + "\n").getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(digest.digest(canonical));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void requireOperation(OperationContext context,
            RequestedOperation expectedOperation) {
        if (context.requestedOperation() != expectedOperation) {
            throw new IllegalArgumentException("Operation context does not match analysis use case");
        }
    }

    private static <T> T required(T result) {
        if (result == null) {
            throw new IllegalStateException("Transaction returned no result");
        }
        return result;
    }
}
