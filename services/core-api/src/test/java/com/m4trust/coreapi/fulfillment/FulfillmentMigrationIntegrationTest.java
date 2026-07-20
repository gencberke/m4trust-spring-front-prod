package com.m4trust.coreapi.fulfillment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Exercises the V20 database boundary directly, after a complete V1..V20 Flyway migration. */
@Testcontainers
class FulfillmentMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.5-alpine");

    static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    @Test
    void enforcesFulfillmentAndEvidenceInvariants() {
        Fixture f = fixture();
        UUID packageId = UUID.randomUUID();

        UUID fulfillmentId = fulfillment(f.dealId, f.tenant, packageId, "IN_PROGRESS", 0L);

        assertThrows(DataAccessException.class,
                () -> fulfillment(f.dealId, f.tenant, packageId, "NOT_STARTED", 0L),
                "only one fulfillment row per deal is allowed");
        assertThrows(DataAccessException.class,
                () -> jdbc.update("INSERT INTO fulfillment (id, deal_id, tenant_id, source_package_id, status, version, created_at, updated_at) VALUES (?, ?, ?, ?, 'INVALID', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                        UUID.randomUUID(), f.otherDealId, f.tenant, packageId),
                "invalid fulfillment status is rejected");
        assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE fulfillment SET status='BAD' WHERE id=?", fulfillmentId),
                "updating to an invalid fulfillment status is rejected");
        assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE fulfillment SET version=-1 WHERE id=?", fulfillmentId),
                "negative fulfillment version is rejected");

        UUID milestoneId = milestone(fulfillmentId, f.dealId, "Primary", "IN_PROGRESS", 0L);
        UUID otherFulfillmentId = fulfillment(f.otherDealId, f.tenant, UUID.randomUUID(), "IN_PROGRESS", 0L);
        UUID otherMilestoneId = milestone(otherFulfillmentId, f.otherDealId, "Other", "IN_PROGRESS", 0L);

        assertThrows(DataAccessException.class,
                () -> milestone(fulfillmentId, f.dealId, "Duplicate", "NOT_STARTED", 0L),
                "only one milestone per fulfillment is allowed");
        assertThrows(DataAccessException.class,
                () -> milestone(otherFulfillmentId, f.dealId, "Cross Deal", "IN_PROGRESS", 0L),
                "a milestone must belong to the same Deal as its fulfillment");
        assertThrows(DataAccessException.class,
                () -> jdbc.update("INSERT INTO fulfillment_milestone (id, fulfillment_id, deal_id, title, status, version, created_at, updated_at) VALUES (?, ?, ?, ?, 'INVALID', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                        UUID.randomUUID(), fulfillmentId, f.dealId, "Bad"),
                "invalid milestone status is rejected");

        assertDoesNotThrow(() -> ruleReference(milestoneId, "delivery-1", "DELIVERY"));
        assertThrows(DataAccessException.class,
                () -> ruleReference(milestoneId, "payment-1", "INVALID"),
                "invalid rule-reference category is rejected");
        assertThrows(DataAccessException.class,
                () -> ruleReference(milestoneId, "delivery-1", "QUALITY"),
                "duplicate rule reference for a milestone is rejected");

        String sha256 = "a".repeat(64);
        UUID pendingEvidence = evidence(UUID.randomUUID(), f.dealId, milestoneId, fulfillmentId,
                "PENDING_UPLOAD", sha256, 0L);
        assertThrows(DataAccessException.class,
                () -> evidence(UUID.randomUUID(), f.dealId, otherMilestoneId, otherFulfillmentId,
                        "PENDING_UPLOAD", sha256, 0L),
                "evidence must belong to the same Deal as its milestone and fulfillment");

        assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE fulfillment_evidence_submission SET status='UNKNOWN' WHERE id=?", pendingEvidence),
                "updating evidence to an invalid status is rejected");
        assertThrows(DataAccessException.class,
                () -> evidence(UUID.randomUUID(), f.dealId, milestoneId, fulfillmentId,
                        "PENDING_UPLOAD", "not-a-hash", 0L),
                "invalid client sha256 is rejected");
        assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE fulfillment_evidence_submission SET verified_sha256='not-a-hash' WHERE id=?", pendingEvidence),
                "invalid verified sha256 is rejected");

        UUID submittedEvidence = evidence(UUID.randomUUID(), f.dealId, milestoneId, fulfillmentId,
                "SUBMITTED", sha256, 1L);
        assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE fulfillment_evidence_submission SET object_key='changed' WHERE id=?",
                        submittedEvidence),
                "evidence object key is immutable");
        assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE fulfillment_evidence_submission SET object_version='changed' WHERE id=?",
                        submittedEvidence),
                "finalized evidence object version is immutable");
        assertThrows(DataAccessException.class,
                () -> evidence(UUID.randomUUID(), f.dealId, milestoneId, fulfillmentId,
                        "SUBMITTED", sha256, 2L),
                "at most one current SUBMITTED evidence per milestone is allowed");
        assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE fulfillment_evidence_submission SET status='SUBMITTED' WHERE id=?", pendingEvidence),
                "transitioning a second evidence to SUBMITTED violates the partial unique index");

        assertThrows(DataAccessException.class,
                () -> jdbc.update("DELETE FROM fulfillment_milestone WHERE id=?", milestoneId),
                "cannot delete a milestone referenced by evidence");
        assertThrows(DataAccessException.class,
                () -> jdbc.update("DELETE FROM fulfillment WHERE id=?", fulfillmentId),
                "cannot delete a fulfillment referenced by a milestone");
    }

    private static UUID fulfillment(UUID dealId, UUID tenantId, UUID sourcePackageId, String status, long version) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO fulfillment (id, deal_id, tenant_id, source_package_id, status, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, dealId, tenantId, sourcePackageId, status, version);
        return id;
    }

    private static UUID milestone(UUID fulfillmentId, UUID dealId, String title, String status, long version) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO fulfillment_milestone (id, fulfillment_id, deal_id, title, status, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, fulfillmentId, dealId, title, status, version);
        return id;
    }

    private static void ruleReference(UUID milestoneId, String ruleReference, String category) {
        jdbc.update("""
                INSERT INTO fulfillment_milestone_rule_reference (milestone_id, rule_reference, category)
                VALUES (?, ?, ?)
                """, milestoneId, ruleReference, category);
    }

    private static UUID evidence(UUID id, UUID dealId, UUID milestoneId, UUID fulfillmentId,
            String status, String sha256, long version) {
        jdbc.update("""
                INSERT INTO fulfillment_evidence_submission (
                    id, deal_id, milestone_id, fulfillment_id, evidence_type, media_type, file_name,
                    status, object_key, object_version, client_size_bytes, client_sha256, verified_size_bytes,
                    verified_sha256, upload_expires_at, created_at, submitted_at, accepted_at,
                    rejected_at, rejection_reason, version
                ) VALUES (?, ?, ?, ?, 'INVOICE', 'application/pdf', 'invoice.pdf', ?, 'obj-key',
                    CASE WHEN ? IN ('SUBMITTED', 'ACCEPTED', 'REJECTED') THEN 'version-1' ELSE NULL END,
                    12, ?,
                    CASE WHEN ? IN ('SUBMITTED', 'ACCEPTED', 'REJECTED') THEN 12 ELSE NULL END,
                    CASE WHEN ? IN ('SUBMITTED', 'ACCEPTED', 'REJECTED') THEN ? ELSE NULL END,
                    CURRENT_TIMESTAMP + INTERVAL '1 hour', CURRENT_TIMESTAMP,
                    CASE WHEN ? IN ('SUBMITTED', 'ACCEPTED', 'REJECTED') THEN CURRENT_TIMESTAMP ELSE NULL END,
                    CASE WHEN ? = 'ACCEPTED' THEN CURRENT_TIMESTAMP ELSE NULL END,
                    CASE WHEN ? = 'REJECTED' THEN CURRENT_TIMESTAMP ELSE NULL END,
                    CASE WHEN ? = 'REJECTED' THEN 'reason' ELSE NULL END,
                    ?)
                """,
                id, dealId, milestoneId, fulfillmentId, status, status, sha256,
                status, status, sha256, status, status, status, status, version);
        return id;
    }

    private static Fixture fixture() {
        UUID tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID deal = UUID.randomUUID();
        UUID otherDeal = UUID.randomUUID();

        jdbc.update("INSERT INTO identity_user(id, email, password_hash, display_name, enabled) VALUES (?, ?, 'x', 'User', true)",
                user, user + "@example.com");
        jdbc.update("INSERT INTO tenant(id) VALUES (?)", tenant);
        jdbc.update("INSERT INTO tenant_user(user_id, tenant_id) VALUES (?, ?)", user, tenant);

        for (UUID entity : java.util.List.of(buyer, seller)) {
            jdbc.update("INSERT INTO legal_entity(id, tenant_id, legal_name, registration_number) VALUES (?, ?, 'Entity', ?)",
                    entity, tenant, "R" + entity);
            jdbc.update("INSERT INTO legal_entity_membership(id, tenant_id, legal_entity_id, user_id, role) VALUES (?, ?, ?, ?, 'ADMIN')",
                    UUID.randomUUID(), tenant, entity, user);
        }

        deal(deal, tenant, buyer, user, "DL-0000000001");
        deal(otherDeal, tenant, buyer, user, "DL-0000000002");

        for (UUID d : java.util.List.of(deal, otherDeal)) {
            for (UUID entity : java.util.List.of(buyer, seller)) {
                jdbc.update("INSERT INTO deal_participant(deal_id, tenant_id, legal_entity_id, legal_entity_tenant_id) VALUES (?, ?, ?, ?)",
                        d, tenant, entity, tenant);
            }
        }

        return new Fixture(tenant, deal, otherDeal);
    }

    private static void deal(UUID id, UUID tenant, UUID entity, UUID user, String reference) {
        jdbc.update("INSERT INTO deal(id, tenant_id, reference, title, deal_status, initiator_legal_entity_id, created_by) VALUES (?, ?, ?, 'Deal', 'DRAFT', ?, ?)",
                id, tenant, reference, entity, user);
    }

    record Fixture(UUID tenant, UUID dealId, UUID otherDealId) {
    }
}
