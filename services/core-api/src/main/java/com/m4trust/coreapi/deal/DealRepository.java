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
import org.springframework.transaction.annotation.Transactional;

@Repository
class DealRepository {

    private static final String SELECT_VISIBLE_DEALS = """
            SELECT
                deal.id,
                deal.tenant_id,
                deal.reference,
                deal.title,
                deal.description,
                deal.deal_status,
                deal.initiator_legal_entity_id,
                deal.created_by,
                deal.created_at,
                deal.updated_at,
                deal.version
            FROM deal
            WHERE EXISTS (
                  SELECT 1
                  FROM deal_participant participant
                  WHERE participant.deal_id = deal.id
                    AND participant.legal_entity_id = ?
                    AND participant.legal_entity_tenant_id = ?
              )
            """;

    private final JdbcTemplate jdbcTemplate;

    DealRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    String nextReference() {
        return jdbcTemplate.queryForObject("""
                SELECT 'DL-' || lpad(
                    nextval('deal_reference_sequence')::text,
                    10,
                    '0'
                )
                """, String.class);
    }

    @Transactional
    void insert(DealRecord deal, UUID initiatorLegalEntityTenantId) {
        jdbcTemplate.update("""
                INSERT INTO deal (
                    id,
                    tenant_id,
                    reference,
                    title,
                    description,
                    deal_status,
                    initiator_legal_entity_id,
                    created_by,
                    created_at,
                    updated_at,
                    version
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                deal.id(),
                deal.tenantId(),
                deal.reference(),
                deal.title(),
                deal.description(),
                deal.status().name(),
                deal.initiatorLegalEntityId(),
                deal.createdBy(),
                Timestamp.from(deal.createdAt()),
                Timestamp.from(deal.updatedAt()),
                deal.version());
        jdbcTemplate.update("""
                INSERT INTO deal_participant (
                    deal_id,
                    tenant_id,
                    legal_entity_id,
                    legal_entity_tenant_id,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?)
                """,
                deal.id(),
                deal.tenantId(),
                deal.initiatorLegalEntityId(),
                initiatorLegalEntityTenantId,
                Timestamp.from(deal.createdAt()));
    }

    Optional<DealRecord> findVisibleById(
            UUID legalEntityTenantId, UUID legalEntityId, UUID dealId) {
        return jdbcTemplate.query(
                        SELECT_VISIBLE_DEALS + " AND deal.id = ?",
                        this::mapDeal,
                        legalEntityId,
                        legalEntityTenantId,
                        dealId)
                .stream()
                .findFirst();
    }

    List<DealRecord> findVisiblePage(
            UUID legalEntityTenantId,
            UUID legalEntityId,
            DealStatus status,
            DealSort sort,
            int limit,
            long offset) {
        String statusPredicate = status == null
                ? ""
                : " AND deal.deal_status = ?";
        String sql = SELECT_VISIBLE_DEALS
                + statusPredicate
                + " ORDER BY "
                + sort.orderByClause
                + " LIMIT ? OFFSET ?";
        if (status == null) {
            return jdbcTemplate.query(
                    sql, this::mapDeal, legalEntityId, legalEntityTenantId,
                    limit, offset);
        }
        return jdbcTemplate.query(
                sql,
                this::mapDeal,
                legalEntityId,
                legalEntityTenantId,
                status.name(),
                limit,
                offset);
    }

    long countVisible(
            UUID legalEntityTenantId, UUID legalEntityId, DealStatus status) {
        String statusPredicate = status == null
                ? ""
                : " AND deal.deal_status = ?";
        String sql = """
                SELECT count(*)
                FROM deal
                WHERE EXISTS (
                      SELECT 1
                      FROM deal_participant participant
                      WHERE participant.deal_id = deal.id
                        AND participant.legal_entity_id = ?
                        AND participant.legal_entity_tenant_id = ?
                  )
                """ + statusPredicate;
        if (status == null) {
            return jdbcTemplate.queryForObject(
                    sql, Long.class, legalEntityId, legalEntityTenantId);
        }
        return jdbcTemplate.queryForObject(
                sql, Long.class, legalEntityId, legalEntityTenantId,
                status.name());
    }

    boolean updateBasicFields(
            UUID legalEntityTenantId,
            UUID legalEntityId,
            UUID dealId,
            long expectedVersion,
            String title,
            String description,
            Instant updatedAt) {
        return jdbcTemplate.update("""
                UPDATE deal
                SET title = ?,
                    description = ?,
                    updated_at = ?,
                    version = version + 1
                WHERE id = ?
                  AND version = ?
                  AND deal_status IN ('DRAFT', 'ACTIVE')
                  AND initiator_legal_entity_id = ?
                  AND EXISTS (
                      SELECT 1
                      FROM deal_participant participant
                      WHERE participant.deal_id = deal.id
                        AND participant.legal_entity_id = ?
                        AND participant.legal_entity_tenant_id = ?
                  )
                """,
                title,
                description,
                Timestamp.from(updatedAt),
                dealId,
                expectedVersion,
                legalEntityId,
                legalEntityId,
                legalEntityTenantId) == 1;
    }

    boolean updateStatus(
            UUID legalEntityTenantId,
            UUID legalEntityId,
            UUID dealId,
            DealStatus expectedStatus,
            DealStatus nextStatus,
            long expectedVersion,
            Instant updatedAt) {
        return jdbcTemplate.update("""
                UPDATE deal
                SET deal_status = ?,
                    updated_at = ?,
                    version = version + 1
                WHERE id = ?
                  AND deal_status = ?
                  AND version = ?
                  AND initiator_legal_entity_id = ?
                  AND EXISTS (
                      SELECT 1
                      FROM deal_participant participant
                      WHERE participant.deal_id = deal.id
                        AND participant.legal_entity_id = ?
                        AND participant.legal_entity_tenant_id = ?
                  )
                """,
                nextStatus.name(),
                Timestamp.from(updatedAt),
                dealId,
                expectedStatus.name(),
                expectedVersion,
                legalEntityId,
                legalEntityId,
                legalEntityTenantId) == 1;
    }

    private DealRecord mapDeal(
            ResultSet resultSet, int rowNumber) throws SQLException {
        return new DealRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getString("reference"),
                resultSet.getString("title"),
                resultSet.getString("description"),
                DealStatus.valueOf(resultSet.getString("deal_status")),
                resultSet.getObject("initiator_legal_entity_id", UUID.class),
                resultSet.getObject("created_by", UUID.class),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getLong("version"));
    }

    enum DealSort {
        CREATED_AT_ASC("deal.created_at ASC, deal.id ASC"),
        CREATED_AT_DESC("deal.created_at DESC, deal.id DESC"),
        TITLE_ASC("lower(deal.title) ASC, deal.id ASC"),
        TITLE_DESC("lower(deal.title) DESC, deal.id DESC");

        private final String orderByClause;

        DealSort(String orderByClause) {
            this.orderByClause = orderByClause;
        }
    }

    record DealRecord(
            UUID id,
            UUID tenantId,
            String reference,
            String title,
            String description,
            DealStatus status,
            UUID initiatorLegalEntityId,
            UUID createdBy,
            Instant createdAt,
            Instant updatedAt,
            long version) {
    }
}
