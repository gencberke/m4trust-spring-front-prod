package com.m4trust.coreapi.contractintelligence;
import java.net.URI; import java.util.UUID;
import com.m4trust.coreapi.api.CorrelationIdFilter; import com.m4trust.coreapi.organization.*;
import org.springframework.http.ResponseEntity; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/deals") class AnalysisController {
 private final AnalysisService service; AnalysisController(AnalysisService service){this.service=service;}
 @GetMapping("/{dealId}/document-analysis") DealDocumentAnalysis get(@ResolvedOperationContext(RequestedOperation.DEAL_DOCUMENT_ANALYSIS_READ) OperationContext c,@PathVariable UUID dealId){return service.get(c,dealId);}
 @PostMapping("/{dealId}/document-analysis") ResponseEntity<DealDocumentAnalysis> request(@ResolvedOperationContext(RequestedOperation.DEAL_DOCUMENT_ANALYSIS_REQUEST) OperationContext c,@PathVariable UUID dealId,@RequestHeader("Idempotency-Key") UUID key,@RequestAttribute(CorrelationIdFilter.ATTRIBUTE) String correlation){return ResponseEntity.accepted().location(URI.create("/api/v1/deals/"+dealId+"/document-analysis")).body(service.request(c,dealId,key,UUID.fromString(correlation)));}
}
