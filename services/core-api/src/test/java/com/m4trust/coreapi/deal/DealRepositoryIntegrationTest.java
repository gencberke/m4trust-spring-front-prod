package com.m4trust.coreapi.deal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.m4trust.coreapi.deal.DealRepository.DealRecord;
import com.m4trust.coreapi.deal.DealRepository.DealSort;
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
import com.m4trust.coreapi.ratification.RatificationSourcePorts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("local")
@Testcontainers
class DealRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.5-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DealRepository repository;

    @Autowired
    private RatificationDealSourceAdapter ratificationDeals;

    @Autowired
    private TransactionTemplate transactions;

    private UUID tenantId;
    private UUID userId;
    private UUID participantEntityId;
    private UUID otherEntityId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM spring_session_attributes");
        jdbcTemplate.update("DELETE FROM spring_session");
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    ratification_package_approval,
                    ratification_package,
                    ratification_package_snapshot,
                    contract_intelligence_rule_set_version,
                    contract_intelligence_extraction_result_version,
                    contract_intelligence_analysis_job,
                    http_idempotency_record,
                    deal_invitation,
                    deal_participant,
                    document,
                    deal,
                    audit_record,
                    legal_entity_membership,
                    legal_entity,
                    tenant_user,
                    tenant,
                    identity_user
                """);
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        participantEntityId = UUID.randomUUID();
        otherEntityId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO identity_user (
                    id, email, password_hash, display_name, enabled
                )
                VALUES (?, ?, ?, ?, true)
                """, userId, "deal-owner@example.com", "test-hash", "Deal Owner");
        jdbcTemplate.update("INSERT INTO tenant (id) VALUES (?)", tenantId);
        jdbcTemplate.update("""
                INSERT INTO tenant_user (user_id, tenant_id)
                VALUES (?, ?)
                """, userId, tenantId);
        insertLegalEntity(participantEntityId, "Participant Entity", "PART-1");
        insertLegalEntity(otherEntityId, "Other Entity", "OTHER-1");
    }

    @Test
    void insertMakesTheDealVisibleOnlyToItsInitialParticipant() {
        DealRecord deal = newDeal(
                UUID.randomUUID(), "Initial Deal", Instant.parse(
                        "2026-07-16T10:00:00Z"));

        repository.insert(deal, tenantId);

        assertTrue(repository.findVisibleById(
                tenantId, participantEntityId, deal.id()).isPresent());
        assertFalse(repository.findVisibleById(
                tenantId, otherEntityId, deal.id()).isPresent());
        assertEquals(1, repository.countVisible(
                tenantId, participantEntityId, null));
        assertEquals(0, repository.countVisible(
                tenantId, otherEntityId, null));
        assertEquals(tenantId, jdbcTemplate.queryForObject("""
                SELECT legal_entity_tenant_id
                FROM deal_participant
                WHERE deal_id = ?
                  AND legal_entity_id = ?
                """, UUID.class, deal.id(), participantEntityId));
    }

    @Test
    void paginationSortsDeterministicallyWithAnIdentifierTieBreaker() {
        Instant sameCreationTime = Instant.parse("2026-07-16T10:00:00Z");
        DealRecord lowId = newDeal(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "Same", sameCreationTime);
        DealRecord highId = newDeal(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "Same", sameCreationTime);
        repository.insert(lowId, tenantId);
        repository.insert(highId, tenantId);
        assertTrue(lowId.reference().matches("DL-[0-9]{10}"));
        assertFalse(lowId.reference().equals(highId.reference()));

        List<UUID> firstPage = repository.findVisiblePage(
                        tenantId,
                        participantEntityId,
                        null,
                        DealSort.CREATED_AT_DESC,
                        1,
                        0)
                .stream()
                .map(DealRecord::id)
                .toList();
        List<UUID> secondPage = repository.findVisiblePage(
                        tenantId,
                        participantEntityId,
                        null,
                        DealSort.CREATED_AT_DESC,
                        1,
                        1)
                .stream()
                .map(DealRecord::id)
                .toList();

        assertEquals(List.of(highId.id()), firstPage);
        assertEquals(List.of(lowId.id()), secondPage);
    }

    @Test
    void versionCheckedUpdatesRejectStaleWritersAtomically() {
        DealRecord deal = newDeal(
                UUID.randomUUID(), "Original", Instant.parse(
                        "2026-07-16T10:00:00Z"));
        repository.insert(deal, tenantId);

        assertTrue(repository.updateBasicFields(
                tenantId,
                participantEntityId,
                deal.id(),
                0,
                "First Writer",
                "saved",
                Instant.parse("2026-07-16T10:01:00Z")));
        assertFalse(repository.updateBasicFields(
                tenantId,
                participantEntityId,
                deal.id(),
                0,
                "Stale Writer",
                null,
                Instant.parse("2026-07-16T10:02:00Z")));

        DealRecord updated = repository.findVisibleById(
                tenantId, participantEntityId, deal.id()).orElseThrow();
        assertEquals("First Writer", updated.title());
        assertEquals(1, updated.version());

        assertTrue(repository.updateStatus(
                tenantId,
                participantEntityId,
                deal.id(),
                DealStatus.DRAFT,
                DealStatus.CANCELLED,
                1,
                Instant.parse("2026-07-16T10:03:00Z")));
        assertFalse(repository.updateBasicFields(
                tenantId,
                participantEntityId,
                deal.id(),
                2,
                "Cancelled Edit",
                null,
                Instant.parse("2026-07-16T10:04:00Z")));
        assertFalse(repository.updateStatus(
                tenantId,
                participantEntityId,
                deal.id(),
                DealStatus.DRAFT,
                DealStatus.ACTIVE,
                1,
                Instant.parse("2026-07-16T10:05:00Z")));
    }

    @Test
    void partyConstraintsRequireParticipantsOfTheSameDealAndRepositoryMapsAssignments() {
        Instant createdAt = Instant.parse("2026-07-16T10:00:00Z");
        DealRecord targetDeal = newDeal(UUID.randomUUID(), "Target", createdAt);
        DealRecord otherDeal = newDeal(UUID.randomUUID(), "Other", createdAt);
        repository.insert(targetDeal, tenantId);
        repository.insert(otherDeal, tenantId);
        jdbcTemplate.update("""
                INSERT INTO deal_participant (
                    deal_id, tenant_id, legal_entity_id,
                    legal_entity_tenant_id, created_at
                ) VALUES (?, ?, ?, ?, ?)
                """, otherDeal.id(), tenantId, otherEntityId, tenantId,
                java.sql.Timestamp.from(createdAt));

        DealRecord unassigned = repository.findVisibleById(
                tenantId, participantEntityId, targetDeal.id()).orElseThrow();
        assertNull(unassigned.buyerLegalEntityId());
        assertNull(unassigned.sellerLegalEntityId());

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("""
                        UPDATE deal
                        SET buyer_legal_entity_id = ?, seller_legal_entity_id = ?
                        WHERE id = ?
                        """, participantEntityId, participantEntityId,
                        targetDeal.id()));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("""
                        UPDATE deal
                        SET buyer_legal_entity_id = ?
                        WHERE id = ?
                        """, otherEntityId, targetDeal.id()));

        jdbcTemplate.update("""
                INSERT INTO deal_participant (
                    deal_id, tenant_id, legal_entity_id,
                    legal_entity_tenant_id, created_at
                ) VALUES (?, ?, ?, ?, ?)
                """, targetDeal.id(), tenantId, otherEntityId, tenantId,
                java.sql.Timestamp.from(createdAt));
        jdbcTemplate.update("""
                UPDATE deal
                SET buyer_legal_entity_id = ?, seller_legal_entity_id = ?
                WHERE id = ?
                """, participantEntityId, otherEntityId, targetDeal.id());

        DealRecord assigned = repository.findVisibleById(
                tenantId, participantEntityId, targetDeal.id()).orElseThrow();
        assertEquals(participantEntityId, assigned.buyerLegalEntityId());
        assertEquals(otherEntityId, assigned.sellerLegalEntityId());
        assertEquals(assigned, Deal.rehydrate(assigned).toRecord());
    }

    @Test
    void ratificationTargetUsesParticipantVisibilityAndImmutablePartyNames() {
        Instant now = Instant.parse("2026-07-19T10:00:00Z");
        DealRecord deal = newDeal(UUID.randomUUID(), "Ratification target", now);
        repository.insert(deal, tenantId);
        UUID participantTenantId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tenant (id) VALUES (?)", participantTenantId);
        jdbcTemplate.update("""
                INSERT INTO legal_entity (id, tenant_id, legal_name, registration_number)
                VALUES (?, ?, ?, ?)
                """, participantId, participantTenantId, "Cross Tenant Seller", "CROSS-1");
        jdbcTemplate.update("""
                INSERT INTO deal_participant (
                    deal_id, tenant_id, legal_entity_id, legal_entity_tenant_id, created_at
                ) VALUES (?, ?, ?, ?, ?)
                """, deal.id(), tenantId, participantId, participantTenantId,
                java.sql.Timestamp.from(now));
        assertTrue(repository.updateParties(tenantId, participantEntityId, deal.id(), 0,
                participantEntityId, participantId, now.plusSeconds(1)));

        OperationContext participantContext = context(participantTenantId, participantId);
        RatificationSourcePorts.Target target = ratificationDeals.findVisible(participantContext, deal.id())
                .orElseThrow();

        assertEquals(tenantId, target.tenantId());
        assertEquals(deal.id(), target.dealId());
        assertFalse(target.initiator());
        assertEquals(new RatificationSourcePorts.Party(participantEntityId, "Participant Entity"), target.buyer());
        assertEquals(new RatificationSourcePorts.Party(participantId, "Cross Tenant Seller"), target.seller());
        assertTrue(ratificationDeals.findVisible(context(tenantId, otherEntityId), deal.id()).isEmpty());
    }

    @Test
    void ratificationLockRequiresTransactionAndCurrentPackagePointerBumpsDealOnce() {
        Instant createdAt = Instant.parse("2026-07-19T10:00:00Z");
        DealRecord deal = newDeal(UUID.randomUUID(), "Pointer target", createdAt);
        repository.insert(deal, tenantId);
        UUID packageId = insertPackage(deal.id(), participantEntityId, otherEntityId, createdAt);
        OperationContext context = context(tenantId, participantEntityId);

        assertThrows(IllegalTransactionStateException.class,
                () -> ratificationDeals.lockVisibleForCreate(context, deal.id()));
        Instant changedAt = createdAt.plusSeconds(60);
        transactions.executeWithoutResult(status -> {
            assertTrue(ratificationDeals.lockVisibleForCreate(context, deal.id()).isPresent());
            ratificationDeals.pointCurrentPackage(deal.id(), packageId, changedAt);
        });

        DealRecord pointed = repository.findVisibleById(tenantId, participantEntityId, deal.id()).orElseThrow();
        assertEquals(packageId, pointed.currentRatificationPackageId());
        assertEquals(2, pointed.version());
        assertEquals(changedAt, pointed.updatedAt());
    }

    private DealRecord newDeal(UUID dealId, String title, Instant createdAt) {
        return new DealRecord(
                dealId,
                tenantId,
                repository.nextReference(),
                title,
                null,
                DealStatus.DRAFT,
                null,
                null,
                null,
                participantEntityId,
                userId,
                createdAt,
                createdAt,
                0);
    }

    private UUID insertPackage(UUID dealId, UUID buyerId, UUID sellerId, Instant createdAt) {
        jdbcTemplate.update("""
                INSERT INTO deal_participant (
                    deal_id, tenant_id, legal_entity_id, legal_entity_tenant_id, created_at
                ) VALUES (?, ?, ?, ?, ?)
                """, dealId, tenantId, sellerId, tenantId, java.sql.Timestamp.from(createdAt));
        assertTrue(repository.updateParties(tenantId, buyerId, dealId, 0, buyerId, sellerId, createdAt));
        UUID snapshotId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO ratification_package_snapshot (
                    id, schema_version, canonical_snapshot, content_hash, created_at
                ) VALUES (?, 1, '{}'::jsonb, ?, ?)
                """, snapshotId, "a".repeat(64), java.sql.Timestamp.from(createdAt));
        jdbcTemplate.update("""
                INSERT INTO ratification_package (
                    id, deal_id, snapshot_id, version, status,
                    buyer_legal_entity_id, seller_legal_entity_id,
                    amount_minor, currency, created_at
                ) VALUES (?, ?, ?, 0, 'PENDING', ?, ?, 1, 'TRY', ?)
                """, packageId, dealId, snapshotId, buyerId, sellerId,
                java.sql.Timestamp.from(createdAt));
        return packageId;
    }

    private OperationContext context(UUID contextTenantId, UUID legalEntityId) {
        return new OperationContext(userId, contextTenantId, legalEntityId,
                RequestedOperation.DEAL_DETAIL_READ);
    }

    private void insertLegalEntity(
            UUID legalEntityId, String legalName, String registrationNumber) {
        jdbcTemplate.update("""
                INSERT INTO legal_entity (
                    id, tenant_id, legal_name, registration_number
                )
                VALUES (?, ?, ?, ?)
                """,
                legalEntityId,
                tenantId,
                legalName,
                registrationNumber);
    }
}
