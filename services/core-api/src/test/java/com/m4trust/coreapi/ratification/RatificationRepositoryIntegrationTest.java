package com.m4trust.coreapi.ratification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("local")
@Testcontainers
class RatificationRepositoryIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.5-alpine");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private RatificationRepository repository;
    @Autowired private TransactionTemplate transactions;

    private UUID tenantId;
    private UUID userId;
    private UUID buyerId;
    private UUID sellerId;
    private UUID dealId;
    private UUID otherDealId;

    @BeforeEach
    void setUp() {
        jdbc.execute("""
                TRUNCATE TABLE payment_dispatch, payment_operation, funding_unit, funding_plan,
                    ratification_package_approval, ratification_package,
                    ratification_package_snapshot, contract_intelligence_rule_set_version,
                    contract_intelligence_extraction_result_version, contract_intelligence_analysis_job,
                    http_idempotency_record, deal_invitation, deal_participant, document, deal,
                    audit_record, legal_entity_membership, legal_entity, tenant_user, tenant, identity_user
                """);
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        buyerId = UUID.randomUUID();
        sellerId = UUID.randomUUID();
        dealId = UUID.randomUUID();
        otherDealId = UUID.randomUUID();
        jdbc.update("INSERT INTO identity_user (id,email,password_hash,display_name,enabled) VALUES (?,?, 'x','User',true)",
                userId, userId + "@example.test");
        jdbc.update("INSERT INTO tenant (id) VALUES (?)", tenantId);
        jdbc.update("INSERT INTO tenant_user (user_id,tenant_id) VALUES (?,?)", userId, tenantId);
        seedEntity(buyerId, "Buyer");
        seedEntity(sellerId, "Seller");
        seedDeal(dealId, "DL-0000000001");
        seedDeal(otherDealId, "DL-0000000002");
    }

    @Test
    void persistsExactSnapshotWrapperHistoryAndScopedLookup() {
        Instant first = Instant.parse("2026-07-19T10:00:00Z");
        var firstPackage = insertPackage(dealId, first, "{\"schemaVersion\":1,\"dealTitle\":\"First\"}");
        var secondPackage = insertPackage(dealId, first.plusSeconds(1), "{\"schemaVersion\":1,\"dealTitle\":\"Second\"}");

        var loaded = repository.findByDealAndId(dealId, firstPackage.id()).orElseThrow();
        assertEquals(firstPackage.id(), loaded.id());
        assertEquals(dealId, loaded.dealId());
        assertEquals("{\"dealTitle\": \"First\", \"schemaVersion\": 1}", loaded.canonicalSnapshot());
        assertEquals("a".repeat(64), loaded.contentHash());
        assertEquals(1, loaded.snapshotSchemaVersion());
        assertFalse(repository.findByDealAndId(otherDealId, firstPackage.id()).isPresent());
        assertEquals(List.of(firstPackage.id(), secondPackage.id()), repository.listByDealId(dealId).stream()
                .map(RatificationRepository.PackageRecord::id).toList());
    }

    @Test
    void locksWithinTransactionUpdatesVersionAndRetainsApprovals() {
        var packageRecord = insertPackage(dealId, Instant.parse("2026-07-19T10:00:00Z"), "{\"schemaVersion\":1}");
        assertThrows(IllegalTransactionStateException.class,
                () -> repository.findByDealAndIdForUpdate(dealId, packageRecord.id()));
        transactions.executeWithoutResult(status -> assertTrue(repository
                .findByDealAndIdForUpdate(dealId, packageRecord.id()).isPresent()));

        RatificationPackage packageDomain = RatificationPackage.rehydrate(packageRecord);
        packageDomain.ratify(0);
        assertTrue(repository.updateStatus(packageDomain.toRecord(), 0));
        assertFalse(repository.updateStatus(packageDomain.toRecord(), 0));
        assertEquals(RatificationPackageStatus.RATIFIED,
                repository.findByDealAndId(dealId, packageRecord.id()).orElseThrow().status());

        Instant approvedAt = Instant.parse("2026-07-19T10:01:00Z");
        var buyerApproval = new RatificationRepository.ApprovalRecord(UUID.randomUUID(), packageRecord.id(),
                buyerId, userId, approvedAt);
        var sellerApproval = new RatificationRepository.ApprovalRecord(UUID.randomUUID(), packageRecord.id(),
                sellerId, userId, approvedAt.plusSeconds(1));
        repository.insertApproval(sellerApproval);
        repository.insertApproval(buyerApproval);
        assertEquals(buyerApproval, repository.findApprovalByPackageAndEntity(packageRecord.id(), buyerId).orElseThrow());
        assertEquals(List.of(buyerApproval.id(), sellerApproval.id()), repository.listApprovals(packageRecord.id())
                .stream().map(RatificationRepository.ApprovalRecord::id).toList());
    }

    private RatificationRepository.PackageRecord insertPackage(UUID packageDealId, Instant createdAt, String snapshotJson) {
        UUID snapshotId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        var snapshot = new RatificationRepository.SnapshotRecord(snapshotId, 1, snapshotJson, "a".repeat(64), createdAt);
        var packageRecord = new RatificationRepository.PackageRecord(packageId, packageDealId, snapshotId,
                RatificationPackageStatus.PENDING, buyerId, sellerId, 99, "TRY", createdAt, 0);
        repository.insert(snapshot, packageRecord);
        return packageRecord;
    }

    private void seedEntity(UUID entityId, String name) {
        jdbc.update("INSERT INTO legal_entity (id,tenant_id,legal_name,registration_number) VALUES (?,?,?,?)",
                entityId, tenantId, name, name + "-REG");
        jdbc.update("INSERT INTO legal_entity_membership (id,tenant_id,legal_entity_id,user_id,role) VALUES (?,?,?,?, 'ADMIN')",
                UUID.randomUUID(), tenantId, entityId, userId);
    }

    private void seedDeal(UUID id, String reference) {
        jdbc.update("INSERT INTO deal (id,tenant_id,reference,title,deal_status,initiator_legal_entity_id,created_by) VALUES (?,?,?,'Deal','DRAFT',?,?)",
                id, tenantId, reference, buyerId, userId);
        for (UUID entityId : List.of(buyerId, sellerId)) {
            jdbc.update("INSERT INTO deal_participant (deal_id,tenant_id,legal_entity_id,legal_entity_tenant_id) VALUES (?,?,?,?)",
                    id, tenantId, entityId, tenantId);
        }
    }
}
