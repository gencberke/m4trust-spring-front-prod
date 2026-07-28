package com.m4trust.coreapi.document.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.m4trust.coreapi.audit.domain.port.AuditAppendPort;
import com.m4trust.coreapi.document.domain.*;
import com.m4trust.coreapi.document.infra.*;
import com.m4trust.coreapi.document.infra.adapter.*;
import com.m4trust.coreapi.organization.domain.OperationContext;
import com.m4trust.coreapi.organization.domain.RequestedOperation;
import com.m4trust.coreapi.support.PostgresIntegrationTestSupport;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest
@ActiveProfiles({"local", "test"})
@AutoConfigureMockMvc
@Import({
  DocumentUploadFinalizeIntegrationTest.FakeStorageConfiguration.class,
  DocumentUploadFinalizeIntegrationTest.FailingAuditConfiguration.class
})
class DocumentUploadFinalizeIntegrationTest extends PostgresIntegrationTestSupport {

  private static final String SHA = "a".repeat(64);

  @Autowired private DocumentService service;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private FakeStorage storage;

  @Autowired private AtomicBoolean failAudit;

  @Autowired private MockMvc mockMvc;

  private UUID userId;
  private UUID tenantId;
  private UUID legalEntityId;
  private UUID dealId;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute(
        """
                TRUNCATE TABLE dispute_comment, dispute_evidence_snapshot, dispute_case, fulfillment_video_analysis_result, fulfillment_video_analysis_job, fulfillment_evidence_submission, fulfillment_milestone_rule_reference, fulfillment_milestone, fulfillment, release_dispatch, release_operation, settlement, payment_dispatch, payment_operation, funding_unit, funding_plan,
                    contract_intelligence_rule_set_version,
                    contract_intelligence_extraction_result_version,
                    contract_intelligence_analysis_job, http_idempotency_record, deal_invitation,
                    deal_participant, document, ratification_package_approval,
                    ratification_package, ratification_package_snapshot, deal, audit_record,
                    legal_entity_membership, legal_entity, tenant_user, tenant,
                    identity_user
                """);
    userId = UUID.randomUUID();
    tenantId = UUID.randomUUID();
    legalEntityId = UUID.randomUUID();
    dealId = UUID.randomUUID();
    jdbcTemplate.update(
        """
                INSERT INTO identity_user (id, email, password_hash, display_name, enabled)
                VALUES (?, ?, 'test-hash', 'Document User', true)
                """,
        userId,
        "document-upload@example.com");
    jdbcTemplate.update("INSERT INTO tenant (id) VALUES (?)", tenantId);
    jdbcTemplate.update(
        "INSERT INTO tenant_user (user_id, tenant_id) VALUES (?, ?)", userId, tenantId);
    jdbcTemplate.update(
        """
                INSERT INTO legal_entity (id, tenant_id, legal_name, registration_number)
                VALUES (?, ?, 'Document Entity', 'DOCUMENT-ENTITY-1')
                """,
        legalEntityId,
        tenantId);
    jdbcTemplate.update(
        """
                INSERT INTO legal_entity_membership (id, tenant_id, legal_entity_id, user_id, role)
                VALUES (?, ?, ?, ?, 'ADMIN')
                """,
        UUID.randomUUID(),
        tenantId,
        legalEntityId,
        userId);
    jdbcTemplate.update(
        """
                INSERT INTO deal (id, tenant_id, reference, title, deal_status,
                    initiator_legal_entity_id, created_by)
                VALUES (?, ?, 'DL-0000000001', 'Document Deal', 'DRAFT', ?, ?)
                """,
        dealId,
        tenantId,
        legalEntityId,
        userId);
    jdbcTemplate.update(
        """
                INSERT INTO deal_participant (deal_id, tenant_id, legal_entity_id,
                    legal_entity_tenant_id)
                VALUES (?, ?, ?, ?)
                """,
        dealId,
        tenantId,
        legalEntityId,
        tenantId);
    storage.reset();
    failAudit.set(false);
  }

  @Test
  void finalizationIsIdempotentAndStorageCallsStayOutsideTransactions() {
    UUID documentId = createIntent();
    UUID key = UUID.randomUUID();
    AvailableDealDocument first = finalizeDocument(documentId, key);
    int verifiedBeforeReplay = storage.verifyCalls.get();
    AvailableDealDocument replay = finalizeDocument(documentId, key);

    assertEquals(DocumentStatus.AVAILABLE, first.status());
    assertEquals(first, replay);
    assertEquals(
        documentId,
        jdbcTemplate.queryForObject(
            "SELECT current_document_id FROM deal WHERE id = ?", UUID.class, dealId));
    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM audit_record WHERE subject_id = ?", Integer.class, documentId));
    assertEquals(
        1,
        jdbcTemplate.queryForObject("SELECT count(*) FROM http_idempotency_record", Integer.class));
    assertEquals(verifiedBeforeReplay, storage.verifyCalls.get());
    assertFalse(storage.calledInsideTransaction.get());
  }

  @Test
  void nonInitiatorCannotCreateOrFinalizeDocumentsAndTerminalIntentConflicts() {
    OperationContext participant =
        participantContext(RequestedOperation.DEAL_DOCUMENT_UPLOAD_INTENT_CREATE);
    assertThrows(
        DocumentExceptions.MutationForbidden.class,
        () -> service.createIntent(participant, dealId, intentRequest(), UUID.randomUUID()));
    UUID pending = createIntent();
    assertThrows(
        DocumentExceptions.MutationForbidden.class,
        () ->
            service.finalizeUpload(
                participantContext(RequestedOperation.DOCUMENT_UPLOAD_FINALIZE),
                pending,
                new FinalizeDocumentUploadRequest(12, SHA),
                UUID.randomUUID(),
                UUID.randomUUID()));
    jdbcTemplate.update("UPDATE deal SET deal_status = 'CANCELLED' WHERE id = ?", dealId);
    assertThrows(
        DocumentExceptions.UploadNotAllowed.class,
        () -> service.createIntent(intentContext(), dealId, intentRequest(), UUID.randomUUID()));
    assertThrows(
        DocumentExceptions.UploadStateConflict.class,
        () -> finalizeDocument(pending, UUID.randomUUID()));
  }

  @Test
  void nonParticipantListAndDownloadAreRejectedAsNotFound() {
    UUID documentId = createIntent();
    AvailableDealDocument available = finalizeDocument(documentId, UUID.randomUUID());
    OperationContext outsider = outsiderContext();

    assertThrows(
        DocumentExceptions.DealNotFound.class,
        () ->
            service.listHistory(
                withOperation(outsider, RequestedOperation.DEAL_DOCUMENT_LIST_READ), dealId));
    assertThrows(
        DocumentExceptions.NotFound.class,
        () ->
            service.createDownloadLink(
                withOperation(outsider, RequestedOperation.DOCUMENT_DOWNLOAD_LINK_CREATE),
                available.id()));
  }

  private UUID createIntent() {
    DocumentUploadIntent result =
        service.createIntent(intentContext(), dealId, intentRequest(), UUID.randomUUID());
    return result.document().id();
  }

  private AvailableDealDocument finalizeDocument(UUID documentId, UUID key) {
    return service.finalizeUpload(
        finalizeContext(),
        documentId,
        new FinalizeDocumentUploadRequest(12, SHA),
        key,
        UUID.randomUUID());
  }

  private UUID seedReviewRequiredAnalysis(UUID documentId) {
    UUID analysisId = UUID.randomUUID();
    UUID extractionId = UUID.randomUUID();
    jdbcTemplate.update(
        """
                INSERT INTO contract_intelligence_analysis_job (id, tenant_id, deal_id, document_id,
                    object_version, input_sha256, status, requested_at, processing_started_at,
                    completed_at, version)
                VALUES (?, ?, ?, ?, 'immutable-version', ?, 'REVIEW_REQUIRED',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
        analysisId,
        tenantId,
        dealId,
        documentId,
        SHA);
    jdbcTemplate.update(
        """
                INSERT INTO contract_intelligence_extraction_result_version
                    (id, analysis_job_id, schema_version, canonical_result, created_at)
                VALUES (?, ?, '1.0.0', CAST(? AS jsonb), CURRENT_TIMESTAMP)
                """,
        extractionId,
        analysisId,
        extractionResult());
    return analysisId;
  }

  private UUID acceptReview(UUID analysisId, long expectedVersion) throws Exception {
    assertEquals(201, acceptReviewStatus(analysisId, expectedVersion));
    return jdbcTemplate.queryForObject(
        """
                SELECT id FROM contract_intelligence_rule_set_version WHERE source_analysis_id = ?
                """,
        UUID.class,
        analysisId);
  }

  private int acceptReviewStatus(UUID analysisId, long expectedVersion) throws Exception {
    String body =
        """
                {"analysisId":"%s","expectedVersion":%d,"decisions":[
                  {"decision":"KEPT","ruleReference":"payment"}
                ]}
                """
            .formatted(analysisId, expectedVersion);
    return mockMvc
        .perform(
            post("/api/v1/deals/" + dealId + "/extraction-review/accept")
                .with(user(userId.toString()))
                .with(csrf())
                .header("X-M4Trust-Legal-Entity-Id", legalEntityId)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType("application/json")
                .content(body))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  private String extractionResult() {
    return """
                {"parties":[],"rules":[{"ruleReference":"payment","category":"PAYMENT",
                "title":"Payment","description":"Payment term",
                "structuredValue":{"type":"MONEY","amountMinor":100,"currency":"TRY"},
                "confidence":0.9,"sourceReferences":[],"legalBasis":null}],
                "deliveryRequirements":[],"summary":{"requiresManualReview":false,"reviewReasons":[]}}
                """;
  }

  private long dealVersion() {
    return jdbcTemplate.queryForObject("SELECT version FROM deal WHERE id = ?", Long.class, dealId);
  }

  private UUID currentDocument() {
    return jdbcTemplate.queryForObject(
        "SELECT current_document_id FROM deal WHERE id = ?", UUID.class, dealId);
  }

  private String documentStatus(UUID documentId) {
    return jdbcTemplate.queryForObject(
        "SELECT document_status FROM document WHERE id = ?", String.class, documentId);
  }

  private String analysisStatus(UUID analysisId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM contract_intelligence_analysis_job WHERE id = ?",
        String.class,
        analysisId);
  }

  private int count(String table) {
    return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
  }

  private OperationContext intentContext() {
    return new OperationContext(
        userId,
        tenantId,
        legalEntityId,
        com.m4trust.coreapi.organization.domain.LegalEntityRole.ADMIN,
        RequestedOperation.DEAL_DOCUMENT_UPLOAD_INTENT_CREATE);
  }

  private OperationContext finalizeContext() {
    return new OperationContext(
        userId,
        tenantId,
        legalEntityId,
        com.m4trust.coreapi.organization.domain.LegalEntityRole.ADMIN,
        RequestedOperation.DOCUMENT_UPLOAD_FINALIZE);
  }

  private OperationContext downloadContext() {
    return new OperationContext(
        userId,
        tenantId,
        legalEntityId,
        com.m4trust.coreapi.organization.domain.LegalEntityRole.ADMIN,
        RequestedOperation.DOCUMENT_DOWNLOAD_LINK_CREATE);
  }

  private OperationContext withOperation(OperationContext context, RequestedOperation operation) {
    return new OperationContext(
        context.authenticatedUserId(),
        context.tenantId(),
        context.activeLegalEntityId(),
        context.activeLegalEntityRole(),
        operation);
  }

  private OperationContext outsiderContext() {
    UUID outsiderUserId = UUID.randomUUID();
    UUID outsiderEntityId = UUID.randomUUID();
    jdbcTemplate.update(
        """
                INSERT INTO identity_user (id, email, password_hash, display_name, enabled)
                VALUES (?, ?, 'test-hash', 'Outsider User', true)
                """,
        outsiderUserId,
        outsiderUserId + "@example.com");
    jdbcTemplate.update(
        "INSERT INTO tenant_user (user_id, tenant_id) VALUES (?, ?)", outsiderUserId, tenantId);
    jdbcTemplate.update(
        """
                INSERT INTO legal_entity (id, tenant_id, legal_name, registration_number)
                VALUES (?, ?, 'Outsider Entity', ?)
                """,
        outsiderEntityId,
        tenantId,
        "OUTSIDER-" + outsiderEntityId);
    jdbcTemplate.update(
        """
                INSERT INTO legal_entity_membership (id, tenant_id, legal_entity_id, user_id, role)
                VALUES (?, ?, ?, ?, 'ADMIN')
                """,
        UUID.randomUUID(),
        tenantId,
        outsiderEntityId,
        outsiderUserId);
    // Deliberately no deal_participant row: this legal entity is a non-participant.
    return new OperationContext(
        outsiderUserId,
        tenantId,
        outsiderEntityId,
        com.m4trust.coreapi.organization.domain.LegalEntityRole.ADMIN,
        RequestedOperation.DEAL_DOCUMENT_LIST_READ);
  }

  private CreateDocumentUploadIntentRequest intentRequest() {
    return new CreateDocumentUploadIntentRequest("contract.pdf", "application/pdf", 12, SHA);
  }

  private OperationContext participantContext(RequestedOperation operation) {
    UUID participantUserId = UUID.randomUUID();
    UUID participantEntityId = UUID.randomUUID();
    jdbcTemplate.update(
        """
                INSERT INTO identity_user (id, email, password_hash, display_name, enabled)
                VALUES (?, ?, 'test-hash', 'Participant User', true)
                """,
        participantUserId,
        participantUserId + "@example.com");
    jdbcTemplate.update(
        "INSERT INTO tenant_user (user_id, tenant_id) VALUES (?, ?)", participantUserId, tenantId);
    jdbcTemplate.update(
        """
                INSERT INTO legal_entity (id, tenant_id, legal_name, registration_number)
                VALUES (?, ?, 'Participant Entity', ?)
                """,
        participantEntityId,
        tenantId,
        "PARTICIPANT-" + participantEntityId);
    jdbcTemplate.update(
        """
                INSERT INTO legal_entity_membership (id, tenant_id, legal_entity_id, user_id, role)
                VALUES (?, ?, ?, ?, 'ADMIN')
                """,
        UUID.randomUUID(),
        tenantId,
        participantEntityId,
        participantUserId);
    jdbcTemplate.update(
        """
                INSERT INTO deal_participant (deal_id, tenant_id, legal_entity_id,
                    legal_entity_tenant_id)
                VALUES (?, ?, ?, ?)
                """,
        dealId,
        tenantId,
        participantEntityId,
        tenantId);
    return new OperationContext(
        participantUserId,
        tenantId,
        participantEntityId,
        com.m4trust.coreapi.organization.domain.LegalEntityRole.ADMIN,
        operation);
  }

  @TestConfiguration
  static class FakeStorageConfiguration {
    @Bean
    @Primary
    FakeStorage fakeDocumentObjectStorage() {
      return new FakeStorage();
    }
  }

  static class FakeStorage implements DocumentObjectStorage {
    private final AtomicBoolean calledInsideTransaction = new AtomicBoolean();
    private final AtomicBoolean downloadCalledInsideTransaction = new AtomicBoolean();
    private final AtomicInteger verifyCalls = new AtomicInteger();
    private final java.util.concurrent.atomic.AtomicReference<String> lastDownloadObjectVersion =
        new java.util.concurrent.atomic.AtomicReference<>();
    private volatile VerifiedObject verified = new VerifiedObject(12, SHA, "immutable-version");

    @Override
    public DirectUpload createDirectUpload(String objectKey, String mediaType, long contentLength) {
      recordTransactionState();
      return new DirectUpload(
          URI.create("https://storage.example/" + objectKey),
          Map.of("Content-Type", mediaType),
          Instant.now().plusSeconds(600));
    }

    @Override
    public DirectDownload createDirectDownload(String objectKey, String objectVersion) {
      downloadCalledInsideTransaction.compareAndSet(
          false, TransactionSynchronizationManager.isActualTransactionActive());
      lastDownloadObjectVersion.set(objectVersion);
      return new DirectDownload(
          URI.create("https://storage.example/" + objectKey + "?version=" + objectVersion),
          Instant.now().plusSeconds(300));
    }

    @Override
    public VerifiedObject verify(String objectKey) {
      recordTransactionState();
      verifyCalls.incrementAndGet();
      return verified;
    }

    void reset() {
      calledInsideTransaction.set(false);
      downloadCalledInsideTransaction.set(false);
      verifyCalls.set(0);
      lastDownloadObjectVersion.set(null);
      verified = new VerifiedObject(12, SHA, "immutable-version");
    }

    private void recordTransactionState() {
      calledInsideTransaction.compareAndSet(
          false, TransactionSynchronizationManager.isActualTransactionActive());
    }
  }

  @TestConfiguration
  static class FailingAuditConfiguration {
    @Bean
    AtomicBoolean failAudit() {
      return new AtomicBoolean();
    }

    @Bean
    @Primary
    AuditAppendPort testAuditAppender(JdbcTemplate jdbcTemplate, AtomicBoolean failAudit) {
      return record -> {
        jdbcTemplate.update(
            """
                        INSERT INTO audit_record (id, tenant_id, actor_user_id,
                            legal_entity_id, subject_type, subject_id, action,
                            correlation_id, causation_id, occurred_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
            record.id(),
            record.tenantId(),
            record.actorUserId(),
            record.legalEntityId(),
            record.subjectType(),
            record.subjectId(),
            record.action(),
            record.correlationId(),
            record.causationId(),
            java.sql.Timestamp.from(record.occurredAt()));
        if (failAudit.get()) {
          throw new IllegalStateException("forced audit failure");
        }
      };
    }
  }
}
