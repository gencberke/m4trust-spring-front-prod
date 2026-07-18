package com.m4trust.coreapi.document;

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
import com.m4trust.coreapi.deal.DealDocumentMutationPort;
import com.m4trust.coreapi.deal.DealDocumentMutationPort.LockedDealDocumentTarget;
import com.m4trust.coreapi.idempotency.IdempotencyClaim;
import com.m4trust.coreapi.idempotency.IdempotencyRequest;
import com.m4trust.coreapi.idempotency.IdempotencyResultReference;
import com.m4trust.coreapi.idempotency.IdempotencyService;
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Coordinates direct storage transfers with a single atomic document finalization. */
@Service
class DocumentService {

    private static final String IDEMPOTENCY_OPERATION = "DOCUMENT_UPLOAD_FINALIZE";
    private static final String IDEMPOTENCY_RESULT = "AVAILABLE_DEAL_DOCUMENT";
    private static final String AUDIT_SUBJECT = "DOCUMENT";
    private static final String AUDIT_ACTION = "DOCUMENT_UPLOAD_FINALIZED";

    private final DocumentRepository repository;
    private final DealDocumentMutationPort dealMutations;
    private final DocumentObjectStorage storage;
    private final IdempotencyService idempotency;
    private final AuditAppendPort auditAppender;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final long maxUploadSizeBytes;

    DocumentService(DocumentRepository repository,
            DealDocumentMutationPort dealMutations,
            DocumentObjectStorage storage, IdempotencyService idempotency,
            AuditAppendPort auditAppender, TransactionTemplate transactions,
            Clock clock,
            @Value("${app.object-storage.max-upload-size-bytes}") long maxUploadSizeBytes) {
        this.repository = repository;
        this.dealMutations = dealMutations;
        this.storage = storage;
        this.idempotency = idempotency;
        this.auditAppender = auditAppender;
        this.transactions = transactions;
        this.clock = clock;
        this.maxUploadSizeBytes = maxUploadSizeBytes;
    }

    DocumentUploadIntent createIntent(OperationContext context, UUID dealId,
            CreateDocumentUploadIntentRequest request, UUID correlationId) {
        requireOperation(context, RequestedOperation.DEAL_DOCUMENT_UPLOAD_INTENT_CREATE);
        requireUploadSize(request.sizeBytes());
        DocumentMediaType mediaType = mediaType(request.mediaType());
        // Authorization happens before issuing a signed capability and is repeated
        // in the write transaction after the external presigner has returned.
        requireMutationTarget(preflightDealTarget(context, dealId));
        String objectKey = DocumentObjectKeys.newKey();
        DocumentObjectStorage.DirectUpload directUpload = storage.createDirectUpload(
                objectKey, mediaType.value(), request.sizeBytes());
        return required(transactions.execute(status -> {
            requireMutationTarget(lockedDealTarget(context, dealId));
            Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            Document document = Document.createPending(UUID.randomUUID(), dealId,
                    request.fileName(), mediaType, objectKey, request.sizeBytes(),
                    request.sha256(), directUpload.expiresAt(), now);
            repository.insert(document.toRecord());
            return new DocumentUploadIntent(pending(document, true), directUpload.url(),
                    directUpload.headers(), directUpload.expiresAt());
        }));
    }

    AvailableDealDocument finalizeUpload(OperationContext context, UUID documentId,
            FinalizeDocumentUploadRequest request, UUID idempotencyKey,
            UUID correlationId) {
        requireOperation(context, RequestedOperation.DOCUMENT_UPLOAD_FINALIZE);
        IdempotencyRequest idempotencyRequest = idempotencyRequest(context,
                documentId, request, idempotencyKey);
        IdempotencyResultReference completed = idempotency.findCompleted(
                idempotencyRequest).orElse(null);
        if (completed != null) {
            return replayAvailable(completed);
        }
        requireUploadSize(request.sizeBytes());
        DocumentRepository.DocumentRecord preflight = repository.findById(documentId)
                .orElseThrow(DocumentExceptions.NotFound::new);
        requireFinalizeMutationTarget(preflightDocumentTarget(context, preflight.dealId()));
        // This may HEAD/GET and hash the object. It is intentionally before the
        // transaction that claims idempotency and changes document state.
        DocumentObjectStorage.VerifiedObject verified = storage.verify(preflight.objectKey());
        return required(transactions.execute(status -> finalizeInTransaction(context,
                documentId, request, idempotencyRequest, correlationId, verified)));
    }

