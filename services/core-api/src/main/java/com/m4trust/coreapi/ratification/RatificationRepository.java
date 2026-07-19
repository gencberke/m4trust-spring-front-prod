package com.m4trust.coreapi.ratification;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Ratification-owned persistence for immutable snapshots, package wrappers, and approvals. */
@Repository
class RatificationRepository {
    private static final String PACKAGE_COLUMNS = """
            package.id, package.deal_id, package.snapshot_id, package.status,
            package.buyer_legal_entity_id, package.seller_legal_entity_id,
            package.amount_minor, package.currency, package.created_at, package.version,
            snapshot.schema_version, snapshot.canonical_snapshot::text, snapshot.content_hash
            """;

    private final JdbcTemplate jdbc;

    RatificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    void insert(SnapshotRecord snapshot, PackageRecord packageRecord) {
        jdbc.update("""
                INSERT INTO ratification_package_snapshot (
                    id, schema_version, canonical_snapshot, content_hash, created_at
                ) VALUES (?, ?, CAST(? AS jsonb), ?, ?)
                """, snapshot.id(), snapshot.schemaVersion(), snapshot.canonicalSnapshot(), snapshot.contentHash(),
                Timestamp.from(snapshot.createdAt()));
        jdbc.update("""
                INSERT INTO ratification_package (
                    id, deal_id, snapshot_id, version, status, buyer_legal_entity_id,
                    seller_legal_entity_id, amount_minor, currency, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, packageRecord.id(), packageRecord.dealId(), packageRecord.snapshotId(),
                packageRecord.version(), packageRecord.status().name(), packageRecord.buyerLegalEntityId(),
                packageRecord.sellerLegalEntityId(), packageRecord.amountMinor(), packageRecord.currency(),
                Timestamp.from(packageRecord.createdAt()));
    }

    Optional<PackageRecord> findByDealAndId(UUID dealId, UUID packageId) {
        return jdbc.query("""
                SELECT %s
                FROM ratification_package package
                JOIN ratification_package_snapshot snapshot ON snapshot.id = package.snapshot_id
                WHERE package.deal_id = ? AND package.id = ?
                """.formatted(PACKAGE_COLUMNS), this::mapPackage, dealId, packageId).stream().findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    Optional<PackageRecord> findByDealAndIdForUpdate(UUID dealId, UUID packageId) {
        return jdbc.query("""
                SELECT %s
                FROM ratification_package package
                JOIN ratification_package_snapshot snapshot ON snapshot.id = package.snapshot_id
                WHERE package.deal_id = ? AND package.id = ?
                FOR UPDATE OF package
                """.formatted(PACKAGE_COLUMNS), this::mapPackage, dealId, packageId).stream().findFirst();
    }

    List<PackageRecord> listByDealId(UUID dealId) {
        return jdbc.query("""
                SELECT %s
                FROM ratification_package package
                JOIN ratification_package_snapshot snapshot ON snapshot.id = package.snapshot_id
                WHERE package.deal_id = ?
                ORDER BY package.created_at ASC, package.id ASC
                """.formatted(PACKAGE_COLUMNS), this::mapPackage, dealId);
    }

    boolean updateStatus(PackageRecord packageRecord, long previousVersion) {
        return jdbc.update("""
                UPDATE ratification_package
                SET status = ?, version = ?
                WHERE id = ? AND deal_id = ? AND version = ?
                """, packageRecord.status().name(), packageRecord.version(), packageRecord.id(),
                packageRecord.dealId(), previousVersion) == 1;
    }

    void insertApproval(ApprovalRecord approval) {
        jdbc.update("""
                INSERT INTO ratification_package_approval (
                    id, package_id, legal_entity_id, approved_by_user_id, approved_at
                ) VALUES (?, ?, ?, ?, ?)
                """, approval.id(), approval.packageId(), approval.legalEntityId(), approval.approvedByUserId(),
                Timestamp.from(approval.approvedAt()));
    }

    Optional<ApprovalRecord> findApprovalByPackageAndEntity(UUID packageId, UUID legalEntityId) {
        return jdbc.query("""
                SELECT id, package_id, legal_entity_id, approved_by_user_id, approved_at
                FROM ratification_package_approval
                WHERE package_id = ? AND legal_entity_id = ?
                """, this::mapApproval, packageId, legalEntityId).stream().findFirst();
    }

    List<ApprovalRecord> listApprovals(UUID packageId) {
        return jdbc.query("""
                SELECT id, package_id, legal_entity_id, approved_by_user_id, approved_at
                FROM ratification_package_approval
                WHERE package_id = ?
                ORDER BY approved_at ASC, id ASC
                """, this::mapApproval, packageId);
    }

    private PackageRecord mapPackage(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PackageRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("deal_id", UUID.class),
                resultSet.getObject("snapshot_id", UUID.class),
                RatificationPackageStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("buyer_legal_entity_id", UUID.class),
                resultSet.getObject("seller_legal_entity_id", UUID.class),
                resultSet.getLong("amount_minor"),
                resultSet.getString("currency"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getLong("version"),
                resultSet.getInt("schema_version"),
                resultSet.getString("canonical_snapshot"),
                resultSet.getString("content_hash"));
    }

    private ApprovalRecord mapApproval(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ApprovalRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("package_id", UUID.class),
                resultSet.getObject("legal_entity_id", UUID.class),
                resultSet.getObject("approved_by_user_id", UUID.class),
                resultSet.getTimestamp("approved_at").toInstant());
    }

    record SnapshotRecord(UUID id, int schemaVersion, String canonicalSnapshot,
            String contentHash, Instant createdAt) { }

    record PackageRecord(UUID id, UUID dealId, UUID snapshotId, RatificationPackageStatus status,
            UUID buyerLegalEntityId, UUID sellerLegalEntityId, long amountMinor, String currency,
            Instant createdAt, long version, Integer snapshotSchemaVersion,
            String canonicalSnapshot, String contentHash) {
        PackageRecord(UUID id, UUID dealId, UUID snapshotId, RatificationPackageStatus status,
                UUID buyerLegalEntityId, UUID sellerLegalEntityId, long amountMinor, String currency,
                Instant createdAt, long version) {
            this(id, dealId, snapshotId, status, buyerLegalEntityId, sellerLegalEntityId,
                    amountMinor, currency, createdAt, version, null, null, null);
        }
    }

    record ApprovalRecord(UUID id, UUID packageId, UUID legalEntityId,
            UUID approvedByUserId, Instant approvedAt) { }
}
