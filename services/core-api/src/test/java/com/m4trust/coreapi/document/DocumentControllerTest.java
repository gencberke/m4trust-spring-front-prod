package com.m4trust.coreapi.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class DocumentControllerTest {

    private static final String SHA = "a".repeat(64);

    @Test
    void intentReturnsCommittedCreatedShapeAndMissingFinalizeKeyMapsToMalformedRequest() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID legalEntityId = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        DocumentController controller = new DocumentController(
                new RecordingDocumentService(documentId, dealId));

        ResponseEntity<DocumentUploadIntent> response = controller.createUploadIntent(
                new OperationContext(userId, tenantId, legalEntityId,
                        RequestedOperation.DEAL_DOCUMENT_UPLOAD_INTENT_CREATE),
                dealId.toString(), request(), UUID.randomUUID().toString());

        assertEquals(201, response.getStatusCode().value());
        assertEquals("/api/v1/documents/" + documentId,
                response.getHeaders().getLocation().toString());
        assertEquals(documentId, response.getBody().document().id());
        assertEquals("https://storage.example/put", response.getBody().uploadUrl().toString());
        assertThrows(
                DocumentExceptions.MalformedRequest.class,
                () -> controller.finalizeUpload(new OperationContext(userId, tenantId,
                        legalEntityId, RequestedOperation.DOCUMENT_UPLOAD_FINALIZE),
                        documentId.toString(), null,
                        new FinalizeDocumentUploadRequest(12, SHA),
                        UUID.randomUUID().toString()));
        MockHttpServletRequest httpRequest = new MockHttpServletRequest("POST",
                "/api/v1/documents/" + documentId + "/finalize");
        ResponseEntity<?> problem = new DocumentExceptionHandler().malformed(httpRequest);
        assertEquals(400, problem.getStatusCode().value());
        assertEquals("MALFORMED_REQUEST", ((org.springframework.http.ProblemDetail)
                problem.getBody()).getProperties().get("code"));
    }

    private CreateDocumentUploadIntentRequest request() {
        return new CreateDocumentUploadIntentRequest("contract.pdf", "application/pdf", 12, SHA);
    }

    private static class RecordingDocumentService extends DocumentService {
        private final UUID documentId;
        private final UUID dealId;

        RecordingDocumentService(UUID documentId, UUID dealId) {
            super(null, null, null, null, null, null, null, Clock.systemUTC(), null, 1);
            this.documentId = documentId;
            this.dealId = dealId;
        }

        @Override
        DocumentUploadIntent createIntent(OperationContext context, UUID ignoredDealId,
                CreateDocumentUploadIntentRequest request, UUID correlationId) {
            Instant now = Instant.parse("2026-07-18T00:00:00Z");
            PendingDealDocument pending = new PendingDealDocument(documentId, dealId,
                    request.fileName(), request.mediaType(), DocumentStatus.PENDING_UPLOAD,
                    request.sizeBytes(), request.sha256(), now.plusSeconds(60), now,
                    new DocumentAvailableActions(true, false));
            return new DocumentUploadIntent(pending, URI.create("https://storage.example/put"),
                    Map.of("Content-Type", request.mediaType()), now.plusSeconds(60));
        }
    }
}
