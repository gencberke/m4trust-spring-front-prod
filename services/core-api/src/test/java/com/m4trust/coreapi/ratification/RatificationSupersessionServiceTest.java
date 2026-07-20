package com.m4trust.coreapi.ratification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.m4trust.coreapi.audit.AuditAppendPort;
import com.m4trust.coreapi.audit.AuditRecord;
import com.m4trust.coreapi.organization.LegalEntityRole;
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RatificationSupersessionServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-19T15:00:00Z");
    private final RatificationRepository packages = mock(RatificationRepository.class);
    private final AuditAppendPort audit = mock(AuditAppendPort.class);
    private final RatificationSupersessionService service =
            new RatificationSupersessionService(packages, audit);
    private final OperationContext context = new OperationContext(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), LegalEntityRole.ADMIN,
            RequestedOperation.DEAL_UPDATE);

    @Test
    void nullCurrentPackageIsASafeNoOp() {
        service.supersedePending(
                context, UUID.randomUUID(), null, UUID.randomUUID(), NOW);

        verify(packages, never()).findByDealAndIdForUpdate(any(), any());
        verify(audit, never()).append(any());
    }

    @Test
    void pendingPackageIsLockedSupersededAndAuditedForTheActorTenant() {
        UUID dealId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        var pending = record(dealId, packageId, RatificationPackageStatus.PENDING, 4);
        when(packages.findByDealAndIdForUpdate(dealId, packageId))
                .thenReturn(Optional.of(pending));
        when(packages.updateStatus(any(), eq(4L))).thenReturn(true);

        service.supersedePending(
                context, dealId, packageId, UUID.randomUUID(), NOW);

        ArgumentCaptor<RatificationRepository.PackageRecord> updated =
                ArgumentCaptor.forClass(RatificationRepository.PackageRecord.class);
        verify(packages).updateStatus(updated.capture(), eq(4L));
        assertEquals(RatificationPackageStatus.SUPERSEDED, updated.getValue().status());
        assertEquals(5, updated.getValue().version());
        ArgumentCaptor<AuditRecord> record = ArgumentCaptor.forClass(AuditRecord.class);
        verify(audit).append(record.capture());
        assertEquals(context.tenantId(), record.getValue().tenantId());
        assertEquals(context.authenticatedUserId(), record.getValue().actorUserId());
        assertEquals(context.activeLegalEntityId(), record.getValue().legalEntityId());
        assertEquals("RATIFICATION_PACKAGE", record.getValue().subjectType());
        assertEquals(packageId, record.getValue().subjectId());
        assertEquals("RATIFICATION_PACKAGE_SUPERSEDED", record.getValue().action());
        assertEquals(NOW, record.getValue().occurredAt());
    }

    @Test
    void rejectedAndAlreadySupersededPackagesAreSafeNoOps() {
        UUID dealId = UUID.randomUUID();
        UUID rejectedId = UUID.randomUUID();
        UUID supersededId = UUID.randomUUID();
        when(packages.findByDealAndIdForUpdate(dealId, rejectedId))
                .thenReturn(Optional.of(record(
                        dealId, rejectedId, RatificationPackageStatus.REJECTED, 1)));
        when(packages.findByDealAndIdForUpdate(dealId, supersededId))
                .thenReturn(Optional.of(record(
                        dealId, supersededId, RatificationPackageStatus.SUPERSEDED, 1)));

        service.supersedePending(context, dealId, rejectedId, UUID.randomUUID(), NOW);
        service.supersedePending(context, dealId, supersededId, UUID.randomUUID(), NOW);

        verify(packages, never()).updateStatus(any(), any(Long.class));
        verify(audit, never()).append(any());
    }

    @Test
    void missingOrRatifiedCurrentPackageFailsClosed() {
        UUID dealId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();
        UUID ratifiedId = UUID.randomUUID();
        when(packages.findByDealAndIdForUpdate(dealId, missingId)).thenReturn(Optional.empty());
        when(packages.findByDealAndIdForUpdate(dealId, ratifiedId))
                .thenReturn(Optional.of(record(
                        dealId, ratifiedId, RatificationPackageStatus.RATIFIED, 2)));

        assertThrows(RatificationSupersessionPort.InvariantViolation.class,
                () -> service.supersedePending(
                        context, dealId, missingId, UUID.randomUUID(), NOW));
        assertThrows(RatificationSupersessionPort.InvariantViolation.class,
                () -> service.supersedePending(
                        context, dealId, ratifiedId, UUID.randomUUID(), NOW));
        verify(audit, never()).append(any());
    }

    @Test
    void optimisticFailureIsStaleAndDoesNotAudit() {
        UUID dealId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        when(packages.findByDealAndIdForUpdate(dealId, packageId))
                .thenReturn(Optional.of(record(
                        dealId, packageId, RatificationPackageStatus.PENDING, 0)));
        when(packages.updateStatus(any(), eq(0L))).thenReturn(false);

        assertThrows(RatificationSupersessionPort.Stale.class,
                () -> service.supersedePending(
                        context, dealId, packageId, UUID.randomUUID(), NOW));

        verify(audit, never()).append(any());
    }

    private static RatificationRepository.PackageRecord record(
            UUID dealId,
            UUID packageId,
            RatificationPackageStatus status,
            long version) {
        return new RatificationRepository.PackageRecord(
                packageId, dealId, UUID.randomUUID(), status,
                UUID.randomUUID(), UUID.randomUUID(), 100, "TRY", NOW, version,
                1, "{}", "a".repeat(64));
    }
}
