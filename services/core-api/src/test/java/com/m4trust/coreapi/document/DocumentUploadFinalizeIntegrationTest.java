package com.m4trust.coreapi.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.m4trust.coreapi.audit.AuditAppendPort;
import com.m4trust.coreapi.audit.AuditRecord;
import com.m4trust.coreapi.idempotency.IdempotencyKeyReusedException;
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("local")
@Testcontainers
@Import({DocumentUploadFinalizeIntegrationTest.FakeStorageConfiguration.class,
        DocumentUploadFinalizeIntegrationTest.FailingAuditConfiguration.class})
class DocumentUploadFinalizeIntegrationTest {

    private static final String SHA = "a".repeat(64);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.5-alpine");

    @Autowired
    private DocumentService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FakeStorage storage;

    @Autowired
    private AtomicBoolean failAudit;

    private UUID userId;
    private UUID tenantId;
    private UUID legalEntityId;
    private UUID dealId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE contract_intelligence_analysis_job, http_idempotency_record, deal_invitation,
                    deal_participant, document, deal, audit_record,
                    legal_entity_membership, legal_entity, tenant_user, tenant,
                    identity_user
                """);
        userId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        legalEntityId = UUID.randomUUID();
        dealId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO identity_user (id, email, password_hash, display_name, enabled)
                VALUES (?, ?, 'test-hash', 'Document User', true)
                """, userId, "document-upload@example.com");
        jdbcTemplate.update("INSERT INTO tenant (id) VALUES (?)", tenantId);
        jdbcTemplate.update("INSERT INTO tenant_user (user_id, tenant_id) VALUES (?, ?)",
                userId, tenantId);
        jdbcTemplate.update("""
                INSERT INTO legal_entity (id, tenant_id, legal_name, registration_number)
                VALUES (?, ?, 'Document Entity', 'DOCUMENT-ENTITY-1')
                """, legalEntityId, tenantId);
        jdbcTemplate.update("""
                INSERT INTO legal_entity_membership (id, tenant_id, legal_entity_id, user_id, role)
                VALUES (?, ?, ?, ?, 'ADMIN')
                """, UUID.randomUUID(), tenantId, legalEntityId, userId);
        jdbcTemplate.update("""
                INSERT INTO deal (id, tenant_id, reference, title, deal_status,
                    initiator_legal_entity_id, created_by)
                VALUES (?, ?, 'DL-0000000001', 'Document Deal', 'DRAFT', ?, ?)
                """, dealId, tenantId, legalEntityId, userId);
        jdbcTemplate.update("""
                INSERT INTO deal_participant (deal_id, tenant_id, legal_entity_id,
                    legal_entity_tenant_id)
                VALUES (?, ?, ?, ?)
                """, dealId, tenantId, legalEntityId, tenantId);
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
        assertEquals(documentId, jdbcTemplate.queryForObject(
                "SELECT current_document_id FROM deal WHERE id = ?", UUID.class, dealId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_record WHERE subject_id = ?", Integer.class,
                documentId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM http_idempotency_record", Integer.class));
        assertEquals(verifiedBeforeReplay, storage.verifyCalls.get());
        assertFalse(storage.calledInsideTransaction.get());
    }

    @Test
    void reusedKeyWithDifferentRequestFailsBeforeStorageOrAdditionalSideEffects() {
        UUID documentId = createIntent();
        UUID key = UUID.randomUUID();
        finalizeDocument(documentId, key);
        int verifiedBeforeConflict = storage.verifyCalls.get();

        assertThrows(IdempotencyKeyReusedException.class,
                () -> service.finalizeUpload(finalizeContext(), documentId,
                        new FinalizeDocumentUploadRequest(11, SHA), key,
                        UUID.randomUUID()));

        assertEquals(verifiedBeforeConflict, storage.verifyCalls.get());
        assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM audit_record",
                Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM http_idempotency_record", Integer.class));
    }

    @Test
    void replaySurvivesSupersessionAndTerminalDealWithoutStorageOrSecondEffect() {
        UUID first = createIntent();
        UUID firstKey = UUID.randomUUID();
        AvailableDealDocument original = finalizeDocument(first, firstKey);
        UUID second = createIntent();
        finalizeDocument(second, UUID.randomUUID());
        jdbcTemplate.update("UPDATE deal SET deal_status = 'CANCELLED' WHERE id = ?", dealId);
        int verifiedBeforeReplay = storage.verifyCalls.get();

        assertEquals(original, finalizeDocument(first, firstKey));

        assertEquals(verifiedBeforeReplay, storage.verifyCalls.get());
        assertEquals("SUPERSEDED", jdbcTemplate.queryForObject(
                "SELECT document_status FROM document WHERE id = ?", String.class, first));
        assertEquals(2, jdbcTemplate.queryForObject("SELECT count(*) FROM audit_record",
                Integer.class));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM http_idempotency_record", Integer.class));
    }

    @Test
    void verificationMismatchLeavesDocumentPointerAuditAndIdempotencyUntouched() {
        UUID documentId = createIntent();
        storage.verified = new DocumentObjectStorage.VerifiedObject(12,
                "b".repeat(64), "immutable-version");

        assertThrows(DocumentExceptions.VerificationFailed.class,
                () -> finalizeDocument(documentId, UUID.randomUUID()));

        assertEquals("PENDING_UPLOAD", jdbcTemplate.queryForObject(
                "SELECT document_status FROM document WHERE id = ?", String.class,
                documentId));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM audit_record",
                Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM http_idempotency_record", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM deal WHERE id = ? AND current_document_id IS NOT NULL",
                Integer.class, dealId));
        assertFalse(storage.calledInsideTransaction.get());
    }

    @Test
    void expiredPendingDocumentCannotBecomeCurrent() {
        UUID documentId = createIntent();
        jdbcTemplate.update("""
                UPDATE document
                SET created_at = CURRENT_TIMESTAMP - INTERVAL '2 hours',
                    updated_at = CURRENT_TIMESTAMP - INTERVAL '2 hours',
                    upload_expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
                WHERE id = ?
                """, documentId);

        assertThrows(DocumentExceptions.UploadExpired.class,
                () -> finalizeDocument(documentId, UUID.randomUUID()));

        assertEquals("PENDING_UPLOAD", jdbcTemplate.queryForObject(
                "SELECT document_status FROM document WHERE id = ?", String.class,
                documentId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM deal WHERE id = ? AND current_document_id IS NOT NULL",
                Integer.class, dealId));
    }

    @Test
    void nonInitiatorCannotCreateOrFinalizeDocumentsAndTerminalIntentConflicts() {
        OperationContext participant = participantContext(
                RequestedOperation.DEAL_DOCUMENT_UPLOAD_INTENT_CREATE);
        assertThrows(DocumentExceptions.MutationForbidden.class,
                () -> service.createIntent(participant, dealId, intentRequest(), UUID.randomUUID()));
        UUID pending = createIntent();
        assertThrows(DocumentExceptions.MutationForbidden.class,
                () -> service.finalizeUpload(participantContext(
                        RequestedOperation.DOCUMENT_UPLOAD_FINALIZE), pending,
                        new FinalizeDocumentUploadRequest(12, SHA), UUID.randomUUID(),
                        UUID.randomUUID()));
        jdbcTemplate.update("UPDATE deal SET deal_status = 'CANCELLED' WHERE id = ?", dealId);
        assertThrows(DocumentExceptions.UploadNotAllowed.class,
                () -> service.createIntent(intentContext(), dealId, intentRequest(),
                        UUID.randomUUID()));
        assertThrows(DocumentExceptions.UploadStateConflict.class,
                () -> finalizeDocument(pending, UUID.randomUUID()));
    }

    @Test
    void auditFailureRollsBackDocumentPointerSupersedeAndIdempotencyResult() {
        UUID first = createIntent();
        finalizeDocument(first, UUID.randomUUID());
        UUID pending = createIntent();
        failAudit.set(true);

        assertThrows(IllegalStateException.class,
                () -> finalizeDocument(pending, UUID.randomUUID()));

        assertEquals("PENDING_UPLOAD", jdbcTemplate.queryForObject(
                "SELECT document_status FROM document WHERE id = ?", String.class, pending));
        assertEquals("AVAILABLE", jdbcTemplate.queryForObject(
                "SELECT document_status FROM document WHERE id = ?", String.class, first));
        assertEquals(first, jdbcTemplate.queryForObject(
                "SELECT current_document_id FROM deal WHERE id = ?", UUID.class, dealId));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM audit_record",
                Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM http_idempotency_record", Integer.class));
    }

    @Test
    void nonParticipantListAndDownloadAreRejectedAsNotFound() {
        UUID documentId = createIntent();
        AvailableDealDocument available = finalizeDocument(documentId, UUID.randomUUID());
        OperationContext outsider = outsiderContext();

        assertThrows(DocumentExceptions.DealNotFound.class,
                () -> service.listHistory(withOperation(outsider,
                        RequestedOperation.DEAL_DOCUMENT_LIST_READ), dealId));
        assertThrows(DocumentExceptions.NotFound.class,
                () -> service.createDownloadLink(withOperation(outsider,
                        RequestedOperation.DOCUMENT_DOWNLOAD_LINK_CREATE), available.id()));
    }

    @Test
    void participantCanDownloadAvailableDocumentAndSeeItInHistory() {
        UUID documentId = createIntent();
        AvailableDealDocument available = finalizeDocument(documentId, UUID.randomUUID());
        OperationContext participant = participantContext(
                RequestedOperation.DOCUMENT_DOWNLOAD_LINK_CREATE);

        DocumentDownloadLink link = service.createDownloadLink(participant, documentId);

        assertEquals(documentId, link.documentId());
        assertEquals(available.objectVersion(), link.objectVersion());
        assertFalse(storage.downloadCalledInsideTransaction.get());

        DealDocumentHistory history = service.listHistory(withOperation(participant,
                RequestedOperation.DEAL_DOCUMENT_LIST_READ), dealId);
        assertEquals(1, history.items().size());
        HistoricalDealDocument item = (HistoricalDealDocument) history.items().get(0);
        assertEquals(DocumentStatus.AVAILABLE, item.status());
        assertTrue(item.availableActions().canDownload());
        assertFalse(item.availableActions().canFinalize());
    }

    @Test
    void downloadLinkPinsRecordedObjectVersionAcrossSupersession() {
        UUID first = createIntent();
        finalizeDocument(first, UUID.randomUUID());
        storage.verified = new DocumentObjectStorage.VerifiedObject(12, SHA, "version-two");
        UUID second = createIntent();
        finalizeDocument(second, UUID.randomUUID());

        DocumentDownloadLink supersededLink = service.createDownloadLink(downloadContext(),
                first);

        assertEquals("immutable-version", supersededLink.objectVersion());
        assertEquals("immutable-version", storage.lastDownloadObjectVersion.get());
        assertEquals("SUPERSEDED", jdbcTemplate.queryForObject(
                "SELECT document_status FROM document WHERE id = ?", String.class, first));
    }

    @Test
    void pendingUploadDownloadLinkIsRejectedWithConflict() {
        UUID pending = createIntent();

        assertThrows(DocumentExceptions.DownloadNotAvailable.class,
                () -> service.createDownloadLink(downloadContext(), pending));
    }

    @Test
    void concurrentFinalizesLeaveOneCurrentAvailableDocumentAndRetainSupersededHistory()
            throws Exception {
        UUID first = createIntent();
        UUID second = createIntent();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<AvailableDealDocument> one = executor.submit(
                    () -> finalizeDocument(first, UUID.randomUUID()));
            Future<AvailableDealDocument> two = executor.submit(
                    () -> finalizeDocument(second, UUID.randomUUID()));
            one.get(20, TimeUnit.SECONDS);
            two.get(20, TimeUnit.SECONDS);
        }

        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT count(*) FROM document
                WHERE deal_id = ? AND document_status = 'AVAILABLE'
                """, Integer.class, dealId));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT count(*) FROM document
                WHERE deal_id = ? AND document_status = 'SUPERSEDED'
                """, Integer.class, dealId));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT count(*) FROM deal d JOIN document current_document
                  ON current_document.id = d.current_document_id
                WHERE d.id = ? AND current_document.document_status = 'AVAILABLE'
                """, Integer.class, dealId));
    }

    private UUID createIntent() {
        DocumentUploadIntent result = service.createIntent(intentContext(), dealId,
                intentRequest(), UUID.randomUUID());
        return result.document().id();
    }

    private AvailableDealDocument finalizeDocument(UUID documentId, UUID key) {
        return service.finalizeUpload(finalizeContext(), documentId,
                new FinalizeDocumentUploadRequest(12, SHA), key, UUID.randomUUID());
    }

    private OperationContext intentContext() {
        return new OperationContext(userId, tenantId, legalEntityId,
                RequestedOperation.DEAL_DOCUMENT_UPLOAD_INTENT_CREATE);
    }

    private OperationContext finalizeContext() {
        return new OperationContext(userId, tenantId, legalEntityId,
                RequestedOperation.DOCUMENT_UPLOAD_FINALIZE);
    }

    private OperationContext downloadContext() {
        return new OperationContext(userId, tenantId, legalEntityId,
                RequestedOperation.DOCUMENT_DOWNLOAD_LINK_CREATE);
    }

    private OperationContext withOperation(OperationContext context,
            RequestedOperation operation) {
        return new OperationContext(context.authenticatedUserId(), context.tenantId(),
                context.activeLegalEntityId(), operation);
    }

    private OperationContext outsiderContext() {
        UUID outsiderUserId = UUID.randomUUID();
        UUID outsiderEntityId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO identity_user (id, email, password_hash, display_name, enabled)
                VALUES (?, ?, 'test-hash', 'Outsider User', true)
                """, outsiderUserId, outsiderUserId + "@example.com");
        jdbcTemplate.update("INSERT INTO tenant_user (user_id, tenant_id) VALUES (?, ?)",
                outsiderUserId, tenantId);
        jdbcTemplate.update("""
                INSERT INTO legal_entity (id, tenant_id, legal_name, registration_number)
                VALUES (?, ?, 'Outsider Entity', ?)
                """, outsiderEntityId, tenantId, "OUTSIDER-" + outsiderEntityId);
        jdbcTemplate.update("""
                INSERT INTO legal_entity_membership (id, tenant_id, legal_entity_id, user_id, role)
                VALUES (?, ?, ?, ?, 'ADMIN')
                """, UUID.randomUUID(), tenantId, outsiderEntityId, outsiderUserId);
        // Deliberately no deal_participant row: this legal entity is a non-participant.
        return new OperationContext(outsiderUserId, tenantId, outsiderEntityId,
                RequestedOperation.DEAL_DOCUMENT_LIST_READ);
    }

    private CreateDocumentUploadIntentRequest intentRequest() {
        return new CreateDocumentUploadIntentRequest("contract.pdf", "application/pdf", 12, SHA);
    }

    private OperationContext participantContext(RequestedOperation operation) {
        UUID participantUserId = UUID.randomUUID();
        UUID participantEntityId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO identity_user (id, email, password_hash, display_name, enabled)
                VALUES (?, ?, 'test-hash', 'Participant User', true)
                """, participantUserId, participantUserId + "@example.com");
        jdbcTemplate.update("INSERT INTO tenant_user (user_id, tenant_id) VALUES (?, ?)",
                participantUserId, tenantId);
        jdbcTemplate.update("""
                INSERT INTO legal_entity (id, tenant_id, legal_name, registration_number)
                VALUES (?, ?, 'Participant Entity', ?)
                """, participantEntityId, tenantId, "PARTICIPANT-" + participantEntityId);
        jdbcTemplate.update("""
                INSERT INTO legal_entity_membership (id, tenant_id, legal_entity_id, user_id, role)
                VALUES (?, ?, ?, ?, 'ADMIN')
                """, UUID.randomUUID(), tenantId, participantEntityId, participantUserId);
        jdbcTemplate.update("""
                INSERT INTO deal_participant (deal_id, tenant_id, legal_entity_id,
                    legal_entity_tenant_id)
                VALUES (?, ?, ?, ?)
                """, dealId, tenantId, participantEntityId, tenantId);
        return new OperationContext(participantUserId, tenantId, participantEntityId, operation);
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
        private final java.util.concurrent.atomic.AtomicReference<String>
                lastDownloadObjectVersion = new java.util.concurrent.atomic.AtomicReference<>();
        private volatile VerifiedObject verified = new VerifiedObject(12, SHA,
                "immutable-version");

        @Override
        public DirectUpload createDirectUpload(String objectKey, String mediaType,
                long contentLength) {
            recordTransactionState();
            return new DirectUpload(URI.create("https://storage.example/" + objectKey),
                    Map.of("Content-Type", mediaType), Instant.now().plusSeconds(600));
        }

        @Override
        public DirectDownload createDirectDownload(String objectKey,
                String objectVersion) {
            downloadCalledInsideTransaction.compareAndSet(false,
                    TransactionSynchronizationManager.isActualTransactionActive());
            lastDownloadObjectVersion.set(objectVersion);
            return new DirectDownload(URI.create("https://storage.example/" + objectKey
                    + "?version=" + objectVersion), Instant.now().plusSeconds(300));
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
            calledInsideTransaction.compareAndSet(false,
                    TransactionSynchronizationManager.isActualTransactionActive());
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
        AuditAppendPort testAuditAppender(JdbcTemplate jdbcTemplate,
                AtomicBoolean failAudit) {
            return record -> {
                jdbcTemplate.update("""
                        INSERT INTO audit_record (id, tenant_id, actor_user_id,
                            legal_entity_id, subject_type, subject_id, action,
                            correlation_id, causation_id, occurred_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, record.id(), record.tenantId(), record.actorUserId(),
                        record.legalEntityId(), record.subjectType(), record.subjectId(),
                        record.action(), record.correlationId(), record.causationId(),
                        java.sql.Timestamp.from(record.occurredAt()));
                if (failAudit.get()) {
                    throw new IllegalStateException("forced audit failure");
                }
            };
        }
    }
}
