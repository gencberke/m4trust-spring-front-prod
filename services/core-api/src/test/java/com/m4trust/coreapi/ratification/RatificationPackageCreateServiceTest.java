package com.m4trust.coreapi.ratification;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.m4trust.coreapi.audit.AuditAppendPort;
import com.m4trust.coreapi.idempotency.IdempotencyClaim;
import com.m4trust.coreapi.idempotency.IdempotencyClaimStatus;
import com.m4trust.coreapi.idempotency.IdempotencyResultReference;
import com.m4trust.coreapi.idempotency.IdempotencyService;
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class RatificationPackageCreateServiceTest {
    private final RatificationSourcePorts.DealTarget deals = mock(RatificationSourcePorts.DealTarget.class);
    private final RatificationSourcePorts.AvailableDocument documents = mock(RatificationSourcePorts.AvailableDocument.class);
    private final RatificationSourcePorts.AcceptedRuleSet ruleSets = mock(RatificationSourcePorts.AcceptedRuleSet.class);
    private final RatificationRepository packages = mock(RatificationRepository.class);
    private final RatificationSnapshotAssembler assembler = mock(RatificationSnapshotAssembler.class);
    private final CanonicalSnapshotHasher hasher = new CanonicalSnapshotHasher();
    private final RatificationPackageReadService reads = mock(RatificationPackageReadService.class);
    private final IdempotencyService idempotency = mock(IdempotencyService.class);
    private final AuditAppendPort audit = mock(AuditAppendPort.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final OperationContext context = new OperationContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            RequestedOperation.DEAL_RATIFICATION_PACKAGE_CREATE);
    private final RatificationPackageCreateService service = new RatificationPackageCreateService(deals, documents,
            ruleSets, packages, assembler, hasher, new ObjectMapper(), reads, idempotency, audit, transactions,
            Clock.fixed(Instant.parse("2026-07-19T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void locksDealBeforeCurrentPackageAndSupersedesBeforeReplacingIt() {
        UUID dealId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID ruleSetId = UUID.randomUUID();
        UUID previousId = UUID.randomUUID();
        var target = target(dealId, documentId, ruleSetId, previousId);
        var previous = new RatificationRepository.PackageRecord(previousId, dealId, UUID.randomUUID(),
                RatificationPackageStatus.PENDING, target.buyer().legalEntityId(), target.seller().legalEntityId(),
                10, "TRY", Instant.now(), 0, 1, "{}", "old");
        var request = new CreateRatificationPackageRequest(7, 20, "TRY");
        var detail = mock(RatificationPackageReadDtos.Detail.class);
        inTransaction();
        when(idempotency.findCompleted(any())).thenReturn(Optional.empty());
        when(idempotency.claim(any())).thenReturn(new IdempotencyClaim(UUID.randomUUID(), IdempotencyClaimStatus.CLAIMED, null));
        when(deals.lockVisibleForCreate(context, dealId)).thenReturn(Optional.of(target));
        when(packages.findByDealAndIdForUpdate(dealId, previousId)).thenReturn(Optional.of(previous));
        when(documents.find(documentId)).thenReturn(Optional.of(new RatificationSourcePorts.Document(documentId, dealId, "v1", "a".repeat(64))));
        when(ruleSets.find(ruleSetId)).thenReturn(Optional.of(new RatificationSourcePorts.RuleSet(ruleSetId, dealId, 1, java.util.List.of())));
        when(assembler.assemble(any(), any(), any(), eq(20L), eq("TRY")))
                .thenReturn(new RatificationSnapshotAssembler.Result(null, "{}", "new"));
        when(packages.updateStatus(any(), eq(0L))).thenReturn(true);
        when(reads.project(eq(context), eq(target), any())).thenReturn(detail);

        assertSame(detail, service.create(context, dealId, request, UUID.randomUUID(), UUID.randomUUID()));

        InOrder order = inOrder(deals, packages);
        order.verify(deals).lockVisibleForCreate(context, dealId);
        order.verify(packages).findByDealAndIdForUpdate(dealId, previousId);
        order.verify(packages).updateStatus(any(), eq(0L));
        order.verify(packages).insert(any(), any());
        order.verify(deals).pointCurrentPackage(eq(dealId), any(), any());
    }

    @Test
    void completedKeyReplaysAfterLocksWithoutCurrentMutationChecks() {
        UUID packageId = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        var reference = new IdempotencyResultReference("RATIFICATION_PACKAGE", packageId);
        var record = mock(RatificationRepository.PackageRecord.class);
        var detail = mock(RatificationPackageReadDtos.Detail.class);
        var target = target(dealId, UUID.randomUUID(), UUID.randomUUID(), null);
        inTransaction();
        when(deals.lockVisibleForCreate(context, dealId)).thenReturn(Optional.of(target));
        when(idempotency.claim(any())).thenReturn(new IdempotencyClaim(UUID.randomUUID(), IdempotencyClaimStatus.REPLAY, reference));
        when(packages.findByDealAndId(dealId, packageId)).thenReturn(Optional.of(record));
        when(reads.project(context, target, record)).thenReturn(detail);

        assertSame(detail, service.create(context, dealId,
                new CreateRatificationPackageRequest(999, -1, "bad"), UUID.randomUUID(), UUID.randomUUID()));

        verify(deals).lockVisibleForCreate(context, dealId);
        verify(documents, never()).find(any());
        verify(ruleSets, never()).find(any());
    }

    @SuppressWarnings("unchecked")
    private void inTransaction() {
        when(transactions.execute(any(TransactionCallback.class))).thenAnswer(invocation ->
                ((TransactionCallback<Object>) invocation.getArgument(0)).doInTransaction(mock(TransactionStatus.class)));
    }

    private RatificationSourcePorts.Target target(UUID dealId, UUID documentId, UUID ruleSetId, UUID currentPackageId) {
        return new RatificationSourcePorts.Target(dealId, context.tenantId(), "DRAFT", 7, "DL-1", "Deal", true,
                new RatificationSourcePorts.Party(UUID.randomUUID(), "Buyer"),
                new RatificationSourcePorts.Party(UUID.randomUUID(), "Seller"), documentId, ruleSetId, currentPackageId);
    }
}
