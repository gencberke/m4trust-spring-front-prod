package com.m4trust.coreapi.payment;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class SettlementRepository {

    private final JdbcTemplate jdbc;

    SettlementRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void insertSettlement(SettlementRecord settlement) {
        jdbc.update("""
                INSERT INTO settlement (
                    id, deal_id, funding_unit_id, tenant_id, status, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, settlement.id(), settlement.dealId(), settlement.fundingUnitId(), settlement.tenantId(),
                settlement.status().name(), settlement.version(),
                Timestamp.from(settlement.createdAt()), Timestamp.from(settlement.updatedAt()));
    }

    Optional<SettlementRecord> findByDealId(UUID dealId) {
        return jdbc.query("SELECT * FROM settlement WHERE deal_id = ?",
                this::mapSettlement, dealId).stream().findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    Optional<SettlementRecord> findByDealIdForUpdate(UUID dealId) {
        return jdbc.query("SELECT * FROM settlement WHERE deal_id = ? FOR UPDATE",
                this::mapSettlement, dealId).stream().findFirst();
    }

    Optional<SettlementRecord> findById(UUID settlementId) {
        return jdbc.query("SELECT * FROM settlement WHERE id = ?",
                this::mapSettlement, settlementId).stream().findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    Optional<SettlementRecord> findByIdForUpdate(UUID settlementId) {
        return jdbc.query("SELECT * FROM settlement WHERE id = ? FOR UPDATE",
                this::mapSettlement, settlementId).stream().findFirst();
    }

    boolean updateSettlement(SettlementRecord settlement, long previousVersion) {
        return jdbc.update("""
                UPDATE settlement SET status = ?, updated_at = ?, version = ?
                WHERE id = ? AND version = ?
                """, settlement.status().name(), Timestamp.from(settlement.updatedAt()),
                settlement.version(), settlement.id(), previousVersion) == 1;
    }

    boolean insertSettlementIfAbsent(SettlementRecord settlement) {
        try {
            insertSettlement(settlement);
            return true;
        } catch (DataIntegrityViolationException conflict) {
            return false;
        }
    }

    void insertOperation(ReleaseOperationRecord operation) {
        jdbc.update("""
                INSERT INTO release_operation (
                    id, settlement_id, provider_key, status, provider_reference, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, operation.id(), operation.settlementId(), operation.providerKey(), operation.status().name(),
                operation.providerReference(), operation.version(),
                Timestamp.from(operation.createdAt()), Timestamp.from(operation.updatedAt()));
    }

    Optional<ReleaseOperationRecord> findOperationBySettlement(UUID settlementId) {
        return jdbc.query("SELECT * FROM release_operation WHERE settlement_id = ?",
                this::mapOperation, settlementId).stream().findFirst();
    }

    Optional<ReleaseOperationLookup> findOperationById(UUID operationId) {
        return jdbc.query("""
                SELECT op.*, s.deal_id, s.tenant_id, s.funding_unit_id,
                       fu.amount_minor, fu.currency, d.version AS deal_version
                FROM release_operation op
                JOIN settlement s ON s.id = op.settlement_id
                JOIN funding_unit fu ON fu.id = s.funding_unit_id
                JOIN deal d ON d.id = s.deal_id
                WHERE op.id = ?
                """, this::mapOperationLookup, operationId).stream().findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    Optional<ReleaseOperationLookup> findOperationByIdForUpdate(UUID operationId) {
        return jdbc.query("""
                SELECT op.*, s.deal_id, s.tenant_id, s.funding_unit_id,
                       fu.amount_minor, fu.currency, d.version AS deal_version
                FROM release_operation op
                JOIN settlement s ON s.id = op.settlement_id
                JOIN funding_unit fu ON fu.id = s.funding_unit_id
                JOIN deal d ON d.id = s.deal_id
                WHERE op.id = ? FOR UPDATE OF op
                """, this::mapOperationLookup, operationId).stream().findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    Optional<ReleaseOperationRecord> findOperationBySettlementForUpdate(UUID settlementId) {
        return jdbc.query("SELECT * FROM release_operation WHERE settlement_id = ? FOR UPDATE",
                this::mapOperation, settlementId).stream().findFirst();
    }

    boolean updateOperation(ReleaseOperationRecord operation, long previousVersion) {
        return jdbc.update("""
                UPDATE release_operation
                SET status = ?, provider_reference = ?, updated_at = ?, version = ?
                WHERE id = ? AND version = ?
                """, operation.status().name(), operation.providerReference(),
                Timestamp.from(operation.updatedAt()), operation.version(),
                operation.id(), previousVersion) == 1;
    }

    void insertDispatch(DispatchRecord dispatch) {
        jdbc.update("""
                INSERT INTO release_dispatch (
                    id, release_operation_id, dispatch_type, provider_key, amount_minor, currency,
                    created_at, available_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, dispatch.id(), dispatch.releaseOperationId(), dispatch.dispatchType().name(),
                dispatch.providerKey(), dispatch.amountMinor(), dispatch.currency(),
                Timestamp.from(dispatch.createdAt()), Timestamp.from(dispatch.createdAt()));
    }

    private SettlementRecord mapSettlement(ResultSet rs, int row) throws SQLException {
        return new SettlementRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("deal_id", UUID.class),
                rs.getObject("funding_unit_id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                SettlementStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getLong("version"));
    }

    private ReleaseOperationRecord mapOperation(ResultSet rs, int row) throws SQLException {
        return new ReleaseOperationRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("settlement_id", UUID.class),
                rs.getObject("provider_key", UUID.class),
                ReleaseOperationStatus.valueOf(rs.getString("status")),
                rs.getString("provider_reference"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getLong("version"));
    }

    private ReleaseOperationLookup mapOperationLookup(ResultSet rs, int row) throws SQLException {
        return new ReleaseOperationLookup(mapOperation(rs, row),
                rs.getObject("deal_id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("funding_unit_id", UUID.class),
                rs.getLong("amount_minor"),
                rs.getString("currency"),
                rs.getLong("deal_version"));
    }

    record SettlementRecord(UUID id, UUID dealId, UUID fundingUnitId, UUID tenantId,
            SettlementStatus status, Instant createdAt, Instant updatedAt, long version) { }

    record ReleaseOperationRecord(UUID id, UUID settlementId, UUID providerKey, ReleaseOperationStatus status,
            String providerReference, Instant createdAt, Instant updatedAt, long version) { }

    record ReleaseOperationLookup(ReleaseOperationRecord operation, UUID dealId, UUID tenantId,
            UUID fundingUnitId, long amountMinor, String currency, long dealVersion) { }

    record DispatchRecord(UUID id, UUID releaseOperationId, DispatchType dispatchType, UUID providerKey,
            long amountMinor, String currency, Instant createdAt) { }

    enum DispatchType { INITIATE, RECONCILE }
}
