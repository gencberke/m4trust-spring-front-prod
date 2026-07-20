package com.m4trust.coreapi.fulfillment;

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
class FulfillmentRepository {

    private final JdbcTemplate jdbcTemplate;

    FulfillmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void insert(Fulfillment.FulfillmentRecord fulfillment) {
        jdbcTemplate.update("""
                INSERT INTO fulfillment (
                    id, deal_id, tenant_id, source_package_id, status,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, fulfillment.id(), fulfillment.dealId(), fulfillment.tenantId(),
                fulfillment.sourcePackageId(), fulfillment.status().name(),
                fulfillment.version(), Timestamp.from(fulfillment.createdAt()),
                Timestamp.from(fulfillment.updatedAt()));
    }

    Optional<Fulfillment.FulfillmentRecord> findByDealId(UUID dealId) {
        return jdbcTemplate.query("SELECT * FROM fulfillment WHERE deal_id = ?",
                this::mapFulfillment, dealId).stream().findFirst();
    }

    Optional<Fulfillment.FulfillmentRecord> findByDealIdForUpdate(UUID dealId) {
        return jdbcTemplate.query("SELECT * FROM fulfillment WHERE deal_id = ? FOR UPDATE",
                this::mapFulfillment, dealId).stream().findFirst();
    }

    Optional<Fulfillment.FulfillmentRecord> findById(UUID fulfillmentId) {
        return jdbcTemplate.query("SELECT * FROM fulfillment WHERE id = ?",
                this::mapFulfillment, fulfillmentId).stream().findFirst();
    }

    Optional<Fulfillment.FulfillmentRecord> findByIdForUpdate(UUID fulfillmentId) {
        return jdbcTemplate.query("SELECT * FROM fulfillment WHERE id = ? FOR UPDATE",
                this::mapFulfillment, fulfillmentId).stream().findFirst();
    }

    boolean update(Fulfillment.FulfillmentRecord fulfillment, long previousVersion) {
        return jdbcTemplate.update("""
                UPDATE fulfillment
                SET status = ?, updated_at = ?, version = ?
                WHERE id = ? AND version = ?
                """, fulfillment.status().name(),
                Timestamp.from(fulfillment.updatedAt()), fulfillment.version(),
                fulfillment.id(), previousVersion) == 1;
    }

    private Fulfillment.FulfillmentRecord mapFulfillment(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new Fulfillment.FulfillmentRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("deal_id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("source_package_id", UUID.class),
                FulfillmentStatus.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getLong("version"));
    }
}