    private AvailableDealDocument finalizeInTransaction(OperationContext context,
            UUID documentId, FinalizeDocumentUploadRequest request,
            IdempotencyRequest idempotencyRequest, UUID correlationId,
            DocumentObjectStorage.VerifiedObject verified) {
        IdempotencyClaim claim = idempotency.claim(idempotencyRequest);
        if (claim.isReplay()) {
            return replayAvailable(claim.resultReference());
        }
        DocumentRepository.DocumentRecord current = repository.findById(documentId)
                .orElseThrow(DocumentExceptions.NotFound::new);
        LockedDealDocumentTarget target = requireFinalizeMutationTarget(
                lockedTarget(context, current.dealId()));
        Document document = repository.findByIdForUpdate(documentId)
                .map(Document::rehydrate)
                .orElseThrow(DocumentExceptions.NotFound::new);
        if (!document.dealId().equals(target.dealId())) {
            throw new DocumentExceptions.NotFound();
        }
        if (document.status() != DocumentStatus.PENDING_UPLOAD) {
            throw new DocumentExceptions.UploadStateConflict();
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (!now.isBefore(document.uploadExpiresAt())) {
            throw new DocumentExceptions.UploadExpired();
        }
        if (verified.sizeBytes() != request.sizeBytes()
                || !verified.sha256().equals(request.sha256())) {
            throw new DocumentExceptions.VerificationFailed();
        }
        long previousDocumentVersion = document.toRecord().version();
        try {
            document.markAvailable(verified.sizeBytes(), verified.sha256(),
                    verified.objectVersion(), now);
        } catch (IllegalArgumentException exception) {
            throw new DocumentExceptions.VerificationFailed();
        }
        if (!repository.update(document.toRecord(), previousDocumentVersion)) {
            throw new DocumentExceptions.UploadStateConflict();
        }
        UUID previousCurrentDocumentId = target.currentDocumentId();
        dealMutations.repointCurrentDocument(target.dealId(), document.id(), now);
        if (previousCurrentDocumentId != null) {
            Document previous = repository.findByIdForUpdate(previousCurrentDocumentId)
                    .map(Document::rehydrate)
                    .orElseThrow(() -> new IllegalStateException(
                            "Deal current document is unavailable"));
            if (!previous.dealId().equals(target.dealId())
                    || previous.status() != DocumentStatus.AVAILABLE) {
                throw new IllegalStateException("Deal current document is invalid");
            }
            long previousVersion = previous.toRecord().version();
            previous.supersede(now);
            if (!repository.update(previous.toRecord(), previousVersion)) {
                throw new DocumentExceptions.UploadStateConflict();
            }
        }
        auditAppender.append(new AuditRecord(UUID.randomUUID(), target.tenantId(),
                context.authenticatedUserId(), context.activeLegalEntityId(),
                AUDIT_SUBJECT, document.id(), AUDIT_ACTION, correlationId, null, now));
        idempotency.recordResult(claim, new IdempotencyResultReference(
                IDEMPOTENCY_RESULT, document.id()));
        return available(document, true);
    }

    private LockedDealDocumentTarget preflightDealTarget(OperationContext context,
            UUID dealId) {
        return required(transactions.execute(status -> lockedDealTarget(context, dealId)));
    }

    private LockedDealDocumentTarget preflightDocumentTarget(OperationContext context,
            UUID dealId) {
        return required(transactions.execute(status -> lockedTarget(context, dealId)));
    }

    private LockedDealDocumentTarget lockedDealTarget(OperationContext context,
            UUID dealId) {
        return dealMutations.lockForDocumentMutation(context, dealId)
                .orElseThrow(DocumentExceptions.DealNotFound::new);
    }

    private LockedDealDocumentTarget lockedTarget(OperationContext context,
            UUID dealId) {
        return dealMutations.lockForDocumentMutation(context, dealId)
                .orElseThrow(DocumentExceptions.NotFound::new);
    }

    private LockedDealDocumentTarget requireMutationTarget(
            LockedDealDocumentTarget target) {
        if (!target.initiator()) {
            throw new DocumentExceptions.MutationForbidden();
        }
        if (!target.acceptsDocuments()) {
            throw new DocumentExceptions.UploadNotAllowed();
        }
        return target;
    }

    private LockedDealDocumentTarget requireFinalizeMutationTarget(
            LockedDealDocumentTarget target) {
        if (!target.initiator()) {
            throw new DocumentExceptions.MutationForbidden();
        }
        if (!target.acceptsDocuments()) {
            throw new DocumentExceptions.UploadStateConflict();
        }
        return target;
    }

    private PendingDealDocument pending(Document document, boolean canFinalize) {
        return new PendingDealDocument(document.id(), document.dealId(),
                document.fileName(), document.mediaType().value(), document.status(),
                document.declaredSizeBytes(), document.declaredSha256(),
                document.uploadExpiresAt(), document.createdAt(),
                new DocumentAvailableActions(canFinalize, false));
    }

    private AvailableDealDocument available(Document document, boolean canDownload) {
        return new AvailableDealDocument(document.id(), document.dealId(),
                document.fileName(), document.mediaType().value(), document.status(),
                document.verifiedSizeBytes(), document.verifiedSha256(),
                document.objectVersion(), document.createdAt(), document.availableAt(),
                new DocumentAvailableActions(false, canDownload));
    }

    private AvailableDealDocument replayAvailable(
            IdempotencyResultReference reference) {
        Document document = repository.findById(reference.id())
                .map(Document::rehydrate)
                .filter(result -> result.status() != DocumentStatus.PENDING_UPLOAD)
                .orElseThrow(DocumentExceptions.UploadStateConflict::new);
        return new AvailableDealDocument(document.id(), document.dealId(),
                document.fileName(), document.mediaType().value(),
                DocumentStatus.AVAILABLE, document.verifiedSizeBytes(),
                document.verifiedSha256(), document.objectVersion(),
                document.createdAt(), document.availableAt(),
                new DocumentAvailableActions(false, true));
    }

    private void requireUploadSize(long sizeBytes) {
        if (sizeBytes <= 0 || sizeBytes > maxUploadSizeBytes) {
            throw new DocumentExceptions.Validation("sizeBytes");
        }
    }

    private DocumentMediaType mediaType(String value) {
        try {
            return DocumentMediaType.fromValue(value);
        } catch (IllegalArgumentException exception) {
            throw new DocumentExceptions.MalformedRequest();
        }
    }

    private void requireOperation(OperationContext context, RequestedOperation expected) {
        if (context.requestedOperation() != expected) {
            throw new IllegalArgumentException("Operation context does not match document use case");
        }
    }

    private String canonicalHash(UUID activeLegalEntityId, UUID documentId,
            long sizeBytes, String sha256) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((activeLegalEntityId + "\n"
                    + documentId + "\n" + sizeBytes + "\n" + sha256)
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private IdempotencyRequest idempotencyRequest(OperationContext context,
            UUID documentId, FinalizeDocumentUploadRequest request,
            UUID idempotencyKey) {
        return new IdempotencyRequest(context.authenticatedUserId(),
                context.tenantId(), IDEMPOTENCY_OPERATION, idempotencyKey,
                canonicalHash(context.activeLegalEntityId(), documentId,
                        request.sizeBytes(), request.sha256()));
    }

    private static <T> T required(T value) {
        if (value == null) {
            throw new IllegalStateException("Transaction returned no result");
        }
        return value;
    }
}
