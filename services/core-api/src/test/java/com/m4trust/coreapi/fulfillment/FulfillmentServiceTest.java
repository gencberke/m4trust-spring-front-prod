package com.m4trust.coreapi.fulfillment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.m4trust.coreapi.audit.AuditAppendPort;
import com.m4trust.coreapi.idempotency.IdempotencyClaim;
import com.m4trust.coreapi.idempotency.IdempotencyClaimStatus;
import com.m4trust.coreapi.idempotency.IdempotencyRequest;
import com.m4trust.coreapi.idempotency.IdempotencyResultReference;
import com.m4trust.coreapi.idempotency.IdempotencyService;
import com.m4trust.coreapi.organization.LegalEntityRole;
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class FulfillmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-20T12:00:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID DEAL = UUID.randomUUID();
    private static final UUID SELLER = UUID.randomUUID();
    private static final UUID BUYER = UUID.randomUUID();
    private static final UUID PACKAGE = UUID.randomUUID();

    private final FulfillmentRepository fulfillmentRepository = mock(FulfillmentRepository.class);
    private final MilestoneRepository milestoneRepository = mock(MilestoneRepository.class);
    private final EvidenceSubmissionRepository evidenceRepository = mock(EvidenceSubmissionRepository.class);
    private final FulfillmentSourcePorts.DealTarget deals = mock(FulfillmentSourcePorts.DealTarget.class);
    private final FulfillmentObjectStorage storage = mock(FulfillmentObjectStorage.class);
    private final IdempotencyService idempotency = mock(IdempotencyService.class);
    private final AuditAppendPort audit = mock(AuditAppendPort.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final FulfillmentService service = new FulfillmentService(
            fulfillmentRepository, milestoneRepository, evidenceRepository,
            deals, storage, idempotency, audit, transactions, clock, 1024 * 1024);

    {
        when(transactions.execute(any())).thenAnswer(invocation ->
                invocation.<TransactionCallback<Object>>getArgument(0)
                        .doInTransaction(mock(TransactionStatus.class)));
        when(idempotency.findCompleted(any())).thenReturn(Optional.empty());
        when(idempotency.claim(any())).thenReturn(
                new IdempotencyClaim(UUID.randomUUID(), IdempotencyClaimStatus.CLAIMED, null));
    }

    @Test
    void startFulfillmentCreatesRecordAndMilestoneForActiveFundedDeal() {
        OperationContext context = context(RequestedOperation.FULFILLMENT_START, SELLER, LegalEntityRole.ADMIN);
        stubDeal(SELLER);

        FulfillmentDetail detail = service.startFulfillment(context, DEAL,
                new StartFulfillmentRequest(5L), UUID.randomUUID(), UUID.randomUUID());

        assertEquals(DEAL, detail.dealId());
        assertEquals(FulfillmentStatus.IN_PROGRESS, detail.status());
        assertNotNull(detail.milestone());
        ArgumentCaptor<Fulfillment.FulfillmentRecord> fulfillmentCaptor =
                ArgumentCaptor.forClass(Fulfillment.FulfillmentRecord.class);
        verify(fulfillmentRepository).insert(fulfillmentCaptor.capture());
        assertEquals(PACKAGE, fulfillmentCaptor.getValue().sourcePackageId());
        verify(milestoneRepository).insert(any(Milestone.MilestoneRecord.class), any(List.class));
        verify(idempotency).recordResult(any(IdempotencyClaim.class), any(IdempotencyResultReference.class));
    }

    @Test
    void startFulfillmentRejectsNonSeller() {
        OperationContext context = context(RequestedOperation.FULFILLMENT_START, BUYER, LegalEntityRole.ADMIN);
        stubDeal(SELLER);

        assertThrows(FulfillmentExceptions.StartForbidden.class,
                () -> service.startFulfillment(context, DEAL, new StartFulfillmentRequest(5L),
                        UUID.randomUUID(), UUID.randomUUID()));
        verify(fulfillmentRepository, never()).insert(any());
    }

    @Test
    void createUploadIntentCreatesPendingSubmissionForSeller() {
        OperationContext context = context(RequestedOperation.EVIDENCE_UPLOAD_INTENT_CREATE,
                SELLER, LegalEntityRole.MEMBER);
        Fulfillment.FulfillmentRecord fulfillment = new Fulfillment.FulfillmentRecord(
                UUID.randomUUID(), DEAL, TENANT, PACKAGE, FulfillmentStatus.IN_PROGRESS,
                NOW, NOW, 0);
        Milestone.MilestoneRecord milestone = new Milestone.MilestoneRecord(
                UUID.randomUUID(), fulfillment.id(), DEAL, "Primary", null,
                FulfillmentStatus.IN_PROGRESS, NOW, NOW, 0);
        stubDeal(SELLER);
        when(fulfillmentRepository.findByDealId(DEAL)).thenReturn(Optional.of(fulfillment));
        when(fulfillmentRepository.findByDealIdForUpdate(DEAL)).thenReturn(Optional.of(fulfillment));
        when(milestoneRepository.findByFulfillmentId(fulfillment.id())).thenReturn(Optional.of(milestone));
        when(milestoneRepository.findByFulfillmentIdForUpdate(fulfillment.id())).thenReturn(Optional.of(milestone));
        when(milestoneRepository.update(any(Milestone.MilestoneRecord.class), anyLong())).thenReturn(true);
        when(fulfillmentRepository.update(any(Fulfillment.FulfillmentRecord.class), anyLong())).thenReturn(true);
        when(storage.createDirectUpload(anyString(), anyString(), anyLong())).thenReturn(
                new FulfillmentObjectStorage.DirectUpload(URI.create("https://s3/upload"),
                        Map.of("x-amz-meta-foo", "bar"), NOW.plusSeconds(60)));

        EvidenceUploadIntent intent = service.createEvidenceUploadIntent(context, DEAL,
                new CreateEvidenceUploadIntentRequest("DELIVERY_NOTE", "application/pdf",
                        "receipt.pdf", 1000L, "a".repeat(64)), UUID.randomUUID());

        assertEquals("receipt.pdf", intent.evidence().fileName());
        assertEquals(EvidenceSubmissionStatus.PENDING_UPLOAD, intent.evidence().status());
        verify(evidenceRepository).insert(any(EvidenceSubmission.EvidenceSubmissionRecord.class));
    }

    @Test
    void downloadLinkRequiresDealVisibilityBeforeEvidenceLookup() {
        OperationContext context = context(RequestedOperation.EVIDENCE_DOWNLOAD_LINK_CREATE,
                BUYER, LegalEntityRole.ADMIN);
        when(deals.findVisible(any(), any())).thenReturn(Optional.empty());

        assertThrows(FulfillmentExceptions.DealNotFound.class,
                () -> service.createDownloadLink(context, DEAL, UUID.randomUUID()));
        verify(evidenceRepository, never()).findById(any());
    }

    @Test
    void downloadLinkReturnsDealNotFoundForHiddenDealEvenWhenEvidenceExists() {
        OperationContext context = context(RequestedOperation.EVIDENCE_DOWNLOAD_LINK_CREATE,
                BUYER, LegalEntityRole.ADMIN);
        UUID evidenceId = UUID.randomUUID();
        when(deals.findVisible(any(), any())).thenReturn(Optional.empty());

        assertThrows(FulfillmentExceptions.DealNotFound.class,
                () -> service.createDownloadLink(context, DEAL, evidenceId));
        verify(evidenceRepository, never()).findById(any());
    }

    @Test
    void downloadLinkReturnsFulfillmentNotFoundWhenDealVisibleButFulfillmentMissing() {
        OperationContext context = context(RequestedOperation.EVIDENCE_DOWNLOAD_LINK_CREATE,
                BUYER, LegalEntityRole.ADMIN);
        stubDeal(BUYER);
        when(fulfillmentRepository.findByDealId(DEAL)).thenReturn(Optional.empty());

        assertThrows(FulfillmentExceptions.FulfillmentNotFound.class,
                () -> service.createDownloadLink(context, DEAL, UUID.randomUUID()));
        verify(evidenceRepository, never()).findById(any());
    }

    @Test
    void downloadLinkReturnsEvidenceNotFoundWhenFulfillmentExistsButEvidenceMissing() {
        OperationContext context = context(RequestedOperation.EVIDENCE_DOWNLOAD_LINK_CREATE,
                BUYER, LegalEntityRole.ADMIN);
        stubDeal(BUYER);
        when(fulfillmentRepository.findByDealId(DEAL)).thenReturn(Optional.of(
                new Fulfillment.FulfillmentRecord(UUID.randomUUID(), DEAL, TENANT, PACKAGE,
                        FulfillmentStatus.REVIEW_REQUIRED, NOW, NOW, 0)));
        when(evidenceRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(FulfillmentExceptions.EvidenceNotFound.class,
                () -> service.createDownloadLink(context, DEAL, UUID.randomUUID()));
    }

    @Test
    void getFulfillmentReturnsFulfillmentNotFoundWhenDealVisibleButRecordMissing() {
        OperationContext context = context(RequestedOperation.FULFILLMENT_READ, BUYER, LegalEntityRole.ADMIN);
        stubDeal(BUYER);
        when(fulfillmentRepository.findByDealId(DEAL)).thenReturn(Optional.empty());

        assertThrows(FulfillmentExceptions.FulfillmentNotFound.class,
                () -> service.getFulfillment(context, DEAL));
    }

    @Test
    void getFulfillmentReturnsDealNotFoundWhenDealHidden() {
        OperationContext context = context(RequestedOperation.FULFILLMENT_READ, BUYER, LegalEntityRole.ADMIN);
        when(deals.findVisible(any(), any())).thenReturn(Optional.empty());

        assertThrows(FulfillmentExceptions.DealNotFound.class,
                () -> service.getFulfillment(context, DEAL));
    }


    private void stubDeal(UUID activeEntity) {
        FulfillmentSourcePorts.Target target = new FulfillmentSourcePorts.Target(
                DEAL, TENANT, "ACTIVE", 5L, BUYER, SELLER, "FUNDED", PACKAGE, List.of());
        when(deals.findVisible(any(), any())).thenReturn(Optional.of(target));
        when(deals.lockVisibleForStart(any(), any())).thenReturn(Optional.of(target));
    }

    private static OperationContext context(RequestedOperation operation, UUID activeEntity,
            LegalEntityRole role) {
        return new OperationContext(USER, TENANT, activeEntity, role, operation);
    }
}
