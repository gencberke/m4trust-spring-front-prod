package com.m4trust.coreapi.ratification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
import com.m4trust.coreapi.organization.LegalEntityRole;
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class RatificationPackageActionServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-19T12:00:00Z");
    private static final UUID ACTOR_USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID BUYER = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000004");

    private final RatificationSourcePorts.DealTarget deals = mock(RatificationSourcePorts.DealTarget.class);
    private final RatificationRepository packages = mock(RatificationRepository.class);
    private final RatificationPackageReadService reads = mock(RatificationPackageReadService.class);
    private final IdempotencyService idempotency = mock(IdempotencyService.class);
    private final AuditAppendPort audit = mock(AuditAppendPort.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final RatificationPackageActionService service = new RatificationPackageActionService(
            deals, packages, reads, idempotency, audit, transactions,
            Clock.fixed(NOW, ZoneOffset.UTC), new CanonicalSnapshotHasher(), new ObjectMapper());

    @BeforeEach
    void runCallbacksInsideTheTestTransactionBoundary() {
        runInTransaction();
    }

    @Test
    void locksDealThenCurrentPackageThenClaimsIdempotency() {
        UUID dealId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        var target = target(dealId, "DRAFT", packageId);
        var current = record(packageId, dealId, RatificationPackageStatus.PENDING, 0);
        claimed();
        when(deals.lockVisibleForCreate(any(), eq(dealId))).thenReturn(Optional.of(target));
        when(packages.findByDealAndIdForUpdate(dealId, packageId)).thenReturn(Optional.of(current));
        when(packages.findApprovalByPackageAndEntity(packageId, BUYER)).thenReturn(Optional.of(approval(BUYER)));
        when(packages.findByDealAndId(dealId, packageId)).thenReturn(Optional.of(stored(current)));
        when(reads.project(any(), eq(target), any())).thenReturn(mock(RatificationPackageReadDtos.Detail.class));

        service.approve(context(BUYER, LegalEntityRole.ADMIN, true), dealId, packageId,
                new RatificationPackageActionRequest(0), UUID.randomUUID(), UUID.randomUUID());

        InOrder order = inOrder(deals, packages, idempotency);
        order.verify(deals).lockVisibleForCreate(any(), eq(dealId));
        order.verify(packages).findByDealAndIdForUpdate(dealId, packageId);
        order.verify(idempotency).claim(any());
    }

    @Test
    void replayReturnsHistoricalResultAfterCurrentLifecycleAndRoleChangesWithoutMutation() {
        UUID dealId = UUID.randomUUID();
        UUID currentId = UUID.randomUUID();
        UUID historicalId = UUID.randomUUID();
        var target = target(dealId, "ACTIVE", currentId);
        var current = record(currentId, dealId, RatificationPackageStatus.RATIFIED, 2);
        var historical = stored(record(historicalId, dealId, RatificationPackageStatus.SUPERSEDED, 1));
        var detail = mock(RatificationPackageReadDtos.Detail.class);
        OperationContext member = context(BUYER, LegalEntityRole.MEMBER, true);
        when(deals.lockVisibleForCreate(member, dealId)).thenReturn(Optional.of(target));
        when(packages.findByDealAndIdForUpdate(dealId, currentId)).thenReturn(Optional.of(current));
        when(idempotency.claim(any())).thenReturn(replay(historicalId));
        when(packages.findByDealAndId(dealId, historicalId)).thenReturn(Optional.of(historical));
        when(reads.project(member, target, historical)).thenReturn(detail);

        assertSame(detail, service.approve(member, dealId, historicalId,
                new RatificationPackageActionRequest(0), UUID.randomUUID(), UUID.randomUUID()));

        verify(packages, never()).findApprovalByPackageAndEntity(any(), any());
        verify(packages, never()).insertApproval(any());
        verify(packages, never()).updateStatus(any(), any(Long.class));
        verify(deals, never()).activateCurrentPackage(any(), any(), any());
        verify(idempotency, never()).recordResult(any(), any());
        verify(audit, never()).append(any());
    }

    @Test
    void freshAbsentTargetIsNotFound() {
        UUID dealId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        OperationContext context = context(BUYER, LegalEntityRole.ADMIN, true);
        claimed();
        when(deals.lockVisibleForCreate(context, dealId)).thenReturn(Optional.of(target(dealId, "DRAFT", null)));
        when(packages.findByDealAndId(dealId, packageId)).thenReturn(Optional.empty());

        assertThrows(RatificationPackageActionService.NotFound.class,
                () -> service.approve(context, dealId, packageId,
                        new RatificationPackageActionRequest(0), UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void freshVisibleHistoricalNonCurrentTargetIsStale() {
        UUID dealId = UUID.randomUUID();
        UUID currentId = UUID.randomUUID();
        UUID historicalId = UUID.randomUUID();
        OperationContext context = context(BUYER, LegalEntityRole.ADMIN, true);
        claimed();
        when(deals.lockVisibleForCreate(context, dealId))
                .thenReturn(Optional.of(target(dealId, "DRAFT", currentId)));
        when(packages.findByDealAndIdForUpdate(dealId, currentId))
                .thenReturn(Optional.of(record(currentId, dealId, RatificationPackageStatus.PENDING, 0)));
        when(packages.findByDealAndId(dealId, historicalId))
                .thenReturn(Optional.of(record(historicalId, dealId, RatificationPackageStatus.SUPERSEDED, 1)));

        assertThrows(RatificationPackageActionService.Stale.class,
                () -> service.approve(context, dealId, historicalId,
                        new RatificationPackageActionRequest(0), UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void memberAndWrongEntityAreForbidden() {
        assertForbidden(context(BUYER, LegalEntityRole.MEMBER, true));
        assertForbidden(context(UUID.randomUUID(), LegalEntityRole.ADMIN, true));
    }

    @Test
    void staleVersionIsCheckedBeforeSameEntityDuplicate() {
        UUID dealId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        OperationContext context = context(BUYER, LegalEntityRole.ADMIN, true);
        claimed();
        stubCurrent(context, target(dealId, "DRAFT", packageId),
                record(packageId, dealId, RatificationPackageStatus.PENDING, 2));

        assertThrows(RatificationPackageActionService.Stale.class,
                () -> service.approve(context, dealId, packageId,
                        new RatificationPackageActionRequest(1), UUID.randomUUID(), UUID.randomUUID()));

        verify(packages, never()).findApprovalByPackageAndEntity(any(), any());
        verify(idempotency, never()).recordResult(any(), any());
    }

    @Test
    void sameEntityDuplicateAtCurrentVersionRecordsResultWithoutMutationAndReloadsStoredProjection() {
        UUID dealId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        OperationContext context = context(BUYER, LegalEntityRole.ADMIN, true);
        var target = target(dealId, "DRAFT", packageId);
        var current = record(packageId, dealId, RatificationPackageStatus.PENDING, 1);
        var stored = stored(current);
        var detail = mock(RatificationPackageReadDtos.Detail.class);
        claimed();
        stubCurrent(context, target, current);
        when(packages.findApprovalByPackageAndEntity(packageId, BUYER))
                .thenReturn(Optional.of(approval(BUYER)));
        when(packages.findByDealAndId(dealId, packageId)).thenReturn(Optional.of(stored));
        when(reads.project(context, target, stored)).thenReturn(detail);

        assertSame(detail, service.approve(context, dealId, packageId,
                new RatificationPackageActionRequest(1), UUID.randomUUID(), UUID.randomUUID()));

        verify(idempotency).recordResult(any(), eq(new IdempotencyResultReference("RATIFICATION_PACKAGE", packageId)));
        verify(packages, never()).insertApproval(any());
        verify(packages, never()).updateStatus(any(), any(Long.class));
        verify(audit, never()).append(any());
        verify(reads).project(context, target, stored);
    }

    @Test
    void firstDistinctApprovalRemainsPendingAdvancesVersionAndAppendsOnce() {
        UUID dealId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        OperationContext context = context(BUYER, LegalEntityRole.ADMIN, true);
        var target = target(dealId, "DRAFT", packageId);
        var current = record(packageId, dealId, RatificationPackageStatus.PENDING, 0);
        var stored = stored(record(packageId, dealId, RatificationPackageStatus.PENDING, 1));
        claimed();
        stubCurrent(context, target, current);
        when(packages.findApprovalByPackageAndEntity(packageId, BUYER)).thenReturn(Optional.empty());
        when(packages.listApprovals(packageId)).thenReturn(List.of(approval(BUYER)));
        when(packages.updateStatus(any(), eq(0L))).thenReturn(true);
        when(packages.findByDealAndId(dealId, packageId)).thenReturn(Optional.of(stored));
        when(reads.project(context, target, stored)).thenReturn(mock(RatificationPackageReadDtos.Detail.class));

        service.approve(context, dealId, packageId,
                new RatificationPackageActionRequest(0), UUID.randomUUID(), UUID.randomUUID());

        ArgumentCaptor<RatificationRepository.PackageRecord> updated =
                ArgumentCaptor.forClass(RatificationRepository.PackageRecord.class);
        verify(packages).updateStatus(updated.capture(), eq(0L));
        assertEquals(RatificationPackageStatus.PENDING, updated.getValue().status());
        assertEquals(1, updated.getValue().version());
        verify(packages).insertApproval(any());
        verify(deals, never()).activateCurrentPackage(any(), any(), any());
        verifyPackageAuditActions("RATIFICATION_PACKAGE_APPROVED");
    }

    @Test
    void secondDistinctApprovalRatifiesActivatesAndWritesActorTenantAudits() {
        UUID dealId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        OperationContext context = context(BUYER, LegalEntityRole.ADMIN, true);
        var target = target(dealId, "DRAFT", packageId);
        var current = record(packageId, dealId, RatificationPackageStatus.PENDING, 1);
        var stored = stored(record(packageId, dealId, RatificationPackageStatus.RATIFIED, 2));
        claimed();
        stubCurrent(context, target, current);
        when(packages.findApprovalByPackageAndEntity(packageId, BUYER)).thenReturn(Optional.empty());
        when(packages.listApprovals(packageId)).thenReturn(List.of(approval(SELLER), approval(BUYER)));
        when(packages.updateStatus(any(), eq(1L))).thenReturn(true);
        when(packages.findByDealAndId(dealId, packageId)).thenReturn(Optional.of(stored));
        when(reads.project(context, target, stored)).thenReturn(mock(RatificationPackageReadDtos.Detail.class));

        service.approve(context, dealId, packageId,
                new RatificationPackageActionRequest(1), UUID.randomUUID(), UUID.randomUUID());

        ArgumentCaptor<RatificationRepository.PackageRecord> updated =
                ArgumentCaptor.forClass(RatificationRepository.PackageRecord.class);
        verify(packages).updateStatus(updated.capture(), eq(1L));
        assertEquals(RatificationPackageStatus.RATIFIED, updated.getValue().status());
        assertEquals(2, updated.getValue().version());
        verify(deals).activateCurrentPackage(dealId, packageId, NOW);
        ArgumentCaptor<AuditRecord> records = ArgumentCaptor.forClass(AuditRecord.class);
        verify(audit, times(3)).append(records.capture());
        assertEquals(List.of(
                        "RATIFICATION_PACKAGE_APPROVED",
                        "RATIFICATION_PACKAGE_RATIFIED",
                        "DEAL_ACTIVATED"),
                records.getAllValues().stream().map(AuditRecord::action).toList());
        records.getAllValues().forEach(record -> {
            assertEquals(ACTOR_TENANT, record.tenantId());
            assertEquals(ACTOR_USER, record.actorUserId());
            assertEquals(BUYER, record.legalEntityId());
        });
    }

    @Test
    void rejectTransitionsPendingAndTerminalPackagesConflict() {
        UUID dealId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        OperationContext context = context(BUYER, LegalEntityRole.ADMIN, false);
        var target = target(dealId, "DRAFT", packageId);
        var current = record(packageId, dealId, RatificationPackageStatus.PENDING, 0);
        var stored = stored(record(packageId, dealId, RatificationPackageStatus.REJECTED, 1));
        claimed();
        stubCurrent(context, target, current);
        when(packages.updateStatus(any(), eq(0L))).thenReturn(true);
        when(packages.findByDealAndId(dealId, packageId)).thenReturn(Optional.of(stored));
        when(reads.project(context, target, stored)).thenReturn(mock(RatificationPackageReadDtos.Detail.class));

        service.reject(context, dealId, packageId,
                new RatificationPackageActionRequest(0), UUID.randomUUID(), UUID.randomUUID());

        ArgumentCaptor<RatificationRepository.PackageRecord> updated =
                ArgumentCaptor.forClass(RatificationRepository.PackageRecord.class);
        verify(packages).updateStatus(updated.capture(), eq(0L));
        assertEquals(RatificationPackageStatus.REJECTED, updated.getValue().status());
        verifyPackageAuditActions("RATIFICATION_PACKAGE_REJECTED");
    }

    @Test
    void approveAndRejectConflictOnTerminalPackage() {
        assertTerminalConflict(true);
        assertTerminalConflict(false);
    }

    @Test
    void optimisticPackageUpdateFailurePreventsActivationAndResult() {
        UUID dealId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        OperationContext context = context(BUYER, LegalEntityRole.ADMIN, true);
        var target = target(dealId, "DRAFT", packageId);
        claimed();
        stubCurrent(context, target,
                record(packageId, dealId, RatificationPackageStatus.PENDING, 1));
        when(packages.findApprovalByPackageAndEntity(packageId, BUYER)).thenReturn(Optional.empty());
        when(packages.listApprovals(packageId)).thenReturn(List.of(approval(SELLER), approval(BUYER)));
        when(packages.updateStatus(any(), eq(1L))).thenReturn(false);

        assertThrows(RatificationPackageActionService.Stale.class,
                () -> service.approve(context, dealId, packageId,
                        new RatificationPackageActionRequest(1), UUID.randomUUID(), UUID.randomUUID()));

        verify(packages).insertApproval(any());
        verify(deals, never()).activateCurrentPackage(any(), any(), any());
        verify(idempotency, never()).recordResult(any(), any());
        verify(reads, never()).project(any(), any(), any());
    }

    @Test
    void canonicalFingerprintsAreStableAndBindActionTargetVersionAndActor() {
        UUID dealId = UUID.randomUUID();
        UUID firstPackage = UUID.randomUUID();
        UUID secondPackage = UUID.randomUUID();
        var firstTarget = target(dealId, "ACTIVE", firstPackage);
        var secondTarget = target(dealId, "ACTIVE", secondPackage);
        var firstRecord = record(firstPackage, dealId, RatificationPackageStatus.RATIFIED, 3);
        var secondRecord = record(secondPackage, dealId, RatificationPackageStatus.RATIFIED, 3);
        when(deals.lockVisibleForCreate(any(), eq(dealId)))
                .thenReturn(Optional.of(firstTarget), Optional.of(firstTarget), Optional.of(firstTarget),
                        Optional.of(firstTarget), Optional.of(secondTarget), Optional.of(firstTarget));
        when(packages.findByDealAndIdForUpdate(dealId, firstPackage)).thenReturn(Optional.of(firstRecord));
        when(packages.findByDealAndIdForUpdate(dealId, secondPackage)).thenReturn(Optional.of(secondRecord));
        when(idempotency.claim(any())).thenReturn(replay(firstPackage));
        when(packages.findByDealAndId(dealId, firstPackage)).thenReturn(Optional.of(stored(firstRecord)));
        when(reads.project(any(), any(), any())).thenReturn(mock(RatificationPackageReadDtos.Detail.class));
        OperationContext buyerApprove = context(BUYER, LegalEntityRole.ADMIN, true);
        OperationContext buyerReject = context(BUYER, LegalEntityRole.ADMIN, false);
        OperationContext sellerApprove = context(SELLER, LegalEntityRole.ADMIN, true);

        service.approve(buyerApprove, dealId, firstPackage,
                new RatificationPackageActionRequest(3), UUID.randomUUID(), UUID.randomUUID());
        service.approve(buyerApprove, dealId, firstPackage,
                new RatificationPackageActionRequest(3), UUID.randomUUID(), UUID.randomUUID());
        service.reject(buyerReject, dealId, firstPackage,
                new RatificationPackageActionRequest(3), UUID.randomUUID(), UUID.randomUUID());
        service.approve(buyerApprove, dealId, firstPackage,
                new RatificationPackageActionRequest(4), UUID.randomUUID(), UUID.randomUUID());
        service.approve(buyerApprove, dealId, secondPackage,
                new RatificationPackageActionRequest(3), UUID.randomUUID(), UUID.randomUUID());
        service.approve(sellerApprove, dealId, firstPackage,
                new RatificationPackageActionRequest(3), UUID.randomUUID(), UUID.randomUUID());

        ArgumentCaptor<IdempotencyRequest> requests = ArgumentCaptor.forClass(IdempotencyRequest.class);
        verify(idempotency, times(6)).claim(requests.capture());
        List<String> hashes = requests.getAllValues().stream()
                .map(IdempotencyRequest::canonicalRequestHash)
                .toList();
        assertEquals(hashes.get(0), hashes.get(1));
        assertNotEquals(hashes.get(0), hashes.get(2));
        assertNotEquals(hashes.get(0), hashes.get(3));
        assertNotEquals(hashes.get(0), hashes.get(4));
        assertNotEquals(hashes.get(0), hashes.get(5));
    }

    private void assertForbidden(OperationContext context) {
        UUID dealId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        claimed();
        stubCurrent(context, target(dealId, "DRAFT", packageId),
                record(packageId, dealId, RatificationPackageStatus.PENDING, 0));
        assertThrows(RatificationPackageActionService.Forbidden.class,
                () -> service.approve(context, dealId, packageId,
                        new RatificationPackageActionRequest(0), UUID.randomUUID(), UUID.randomUUID()));
    }

    private void assertTerminalConflict(boolean approve) {
        UUID dealId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        OperationContext context = context(BUYER, LegalEntityRole.ADMIN, approve);
        claimed();
        stubCurrent(context, target(dealId, "DRAFT", packageId),
                record(packageId, dealId, RatificationPackageStatus.REJECTED, 1));
        if (approve) {
            assertThrows(RatificationPackageActionService.State.class,
                    () -> service.approve(context, dealId, packageId,
                            new RatificationPackageActionRequest(1), UUID.randomUUID(), UUID.randomUUID()));
        } else {
            assertThrows(RatificationPackageActionService.State.class,
                    () -> service.reject(context, dealId, packageId,
                            new RatificationPackageActionRequest(1), UUID.randomUUID(), UUID.randomUUID()));
        }
        verify(packages, never()).updateStatus(any(), any(Long.class));
    }

    private void stubCurrent(
            OperationContext context,
            RatificationSourcePorts.Target target,
            RatificationRepository.PackageRecord current) {
        when(deals.lockVisibleForCreate(context, target.dealId())).thenReturn(Optional.of(target));
        when(packages.findByDealAndIdForUpdate(target.dealId(), current.id()))
                .thenReturn(Optional.of(current));
    }

    private void verifyPackageAuditActions(String... expected) {
        ArgumentCaptor<AuditRecord> records = ArgumentCaptor.forClass(AuditRecord.class);
        verify(audit, times(expected.length)).append(records.capture());
        assertEquals(List.of(expected), records.getAllValues().stream().map(AuditRecord::action).toList());
    }

    private void claimed() {
        when(idempotency.claim(any())).thenReturn(new IdempotencyClaim(
                UUID.randomUUID(), IdempotencyClaimStatus.CLAIMED, null));
    }

    private static IdempotencyClaim replay(UUID packageId) {
        return new IdempotencyClaim(
                UUID.randomUUID(), IdempotencyClaimStatus.REPLAY,
                new IdempotencyResultReference("RATIFICATION_PACKAGE", packageId));
    }

    @SuppressWarnings("unchecked")
    private void runInTransaction() {
        when(transactions.execute(any(TransactionCallback.class))).thenAnswer(invocation ->
                ((TransactionCallback<Object>) invocation.getArgument(0))
                        .doInTransaction(mock(TransactionStatus.class)));
    }

    private static OperationContext context(UUID entity, LegalEntityRole role, boolean approve) {
        return new OperationContext(
                ACTOR_USER, ACTOR_TENANT, entity, role,
                approve
                        ? RequestedOperation.DEAL_RATIFICATION_PACKAGE_APPROVE
                        : RequestedOperation.DEAL_RATIFICATION_PACKAGE_REJECT);
    }

    private static RatificationSourcePorts.Target target(UUID dealId, String status, UUID currentPackageId) {
        return new RatificationSourcePorts.Target(
                dealId, UUID.randomUUID(), status, 9, "DL-1", "Deal", false,
                new RatificationSourcePorts.Party(BUYER, "Buyer"),
                new RatificationSourcePorts.Party(SELLER, "Seller"),
                UUID.randomUUID(), UUID.randomUUID(), currentPackageId);
    }

    private static RatificationRepository.PackageRecord record(
            UUID packageId,
            UUID dealId,
            RatificationPackageStatus status,
            long version) {
        return new RatificationRepository.PackageRecord(
                packageId, dealId, UUID.randomUUID(), status, BUYER, SELLER,
                100, "TRY", NOW, version, null, null, null);
    }

    private static RatificationRepository.PackageRecord stored(
            RatificationRepository.PackageRecord record) {
        return new RatificationRepository.PackageRecord(
                record.id(), record.dealId(), record.snapshotId(), record.status(),
                record.buyerLegalEntityId(), record.sellerLegalEntityId(), record.amountMinor(),
                record.currency(), record.createdAt(), record.version(), 1, "{}", "a".repeat(64));
    }

    private static RatificationRepository.ApprovalRecord approval(UUID entity) {
        return new RatificationRepository.ApprovalRecord(
                UUID.randomUUID(), UUID.randomUUID(), entity, UUID.randomUUID(), NOW);
    }
}
