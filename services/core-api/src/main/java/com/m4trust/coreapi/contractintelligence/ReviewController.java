package com.m4trust.coreapi.contractintelligence;

import java.net.URI;
import java.util.UUID;
import com.m4trust.coreapi.api.CorrelationIdFilter;
import com.m4trust.coreapi.organization.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/deals")
class ReviewController {
  private final ReviewService service;
  ReviewController(ReviewService service){this.service=service;}
  @GetMapping("/{dealId}/extraction-review") Object review(@ResolvedOperationContext(RequestedOperation.DEAL_EXTRACTION_REVIEW_READ) OperationContext c,@PathVariable String dealId){return service.review(c,id(dealId));}
  @PostMapping("/{dealId}/extraction-review/accept") ResponseEntity<Object> accept(@ResolvedOperationContext(RequestedOperation.DEAL_EXTRACTION_REVIEW_ACCEPT) OperationContext c,@PathVariable String dealId,@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestAttribute(CorrelationIdFilter.ATTRIBUTE) String correlation,@RequestBody JsonNode body){UUID d=id(dealId);Object result=service.accept(c,d,id(key),id(correlation),body); return ResponseEntity.created(URI.create("/api/v1/deals/"+d+"/rule-set-versions/"+((java.util.Map<?,?>)result).get("id"))).body(result);}
  @GetMapping("/{dealId}/rule-set-versions") Object history(@ResolvedOperationContext(RequestedOperation.DEAL_RULE_SET_VERSION_READ) OperationContext c,@PathVariable String dealId){return service.history(c,id(dealId));}
  @GetMapping("/{dealId}/rule-set-versions/{versionId}") Object version(@ResolvedOperationContext(RequestedOperation.DEAL_RULE_SET_VERSION_READ) OperationContext c,@PathVariable String dealId,@PathVariable String versionId){return service.version(c,id(dealId),id(versionId));}
  private static UUID id(String value){try{return UUID.fromString(value);}catch(Exception e){throw new AnalysisExceptions.MalformedRequest();}}
}
