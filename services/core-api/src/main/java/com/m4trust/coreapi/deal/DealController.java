package com.m4trust.coreapi.deal;

import java.net.URI;
import java.util.UUID;

import com.m4trust.coreapi.api.CorrelationIdFilter;
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
import com.m4trust.coreapi.organization.ResolvedOperationContext;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/deals")
public class DealController {

    private final DealService service;
    private final DealInvitationService invitationService;

    DealController(DealService service,
            DealInvitationService invitationService) {
        this.service = service;
        this.invitationService = invitationService;
    }

    @PostMapping
    ResponseEntity<DealDetail> create(
            @ResolvedOperationContext(RequestedOperation.DEAL_CREATE)
            OperationContext context,
            @Valid @RequestBody CreateDealRequest request,
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE)
            String correlationId) {
        DealDetail created = service.create(
                context, request, UUID.fromString(correlationId));
        return ResponseEntity.created(
                        URI.create("/api/v1/deals/" + created.id()))
                .body(created);
    }

    @GetMapping
    DealPage list(
            @ResolvedOperationContext(RequestedOperation.DEAL_LIST_READ)
            OperationContext context,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return service.list(
                context, DealQuery.parse(page, size, status, sort));
    }

    @GetMapping("/{dealId}")
    DealDetail get(
            @ResolvedOperationContext(RequestedOperation.DEAL_DETAIL_READ)
            OperationContext context,
            @PathVariable String dealId) {
        return service.get(context, parseDealId(dealId));
    }

    @PatchMapping("/{dealId}")
    DealDetail update(
            @ResolvedOperationContext(RequestedOperation.DEAL_UPDATE)
            OperationContext context,
            @PathVariable String dealId,
            @Valid @RequestBody UpdateDealRequest request,
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE)
            String correlationId) {
        return service.update(context, parseDealId(dealId), request,
                UUID.fromString(correlationId));
    }

    @PatchMapping("/{dealId}/parties")
    DealDetail updateParties(
            @ResolvedOperationContext(RequestedOperation.DEAL_PARTIES_UPDATE)
            OperationContext context,
            @PathVariable String dealId,
            @Valid @RequestBody UpdateDealPartiesRequest request,
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE)
            String correlationId) {
        return service.updateParties(context, parseDealId(dealId), request,
                UUID.fromString(correlationId));
    }

    @PostMapping("/{dealId}/cancel")
    DealDetail cancel(
            @ResolvedOperationContext(RequestedOperation.DEAL_CANCEL)
            OperationContext context,
            @PathVariable String dealId,
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE)
            String correlationId) {
        return service.cancel(context, parseDealId(dealId),
                UUID.fromString(correlationId));
    }

    @PostMapping("/{dealId}/invitations")
    ResponseEntity<DealInvitationProjection> createInvitation(
            @ResolvedOperationContext(RequestedOperation.DEAL_INVITATION_CREATE)
            OperationContext context,
            @PathVariable String dealId,
            @RequestHeader(value = "Idempotency-Key", required = false)
            String idempotencyKey,
            @Valid @RequestBody CreateDealInvitationRequest request,
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE)
            String correlationId) {
        DealInvitationProjection created = invitationService.create(context,
                parseDealId(dealId), request, parseIdempotencyKey(idempotencyKey),
                UUID.fromString(correlationId));
        return ResponseEntity.created(URI.create(
                        "/api/v1/deal-invitations/" + created.id()))
                .body(created);
    }

    @GetMapping("/{dealId}/invitations")
    DealInvitationPage listInvitations(
            @ResolvedOperationContext(RequestedOperation.DEAL_INVITATION_LIST_READ)
            OperationContext context,
            @PathVariable String dealId,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size) {
        return invitationService.listForDeal(context, parseDealId(dealId),
                InvitationPageQuery.parse(page, size));
    }

    private UUID parseDealId(String dealId) {
        try {
            return UUID.fromString(dealId);
        } catch (IllegalArgumentException exception) {
            throw new MalformedDealRequestException();
        }
    }

    private UUID parseIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            throw new MalformedDealRequestException();
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new MalformedDealRequestException();
        }
    }
}
