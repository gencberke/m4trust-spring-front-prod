package com.m4trust.coreapi.organization;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.m4trust.coreapi.audit.AuditAppendPort;
import com.m4trust.coreapi.audit.AuditRecord;
import com.m4trust.coreapi.organization.IdentityMemberProjectionPort.IdentityMemberProjection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class LegalEntityService implements CurrentMembershipQueryPort,
        InvitationLegalEntityQueryPort {

    private static final String LEGAL_ENTITY_SUBJECT = "LEGAL_ENTITY";
    private static final String MEMBERSHIP_SUBJECT = "LEGAL_ENTITY_MEMBERSHIP";
    private static final String LEGAL_ENTITY_CREATED = "LEGAL_ENTITY_CREATED";
    private static final String MEMBERSHIP_ASSIGNED = "MEMBERSHIP_ASSIGNED";

    private final OrganizationRepository repository;
    private final IdentityMemberProjectionPort identityProjections;
    private final AuditAppendPort auditAppender;
    private final Clock clock;

    LegalEntityService(OrganizationRepository repository,
            IdentityMemberProjectionPort identityProjections,
            AuditAppendPort auditAppender, Clock clock) {
        this.repository = repository;
        this.identityProjections = identityProjections;
        this.auditAppender = auditAppender;
        this.clock = clock;
    }

    @Transactional
    LegalEntity create(UUID authenticatedUserId,
            CreateLegalEntityRequest request, UUID correlationId) {
        UUID tenantId = repository.findTenantIdForUser(authenticatedUserId)
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user has no tenant"));
        UUID legalEntityId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        Instant occurredAt = clock.instant();

        repository.insertLegalEntity(legalEntityId, tenantId,
                request.legalName(), request.registrationNumber());
        repository.insertMembership(membershipId, tenantId, legalEntityId,
                authenticatedUserId, LegalEntityRole.ADMIN);
        auditAppender.append(new AuditRecord(
                UUID.randomUUID(),
                tenantId,
                authenticatedUserId,
                legalEntityId,
                LEGAL_ENTITY_SUBJECT,
                legalEntityId,
                LEGAL_ENTITY_CREATED,
                correlationId,
                null,
                occurredAt));
        auditAppender.append(new AuditRecord(
                UUID.randomUUID(),
                tenantId,
                authenticatedUserId,
                legalEntityId,
                MEMBERSHIP_SUBJECT,
                membershipId,
                MEMBERSHIP_ASSIGNED,
                correlationId,
                null,
                occurredAt));

        return new LegalEntity(legalEntityId, request.legalName(),
                request.registrationNumber());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LegalEntityMembership> findMemberships(
            UUID authenticatedUserId) {
        return List.copyOf(repository.findMemberships(authenticatedUserId));
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<InvitationLegalEntityMembership>
            findCurrentMembership(UUID userId, UUID legalEntityId) {
        return repository.findCurrentMembership(userId, legalEntityId);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<UUID> findTenantIdForUser(UUID userId) {
        return repository.findTenantIdForUser(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> findLegalNames(java.util.Set<UUID> legalEntityIds) {
        return repository.findLegalNames(legalEntityIds);
    }

    @Transactional(readOnly = true)
    LegalEntity get(OperationContext context) {
        requireOperation(context, RequestedOperation.LEGAL_ENTITY_DETAIL_READ);
        return repository.findLegalEntity(
                        context.tenantId(), context.activeLegalEntityId())
                .orElseThrow(LegalEntityNotFoundException::new);
    }

    @Transactional(readOnly = true)
    LegalEntityMemberList listMembers(OperationContext context) {
        requireOperation(context, RequestedOperation.LEGAL_ENTITY_MEMBERS_READ);
        List<OrganizationRepository.MemberAssignment> assignments =
                repository.findMemberAssignments(
                        context.tenantId(), context.activeLegalEntityId());
        Map<UUID, IdentityMemberProjection> identities =
                identityProjections.findByIds(assignments.stream()
                        .map(OrganizationRepository.MemberAssignment::userId)
                        .toList());
        List<LegalEntityMember> members = assignments.stream()
                .map(assignment -> toMember(assignment, identities))
                .toList();
        return new LegalEntityMemberList(members);
    }

    private LegalEntityMember toMember(
            OrganizationRepository.MemberAssignment assignment,
            Map<UUID, IdentityMemberProjection> identities) {
        IdentityMemberProjection identity = identities.get(assignment.userId());
        if (identity == null) {
            throw new IllegalStateException(
                    "Membership identity projection is unavailable");
        }
        return new LegalEntityMember(
                identity.userId(),
                identity.email(),
                identity.displayName(),
                assignment.role());
    }

    private void requireOperation(
            OperationContext context, RequestedOperation expected) {
        if (context.requestedOperation() != expected) {
            throw new IllegalArgumentException(
                    "Operation context does not match the requested use case");
        }
    }
}
