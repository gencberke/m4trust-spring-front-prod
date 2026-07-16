package com.m4trust.coreapi.organization;

import java.net.URI;
import java.util.UUID;

import com.m4trust.coreapi.api.CorrelationIdFilter;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/legal-entities")
public class LegalEntityController {

    private final LegalEntityService service;

    LegalEntityController(LegalEntityService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<LegalEntity> create(
            @Valid @RequestBody CreateLegalEntityRequest request,
            Authentication authentication,
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE)
            String correlationId) {
        LegalEntity legalEntity = service.create(
                authenticatedUserId(authentication),
                request,
                UUID.fromString(correlationId));
        return ResponseEntity.created(URI.create(
                        "/api/v1/legal-entities/" + legalEntity.id()))
                .body(legalEntity);
    }

    @GetMapping
    LegalEntityMembershipList list(Authentication authentication) {
        return new LegalEntityMembershipList(
                service.findMemberships(
                        authenticatedUserId(authentication)));
    }

    @GetMapping("/{legalEntityId}")
    LegalEntity get(
            @ResolvedOperationContext(
                    value = RequestedOperation.LEGAL_ENTITY_DETAIL_READ,
                    legalEntityPathVariable = "legalEntityId")
            OperationContext context) {
        return service.get(context);
    }

    @GetMapping("/{legalEntityId}/members")
    LegalEntityMemberList listMembers(
            @ResolvedOperationContext(
                    value = RequestedOperation.LEGAL_ENTITY_MEMBERS_READ,
                    legalEntityPathVariable = "legalEntityId")
            OperationContext context) {
        return service.listMembers(context);
    }

    private UUID authenticatedUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
