package com.m4trust.coreapi.payment.api;

import com.m4trust.coreapi.api.infra.CorrelationIdFilter;
import com.m4trust.coreapi.organization.api.ResolvedOperationContext;
import com.m4trust.coreapi.organization.domain.OperationContext;
import com.m4trust.coreapi.organization.domain.RequestedOperation;
import com.m4trust.coreapi.payment.api.dto.*;
import com.m4trust.coreapi.payment.domain.*;
import com.m4trust.coreapi.payment.infra.*;
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
import org.springframework.web.bind.annotation.RestController;

/** HTTP surface for the funding plan, payment operation initiation, read, and reconciliation. */
@RestController
class FundingController {

  private final FundingPlanCreateService createService;
  private final PaymentOperationInitiateService initiateService;
  private final PaymentOperationReconcileService reconcileService;
  private final FundingReadService readService;

  FundingController(
      FundingPlanCreateService createService,
      PaymentOperationInitiateService initiateService,
      PaymentOperationReconcileService reconcileService,
      FundingReadService readService) {
    this.createService = createService;
    this.initiateService = initiateService;
    this.reconcileService = reconcileService;
    this.readService = readService;
  }

  @PostMapping("/api/v1/deals/{dealId}/funding-plan")
  ResponseEntity<FundingPlanDetailView> createFundingPlan(
      @ResolvedOperationContext(RequestedOperation.DEAL_FUNDING_PLAN_CREATE)
          OperationContext context,
      @PathVariable String dealId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody CreateFundingPlanHttpRequest request,
      @RequestAttribute(CorrelationIdFilter.ATTRIBUTE) String correlationId) {
    UUID parsedDealId = id(dealId);
    FundingPlanDetailView created =
        createService.create(
            context,
            parsedDealId,
            request.expectedVersion(),
            id(idempotencyKey),
            id(correlationId));
    URI location = URI.create("/api/v1/deals/" + parsedDealId + "/funding-plan");
    return ResponseEntity.created(location).body(created);
  }

  @GetMapping("/api/v1/deals/{dealId}/funding-plan")
  FundingPlanDetailView getFundingPlan(
      @ResolvedOperationContext(RequestedOperation.DEAL_FUNDING_PLAN_READ) OperationContext context,
      @PathVariable String dealId) {
    return readService.getPlan(context, id(dealId));
  }

  @PostMapping("/api/v1/funding-units/{fundingUnitId}/payment-operations")
  ResponseEntity<PaymentOperationView> initiatePaymentOperation(
      @ResolvedOperationContext(RequestedOperation.FUNDING_UNIT_PAYMENT_OPERATION_INITIATE)
          OperationContext context,
      @PathVariable String fundingUnitId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody InitiatePaymentOperationHttpRequest request,
      @RequestAttribute(CorrelationIdFilter.ATTRIBUTE) String correlationId) {
    PaymentOperationView created =
        initiateService.initiate(
            context,
            id(fundingUnitId),
            request.expectedVersion(),
            id(idempotencyKey),
            id(correlationId));
    URI location = URI.create("/api/v1/payment-operations/" + created.id());
    return ResponseEntity.status(202).location(location).body(created);
  }

  @GetMapping("/api/v1/payment-operations/{paymentOperationId}")
  PaymentOperationView getPaymentOperation(
      @ResolvedOperationContext(RequestedOperation.PAYMENT_OPERATION_READ) OperationContext context,
      @PathVariable String paymentOperationId) {
    return readService.getOperation(context, id(paymentOperationId));
  }

  @PostMapping("/api/v1/payment-operations/{paymentOperationId}/reconcile")
  ResponseEntity<PaymentOperationView> reconcilePaymentOperation(
      @ResolvedOperationContext(RequestedOperation.PAYMENT_OPERATION_RECONCILE)
          OperationContext context,
      @PathVariable String paymentOperationId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody ReconcilePaymentOperationHttpRequest request,
      @RequestAttribute(CorrelationIdFilter.ATTRIBUTE) String correlationId) {
    UUID parsedOperationId = id(paymentOperationId);
    PaymentOperationView result =
        reconcileService.reconcile(
            context,
            parsedOperationId,
            request.expectedVersion(),
            id(idempotencyKey),
            id(correlationId));
    URI location = URI.create("/api/v1/payment-operations/" + parsedOperationId);
    return ResponseEntity.status(202).location(location).body(result);
  }

  private static UUID id(String value) {
    if (value == null || value.isBlank()) {
      throw new PaymentMalformedRequestException();
    }
    try {
      return UUID.fromString(value.trim());
    } catch (IllegalArgumentException exception) {
      throw new PaymentMalformedRequestException();
    }
  }
}
