package com.m4trust.coreapi.deal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class DealInvitationRepository {

    private static final String SELECT_INVITATION = """
            SELECT
                invitation.id AS invitation_id,
                invitation.tenant_id AS invitation_tenant_id,
                invitation.deal_id AS invitation_deal_id,
                invitation.recipient_email,
                invitation.invitation_status,
                invitation.accepted_legal_entity_id,
                invitation.accepted_legal_entity_tenant_id,
                invitation.created_at AS invitation_created_at,
                invitation.updated_at AS invitation_updated_at,
                invitation.version AS invitation_version,
                deal.id AS deal_id,
                deal.tenant_id AS deal_tenant_id,
                deal.reference,
                deal.title,
                deal.description,
                deal.deal_status,
                deal.buyer_legal_entity_id,
                deal.seller_legal_entity_id,
                deal.current_document_id,
                deal.initiator_legal_entity_id,
                deal.created_by,
                deal.created_at AS deal_created_at,
                deal.updated_at AS deal_updated_at,
                deal.version AS deal_version
            FROM deal_invitation invitation
            JOIN deal ON deal.id = invitation.deal_id
                     AND deal.tenant_id = invitation.tenant_id
            """;

    private final JdbcTemplate jdbcTemplate;

    DealInvitationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void insert(InvitationRecord invitation) {
        jdbcTemplate.update("""
                INSERT INTO deal_invitation (
                    id, tenant_id, deal_id, recipient_email, invitation_status,
                    accepted_legal_entity_id, accepted_legal_entity_tenant_id,
                    created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, invitation.id(), invitation.tenantId(), invitation.dealId(),
                invitation.recipientEmail(), invitation.status().name(),
                invitation.acceptedLegalEntityId(),
                invitation.acceptedLegalEntityTenantId(),
                Timestamp.from(invitation.createdAt()), Timestamp.from(invitation.updatedAt()),
                invitation.version());
    }

    Optional<InvitationRecord> findById(UUID invitationId) {
        return jdbcTemplate.query(SELECT_INVITATION + " WHERE invitation.id = ?",
                this::mapInvitation, invitationId).stream().findFirst();
    }

    Optional<InvitationRecord> findByIdAndRecipient(UUID invitationId,
            String recipientEmail) {
        return jdbcTemplate.query(SELECT_INVITATION + """
                WHERE invitation.id = ?
                  AND invitation.recipient_email = ?
                """, this::mapInvitation, invitationId, recipientEmail)
                .stream().findFirst();
    }

    List<InvitationRecord> findByDealId(UUID dealId, int limit, long offset) {
        return jdbcTemplate.query(SELECT_INVITATION + """
                WHERE invitation.deal_id = ?
                ORDER BY invitation.created_at DESC, invitation.id DESC
                LIMIT ? OFFSET ?
                """, this::mapInvitation, dealId, limit, offset);
    }

    long countByDealId(UUID dealId) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM deal_invitation WHERE deal_id = ?
                """, Long.class, dealId);
    }

    List<InvitationRecord> findPendingForRecipient(String recipientEmail,
            int limit, long offset) {
        return jdbcTemplate.query(SELECT_INVITATION + """
                WHERE invitation.recipient_email = ?
                  AND invitation.invitation_status = 'PENDING'
                ORDER BY invitation.created_at DESC, invitation.id DESC
                LIMIT ? OFFSET ?
                """, this::mapInvitation, recipientEmail, limit, offset);
    }

    long countPendingForRecipient(String recipientEmail) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM deal_invitation
                WHERE recipient_email = ? AND invitation_status = 'PENDING'
                """, Long.class, recipientEmail);
    }

    boolean acceptPending(UUID invitationId, String recipientEmail,
            long expectedVersion, UUID legalEntityId, UUID legalEntityTenantId,
            Instant now) {
        return jdbcTemplate.update("""
                UPDATE deal_invitation
                SET invitation_status = 'ACCEPTED',
                    accepted_legal_entity_id = ?,
                    accepted_legal_entity_tenant_id = ?,
                    updated_at = ?,
                    version = version + 1
                WHERE id = ?
                  AND recipient_email = ?
                  AND invitation_status = 'PENDING'
                  AND version = ?
                """, legalEntityId, legalEntityTenantId, Timestamp.from(now),
                invitationId, recipientEmail, expectedVersion) == 1;
    }

    boolean rejectPending(UUID invitationId, String recipientEmail,
            long expectedVersion, Instant now) {
        return transitionPending(invitationId, recipientEmail, expectedVersion,
                DealInvitationStatus.REJECTED, now);
    }

    boolean revokePending(UUID invitationId, UUID initiatorLegalEntityId,
            long expectedVersion, Instant now) {
        return jdbcTemplate.update("""
                UPDATE deal_invitation invitation
                SET invitation_status = 'REVOKED',
                    updated_at = ?,
                    version = invitation.version + 1
                FROM deal
                WHERE invitation.id = ?
                  AND invitation.deal_id = deal.id
                  AND invitation.tenant_id = deal.tenant_id
                  AND deal.initiator_legal_entity_id = ?
                  AND deal.deal_status = 'DRAFT'
                  AND invitation.invitation_status = 'PENDING'
                  AND invitation.version = ?
                """, Timestamp.from(now), invitationId, initiatorLegalEntityId,
                expectedVersion) == 1;
    }

    void insertParticipant(UUID dealId, UUID dealTenantId, UUID legalEntityId,
            UUID legalEntityTenantId, Instant createdAt) {
        jdbcTemplate.update("""
                INSERT INTO deal_participant (
                    deal_id, tenant_id, legal_entity_id,
                    legal_entity_tenant_id, created_at
                ) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (deal_id, legal_entity_id) DO NOTHING
                """, dealId, dealTenantId, legalEntityId, legalEntityTenantId,
                Timestamp.from(createdAt));
    }

    private boolean transitionPending(UUID invitationId, String recipientEmail,
            long expectedVersion, DealInvitationStatus next, Instant now) {
        return jdbcTemplate.update("""
                UPDATE deal_invitation
                SET invitation_status = ?, updated_at = ?, version = version + 1
                WHERE id = ?
                  AND recipient_email = ?
                  AND invitation_status = 'PENDING'
                  AND version = ?
                """, next.name(), Timestamp.from(now), invitationId,
                recipientEmail, expectedVersion) == 1;
    }

    private InvitationRecord mapInvitation(ResultSet resultSet, int rowNumber)
            throws SQLException {
        DealRepository.DealRecord deal = new DealRepository.DealRecord(
                resultSet.getObject("deal_id", UUID.class),
                resultSet.getObject("deal_tenant_id", UUID.class),
                resultSet.getString("reference"), resultSet.getString("title"),
                resultSet.getString("description"),
                DealStatus.valueOf(resultSet.getString("deal_status")),
                resultSet.getObject("buyer_legal_entity_id", UUID.class),
                resultSet.getObject("seller_legal_entity_id", UUID.class),
                resultSet.getObject("current_document_id", UUID.class),
                resultSet.getObject("initiator_legal_entity_id", UUID.class),
                resultSet.getObject("created_by", UUID.class),
                resultSet.getTimestamp("deal_created_at").toInstant(),
                resultSet.getTimestamp("deal_updated_at").toInstant(),
                resultSet.getLong("deal_version"));
        return new InvitationRecord(
                resultSet.getObject("invitation_id", UUID.class),
                resultSet.getObject("invitation_tenant_id", UUID.class),
                resultSet.getObject("invitation_deal_id", UUID.class),
                resultSet.getString("recipient_email"),
                DealInvitationStatus.valueOf(resultSet.getString("invitation_status")),
                resultSet.getObject("accepted_legal_entity_id", UUID.class),
                resultSet.getObject("accepted_legal_entity_tenant_id", UUID.class),
                resultSet.getTimestamp("invitation_created_at").toInstant(),
                resultSet.getTimestamp("invitation_updated_at").toInstant(),
                resultSet.getLong("invitation_version"), deal);
    }

    record InvitationRecord(UUID id, UUID tenantId, UUID dealId,
            String recipientEmail, DealInvitationStatus status,
            UUID acceptedLegalEntityId, UUID acceptedLegalEntityTenantId,
            Instant createdAt, Instant updatedAt, long version,
            DealRepository.DealRecord deal) {
    }
}
