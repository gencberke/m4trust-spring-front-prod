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

/** Payment-owned persistence for FundingPlan/FundingUnit/PaymentOperation/dispatch records. */
@Repository
class FundingRepository {

    private static final String UNIT_COLUMNS = """
            unit.id, unit.funding_plan_id, unit.sequence_no, unit.amount_minor, unit.currency,
            unit.status, unit.created_at, unit.updated_at, unit.version, plan.deal_id, plan.tenant_id
            """;
    private static final String OPERATION_COLUMNS = """
            operation.id, operation.funding_unit_id, operation.provider_key, operation.status,
            operation.provider_reference, operation.created_at, operation.updated_at, operation.version,
            plan.deal_id, plan.tenant_id, unit.amount_minor AS unit_amount_minor, unit.currency AS unit_currency
            """;

    private final JdbcTemplate jdbc;

    FundingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    boolean insertPlanAndUnit(PlanRecord plan, UnitRecord unit) {
        try {
            jdbc.update("""
                    INSERT INTO funding_plan (
                        id, deal_id, tenant_id, amount_minor, currency, version, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, plan.id(), plan.dealId(), plan.tenantId(), plan.amountMinor(), plan.currency(),
                    plan.version(), Timestamp.from(plan.createdAt()), Timestamp.from(plan.updatedAt()));
        } catch (DataIntegrityViolationException conflict) {
            return false;
        }
        jdbc.update("""
                INSERT INTO funding_unit (
                    id, funding_plan_id, sequence_no, amount_minor, currency, status, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, unit.id(), unit.fundingPlanId(), unit.sequenceNo(), unit.amountMinor(), unit.currency(),
                unit.status().name(), unit.version(), Timestamp.from(unit.createdAt()),
                Timestamp.from(unit.updatedAt()));
        return true;
    }

    Optional<PlanRecord> findPlanByDeal(UUID dealId) {
        return jdbc.query("""
                SELECT id, deal_id, tenant_id, amount_minor, currency, created_at, updated_at, version
                FROM funding_plan WHERE deal_id = ?
                """, this::mapPlan, dealId).stream().findFirst();
    }

    Optional<UnitRecord> findUnitByPlan(UUID fundingPlanId) {
        return jdbc.query("""
                SELECT id, funding_plan_id, sequence_no, amount_minor, currency, status, created_at, updated_at, version
                FROM funding_unit WHERE funding_plan_id = ? AND sequence_no = 1
                """, this::mapUnitPlain, fundingPlanId).stream().findFirst();
    }

    Optional<UnitLookup> findUnitById(UUID unitId) {
        return jdbc.query("""
                SELECT %s FROM funding_unit unit
                JOIN funding_plan plan ON plan.id = unit.funding_plan_id
                WHERE unit.id = ?
                """.formatted(UNIT_COLUMNS), this::mapUnitLookup, unitId).stream().findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    Optional<UnitLookup> findUnitByIdForUpdate(UUID unitId) {
        return jdbc.query("""
                SELECT %s FROM funding_unit unit
                JOIN funding_plan plan ON plan.id = unit.funding_plan_id
                WHERE unit.id = ? FOR UPDATE OF unit
                """.formatted(UNIT_COLUMNS), this::mapUnitLookup, unitId).stream().findFirst();
    }

    boolean updateUnitStatus(UnitRecord unit, long previousVersion) {
        return jdbc.update("""
                UPDATE funding_unit SET status = ?, updated_at = ?, version = ?
                WHERE id = ? AND version = ?
                """, unit.status().name(), Timestamp.from(unit.updatedAt()), unit.version(), unit.id(),
                previousVersion) == 1;
    }

    void insertOperation(OperationRecord operation) {
        jdbc.update("""
                INSERT INTO payment_operation (
                    id, funding_unit_id, provider_key, status, provider_reference, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, operation.id(), operation.fundingUnitId(), operation.providerKey(), operation.status().name(),
                operation.providerReference(), operation.version(), Timestamp.from(operation.createdAt()),
                Timestamp.from(operation.updatedAt()));
    }

    Optional<OperationRecord> findInFlightOperation(UUID fundingUnitId) {
        return jdbc.query("""
                SELECT id, funding_unit_id, provider_key, status, provider_reference, created_at, updated_at, version
                FROM payment_operation
                WHERE funding_unit_id = ? AND status IN ('CREATED', 'UNCONFIRMED')
                ORDER BY created_at DESC, id DESC LIMIT 1
                """, this::mapOperationPlain, fundingUnitId).stream().findFirst();
    }

    Optional<OperationRecord> findCurrentOperation(UUID fundingUnitId) {
        return jdbc.query("""
                SELECT id, funding_unit_id, provider_key, status, provider_reference, created_at, updated_at, version
                FROM payment_operation
                WHERE funding_unit_id = ?
                ORDER BY created_at DESC, id DESC LIMIT 1
                """, this::mapOperationPlain, fundingUnitId).stream().findFirst();
    }

    Optional<OperationLookup> findOperationById(UUID operationId) {
        return jdbc.query("""
                SELECT %s FROM payment_operation operation
                JOIN funding_unit unit ON unit.id = operation.funding_unit_id
                JOIN funding_plan plan ON plan.id = unit.funding_plan_id
                WHERE operation.id = ?
                """.formatted(OPERATION_COLUMNS), this::mapOperationLookup, operationId).stream().findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    Optional<OperationLookup> findOperationByIdForUpdate(UUID operationId) {
        return jdbc.query("""
                SELECT %s FROM payment_operation operation
                JOIN funding_unit unit ON unit.id = operation.funding_unit_id
                JOIN funding_plan plan ON plan.id = unit.funding_plan_id
                WHERE operation.id = ? FOR UPDATE OF operation
                """.formatted(OPERATION_COLUMNS), this::mapOperationLookup, operationId).stream().findFirst();
    }

    boolean updateOperationStatus(OperationRecord operation, long previousVersion) {
        return jdbc.update("""
                UPDATE payment_operation SET status = ?, provider_reference = ?, updated_at = ?, version = ?
                WHERE id = ? AND version = ?
                """, operation.status().name(), operation.providerReference(),
                Timestamp.from(operation.updatedAt()), operation.version(), operation.id(), previousVersion) == 1;
    }

    void insertDispatch(DispatchRecord dispatch) {
        jdbc.update("""
                INSERT INTO payment_dispatch (
                    id, payment_operation_id, dispatch_type, provider_key, amount_minor, currency,
                    created_at, available_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, dispatch.id(), dispatch.paymentOperationId(), dispatch.dispatchType().name(),
                dispatch.providerKey(), dispatch.amountMinor(), dispatch.currency(),
                Timestamp.from(dispatch.createdAt()), Timestamp.from(dispatch.createdAt()));
    }

    private PlanRecord mapPlan(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PlanRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("deal_id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getLong("amount_minor"),
                resultSet.getString("currency"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getLong("version"));
    }

    private UnitRecord mapUnitPlain(ResultSet resultSet, int rowNumber) throws SQLException {
        return new UnitRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("funding_plan_id", UUID.class),
                resultSet.getInt("sequence_no"),
                resultSet.getLong("amount_minor"),
                resultSet.getString("currency"),
                FundingUnitStatus.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getLong("version"));
    }

    private UnitLookup mapUnitLookup(ResultSet resultSet, int rowNumber) throws SQLException {
        return new UnitLookup(mapUnitPlain(resultSet, rowNumber), resultSet.getObject("deal_id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class));
    }

    private OperationRecord mapOperationPlain(ResultSet resultSet, int rowNumber) throws SQLException {
        return new OperationRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("funding_unit_id", UUID.class),
                resultSet.getObject("provider_key", UUID.class),
                PaymentOperationStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("provider_reference"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getLong("version"));
    }

    private OperationLookup mapOperationLookup(ResultSet resultSet, int rowNumber) throws SQLException {
        return new OperationLookup(
                mapOperationPlain(resultSet, rowNumber), resultSet.getObject("deal_id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getLong("unit_amount_minor"), resultSet.getString("unit_currency"));
    }

    record PlanRecord(UUID id, UUID dealId, UUID tenantId, long amountMinor, String currency,
            Instant createdAt, Instant updatedAt, long version) { }

    record UnitRecord(UUID id, UUID fundingPlanId, int sequenceNo, long amountMinor, String currency,
            FundingUnitStatus status, Instant createdAt, Instant updatedAt, long version) { }

    record UnitLookup(UnitRecord unit, UUID dealId, UUID tenantId) { }

    record OperationRecord(UUID id, UUID fundingUnitId, UUID providerKey, PaymentOperationStatus status,
            String providerReference, Instant createdAt, Instant updatedAt, long version) { }

    record OperationLookup(OperationRecord operation, UUID dealId, UUID tenantId, long unitAmountMinor,
            String unitCurrency) { }

    record DispatchRecord(UUID id, UUID paymentOperationId, DispatchType dispatchType, UUID providerKey,
            long amountMinor, String currency, Instant createdAt) { }

    enum DispatchType { INITIATE, RECONCILE }
}
