package com.m4trust.coreapi.payment;

import java.net.URI;
import java.util.UUID;

import com.m4trust.coreapi.api.CorrelationIdFilter;
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
import com.m4trust.coreapi.organization.ResolvedOperationContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SettlementController {

    private final SettlementReadService readService;
    private final ReleaseOperationInitiateService initiateService;
    private final ReleaseOperationReconcileService reconcileService;

    SettlementController(SettlementReadService readService, ReleaseOperationInitiateService initiateService,
            ReleaseOperationReconcileService reconcileService) {
        this.readService = readService;
        this.initiateService = initiateService;
        this.reconcileService = reconcileService;
    }

    @GetMapping("/api/v1/deals/{dealId}/settlement")
    SettlementReadDtos.SettlementDetailView getSettlement(
            @ResolvedOperationContext(RequestedOperation.DEAL_SETTLEMENT_READ) OperationContext context,
            @PathVariable String dealId) {
        return readService.get(context, id(dealId));
    }

    @PostMapping("/api/v1/deals/{dealId}/settlement/release")
    ResponseEntity<SettlementReadDtos.ReleaseOperationView> requestSettlementRelease(
            @ResolvedOperationContext(RequestedOperation.DEAL_SETTLEMENT_RELEASE) OperationContext context,
            @PathVariable String dealId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RequestSettlementReleaseHttpRequest request,
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE) String correlationId) {
        SettlementReadDtos.ReleaseOperationView created = initiateService.initiate(context, id(dealId),
                request.expectedDealVersion(), request.expectedSettlementVersion(),
                request.expectedFulfillmentVersion(), request.expectedFundingUnitVersion(),
                id(idempotencyKey), id(correlationId));
        URI location = URI.create("/api/v1/release-operations/" + created.id());
        return ResponseEntity.status(202).location(location).body(created);
    }

    @GetMapping("/api/v1/release-operations/{operationId}")
    SettlementReadDtos.ReleaseOperationView getReleaseOperation(
            @ResolvedOperationContext(RequestedOperation.RELEASE_OPERATION_READ) OperationContext context,
            @PathVariable String operationId) {
        return readService.getOperation(context, id(operationId));
    }

    @PostMapping("/api/v1/release-operations/{operationId}/reconcile")
    ResponseEntity<SettlementReadDtos.ReleaseOperationView> reconcileReleaseOperation(
            @ResolvedOperationContext(RequestedOperation.RELEASE_OPERATION_RECONCILE) OperationContext context,
            @PathVariable String operationId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ReconcileReleaseOperationHttpRequest request,
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE) String correlationId) {
        UUID parsedOperationId = id(operationId);
        SettlementReadDtos.ReleaseOperationView result = reconcileService.reconcile(context, parsedOperationId,
                request.expectedVersion(), id(idempotencyKey), id(correlationId));
        URI location = URI.create("/api/v1/release-operations/" + parsedOperationId);
        return ResponseEntity.status(202).location(location).body(result);
    }

    record RequestSettlementReleaseHttpRequest(
            @Min(0) @Max(9007199254740991L) long expectedDealVersion,
            @Min(0) @Max(9007199254740991L) long expectedSettlementVersion,
            @Min(0) @Max(9007199254740991L) long expectedFulfillmentVersion,
            @Min(0) @Max(9007199254740991L) long expectedFundingUnitVersion) { }

    record ReconcileReleaseOperationHttpRequest(
            @Min(0) @Max(9007199254740991L) long expectedVersion) { }

    private static UUID id(String value) {
        if (value == null || value.isBlank()) {
            throw new SettlementMalformedRequestException();
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new SettlementMalformedRequestException();
        }
    }
}
