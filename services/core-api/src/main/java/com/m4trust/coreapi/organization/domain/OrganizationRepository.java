package com.m4trust.coreapi.organization.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Organization-owned persistence port. JDBC is an infrastructure detail. */
public interface OrganizationRepository {

  Optional<UUID> findTenantIdForUser(UUID userId);

  void insertLegalEntity(
      UUID legalEntityId, UUID tenantId, String legalName, String registrationNumber);

  void insertMembership(
      UUID membershipId, UUID tenantId, UUID legalEntityId, UUID userId, LegalEntityRole role);

  List<LegalEntityMembership> findMemberships(UUID userId);

  Optional<ResolvedMembership> findAuthorizedMembership(UUID userId, UUID legalEntityId);

  Optional<InvitationLegalEntityQueryPort.InvitationLegalEntityMembership> findCurrentMembership(
      UUID userId, UUID legalEntityId);

  Map<UUID, String> findLegalNames(Set<UUID> legalEntityIds);

  Optional<LegalEntity> findLegalEntity(UUID tenantId, UUID legalEntityId);

  List<MemberAssignment> findMemberAssignments(UUID tenantId, UUID legalEntityId);

  record MemberAssignment(UUID userId, LegalEntityRole role) {}

  record ResolvedMembership(UUID tenantId, LegalEntityRole role) {}
}
