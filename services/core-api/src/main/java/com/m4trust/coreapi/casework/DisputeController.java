package com.m4trust.coreapi.casework;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class DisputeController {

    private final DisputeService service;

    DisputeController(DisputeService service) {
        this.service = service;
    }

    @PostMapping("/deals/{dealId}/disputes")
    ResponseEntity<DisputeDetail> openDispute(
            @ResolvedOperationContext(RequestedOperation.DISPUTE_OPEN)
            OperationContext context,
            @PathVariable String dealId,
            @Valid @RequestBody OpenDisputeRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE) String correlationId) {
        UUID parsedDealId = uuid(dealId);
        DisputeDetail created = service.openDispute(
                context, parsedDealId, request, uuid(idempotencyKey), uuid(correlationId));
        return ResponseEntity.created(URI.create("/api/v1/deals/" + parsedDealId + "/disputes/" + created.id()))
                .body(created);
    }

    @GetMapping("/deals/{dealId}/disputes")
    DisputePage listDisputes(
            @ResolvedOperationContext(RequestedOperation.DISPUTE_LIST_READ)
            OperationContext context,
            @PathVariable String dealId,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size,
            @RequestParam(defaultValue = "openedAt,desc") String sort) {
        return service.listDisputes(
                context, uuid(dealId), DisputeQuery.parse(page, size, sort));
    }

    @GetMapping("/deals/{dealId}/disputes/{disputeId}")
    DisputeDetail getDispute(
            @ResolvedOperationContext(RequestedOperation.DISPUTE_DETAIL_READ)
            OperationContext context,
            @PathVariable String dealId,
            @PathVariable String disputeId) {
        return service.getDispute(context, uuid(dealId), uuid(disputeId));
    }

    private UUID uuid(String value) {
        if (value == null || value.isBlank()) {
            throw new CaseworkExceptions.MalformedRequest();
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new CaseworkExceptions.MalformedRequest();
        }
    }
}
