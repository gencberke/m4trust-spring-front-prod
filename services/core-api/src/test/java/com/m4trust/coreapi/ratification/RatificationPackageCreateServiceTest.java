package com.m4trust.coreapi.ratification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.m4trust.coreapi.audit.AuditAppendPort;
import com.m4trust.coreapi.audit.AuditRecord;
import com.m4trust.coreapi.idempotency.IdempotencyClaim;
import com.m4trust.coreapi.idempotency.IdempotencyClaimStatus;
import com.m4trust.coreapi.idempotency.IdempotencyRequest;
import com.m4trust.coreapi.idempotency.IdempotencyResultReference;
import com.m4trust.coreapi.idempotency.IdempotencyService;
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class RatificationPackageCreateServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-19T10:00:00Z");
    private final RatificationSourcePorts.DealTarget deals = mock(RatificationSourcePorts.DealTarget.class);
    private final RatificationSourcePorts.AvailableDocument documents = mock(RatificationSourcePorts.AvailableDocument.class);
    private final RatificationSourcePorts.AcceptedRuleSet ruleSets = mock(RatificationSourcePorts.AcceptedRuleSet.class);
    private final RatificationRepository packages = mock(RatificationRepository.class);
    private final RatificationSnapshotAssembler assembler = mock(RatificationSnapshotAssembler.class);
    private final RatificationPackageReadService reads = mock(RatificationPackageReadService.class);
    private final IdempotencyService idempotency = mock(IdempotencyService.class);
    private final AuditAppendPort audit = mock(AuditAppendPort.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final OperationContext context = new OperationContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            com.m4trust.coreapi.organization.LegalEntityRole.ADMIN,
            RequestedOperation.DEAL_RATIFICATION_PACKAGE_CREATE);
    private final RatificationPackageCreateService service = new RatificationPackageCreateService(deals, documents,
            ruleSets, packages, assembler, new CanonicalSnapshotHasher(), new ObjectMapper(), reads, idempotency,
            audit, transactions, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void firstCreateInsertsSnapshotAndPackageRepointsOnceAuditsActorTenantAndRecordsResult() {
        UUID dealId = UUID.randomUUID();
        var target = target(dealId, "DRAFT", true, 7, null, true);
        var detail = mock(RatificationPackageReadDtos.Detail.class);
        claimed(); ready(target, "new");
        when(deals.lockVisibleForCreate(context, dealId)).thenReturn(Optional.of(target));
        when(reads.project(eq(context), eq(target), any())).thenReturn(detail);

        assertSame(detail, service.create(context, dealId, request(), UUID.randomUUID(), UUID.randomUUID()));

        ArgumentCaptor<RatificationRepository.SnapshotRecord> snapshot = ArgumentCaptor.forClass(RatificationRepository.SnapshotRecord.class);
        ArgumentCaptor<RatificationRepository.PackageRecord> created = ArgumentCaptor.forClass(RatificationRepository.PackageRecord.class);
        ArgumentCaptor<AuditRecord> createAudit = ArgumentCaptor.forClass(AuditRecord.class);
        ArgumentCaptor<IdempotencyResultReference> result = ArgumentCaptor.forClass(IdempotencyResultReference.class);
        verify(packages).insert(snapshot.capture(), created.capture());
        verify(deals).pointCurrentPackage(dealId, created.getValue().id(), NOW);
        verify(audit).append(createAudit.capture());
        verify(idempotency).recordResult(any(), result.capture());
        assertEquals(snapshot.getValue().id(), created.getValue().snapshotId());
        assertEquals(context.tenantId(), createAudit.getValue().tenantId());
        assertEquals("RATIFICATION_PACKAGE_CREATED", createAudit.getValue().action());
        assertEquals(created.getValue().id(), result.getValue().id());
    }

    @Test
    void completedReplayLocksDealThenCurrentBeforeClaimButSkipsReadinessAndMutation() {
        UUID dealId = UUID.randomUUID(); UUID currentId = UUID.randomUUID(); UUID replayId = UUID.randomUUID();
        var target = target(dealId, "ACTIVE", false, 99, currentId, true);
        var current = record(currentId, dealId, target, "old", RatificationPackageStatus.PENDING);
        var replay = record(replayId, dealId, target, "result", RatificationPackageStatus.SUPERSEDED);
        var detail = mock(RatificationPackageReadDtos.Detail.class);
        inTransaction();
        when(deals.lockVisibleForCreate(context, dealId)).thenReturn(Optional.of(target));
        when(packages.findByDealAndIdForUpdate(dealId, currentId)).thenReturn(Optional.of(current));
        when(idempotency.claim(any())).thenReturn(new IdempotencyClaim(UUID.randomUUID(), IdempotencyClaimStatus.REPLAY,
                new IdempotencyResultReference("RATIFICATION_PACKAGE", replayId)));
        when(packages.findByDealAndId(dealId, replayId)).thenReturn(Optional.of(replay));
        when(reads.project(context, target, replay)).thenReturn(detail);

        assertSame(detail, service.create(context, dealId, new CreateRatificationPackageRequest(-1, -1, "bad", null), UUID.randomUUID(), UUID.randomUUID()));

        InOrder order = inOrder(deals, packages, idempotency);
        order.verify(deals).lockVisibleForCreate(context, dealId);
        order.verify(packages).findByDealAndIdForUpdate(dealId, currentId);
        order.verify(idempotency).claim(any());
        verify(documents, never()).find(any()); verify(ruleSets, never()).find(any());
        verify(packages, never()).updateStatus(any(), any(Long.class)); verify(packages, never()).insert(any(), any());
        verify(deals, never()).pointCurrentPackage(any(), any(), any()); verify(audit, never()).append(any());
    }

    @Test
    void freshKeySameHashReturnsCurrentPendingWithoutMutation() {
        UUID dealId = UUID.randomUUID(); UUID currentId = UUID.randomUUID();
        var target = target(dealId, "DRAFT", true, 7, currentId, true);
        var current = record(currentId, dealId, target, "same", RatificationPackageStatus.PENDING);
        var detail = mock(RatificationPackageReadDtos.Detail.class);
        claimed(); ready(target, "same");
        when(deals.lockVisibleForCreate(context, dealId)).thenReturn(Optional.of(target));
        when(packages.findByDealAndIdForUpdate(dealId, currentId)).thenReturn(Optional.of(current));
        when(reads.project(context, target, current)).thenReturn(detail);

        assertSame(detail, service.create(context, dealId, request(), UUID.randomUUID(), UUID.randomUUID()));

        ArgumentCaptor<IdempotencyResultReference> result = ArgumentCaptor.forClass(IdempotencyResultReference.class);
        verify(idempotency).recordResult(any(), result.capture()); assertEquals(currentId, result.getValue().id());
        verify(packages, never()).updateStatus(any(), any(Long.class)); verify(packages, never()).insert(any(), any());
        verify(deals, never()).pointCurrentPackage(any(), any(), any()); verify(audit, never()).append(any());
    }

    @Test
    void differentHashSupersedesThenCreatesInOrder() {
        UUID dealId = UUID.randomUUID(); UUID currentId = UUID.randomUUID();
        var target = target(dealId, "DRAFT", true, 7, currentId, true);
        var current = record(currentId, dealId, target, "old", RatificationPackageStatus.PENDING);
        claimed(); ready(target, "new");
        when(deals.lockVisibleForCreate(context, dealId)).thenReturn(Optional.of(target));
        when(packages.findByDealAndIdForUpdate(dealId, currentId)).thenReturn(Optional.of(current));
        when(packages.updateStatus(any(), eq(0L))).thenReturn(true);
        when(reads.project(eq(context), eq(target), any())).thenReturn(mock(RatificationPackageReadDtos.Detail.class));

        service.create(context, dealId, request(), UUID.randomUUID(), UUID.randomUUID());

        InOrder order = inOrder(packages, deals, audit, idempotency);
        order.verify(packages).updateStatus(any(), eq(0L));
        ArgumentCaptor<AuditRecord> auditRecords = ArgumentCaptor.forClass(AuditRecord.class);
        order.verify(audit).append(auditRecords.capture());
        order.verify(packages).insert(any(), any());
        order.verify(deals).pointCurrentPackage(eq(dealId), any(), eq(NOW));
        order.verify(audit).append(auditRecords.capture());
        order.verify(idempotency).recordResult(any(), any());
        assertEquals(List.of("RATIFICATION_PACKAGE_SUPERSEDED", "RATIFICATION_PACKAGE_CREATED"),
                auditRecords.getAllValues().stream().map(AuditRecord::action).toList());
    }

    @Test
    void validationFailuresDoNotMutatePackages() {
        assertFailure(null, request(), RatificationPackageCreateService.PackageNotFound.class);
        assertFailure(target(UUID.randomUUID(), "DRAFT", false, 7, null, true), request(), RatificationPackageCreateService.Forbidden.class);
        assertFailure(target(UUID.randomUUID(), "ACTIVE", true, 7, null, true), request(), RatificationPackageCreateService.StateConflict.class);
        assertFailure(target(UUID.randomUUID(), "DRAFT", true, 8, null, true), request(), RatificationPackageCreateService.StaleDealVersion.class);
        assertFailure(target(UUID.randomUUID(), "DRAFT", true, 7, null, false), request(), RatificationPackageCreateService.NotReady.class);
        assertFailure(target(UUID.randomUUID(), "DRAFT", true, 7, null, true), new CreateRatificationPackageRequest(7, 0, "try", null), RatificationPackageCreateService.InvalidTerms.class);
    }

    @Test
    void missingDocumentAndRulesAndOptimisticSupersedeFailurePreventNewPackage() {
        UUID dealId = UUID.randomUUID(); var target = target(dealId, "DRAFT", true, 7, null, true);
        claimed(); inTransaction(); when(deals.lockVisibleForCreate(context, dealId)).thenReturn(Optional.of(target));
        when(documents.find(target.currentDocumentId())).thenReturn(Optional.empty());
        assertThrows(RatificationPackageCreateService.NotReady.class, () -> service.create(context, dealId, request(), UUID.randomUUID(), UUID.randomUUID()));
        verify(packages, never()).insert(any(), any());
    }

    @Test
    void missingRuleSetPreventsPackageMutation() {
        UUID dealId = UUID.randomUUID(); var target = target(dealId, "DRAFT", true, 7, null, true);
        claimed(); when(deals.lockVisibleForCreate(context, dealId)).thenReturn(Optional.of(target));
        when(documents.find(target.currentDocumentId())).thenReturn(Optional.of(new RatificationSourcePorts.Document(target.currentDocumentId(), dealId, "v1", "a".repeat(64))));
        when(ruleSets.find(target.currentRuleSetId())).thenReturn(Optional.empty());
        assertThrows(RatificationPackageCreateService.NotReady.class, () -> service.create(context, dealId, request(), UUID.randomUUID(), UUID.randomUUID()));
        verify(packages, never()).insert(any(), any()); verify(deals, never()).pointCurrentPackage(any(), any(), any());
    }

    @Test
    void failedOptimisticSupersedePreventsInsertPointerAndResult() {
        UUID dealId = UUID.randomUUID(); UUID currentId = UUID.randomUUID();
        var target = target(dealId, "DRAFT", true, 7, currentId, true);
        claimed(); ready(target, "new"); when(deals.lockVisibleForCreate(context, dealId)).thenReturn(Optional.of(target));
        when(packages.findByDealAndIdForUpdate(dealId, currentId)).thenReturn(Optional.of(record(currentId, dealId, target, "old", RatificationPackageStatus.PENDING)));
        when(packages.updateStatus(any(), eq(0L))).thenReturn(false);
        assertThrows(RatificationPackage.StaleVersion.class, () -> service.create(context, dealId, request(), UUID.randomUUID(), UUID.randomUUID()));
        verify(packages, never()).insert(any(), any()); verify(deals, never()).pointCurrentPackage(any(), any(), any());
        verify(idempotency, never()).recordResult(any(), any());
    }

    @Test
    void fingerprintIsStableForEquivalentRequestAndChangesWithTerms() {
        UUID dealId = UUID.randomUUID(); var target = target(dealId, "DRAFT", true, 7, null, true); claimed(); ready(target, "same");
        when(assembler.assemble(any(), any(), any(), anyLong(), anyString(), any()))
                .thenReturn(assembledResult("same"));
        when(deals.lockVisibleForCreate(context, dealId)).thenReturn(Optional.of(target)); when(reads.project(eq(context), eq(target), any())).thenReturn(mock(RatificationPackageReadDtos.Detail.class));
        service.create(context, dealId, request(), UUID.randomUUID(), UUID.randomUUID());
        service.create(context, dealId, request(), UUID.randomUUID(), UUID.randomUUID());
        service.create(context, dealId, new CreateRatificationPackageRequest(7, 21, "TRY", null), UUID.randomUUID(), UUID.randomUUID());
        ArgumentCaptor<IdempotencyRequest> requests = ArgumentCaptor.forClass(IdempotencyRequest.class);
        verify(idempotency, times(3)).claim(requests.capture());
        assertEquals(requests.getAllValues().get(0).canonicalRequestHash(), requests.getAllValues().get(1).canonicalRequestHash());
        assertEquals(false, requests.getAllValues().get(0).canonicalRequestHash().equals(requests.getAllValues().get(2).canonicalRequestHash()));
    }

    private void assertFailure(RatificationSourcePorts.Target target, CreateRatificationPackageRequest request, Class<? extends RuntimeException> expected) {
        UUID dealId = target == null ? UUID.randomUUID() : target.dealId(); inTransaction(); when(idempotency.claim(any())).thenReturn(new IdempotencyClaim(UUID.randomUUID(), IdempotencyClaimStatus.CLAIMED, null));
        when(deals.lockVisibleForCreate(context, dealId)).thenReturn(Optional.ofNullable(target));
        assertThrows(expected, () -> service.create(context, dealId, request, UUID.randomUUID(), UUID.randomUUID()));
        verify(packages, never()).insert(any(), any()); verify(deals, never()).pointCurrentPackage(any(), any(), any());
    }

    private void ready(RatificationSourcePorts.Target target, String hash) {
        when(documents.find(target.currentDocumentId())).thenReturn(Optional.of(new RatificationSourcePorts.Document(target.currentDocumentId(), target.dealId(), "v1", "a".repeat(64))));
        when(ruleSets.find(target.currentRuleSetId())).thenReturn(Optional.of(new RatificationSourcePorts.RuleSet(target.currentRuleSetId(), target.dealId(), 1, List.of())));
        when(assembler.assemble(any(), any(), any(), eq(20L), eq("TRY"), any())).thenReturn(assembledResult(hash));
    }

    private RatificationSnapshotAssembler.Result assembledResult(String hash) {
        var snapshot = new RatificationSnapshotAssembler.Snapshot(1, UUID.randomUUID().toString(), "DL-1", "Deal",
                new RatificationSnapshotAssembler.Party(UUID.randomUUID().toString(), "Buyer"),
                new RatificationSnapshotAssembler.Party(UUID.randomUUID().toString(), "Seller"),
                new RatificationSnapshotAssembler.RuleSet(UUID.randomUUID().toString(), 1, List.of()),
                new RatificationSnapshotAssembler.Terms(20, "TRY"),
                new RatificationSnapshotAssembler.Document(UUID.randomUUID().toString(), "v1", "a".repeat(64)),
                null);
        return new RatificationSnapshotAssembler.Result(snapshot, "{}", hash);
    }
    private void claimed() { inTransaction(); when(idempotency.claim(any())).thenReturn(new IdempotencyClaim(UUID.randomUUID(), IdempotencyClaimStatus.CLAIMED, null)); }
    @SuppressWarnings("unchecked") private void inTransaction() { when(transactions.execute(any(TransactionCallback.class))).thenAnswer(i -> ((TransactionCallback<Object>) i.getArgument(0)).doInTransaction(mock(TransactionStatus.class))); }
    private CreateRatificationPackageRequest request() { return new CreateRatificationPackageRequest(7, 20, "TRY", null); }
    private RatificationSourcePorts.Target target(UUID dealId, String status, boolean initiator, long version, UUID current, boolean ready) { UUID document = ready ? UUID.randomUUID() : null; UUID rules = ready ? UUID.randomUUID() : null; return new RatificationSourcePorts.Target(dealId, UUID.randomUUID(), status, version, "DL-1", "Deal", initiator, ready ? new RatificationSourcePorts.Party(UUID.randomUUID(), "Buyer") : null, ready ? new RatificationSourcePorts.Party(UUID.randomUUID(), "Seller") : null, document, rules, current); }
    private RatificationRepository.PackageRecord record(UUID id, UUID dealId, RatificationSourcePorts.Target target, String hash, RatificationPackageStatus status) { return new RatificationRepository.PackageRecord(id, dealId, UUID.randomUUID(), status, target.buyer().legalEntityId(), target.seller().legalEntityId(), 20, "TRY", NOW, 0, 1, "{}", hash); }
}
