package com.m4trust.coreapi.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class OperationContextServiceTest {
    private final OrganizationRepository repository = mock(OrganizationRepository.class);
    private final OperationContextService service = new OperationContextService(repository);

    @Test
    void resolvesAdminMembershipRoleIntoTheOperationContext() {
        assertResolvedRole(LegalEntityRole.ADMIN);
    }

    @Test
    void resolvesMemberMembershipRoleIntoTheOperationContext() {
        assertResolvedRole(LegalEntityRole.MEMBER);
    }

    private void assertResolvedRole(LegalEntityRole role) {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        when(repository.findAuthorizedMembership(userId, entityId))
                .thenReturn(Optional.of(new OrganizationRepository.ResolvedMembership(tenantId, role)));

        OperationContext context = service.resolve(
                userId.toString(), entityId.toString(), entityId.toString(),
                RequestedOperation.DEAL_RATIFICATION_PACKAGE_APPROVE, true);

        assertEquals(userId, context.authenticatedUserId());
        assertEquals(tenantId, context.tenantId());
        assertEquals(entityId, context.activeLegalEntityId());
        assertEquals(role, context.activeLegalEntityRole());
        assertEquals(RequestedOperation.DEAL_RATIFICATION_PACKAGE_APPROVE,
                context.requestedOperation());
    }
}
