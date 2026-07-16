package com.m4trust.coreapi.deal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.m4trust.coreapi.deal.DealRepository.DealRecord;
import com.m4trust.coreapi.deal.DealRepository.DealSort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
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
                    deal_participant,
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

        repository.insert(deal);

        assertTrue(repository.findVisibleById(
                tenantId, participantEntityId, deal.id()).isPresent());
        assertFalse(repository.findVisibleById(
                tenantId, otherEntityId, deal.id()).isPresent());
        assertEquals(1, repository.countVisible(
                tenantId, participantEntityId, null));
        assertEquals(0, repository.countVisible(
                tenantId, otherEntityId, null));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM deal_participant
                WHERE deal_id = ?
                  AND legal_entity_id = ?
                """, Integer.class, deal.id(), participantEntityId));
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
        repository.insert(lowId);
        repository.insert(highId);
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
        repository.insert(deal);

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

    private DealRecord newDeal(UUID dealId, String title, Instant createdAt) {
        return new DealRecord(
                dealId,
                tenantId,
                repository.nextReference(),
                title,
                null,
                DealStatus.DRAFT,
                participantEntityId,
                userId,
                createdAt,
                createdAt,
                0);
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
