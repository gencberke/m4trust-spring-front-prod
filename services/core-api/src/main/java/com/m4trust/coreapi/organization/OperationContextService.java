package com.m4trust.coreapi.organization;

import java.util.UUID;

import org.springframework.stereotype.Service;

/**
 * Single application-layer authority for legal-entity request context parsing
 * and membership verification.
 */
@Service
class OperationContextService {

    private final OrganizationRepository repository;

    OperationContextService(OrganizationRepository repository) {
        this.repository = repository;
    }

    OperationContext resolve(String authenticatedUserIdValue,
            String requestedLegalEntityIdValue,
            String activeLegalEntityIdValue,
            RequestedOperation requestedOperation,
            boolean pathMatchRequired) {
        UUID authenticatedUserId = parseAuthenticatedUserId(
                authenticatedUserIdValue);
        UUID activeLegalEntityId = parseActiveLegalEntityId(
                activeLegalEntityIdValue);
        if (pathMatchRequired) {
            UUID requestedLegalEntityId = parseRequestedLegalEntityId(
                    requestedLegalEntityIdValue);
            if (!requestedLegalEntityId.equals(activeLegalEntityId)) {
                throw new LegalEntityAccessDeniedException();
            }
        }

        OrganizationRepository.ResolvedMembership membership = repository.findAuthorizedMembership(
                        authenticatedUserId, activeLegalEntityId)
                .orElseThrow(LegalEntityNotFoundException::new);
        return new OperationContext(authenticatedUserId, membership.tenantId(),
                activeLegalEntityId, membership.role(), requestedOperation);
    }

    private UUID parseAuthenticatedUserId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Authenticated principal does not carry a UUID", exception);
        }
    }

    private UUID parseRequestedLegalEntityId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new MalformedLegalEntityIdException();
        }
    }

    private UUID parseActiveLegalEntityId(String value) {
        if (value == null || value.isBlank()) {
            throw new LegalEntityAccessDeniedException();
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new LegalEntityAccessDeniedException();
        }
    }
}
