package com.m4trust.coreapi.deal;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Testcontainers
class DealIntegrationTest {

    private static final String LEGAL_ENTITY_HEADER =
            "X-M4Trust-Legal-Entity-Id";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.5-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    private PrincipalContext owner;
    private PrincipalContext participant;
    private PrincipalContext outsider;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM spring_session_attributes");
        jdbcTemplate.update("DELETE FROM spring_session");
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    http_idempotency_record,
                    deal_invitation,
                    deal_participant,
                    deal,
                    audit_record,
                    legal_entity_membership,
                    legal_entity,
                    tenant_user,
                    tenant,
                    identity_user
        """);
        owner = insertPrincipal("owner@example.com", "Owner Entity");
        participant = insertPrincipal("participant@example.com",
                "Participant Entity");
        outsider = insertPrincipal("outsider@example.com", "Outsider Entity");
    }

    @Test
    void createListDetailUpdateAndCancelMatchTheFrozenContract()
            throws Exception {
        String createCorrelation = UUID.randomUUID().toString();
        MvcResult firstCreation = mockMvc.perform(post("/api/v1/deals")
                        .with(user(owner.userId().toString()))
                        .with(csrf())
                        .header(LEGAL_ENTITY_HEADER, owner.legalEntityId())
                        .header("X-Correlation-ID", createCorrelation)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "  Equipment Purchase  "
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Location",
                        matchesPattern("/api/v1/deals/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.*", hasSize(11)))
                .andExpect(jsonPath("$.reference")
                        .value(matchesPattern("DL-[0-9]{10}")))
                .andExpect(jsonPath("$.title").value("Equipment Purchase"))
                .andExpect(jsonPath("$.description").value((Object) null))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.lifecycle").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.availableActions.canUpdate")
                        .value(true))
                .andExpect(jsonPath("$.availableActions.canCancel")
                        .value(true))
                .andExpect(jsonPath("$.availableActions.canCreateInvitation")
                        .value(true))
                .andExpect(jsonPath("$.participants", hasSize(1)))
                .andExpect(jsonPath("$.participants[0].legalEntityId")
                        .value(owner.legalEntityId().toString()))
                .andExpect(jsonPath("$.participants[0].legalName")
                        .value("Owner Entity"))
                .andReturn();
        UUID firstDealId = dealId(firstCreation);

        MvcResult secondCreation = createDeal("Alpha Deal");
        UUID secondDealId = dealId(secondCreation);

        mockMvc.perform(get("/api/v1/deals")
                        .with(user(owner.userId().toString()))
                        .header(LEGAL_ENTITY_HEADER, owner.legalEntityId())
                        .queryParam("page", "0")
                        .queryParam("size", "1")
                        .queryParam("sort", "title,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id")
                        .value(secondDealId.toString()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/v1/deals/" + firstDealId)
                        .with(user(owner.userId().toString()))
                        .header(LEGAL_ENTITY_HEADER, owner.legalEntityId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstDealId.toString()))
                .andExpect(jsonPath("$.description").value((Object) null));

        String updateCorrelation = UUID.randomUUID().toString();
        mockMvc.perform(patch("/api/v1/deals/" + firstDealId)
                        .with(user(owner.userId().toString()))
                        .with(csrf())
                        .header(LEGAL_ENTITY_HEADER, owner.legalEntityId())
                        .header("X-Correlation-ID", updateCorrelation)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "  Updated Equipment  ",
                                  "description": "Updated description",
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Equipment"))
                .andExpect(jsonPath("$.description")
                        .value("Updated description"))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(patch("/api/v1/deals/" + firstDealId)
                        .with(user(owner.userId().toString()))
                        .with(csrf())
                        .header(LEGAL_ENTITY_HEADER, owner.legalEntityId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Stale writer",
                                  "description": null,
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("DEAL_STALE_VERSION"));

        String cancelCorrelation = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/v1/deals/" + firstDealId + "/cancel")
                        .with(user(owner.userId().toString()))
                        .with(csrf())
                        .header(LEGAL_ENTITY_HEADER, owner.legalEntityId())
                        .header("X-Correlation-ID", cancelCorrelation))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.lifecycle").value("CANCELLED"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.availableActions.canUpdate")
                        .value(false))
                .andExpect(jsonPath("$.availableActions.canCancel")
                        .value(false));

        mockMvc.perform(patch("/api/v1/deals/" + firstDealId)
                        .with(user(owner.userId().toString()))
                        .with(csrf())
                        .header(LEGAL_ENTITY_HEADER, owner.legalEntityId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Cancelled edit",
                                  "description": null,
                                  "expectedVersion": 2
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("DEAL_STATE_CONFLICT"));

        mockMvc.perform(post("/api/v1/deals/" + firstDealId + "/cancel")
                        .with(user(owner.userId().toString()))
                        .with(csrf())
                        .header(LEGAL_ENTITY_HEADER, owner.legalEntityId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("DEAL_STATE_CONFLICT"));

        mockMvc.perform(get("/api/v1/deals")
                        .with(user(owner.userId().toString()))
                        .header(LEGAL_ENTITY_HEADER, owner.legalEntityId())
                        .queryParam("status", "CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id")
                        .value(firstDealId.toString()));

        mockMvc.perform(get("/api/v1/deals")
                        .with(user(owner.userId().toString()))
                        .header(LEGAL_ENTITY_HEADER, owner.legalEntityId())
                        .queryParam("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));

        assertEquals(List.of(
                        "DEAL_CREATED", "DEAL_CREATED",
                        "DEAL_UPDATED", "DEAL_CANCELLED"),
                jdbcTemplate.queryForList("""
                        SELECT action
                        FROM audit_record
                        WHERE subject_type = 'DEAL'
                        ORDER BY occurred_at, action
                        """, String.class));
        assertEquals(1, auditCount(firstDealId, "DEAL_CREATED",
                createCorrelation));
        assertEquals(1, auditCount(firstDealId, "DEAL_UPDATED",
                updateCorrelation));
        assertEquals(1, auditCount(firstDealId, "DEAL_CANCELLED",
                cancelCorrelation));
    }

    @Test
    void participantAuthorizationAndLegalEntityContextPreserveNonDisclosure()
            throws Exception {
        UUID dealId = dealId(createDeal("Private Deal"));

        mockMvc.perform(get("/api/v1/deals/" + dealId)
                        .with(user(outsider.userId().toString()))
                        .header(LEGAL_ENTITY_HEADER,
                                outsider.legalEntityId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEAL_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/deals")
                        .with(user(outsider.userId().toString()))
                        .header(LEGAL_ENTITY_HEADER,
                                outsider.legalEntityId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));

        mockMvc.perform(get("/api/v1/deals/" + dealId)
                        .with(user(outsider.userId().toString()))
                        .header(LEGAL_ENTITY_HEADER, owner.legalEntityId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("LEGAL_ENTITY_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/deals/" + dealId)
                        .with(user(owner.userId().toString())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("LEGAL_ENTITY_ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/deals/" + dealId)
                        .with(user(owner.userId().toString()))
                        .header(LEGAL_ENTITY_HEADER, "not-a-uuid"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("LEGAL_ENTITY_ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/deals/" + dealId)
                        .with(user(owner.userId().toString()))
                        .header(LEGAL_ENTITY_HEADER, UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("LEGAL_ENTITY_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/deals/not-a-uuid")
                        .with(user(owner.userId().toString()))
                        .header(LEGAL_ENTITY_HEADER, owner.legalEntityId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void crossTenantParticipantCanReadButOnlyInitiatorCanMutate()
            throws Exception {
        UUID dealId = dealId(createDeal("Cross Tenant Deal"));
        jdbcTemplate.update("""
                INSERT INTO deal_participant (
                    deal_id,
                    tenant_id,
                    legal_entity_id,
                    legal_entity_tenant_id
                )
                VALUES (?, ?, ?, ?)
                """, dealId, owner.tenantId(), participant.legalEntityId(),
                participant.tenantId());

        mockMvc.perform(get("/api/v1/deals")
                        .with(user(participant.userId().toString()))
                        .header(LEGAL_ENTITY_HEADER,
                                participant.legalEntityId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id")
                        .value(dealId.toString()))
                .andExpect(jsonPath("$.items[0].availableActions.canUpdate")
                        .value(false))
                .andExpect(jsonPath("$.items[0].availableActions.canCancel")
                        .value(false));

        mockMvc.perform(get("/api/v1/deals/" + dealId)
                        .with(user(participant.userId().toString()))
                        .header(LEGAL_ENTITY_HEADER,
                                participant.legalEntityId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dealId.toString()))
                .andExpect(jsonPath("$.availableActions.canUpdate")
                        .value(false))
                .andExpect(jsonPath("$.availableActions.canCancel")
                        .value(false));

        mockMvc.perform(patch("/api/v1/deals/" + dealId)
                        .with(user(participant.userId().toString()))
                        .with(csrf())
                        .header(LEGAL_ENTITY_HEADER,
                                participant.legalEntityId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Forced update",
                                  "description": null,
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("DEAL_MUTATION_FORBIDDEN"));

        mockMvc.perform(post("/api/v1/deals/" + dealId + "/cancel")
                        .with(user(participant.userId().toString()))
                        .with(csrf())
                        .header(LEGAL_ENTITY_HEADER,
                                participant.legalEntityId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("DEAL_MUTATION_FORBIDDEN"));

        assertEquals("DRAFT", jdbcTemplate.queryForObject("""
                SELECT deal_status FROM deal WHERE id = ?
                """, String.class, dealId));
        assertEquals(0L, jdbcTemplate.queryForObject("""
                SELECT version FROM deal WHERE id = ?
                """, Long.class, dealId));

        mockMvc.perform(get("/api/v1/deals/" + dealId)
                        .with(user(outsider.userId().toString()))
                        .header(LEGAL_ENTITY_HEADER,
                                outsider.legalEntityId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEAL_NOT_FOUND"));
    }

    @Test
    void invitationIsIdempotentAndAcceptanceCreatesCrossTenantParticipation()
            throws Exception {
        UUID dealId = dealId(createDeal("Invitation Deal"));
        UUID key = UUID.randomUUID();
        MvcResult created = createInvitation(
                dealId, participant.email(), key);
        UUID invitationId = UUID.fromString(JsonPath.read(
                created.getResponse().getContentAsString(), "$.id"));

        mockMvc.perform(post("/api/v1/deals/" + dealId + "/invitations")
                        .with(user(owner.userId().toString()))
                        .with(csrf())
                        .header(LEGAL_ENTITY_HEADER, owner.legalEntityId())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientEmail":"%s"}
                                """.formatted(participant.email())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(invitationId.toString()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM deal_invitation", Integer.class));

        mockMvc.perform(post("/api/v1/deals/" + dealId + "/invitations")
                        .with(user(owner.userId().toString()))
                        .with(csrf())
                        .header(LEGAL_ENTITY_HEADER, owner.legalEntityId())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientEmail":"%s"}
                                """.formatted(outsider.email())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        mockMvc.perform(post("/api/v1/deals/" + dealId + "/invitations")
                        .with(user(owner.userId().toString()))
                        .with(csrf())
                        .header(LEGAL_ENTITY_HEADER, owner.legalEntityId())
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientEmail":"%s"}
                                """.formatted(participant.email())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("DEAL_INVITATION_PENDING_EXISTS"));

        mockMvc.perform(get("/api/v1/deal-invitations/incoming")
                        .with(user(participant.userId().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].deal.*", hasSize(4)))
                .andExpect(jsonPath("$.items[0].deal.id")
                        .value(dealId.toString()))
                .andExpect(jsonPath("$.items[0].deal.initiatorLegalName")
                        .value("Owner Entity"))
                .andExpect(jsonPath("$.items[0].availableActions.canAccept")
                        .value(true));

        mockMvc.perform(post("/api/v1/deal-invitations/" + invitationId
                        + "/accept")
                        .with(user(outsider.userId().toString()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"legalEntityId":"%s","expectedVersion":0}
                                """.formatted(outsider.legalEntityId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("DEAL_INVITATION_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/deal-invitations/" + invitationId
                        + "/accept")
                        .with(user(participant.userId().toString()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"legalEntityId":"%s","expectedVersion":0}
                                """.formatted(participant.legalEntityId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(post("/api/v1/deal-invitations/" + invitationId
                        + "/accept")
                        .with(user(participant.userId().toString()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"legalEntityId":"%s","expectedVersion":0}
                                """.formatted(participant.legalEntityId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        UUID otherEntityId = insertAdditionalLegalEntity(
                participant, "Participant Alternate Entity");
        mockMvc.perform(post("/api/v1/deal-invitations/" + invitationId
                        + "/accept")
                        .with(user(participant.userId().toString()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"legalEntityId":"%s","expectedVersion":0}
                                """.formatted(otherEntityId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("DEAL_INVITATION_ACCEPTED_BY_OTHER_ENTITY"));

        mockMvc.perform(get("/api/v1/deals/" + dealId)
                        .with(user(participant.userId().toString()))
                        .header(LEGAL_ENTITY_HEADER,
                                participant.legalEntityId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants", hasSize(2)))
                .andExpect(jsonPath("$.availableActions.canUpdate")
                        .value(false))
                .andExpect(jsonPath("$.availableActions.canCancel")
                        .value(false))
                .andExpect(jsonPath("$.availableActions.canCreateInvitation")
                        .value(false));
        assertEquals(participant.tenantId(), jdbcTemplate.queryForObject("""
                SELECT legal_entity_tenant_id
                FROM deal_participant
                WHERE deal_id = ? AND legal_entity_id = ?
                """, UUID.class, dealId, participant.legalEntityId()));
        assertEquals(1, invitationAuditCount(
                invitationId, "DEAL_INVITATION_ACCEPTED",
                participant.tenantId()));
    }

    @Test
    void invitationAuthorizationRejectAndRevokeUseIndependentAuthority()
            throws Exception {
        UUID dealId = dealId(createDeal("Invitation Authority"));
        insertParticipant(dealId, participant);

        mockMvc.perform(post("/api/v1/deals/" + dealId + "/invitations")
                        .with(user(participant.userId().toString()))
                        .with(csrf())
                        .header(LEGAL_ENTITY_HEADER,
                                participant.legalEntityId())
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientEmail":"%s"}
                                """.formatted(outsider.email())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("DEAL_INVITATION_FORBIDDEN"));

        UUID invitationId = invitationId(createInvitation(
                dealId, outsider.email(), UUID.randomUUID()));
        mockMvc.perform(post("/api/v1/deal-invitations/" + invitationId
                        + "/revoke")
                        .with(user(participant.userId().toString()))
                        .with(csrf())
                        .header(LEGAL_ENTITY_HEADER,
                                participant.legalEntityId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("DEAL_INVITATION_FORBIDDEN"));

        mockMvc.perform(post("/api/v1/deal-invitations/" + invitationId
                        + "/reject")
                        .with(user(outsider.userId().toString()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(post("/api/v1/deal-invitations/" + invitationId
                        + "/revoke")
                        .with(user(owner.userId().toString()))
                        .with(csrf())
                        .header(LEGAL_ENTITY_HEADER, owner.legalEntityId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("DEAL_INVITATION_STATE_CONFLICT"));
        assertEquals(1, invitationAuditCount(
                invitationId, "DEAL_INVITATION_REJECTED",
                outsider.tenantId()));
    }

    @Test
    void concurrentAcceptAndRevokeProduceOneTerminalTransition()
            throws Exception {
        UUID dealId = dealId(createDeal("Invitation Race"));
        UUID invitationId = invitationId(createInvitation(
                dealId, participant.email(), UUID.randomUUID()));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> accept = executor.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/v1/deal-invitations/"
                                + invitationId + "/accept")
                                .with(user(participant.userId().toString()))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"legalEntityId":"%s","expectedVersion":0}
                                        """.formatted(
                                                participant.legalEntityId())))
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> revoke = executor.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/v1/deal-invitations/"
                                + invitationId + "/revoke")
                                .with(user(owner.userId().toString()))
                                .with(csrf())
                                .header(LEGAL_ENTITY_HEADER,
                                        owner.legalEntityId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"expectedVersion\":0}"))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();
            List<Integer> statuses = List.of(accept.get(), revoke.get());
            assertTrue(statuses.contains(200));
            assertTrue(statuses.contains(409));
        } finally {
            executor.shutdownNow();
        }

        String terminalStatus = jdbcTemplate.queryForObject("""
                SELECT invitation_status FROM deal_invitation WHERE id = ?
                """, String.class, invitationId);
        assertTrue(List.of("ACCEPTED", "REVOKED").contains(terminalStatus));
        int participantCount = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM deal_participant WHERE deal_id = ?
                """, Integer.class, dealId);
        assertEquals("ACCEPTED".equals(terminalStatus) ? 2 : 1,
                participantCount);
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT count(*) FROM audit_record
                WHERE subject_type = 'DEAL_INVITATION'
                  AND subject_id = ?
                  AND action IN (
                      'DEAL_INVITATION_ACCEPTED',
                      'DEAL_INVITATION_REVOKED'
                  )
                """, Integer.class, invitationId));
    }

    @Test
    void semanticQueryAndRequiredNullableFieldsReturnStableValidationErrors()
            throws Exception {
        String maxTitle = "X".repeat(200);
        MvcResult normalized = createDeal("  " + maxTitle + "  ");
        assertEquals(maxTitle, JsonPath.read(
                normalized.getResponse().getContentAsString(), "$.title"));

        mockMvc.perform(post("/api/v1/deals")
                        .with(user(owner.userId().toString()))
                        .with(csrf())
                        .header(LEGAL_ENTITY_HEADER, owner.legalEntityId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "   "
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("title"));

        mockMvc.perform(get("/api/v1/deals")
                        .with(user(owner.userId().toString()))
                        .header(LEGAL_ENTITY_HEADER, owner.legalEntityId())
                        .queryParam("page", "-1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("page"))
                .andExpect(jsonPath("$.errors[0].code")
                        .value("OUT_OF_RANGE"));

        mockMvc.perform(get("/api/v1/deals")
                        .with(user(owner.userId().toString()))
                        .header(LEGAL_ENTITY_HEADER, owner.legalEntityId())
                        .queryParam("sort", "version,desc"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("sort"));

        mockMvc.perform(get("/api/v1/deals")
                        .with(user(owner.userId().toString()))
                        .header(LEGAL_ENTITY_HEADER, owner.legalEntityId())
                        .queryParam("page", "not-an-integer"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        UUID dealId = dealId(createDeal("Validation Deal"));
        mockMvc.perform(patch("/api/v1/deals/" + dealId)
                        .with(user(owner.userId().toString()))
                        .with(csrf())
                        .header(LEGAL_ENTITY_HEADER, owner.legalEntityId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Missing description",
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field")
                        .value("description"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));

        mockMvc.perform(patch("/api/v1/deals/" + dealId)
                        .with(user(owner.userId().toString()))
                        .with(csrf())
                        .header(LEGAL_ENTITY_HEADER, owner.legalEntityId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Clear description",
                                  "description": null,
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value((Object) null))
                .andExpect(jsonPath("$.version").value(1));
    }

    private MvcResult createDeal(String title) throws Exception {
        return mockMvc.perform(post("/api/v1/deals")
                        .with(user(owner.userId().toString()))
                        .with(csrf())
                        .header(LEGAL_ENTITY_HEADER, owner.legalEntityId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s"
                                }
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private MvcResult createInvitation(UUID dealId, String recipientEmail,
            UUID idempotencyKey) throws Exception {
        return mockMvc.perform(post("/api/v1/deals/" + dealId + "/invitations")
                        .with(user(owner.userId().toString()))
                        .with(csrf())
                        .header(LEGAL_ENTITY_HEADER, owner.legalEntityId())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientEmail":"%s"}
                                """.formatted(recipientEmail)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.availableActions.canRevoke")
                        .value(true))
                .andReturn();
    }

    private UUID invitationId(MvcResult result) throws Exception {
        return UUID.fromString(JsonPath.read(
                result.getResponse().getContentAsString(), "$.id"));
    }

    private void insertParticipant(UUID dealId, PrincipalContext principal) {
        jdbcTemplate.update("""
                INSERT INTO deal_participant (
                    deal_id, tenant_id, legal_entity_id,
                    legal_entity_tenant_id
                )
                SELECT id, tenant_id, ?, ?
                FROM deal
                WHERE id = ?
                """, principal.legalEntityId(), principal.tenantId(), dealId);
    }

    private UUID insertAdditionalLegalEntity(PrincipalContext principal,
            String legalName) {
        UUID legalEntityId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO legal_entity (
                    id, tenant_id, legal_name, registration_number
                ) VALUES (?, ?, ?, ?)
                """, legalEntityId, principal.tenantId(), legalName,
                "REG-" + legalEntityId);
        jdbcTemplate.update("""
                INSERT INTO legal_entity_membership (
                    id, tenant_id, legal_entity_id, user_id, role
                ) VALUES (?, ?, ?, ?, 'MEMBER')
                """, UUID.randomUUID(), principal.tenantId(), legalEntityId,
                principal.userId());
        return legalEntityId;
    }

    private UUID dealId(MvcResult result) throws Exception {
        return UUID.fromString(JsonPath.read(
                result.getResponse().getContentAsString(), "$.id"));
    }

    private int auditCount(UUID dealId, String action,
            String correlationId) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM audit_record
                WHERE subject_type = 'DEAL'
                  AND subject_id = ?
                  AND action = ?
                  AND correlation_id = ?
                """, Integer.class, dealId, action,
                UUID.fromString(correlationId));
    }

    private int invitationAuditCount(UUID invitationId, String action,
            UUID actorTenantId) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM audit_record
                WHERE tenant_id = ?
                  AND subject_type = 'DEAL_INVITATION'
                  AND subject_id = ?
                  AND action = ?
                """, Integer.class, actorTenantId, invitationId, action);
    }

    private PrincipalContext insertPrincipal(
            String email, String legalName) {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID legalEntityId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO identity_user (
                    id, email, password_hash, display_name, enabled
                )
                VALUES (?, ?, ?, ?, true)
                """, userId, email, "test-hash", legalName + " User");
        jdbcTemplate.update("INSERT INTO tenant (id) VALUES (?)", tenantId);
        jdbcTemplate.update("""
                INSERT INTO tenant_user (user_id, tenant_id)
                VALUES (?, ?)
                """, userId, tenantId);
        jdbcTemplate.update("""
                INSERT INTO legal_entity (
                    id, tenant_id, legal_name, registration_number
                )
                VALUES (?, ?, ?, ?)
                """, legalEntityId, tenantId, legalName,
                "REG-" + legalEntityId);
        jdbcTemplate.update("""
                INSERT INTO legal_entity_membership (
                    id, tenant_id, legal_entity_id, user_id, role
                )
                VALUES (?, ?, ?, ?, 'ADMIN')
                """, UUID.randomUUID(), tenantId, legalEntityId, userId);
        return new PrincipalContext(userId, tenantId, legalEntityId, email);
    }

    private record PrincipalContext(
            UUID userId, UUID tenantId, UUID legalEntityId, String email) {
    }
}
