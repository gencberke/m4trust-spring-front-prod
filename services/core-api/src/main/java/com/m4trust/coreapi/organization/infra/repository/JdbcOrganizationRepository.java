package com.m4trust.coreapi.organization.infra.repository;

import com.m4trust.coreapi.organization.domain.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOrganizationRepository implements OrganizationRepository {

  private final JdbcTemplate jdbcTemplate;

  JdbcOrganizationRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Optional<UUID> findTenantIdForUser(UUID userId) {
    return jdbcTemplate
        .query(
            """
                        SELECT tenant_id
                        FROM tenant_user
                        WHERE user_id = ?
                        """,
            (resultSet, rowNumber) -> resultSet.getObject("tenant_id", UUID.class),
            userId)
        .stream()
        .findFirst();
  }

  @Override
  public void insertLegalEntity(
      UUID legalEntityId, UUID tenantId, String legalName, String registrationNumber) {
    jdbcTemplate.update(
        """
                INSERT INTO legal_entity (
                    id,
                    tenant_id,
                    legal_name,
                    registration_number
                )
                VALUES (?, ?, ?, ?)
                """,
        legalEntityId,
        tenantId,
        legalName,
        registrationNumber);
  }

  @Override
  public void insertMembership(
      UUID membershipId, UUID tenantId, UUID legalEntityId, UUID userId, LegalEntityRole role) {
    jdbcTemplate.update(
        """
                INSERT INTO legal_entity_membership (
                    id,
                    tenant_id,
                    legal_entity_id,
                    user_id,
                    role
                )
                VALUES (?, ?, ?, ?, ?)
                """,
        membershipId,
        tenantId,
        legalEntityId,
        userId,
        role.name());
  }

  @Override
  public List<LegalEntityMembership> findMemberships(UUID userId) {
    return jdbcTemplate.query(
        """
                SELECT
                    legal_entity.id AS legal_entity_id,
                    legal_entity.legal_name,
                    legal_entity.registration_number,
                    membership.role
                FROM legal_entity_membership membership
                JOIN legal_entity
                  ON legal_entity.id = membership.legal_entity_id
                 AND legal_entity.tenant_id = membership.tenant_id
                WHERE membership.user_id = ?
                ORDER BY lower(legal_entity.legal_name), legal_entity.id
                """,
        this::mapMembership,
        userId);
  }

  @Override
  public Optional<ResolvedMembership> findAuthorizedMembership(UUID userId, UUID legalEntityId) {
    return jdbcTemplate
        .query(
            """
                        SELECT membership.tenant_id, membership.role
                        FROM legal_entity_membership membership
                        JOIN legal_entity
                          ON legal_entity.id = membership.legal_entity_id
                         AND legal_entity.tenant_id = membership.tenant_id
                        WHERE membership.user_id = ?
                          AND membership.legal_entity_id = ?
                        """,
            (resultSet, rowNumber) ->
                new ResolvedMembership(
                    resultSet.getObject("tenant_id", UUID.class),
                    LegalEntityRole.valueOf(resultSet.getString("role"))),
            userId,
            legalEntityId)
        .stream()
        .findFirst();
  }

  @Override
  public Optional<InvitationLegalEntityQueryPort.InvitationLegalEntityMembership>
      findCurrentMembership(UUID userId, UUID legalEntityId) {
    return jdbcTemplate
        .query(
            """
                        SELECT legal_entity_id, tenant_id
                        FROM legal_entity_membership
                        WHERE user_id = ?
                          AND legal_entity_id = ?
                        """,
            (resultSet, rowNumber) ->
                new InvitationLegalEntityQueryPort.InvitationLegalEntityMembership(
                    resultSet.getObject("legal_entity_id", UUID.class),
                    resultSet.getObject("tenant_id", UUID.class)),
            userId,
            legalEntityId)
        .stream()
        .findFirst();
  }

  @Override
  public Map<UUID, String> findLegalNames(Set<UUID> legalEntityIds) {
    if (legalEntityIds.isEmpty()) {
      return Map.of();
    }
    String placeholders =
        String.join(", ", java.util.Collections.nCopies(legalEntityIds.size(), "?"));
    return jdbcTemplate.query(
        """
                        SELECT id, legal_name
                        FROM legal_entity
                        WHERE id IN (
                        """
            + placeholders
            + ")",
        resultSet -> {
          Map<UUID, String> names = new java.util.HashMap<>();
          while (resultSet.next()) {
            names.put(resultSet.getObject("id", UUID.class), resultSet.getString("legal_name"));
          }
          return Map.copyOf(names);
        },
        legalEntityIds.toArray());
  }

  @Override
  public Optional<LegalEntity> findLegalEntity(UUID tenantId, UUID legalEntityId) {
    return jdbcTemplate
        .query(
            """
                        SELECT id, legal_name, registration_number
                        FROM legal_entity
                        WHERE tenant_id = ?
                          AND id = ?
                        """,
            this::mapLegalEntity,
            tenantId,
            legalEntityId)
        .stream()
        .findFirst();
  }

  @Override
  public List<MemberAssignment> findMemberAssignments(UUID tenantId, UUID legalEntityId) {
    return jdbcTemplate.query(
        """
                SELECT user_id, role
                FROM legal_entity_membership
                WHERE tenant_id = ?
                  AND legal_entity_id = ?
                ORDER BY created_at, id
                """,
        (resultSet, rowNumber) ->
            new MemberAssignment(
                resultSet.getObject("user_id", UUID.class),
                LegalEntityRole.valueOf(resultSet.getString("role"))),
        tenantId,
        legalEntityId);
  }

  private LegalEntityMembership mapMembership(ResultSet resultSet, int rowNumber)
      throws SQLException {
    return new LegalEntityMembership(
        resultSet.getObject("legal_entity_id", UUID.class),
        resultSet.getString("legal_name"),
        resultSet.getString("registration_number"),
        LegalEntityRole.valueOf(resultSet.getString("role")));
  }

  private LegalEntity mapLegalEntity(ResultSet resultSet, int rowNumber) throws SQLException {
    return new LegalEntity(
        resultSet.getObject("id", UUID.class),
        resultSet.getString("legal_name"),
        resultSet.getString("registration_number"));
  }
}
