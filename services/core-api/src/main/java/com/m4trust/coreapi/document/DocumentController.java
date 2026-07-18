package com.m4trust.coreapi.document;

import java.net.URI;
import java.util.UUID;

import com.m4trust.coreapi.api.CorrelationIdFilter;
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
import com.m4trust.coreapi.organization.ResolvedOperationContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP metadata endpoints only; document binary transfer stays browser-to-storage. */
@RestController
@RequestMapping("/api/v1")
public class DocumentController {

    private final DocumentService service;

    DocumentController(DocumentService service) {
        this.service = service;
    }

    @PostMapping("/deals/{dealId}/documents/upload-intents")
    ResponseEntity<DocumentUploadIntent> createUploadIntent(
            @ResolvedOperationContext(RequestedOperation.DEAL_DOCUMENT_UPLOAD_INTENT_CREATE)
            OperationContext context,
            @PathVariable String dealId,
            @Valid @RequestBody CreateDocumentUploadIntentRequest request,
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE) String correlationId) {
        DocumentUploadIntent result = service.createIntent(context, uuid(dealId),
                request, uuid(correlationId));
        return ResponseEntity.created(URI.create("/api/v1/documents/"
                + result.document().id())).body(result);
    }

    @PostMapping("/documents/{documentId}/finalize")
    AvailableDealDocument finalizeUpload(
            @ResolvedOperationContext(RequestedOperation.DOCUMENT_UPLOAD_FINALIZE)
            OperationContext context,
            @PathVariable String documentId,
            @RequestHeader(value = "Idempotency-Key", required = false)
            String idempotencyKey,
            @Valid @RequestBody FinalizeDocumentUploadRequest request,
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE) String correlationId) {
        return service.finalizeUpload(context, uuid(documentId), request,
                uuid(idempotencyKey), uuid(correlationId));
    }

    @GetMapping("/deals/{dealId}/documents")
    DealDocumentHistory listDealDocuments(
            @ResolvedOperationContext(RequestedOperation.DEAL_DOCUMENT_LIST_READ)
            OperationContext context,
            @PathVariable String dealId) {
        return service.listHistory(context, uuid(dealId));
    }

    @PostMapping("/documents/{documentId}/download-link")
    DocumentDownloadLink createDownloadLink(
            @ResolvedOperationContext(RequestedOperation.DOCUMENT_DOWNLOAD_LINK_CREATE)
            OperationContext context,
            @PathVariable String documentId) {
        return service.createDownloadLink(context, uuid(documentId));
    }

    private UUID uuid(String value) {
        if (value == null || value.isBlank()) {
            throw new DocumentExceptions.MalformedRequest();
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new DocumentExceptions.MalformedRequest();
        }
    }
}
