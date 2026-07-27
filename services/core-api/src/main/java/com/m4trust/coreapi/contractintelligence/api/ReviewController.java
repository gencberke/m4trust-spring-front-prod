package com.m4trust.coreapi.contractintelligence.api;

import com.m4trust.coreapi.api.infra.CorrelationIdFilter;
import com.m4trust.coreapi.contractintelligence.api.dto.*;
import com.m4trust.coreapi.contractintelligence.domain.*;
import com.m4trust.coreapi.contractintelligence.infra.*;
import com.m4trust.coreapi.organization.api.ResolvedOperationContext;
import com.m4trust.coreapi.organization.domain.OperationContext;
import com.m4trust.coreapi.organization.domain.RequestedOperation;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/deals")
class ReviewController {
  private final ReviewService service;

  ReviewController(ReviewService service) {
    this.service = service;
  }

  @GetMapping("/{dealId}/extraction-review")
  Review review(
      @ResolvedOperationContext(RequestedOperation.DEAL_EXTRACTION_REVIEW_READ)
          OperationContext context,
      @PathVariable String dealId) {
    return service.review(context, id(dealId));
  }

  @PostMapping("/{dealId}/extraction-review/accept")
  ResponseEntity<Version> accept(
      @ResolvedOperationContext(RequestedOperation.DEAL_EXTRACTION_REVIEW_ACCEPT)
          OperationContext context,
      @PathVariable String dealId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestAttribute(CorrelationIdFilter.ATTRIBUTE) String correlationId,
      @RequestBody JsonNode body) {
    UUID parsedDealId = id(dealId);
    Version result =
        service.accept(context, parsedDealId, id(idempotencyKey), id(correlationId), body);
    URI location =
        URI.create("/api/v1/deals/" + parsedDealId + "/rule-set-versions/" + result.id());
    return ResponseEntity.created(location).body(result);
  }

  @GetMapping("/{dealId}/rule-set-versions")
  History history(
      @ResolvedOperationContext(RequestedOperation.DEAL_RULE_SET_VERSION_READ)
          OperationContext context,
      @PathVariable String dealId) {
    return service.history(context, id(dealId));
  }

  @GetMapping("/{dealId}/rule-set-versions/{ruleSetVersionId}")
  Version version(
      @ResolvedOperationContext(RequestedOperation.DEAL_RULE_SET_VERSION_READ)
          OperationContext context,
      @PathVariable String dealId,
      @PathVariable String ruleSetVersionId) {
    return service.version(context, id(dealId), id(ruleSetVersionId));
  }

  private static UUID id(String value) {
    try {
      return UUID.fromString(value);
    } catch (Exception exception) {
      throw new AnalysisExceptions.MalformedRequest();
    }
  }
}
