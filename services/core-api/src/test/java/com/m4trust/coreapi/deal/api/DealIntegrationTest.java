package com.m4trust.coreapi.deal.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.m4trust.coreapi.deal.api.port.*;
import com.m4trust.coreapi.deal.domain.*;
import com.m4trust.coreapi.deal.infra.repository.*;
import com.m4trust.coreapi.support.PostgresIntegrationTestSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
class DealIntegrationTest extends PostgresIntegrationTestSupport {

  private static final String LEGAL_ENTITY_HEADER = "X-M4Trust-Legal-Entity-Id";

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private MockMvc mockMvc;

  private PrincipalContext owner;
  private PrincipalContext participant;
  private PrincipalContext outsider;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM spring_session_attributes");
    jdbcTemplate.update("DELETE FROM spring_session");
    jdbcTemplate.execute(
        """
                TRUNCATE TABLE
                    dispute_comment, dispute_evidence_snapshot, dispute_case, fulfillment_video_analysis_result,
                    fulfillment_video_analysis_job,
                    fulfillment_evidence_submission,
                    fulfillment_milestone_rule_reference,
                    fulfillment_milestone,
                    fulfillment,
                    release_dispatch,
                    release_operation,
                    settlement,
                    payment_dispatch,
                    payment_operation,
                    funding_unit,
                    funding_plan,
                    contract_intelligence_rule_set_version,
                    contract_intelligence_extraction_result_version,
                    contract_intelligence_analysis_job,
                    http_idempotency_record,
                    deal_invitation,
                    deal_participant,
                    document,
                    ratification_package_approval,
                    ratification_package,
                    ratification_package_snapshot,
                    deal,
                    audit_record,
                    legal_entity_membership,
                    legal_entity,
                    tenant_user,
                    tenant,
                    identity_user
        """);
    owner = insertPrincipal("owner@example.com", "Owner Entity");
    participant = insertPrincipal("participant@example.com", "Participant Entity");
    outsider = insertPrincipal("outsider@example.com", "Outsider Entity");
  }

  @Test
  void createListDetailUpdateAndCancelMatchTheFrozenContract() throws Exception {
    String createCorrelation = UUID.randomUUID().toString();
    MvcResult firstCreation =
        mockMvc
            .perform(
                post("/api/v1/deals")
                    .with(user(owner.userId().toString()))
                    .with(csrf())
                    .header(LEGAL_ENTITY_HEADER, owner.legalEntityId())
                    .header("X-Correlation-ID", createCorrelation)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                                {
                                  "title": "  Equipment Purchase  "
                                }
                                """))
            .andExpect(status().isCreated())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(header().string("Location", matchesPattern("/api/v1/deals/[0-9a-f-]{36}")))
            .andExpect(jsonPath("$.*", hasSize(21)))
            .andExpect(jsonPath("$.reference").value(matchesPattern("DL-[0-9]{10}")))
            .andExpect(jsonPath("$.title").value("Equipment Purchase"))
            .andExpect(jsonPath("$.description").value((Object) null))
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.lifecycle").value("DRAFT"))
            .andExpect(jsonPath("$.version").value(0))
            .andExpect(jsonPath("$.availableActions.canUpdate").value(true))
            .andExpect(jsonPath("$.availableActions.canCancel").value(true))
            .andExpect(jsonPath("$.analysis.status").value("NOT_REQUESTED"))
            .andExpect(jsonPath("$.availableActions.canCreateInvitation").value(true))
            .andExpect(jsonPath("$.availableActions.canManageParties").value(true))
            .andExpect(jsonPath("$.availableActions.canCreateDocumentUploadIntent").value(true))
            .andExpect(jsonPath("$.availableActions.canCreateRatificationPackage").value(false))
            .andExpect(jsonPath("$.availableActions.canApproveRatification").value(false))
            .andExpect(jsonPath("$.availableActions.canRejectRatification").value(false))
            .andExpect(jsonPath("$.buyer").value((Object) null))
            .andExpect(jsonPath("$.seller").value((Object) null))
            .andExpect(jsonPath("$.currentDocument").value((Object) null))
            .andExpect(jsonPath("$.ratification.readiness").value("NOT_READY"))
            .andExpect(jsonPath("$.ratification.currentPackage").value((Object) null))
            .andExpect(jsonPath("$.funding.fundingStatus").value("NOT_CONFIGURED"))
            .andExpect(jsonPath("$.funding.fundingPlanId").value((Object) null))
            .andExpect(jsonPath("$.settlement").value((Object) null))
            .andExpect(jsonPath("$.participants", hasSize(1)))
            .andExpect(
                jsonPath("$.participants[0].legalEntityId").value(owner.legalEntityId().toString()))
            .andExpect(jsonPath("$.participants[0].legalName").value("Owner Entity"))
            .andExpect(jsonPath("$.participants[0].partyRoles", hasSize(0)))
            .andReturn();
    UUID firstDealId = dealId(firstCreation);

    MvcResult secondCreation = createDeal("Alpha Deal");
    UUID secondDealId = dealId(secondCreation);

    mockMvc
        .perform(
            get("/api/v1/deals")
                .with(user(owner.userId().toString()))
                .header(LEGAL_ENTITY_HEADER, owner.legalEntityId())
                .queryParam("page", "0")
                .queryParam("size", "1")
                .queryParam("sort", "title,asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].id").value(secondDealId.toString()))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(1))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.totalPages").value(2));

    mockMvc
        .perform(
            get("/api/v1/deals/" + firstDealId)
                .with(user(owner.userId().toString()))
                .header(LEGAL_ENTITY_HEADER, owner.legalEntityId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(firstDealId.toString()))
        .andExpect(jsonPath("$.description").value((Object) null));

    String updateCorrelation = UUID.randomUUID().toString();
    mockMvc
        .perform(
            patch("/api/v1/deals/" + firstDealId)
                .with(user(owner.userId().toString()))
                .with(csrf())
                .header(LEGAL_ENTITY_HEADER, owner.legalEntityId())
                .header("X-Correlation-ID", updateCorrelation)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "title": "  Updated Equipment  ",
                                  "description": "Updated description",
                                  "expectedVersion": 0
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Updated Equipment"))
        .andExpect(jsonPath("$.description").value("Updated description"))
        .andExpect(jsonPath("$.version").value(1));

    mockMvc
        .perform(
            patch("/api/v1/deals/" + firstDealId)
                .with(user(owner.userId().toString()))
                .with(csrf())
                .header(LEGAL_ENTITY_HEADER, owner.legalEntityId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "title": "Stale writer",
                                  "description": null,
                                  "expectedVersion": 0
                                }
                                """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DEAL_STALE_VERSION"));

    String cancelCorrelation = UUID.randomUUID().toString();
    mockMvc
        .perform(
            post("/api/v1/deals/" + firstDealId + "/cancel")
                .with(user(owner.userId().toString()))
                .with(csrf())
                .header(LEGAL_ENTITY_HEADER, owner.legalEntityId())
                .header("X-Correlation-ID", cancelCorrelation))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(jsonPath("$.lifecycle").value("CANCELLED"))
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.availableActions.canUpdate").value(false))
        .andExpect(jsonPath("$.availableActions.canCancel").value(false));

    mockMvc
        .perform(
            patch("/api/v1/deals/" + firstDealId)
                .with(user(owner.userId().toString()))
                .with(csrf())
                .header(LEGAL_ENTITY_HEADER, owner.legalEntityId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "title": "Cancelled edit",
                                  "description": null,
                                  "expectedVersion": 2
                                }
                                """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DEAL_STATE_CONFLICT"));

    mockMvc
        .perform(
            post("/api/v1/deals/" + firstDealId + "/cancel")
                .with(user(owner.userId().toString()))
                .with(csrf())
                .header(LEGAL_ENTITY_HEADER, owner.legalEntityId()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DEAL_STATE_CONFLICT"));

    mockMvc
        .perform(
            get("/api/v1/deals")
                .with(user(owner.userId().toString()))
                .header(LEGAL_ENTITY_HEADER, owner.legalEntityId())
                .queryParam("status", "CANCELLED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].id").value(firstDealId.toString()));

    mockMvc
        .perform(
            get("/api/v1/deals")
                .with(user(owner.userId().toString()))
                .header(LEGAL_ENTITY_HEADER, owner.legalEntityId())
                .queryParam("status", "ACTIVE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.items", hasSize(0)))
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.totalPages").value(0));

    assertEquals(
        List.of(
            "DEAL_CREATED", "DEAL_CREATED",
            "DEAL_UPDATED", "DEAL_CANCELLED"),
        jdbcTemplate.queryForList(
            """
                        SELECT action
                        FROM audit_record
                        WHERE subject_type = 'DEAL'
                        ORDER BY occurred_at, action
                        """,
            String.class));
    assertEquals(1, auditCount(firstDealId, "DEAL_CREATED", createCorrelation));
    assertEquals(1, auditCount(firstDealId, "DEAL_UPDATED", updateCorrelation));
    assertEquals(1, auditCount(firstDealId, "DEAL_CANCELLED", cancelCorrelation));
  }

  @Test
  void crossTenantParticipantCanReadButOnlyInitiatorCanMutate() throws Exception {
    UUID dealId = dealId(createDeal("Cross Tenant Deal"));
    UUID invitationId =
        invitationId(createInvitation(dealId, participant.email(), UUID.randomUUID()));

    mockMvc
        .perform(
            post("/api/v1/deal-invitations/" + invitationId + "/accept")
                .with(user(participant.userId().toString()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"legalEntityId":"%s","expectedVersion":0}
                    """
                        .formatted(participant.legalEntityId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACCEPTED"));

    mockMvc
        .perform(
            patch("/api/v1/deals/" + dealId + "/parties")
                .with(user(owner.userId().toString()))
                .with(csrf())
                .header(LEGAL_ENTITY_HEADER, owner.legalEntityId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"buyerLegalEntityId":"%s","sellerLegalEntityId":"%s","expectedVersion":0}
                    """
                        .formatted(owner.legalEntityId(), participant.legalEntityId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.buyer.legalEntityId").value(owner.legalEntityId().toString()))
        .andExpect(
            jsonPath("$.seller.legalEntityId").value(participant.legalEntityId().toString()));

    mockMvc
        .perform(
            get("/api/v1/deals")
                .with(user(participant.userId().toString()))
                .header(LEGAL_ENTITY_HEADER, participant.legalEntityId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].id").value(dealId.toString()))
        .andExpect(jsonPath("$.items[0].availableActions.canUpdate").value(false))
        .andExpect(jsonPath("$.items[0].availableActions.canCancel").value(false))
        .andExpect(jsonPath("$.items[0].availableActions.canManageParties").value(false));

    mockMvc
        .perform(
            get("/api/v1/deals/" + dealId)
                .with(user(participant.userId().toString()))
                .header(LEGAL_ENTITY_HEADER, participant.legalEntityId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(dealId.toString()))
        .andExpect(jsonPath("$.availableActions.canUpdate").value(false))
        .andExpect(jsonPath("$.availableActions.canCancel").value(false))
        .andExpect(jsonPath("$.availableActions.canManageParties").value(false));

    mockMvc
        .perform(
            patch("/api/v1/deals/" + dealId)
                .with(user(participant.userId().toString()))
                .with(csrf())
                .header(LEGAL_ENTITY_HEADER, participant.legalEntityId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "title": "Forced update",
                                  "description": null,
                                  "expectedVersion": 0
                                }
                                """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("DEAL_MUTATION_FORBIDDEN"));

    mockMvc
        .perform(
            post("/api/v1/deals/" + dealId + "/cancel")
                .with(user(participant.userId().toString()))
                .with(csrf())
                .header(LEGAL_ENTITY_HEADER, participant.legalEntityId()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("DEAL_MUTATION_FORBIDDEN"));

    mockMvc
        .perform(
            patch("/api/v1/deals/" + dealId + "/parties")
                .with(user(participant.userId().toString()))
                .with(csrf())
                .header(LEGAL_ENTITY_HEADER, participant.legalEntityId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "buyerLegalEntityId": null,
                                  "sellerLegalEntityId": null,
                                  "expectedVersion": 0
                                }
                                """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("DEAL_MUTATION_FORBIDDEN"));

    assertEquals(
        "DRAFT",
        jdbcTemplate.queryForObject(
            """
                SELECT deal_status FROM deal WHERE id = ?
                """,
            String.class,
            dealId));
    assertEquals(
        1L,
        jdbcTemplate.queryForObject(
            """
                SELECT version FROM deal WHERE id = ?
                """,
            Long.class,
            dealId));

    mockMvc
        .perform(
            get("/api/v1/deals/" + dealId)
                .with(user(outsider.userId().toString()))
                .header(LEGAL_ENTITY_HEADER, outsider.legalEntityId()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DEAL_NOT_FOUND"));
  }

  private MvcResult createDeal(String title) throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/deals")
                .with(user(owner.userId().toString()))
                .with(csrf())
                .header(LEGAL_ENTITY_HEADER, owner.legalEntityId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "title": "%s"
                                }
                                """
                        .formatted(title)))
        .andExpect(status().isCreated())
        .andReturn();
  }

  private MvcResult createInvitation(UUID dealId, String recipientEmail, UUID idempotencyKey)
      throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/deals/" + dealId + "/invitations")
                .with(user(owner.userId().toString()))
                .with(csrf())
                .header(LEGAL_ENTITY_HEADER, owner.legalEntityId())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"recipientEmail":"%s"}
                                """
                        .formatted(recipientEmail)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.availableActions.canRevoke").value(true))
        .andReturn();
  }

  private UUID invitationId(MvcResult result) throws Exception {
    return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
  }

  private void insertParticipant(UUID dealId, PrincipalContext principal) {
    jdbcTemplate.update(
        """
                INSERT INTO deal_participant (
                    deal_id, tenant_id, legal_entity_id,
                    legal_entity_tenant_id
                )
                SELECT id, tenant_id, ?, ?
                FROM deal
                WHERE id = ?
                """,
        principal.legalEntityId(),
        principal.tenantId(),
        dealId);
  }

  private UUID insertAdditionalLegalEntity(PrincipalContext principal, String legalName) {
    UUID legalEntityId = UUID.randomUUID();
    jdbcTemplate.update(
        """
                INSERT INTO legal_entity (
                    id, tenant_id, legal_name, registration_number
                ) VALUES (?, ?, ?, ?)
                """,
        legalEntityId,
        principal.tenantId(),
        legalName,
        "REG-" + legalEntityId);
    jdbcTemplate.update(
        """
                INSERT INTO legal_entity_membership (
                    id, tenant_id, legal_entity_id, user_id, role
                ) VALUES (?, ?, ?, ?, 'MEMBER')
                """,
        UUID.randomUUID(),
        principal.tenantId(),
        legalEntityId,
        principal.userId());
    return legalEntityId;
  }

  private UUID dealId(MvcResult result) throws Exception {
    return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
  }

  private int auditCount(UUID dealId, String action, String correlationId) {
    return jdbcTemplate.queryForObject(
        """
                SELECT count(*)
                FROM audit_record
                WHERE subject_type = 'DEAL'
                  AND subject_id = ?
                  AND action = ?
                  AND correlation_id = ?
                """,
        Integer.class,
        dealId,
        action,
        UUID.fromString(correlationId));
  }

  private int invitationAuditCount(UUID invitationId, String action, UUID actorTenantId) {
    return jdbcTemplate.queryForObject(
        """
                SELECT count(*)
                FROM audit_record
                WHERE tenant_id = ?
                  AND subject_type = 'DEAL_INVITATION'
                  AND subject_id = ?
                  AND action = ?
                """,
        Integer.class,
        actorTenantId,
        invitationId,
        action);
  }

  private PrincipalContext insertPrincipal(String email, String legalName) {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    UUID legalEntityId = UUID.randomUUID();
    jdbcTemplate.update(
        """
                INSERT INTO identity_user (
                    id, email, password_hash, display_name, enabled
                )
                VALUES (?, ?, ?, ?, true)
                """,
        userId,
        email,
        "test-hash",
        legalName + " User");
    jdbcTemplate.update("INSERT INTO tenant (id) VALUES (?)", tenantId);
    jdbcTemplate.update(
        """
                INSERT INTO tenant_user (user_id, tenant_id)
                VALUES (?, ?)
                """,
        userId,
        tenantId);
    jdbcTemplate.update(
        """
                INSERT INTO legal_entity (
                    id, tenant_id, legal_name, registration_number
                )
                VALUES (?, ?, ?, ?)
                """,
        legalEntityId,
        tenantId,
        legalName,
        "REG-" + legalEntityId);
    jdbcTemplate.update(
        """
                INSERT INTO legal_entity_membership (
                    id, tenant_id, legal_entity_id, user_id, role
                )
                VALUES (?, ?, ?, ?, 'ADMIN')
                """,
        UUID.randomUUID(),
        tenantId,
        legalEntityId,
        userId);
    return new PrincipalContext(userId, tenantId, legalEntityId, email);
  }

  private record PrincipalContext(UUID userId, UUID tenantId, UUID legalEntityId, String email) {}
}
