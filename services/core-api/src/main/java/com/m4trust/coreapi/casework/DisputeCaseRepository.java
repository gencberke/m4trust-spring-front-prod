package com.m4trust.coreapi.casework;

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
class DisputeCaseRepository {

    private final JdbcTemplate jdbcTemplate;

    DisputeCaseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void insert(DisputeCase.DisputeCaseRecord disputeCase) {
        jdbcTemplate.update("""
                INSERT INTO dispute_case (
                    id, deal_id, tenant_id, fulfillment_id, milestone_id, ratification_package_id,
                    fulfillment_status_at_open, fulfillment_version_at_open, milestone_version_at_open,
                    reason_code, subject, statement, status, opening_tenant_id, opening_legal_entity_id,
                    opening_user_id, opening_legal_name, opened_at, acknowledged_at, withdrawn_at,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                disputeCase.id(), disputeCase.dealId(), disputeCase.tenantId(), disputeCase.fulfillmentId(),
                disputeCase.milestoneId(), disputeCase.ratificationPackageId(), disputeCase.fulfillmentStatusAtOpen(),
                disputeCase.fulfillmentVersionAtOpen(), disputeCase.milestoneVersionAtOpen(),
                disputeCase.reasonCode().name(), disputeCase.subject(), disputeCase.statement(),
                disputeCase.status().name(), disputeCase.openingTenantId(), disputeCase.openingLegalEntityId(),
                disputeCase.openingUserId(), disputeCase.openingLegalName(), Timestamp.from(disputeCase.openedAt()),
                timestamp(disputeCase.acknowledgedAt()), timestamp(disputeCase.withdrawnAt()), disputeCase.version(),
                Timestamp.from(disputeCase.createdAt()), Timestamp.from(disputeCase.updatedAt()));
    }

    Optional<DisputeCase.DisputeCaseRecord> findById(UUID disputeId) {
        return jdbcTemplate.query("SELECT * FROM dispute_case WHERE id = ?", this::map, disputeId)
                .stream()
                .findFirst();
    }

    Optional<DisputeCase.DisputeCaseRecord> findByIdAndDealId(UUID disputeId, UUID dealId) {
        return jdbcTemplate.query("SELECT * FROM dispute_case WHERE id = ? AND deal_id = ?",
                this::map, disputeId, dealId).stream().findFirst();
    }

    Optional<DisputeCase.DisputeCaseRecord> findByIdAndDealIdForUpdate(UUID disputeId, UUID dealId) {
        return jdbcTemplate.query("SELECT * FROM dispute_case WHERE id = ? AND deal_id = ? FOR UPDATE",
                this::map, disputeId, dealId).stream().findFirst();
    }

    Optional<DisputeCase.DisputeCaseRecord> findActiveByDealId(UUID dealId) {
        return jdbcTemplate.query("""
                SELECT * FROM dispute_case
                WHERE deal_id = ? AND status IN ('OPEN', 'UNDER_REVIEW')
                """, this::map, dealId).stream().findFirst();
    }

    Optional<DisputeCase.DisputeCaseRecord> findActiveByDealIdForUpdate(UUID dealId) {
        return jdbcTemplate.query("""
                SELECT * FROM dispute_case
                WHERE deal_id = ? AND status IN ('OPEN', 'UNDER_REVIEW')
                FOR UPDATE
                """, this::map, dealId).stream().findFirst();
    }

    List<DisputeCase.DisputeCaseRecord> findByDealIdPage(
            UUID dealId, long offset, int limit, DisputeQuery.DisputeSort sort) {
        String orderBy = sort == DisputeQuery.DisputeSort.OPENED_AT_ASC
                ? "opened_at ASC, id ASC"
                : "opened_at DESC, id DESC";
        return jdbcTemplate.query(
                "SELECT * FROM dispute_case WHERE deal_id = ? ORDER BY " + orderBy + " LIMIT ? OFFSET ?",
                this::map,
                dealId,
                limit,
                offset);
    }

    long countByDealId(UUID dealId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dispute_case WHERE deal_id = ?", Long.class, dealId);
        return count == null ? 0L : count;
    }

    List<DisputeCase.DisputeCaseRecord> findByDealId(UUID dealId) {
        return jdbcTemplate.query("""
                SELECT * FROM dispute_case
                WHERE deal_id = ?
                ORDER BY opened_at DESC, id DESC
                """, this::map, dealId);
    }

    boolean updateLifecycle(DisputeCase.DisputeCaseRecord disputeCase, long previousVersion) {
        return jdbcTemplate.update("""
                UPDATE dispute_case
                SET status = ?, acknowledged_at = ?, withdrawn_at = ?, version = ?, updated_at = ?
                WHERE id = ? AND version = ?
                """,
                disputeCase.status().name(),
                timestamp(disputeCase.acknowledgedAt()),
                timestamp(disputeCase.withdrawnAt()),
                disputeCase.version(),
                Timestamp.from(disputeCase.updatedAt()),
                disputeCase.id(),
                previousVersion) > 0;
    }

    private DisputeCase.DisputeCaseRecord map(ResultSet resultSet, int rowNum) throws SQLException {
        return new DisputeCase.DisputeCaseRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("deal_id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("fulfillment_id", UUID.class),
                resultSet.getObject("milestone_id", UUID.class),
                resultSet.getObject("ratification_package_id", UUID.class),
                resultSet.getString("fulfillment_status_at_open"),
                resultSet.getLong("fulfillment_version_at_open"),
                resultSet.getLong("milestone_version_at_open"),
                DisputeReasonCode.valueOf(resultSet.getString("reason_code")),
                resultSet.getString("subject"),
                resultSet.getString("statement"),
                DisputeStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("opening_tenant_id", UUID.class),
                resultSet.getObject("opening_legal_entity_id", UUID.class),
                resultSet.getObject("opening_user_id", UUID.class),
                resultSet.getString("opening_legal_name"),
                resultSet.getTimestamp("opened_at").toInstant(),
                instant(resultSet.getTimestamp("acknowledged_at")),
                instant(resultSet.getTimestamp("withdrawn_at")),
                resultSet.getLong("version"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
