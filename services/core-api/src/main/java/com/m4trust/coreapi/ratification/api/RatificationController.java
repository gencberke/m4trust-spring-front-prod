package com.m4trust.coreapi.ratification.api;

import com.m4trust.coreapi.api.infra.CorrelationIdFilter;
import com.m4trust.coreapi.organization.api.ResolvedOperationContext;
import com.m4trust.coreapi.organization.domain.OperationContext;
import com.m4trust.coreapi.organization.domain.RequestedOperation;
import com.m4trust.coreapi.ratification.api.dto.*;
import com.m4trust.coreapi.ratification.domain.*;
import com.m4trust.coreapi.ratification.infra.persistence.*;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP surface for ratification package creation, reads, approval, and rejection. */
@RestController
@RequestMapping("/api/v1/deals/{dealId}/ratification-packages")
class RatificationController {

  private final RatificationPackageCreateService createService;
  private final RatificationPackageReadService readService;
  private final RatificationPackageActionService actionService;

  RatificationController(
      RatificationPackageCreateService createService,
      RatificationPackageReadService readService,
      RatificationPackageActionService actionService) {
    this.createService = createService;
    this.readService = readService;
    this.actionService = actionService;
  }

  @PostMapping
  ResponseEntity<RatificationPackageDetail> create(
      @ResolvedOperationContext(RequestedOperation.DEAL_RATIFICATION_PACKAGE_CREATE)
          OperationContext context,
      @PathVariable String dealId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody CreateRatificationPackageHttpRequest request,
      @RequestAttribute(CorrelationIdFilter.ATTRIBUTE) String correlationId) {
    UUID parsedDealId = id(dealId);
    RatificationPackageDetail created =
        createService.create(
            context,
            parsedDealId,
            toServiceRequest(request),
            id(idempotencyKey),
            id(correlationId));
    URI location =
        URI.create("/api/v1/deals/" + parsedDealId + "/ratification-packages/" + created.id());
    return ResponseEntity.created(location).body(created);
  }

  @GetMapping
  RatificationPackageHistory history(
      @ResolvedOperationContext(RequestedOperation.DEAL_RATIFICATION_PACKAGE_HISTORY_READ)
          OperationContext context,
      @PathVariable String dealId) {
    return readService.history(context, id(dealId));
  }

  @GetMapping("/{ratificationPackageId}")
  RatificationPackageDetail detail(
      @ResolvedOperationContext(RequestedOperation.DEAL_RATIFICATION_PACKAGE_READ)
          OperationContext context,
      @PathVariable String dealId,
      @PathVariable String ratificationPackageId) {
    return readService.detail(context, id(dealId), id(ratificationPackageId));
  }

  @PostMapping("/{ratificationPackageId}/approve")
  RatificationPackageDetail approve(
      @ResolvedOperationContext(RequestedOperation.DEAL_RATIFICATION_PACKAGE_APPROVE)
          OperationContext context,
      @PathVariable String dealId,
      @PathVariable String ratificationPackageId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody RatificationPackageActionRequest request,
      @RequestAttribute(CorrelationIdFilter.ATTRIBUTE) String correlationId) {
    return actionService.approve(
        context,
        id(dealId),
        id(ratificationPackageId),
        request,
        id(idempotencyKey),
        id(correlationId));
  }

  @PostMapping("/{ratificationPackageId}/reject")
  RatificationPackageDetail reject(
      @ResolvedOperationContext(RequestedOperation.DEAL_RATIFICATION_PACKAGE_REJECT)
          OperationContext context,
      @PathVariable String dealId,
      @PathVariable String ratificationPackageId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody RatificationPackageActionRequest request,
      @RequestAttribute(CorrelationIdFilter.ATTRIBUTE) String correlationId) {
    return actionService.reject(
        context,
        id(dealId),
        id(ratificationPackageId),
        request,
        id(idempotencyKey),
        id(correlationId));
  }

  private static CreateRatificationPackageRequest toServiceRequest(
      CreateRatificationPackageHttpRequest request) {
    return new CreateRatificationPackageRequest(
        request.expectedVersion(),
        request.commercialTerms().amountMinor(),
        request.commercialTerms().currency(),
        request.disputeWindowDays(),
        request.evidencePolicy());
  }

  private static UUID id(String value) {
    if (value == null || value.isBlank()) {
      throw new RatificationMalformedRequestException();
    }
    try {
      return UUID.fromString(value.trim());
    } catch (IllegalArgumentException exception) {
      throw new RatificationMalformedRequestException();
    }
  }
}
