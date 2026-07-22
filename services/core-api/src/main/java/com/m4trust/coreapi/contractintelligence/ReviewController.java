package com.m4trust.coreapi.contractintelligence;

import java.net.URI;
import java.util.UUID;
import com.m4trust.coreapi.api.CorrelationIdFilter;
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
import com.m4trust.coreapi.organization.ResolvedOperationContext;
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
    ReviewDtos.Review review(
            @ResolvedOperationContext(RequestedOperation.DEAL_EXTRACTION_REVIEW_READ) OperationContext context,
            @PathVariable String dealId) {
        return service.review(context, id(dealId));
    }

    @PostMapping("/{dealId}/extraction-review/accept")
    ResponseEntity<ReviewDtos.Version> accept(
            @ResolvedOperationContext(RequestedOperation.DEAL_EXTRACTION_REVIEW_ACCEPT) OperationContext context,
            @PathVariable String dealId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE) String correlationId,
            @RequestBody JsonNode body) {
        UUID parsedDealId = id(dealId);
        ReviewDtos.Version result = service.accept(context, parsedDealId, id(idempotencyKey),
                id(correlationId), body);
        URI location = URI.create("/api/v1/deals/" + parsedDealId + "/rule-set-versions/" + result.id());
        return ResponseEntity.created(location).body(result);
    }

    @GetMapping("/{dealId}/rule-set-versions")
    ReviewDtos.History history(
            @ResolvedOperationContext(RequestedOperation.DEAL_RULE_SET_VERSION_READ) OperationContext context,
            @PathVariable String dealId) {
        return service.history(context, id(dealId));
    }

    @GetMapping("/{dealId}/rule-set-versions/{ruleSetVersionId}")
    ReviewDtos.Version version(
            @ResolvedOperationContext(RequestedOperation.DEAL_RULE_SET_VERSION_READ) OperationContext context,
            @PathVariable String dealId, @PathVariable String ruleSetVersionId) {
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
