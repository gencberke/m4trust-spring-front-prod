package com.m4trust.coreapi.contractintelligence;

import java.net.URI;
import java.util.UUID;

import com.m4trust.coreapi.api.CorrelationIdFilter;
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
import com.m4trust.coreapi.organization.ResolvedOperationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/deals")
class AnalysisController {

    private final AnalysisService service;

    AnalysisController(AnalysisService service) {
        this.service = service;
    }

    @GetMapping("/{dealId}/document-analysis")
    DealDocumentAnalysis get(@ResolvedOperationContext(
            RequestedOperation.DEAL_DOCUMENT_ANALYSIS_READ) OperationContext context,
            @PathVariable String dealId) {
        return service.get(context, uuid(dealId));
    }

    @PostMapping("/{dealId}/document-analysis")
    ResponseEntity<DealDocumentAnalysis> request(@ResolvedOperationContext(
            RequestedOperation.DEAL_DOCUMENT_ANALYSIS_REQUEST) OperationContext context,
            @PathVariable String dealId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE) String correlationId) {
        UUID parsedDealId = uuid(dealId);
        DealDocumentAnalysis accepted = service.request(context, parsedDealId, uuid(idempotencyKey),
                uuid(correlationId));
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/deals/" + parsedDealId + "/document-analysis"))
                .body(accepted);
    }

    private UUID uuid(String value) {
        if (value == null || value.isBlank()) {
            throw new AnalysisExceptions.MalformedRequest();
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new AnalysisExceptions.MalformedRequest();
        }
    }
}
