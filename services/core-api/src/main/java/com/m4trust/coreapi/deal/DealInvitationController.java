package com.m4trust.coreapi.deal;

import java.util.UUID;

import com.m4trust.coreapi.api.CorrelationIdFilter;
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
import com.m4trust.coreapi.organization.ResolvedOperationContext;
import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/deal-invitations")
class DealInvitationController {

    private final DealInvitationService service;

    DealInvitationController(DealInvitationService service) {
        this.service = service;
    }

    @GetMapping("/incoming")
    IncomingDealInvitationPage incoming(
            Authentication authentication,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size) {
        return service.listIncoming(authenticatedUserId(authentication),
                InvitationPageQuery.parse(page, size));
    }

    @PostMapping("/{invitationId}/accept")
    IncomingDealInvitation accept(
            Authentication authentication,
            @PathVariable String invitationId,
            @Valid @RequestBody AcceptDealInvitationRequest request,
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE)
            String correlationId) {
        return service.accept(authenticatedUserId(authentication),
                parseInvitationId(invitationId), request,
                UUID.fromString(correlationId));
    }

    @PostMapping("/{invitationId}/reject")
    IncomingDealInvitation reject(
            Authentication authentication,
            @PathVariable String invitationId,
            @Valid @RequestBody DealInvitationTerminalActionRequest request,
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE)
            String correlationId) {
        return service.reject(authenticatedUserId(authentication),
                parseInvitationId(invitationId), request,
                UUID.fromString(correlationId));
    }

    @PostMapping("/{invitationId}/revoke")
    DealInvitationProjection revoke(
            @ResolvedOperationContext(RequestedOperation.DEAL_INVITATION_REVOKE)
            OperationContext context,
            @PathVariable String invitationId,
            @Valid @RequestBody DealInvitationTerminalActionRequest request,
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE)
            String correlationId) {
        return service.revoke(context, parseInvitationId(invitationId), request,
                UUID.fromString(correlationId));
    }

    private UUID parseInvitationId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new MalformedDealRequestException();
        }
    }

    private UUID authenticatedUserId(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Authenticated principal does not carry a UUID", exception);
        }
    }
}
