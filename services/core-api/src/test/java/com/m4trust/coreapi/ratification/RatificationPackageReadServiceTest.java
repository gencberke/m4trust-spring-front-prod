package com.m4trust.coreapi.ratification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class RatificationPackageReadServiceTest {
    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TENANT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private final ObjectMapper json = new ObjectMapper();
    private final CanonicalSnapshotHasher hasher = new CanonicalSnapshotHasher();
    private final RatificationSourcePorts.DealTarget deals = mock(RatificationSourcePorts.DealTarget.class);
    private final RatificationRepository packages = mock(RatificationRepository.class);
    private final RatificationPackageReadService service =
            new RatificationPackageReadService(deals, packages, json, hasher);

    @Test
    void projectsBuyerAndSellerApprovalsWithEntityScopedApproverPrivacy() throws Exception {
        Fixture fixture = fixture();
        var buyerApproval = approval(fixture, fixture.buyerId, fixture.buyerUserId, 1);
        var sellerApproval = approval(fixture, fixture.sellerId, fixture.sellerUserId, 2);
        stubVisible(fixture, fixture.buyerId);
        when(packages.findByDealAndId(fixture.dealId, fixture.packageRecord.id()))
                .thenReturn(Optional.of(fixture.packageRecord));
        when(packages.listApprovals(fixture.packageRecord.id())).thenReturn(List.of(sellerApproval, buyerApproval));

        var buyerDetail = service.detail(context(fixture.buyerId, RequestedOperation.DEAL_RATIFICATION_PACKAGE_READ),
                fixture.dealId, fixture.packageRecord.id());
        assertEquals(List.of(fixture.buyerId, fixture.sellerId), buyerDetail.approvals().stream()
                .map(RatificationPackageReadDtos.Approval::legalEntityId).toList());
        assertEquals("Buyer", buyerDetail.approvals().get(0).legalName());
        assertEquals("APPROVED", buyerDetail.approvals().get(0).status());
        assertEquals(fixture.buyerUserId, buyerDetail.approvals().get(0).approverUserId());
        assertEquals(null, buyerDetail.approvals().get(1).approverUserId());
        assertFalse(buyerDetail.availableActions().canApprove());
        assertFalse(buyerDetail.availableActions().canReject());

        stubVisible(fixture, fixture.sellerId);
        var sellerDetail = service.detail(context(fixture.sellerId, RequestedOperation.DEAL_RATIFICATION_PACKAGE_READ),
                fixture.dealId, fixture.packageRecord.id());
        assertEquals(null, sellerDetail.approvals().get(0).approverUserId());
        assertEquals(fixture.sellerUserId, sellerDetail.approvals().get(1).approverUserId());
    }

    @Test
    void historyPreservesRepositoryOrderAndMutableWrapperDoesNotChangeSnapshotHash() throws Exception {
        Fixture fixture = fixture();
        var later = new RatificationRepository.PackageRecord(UUID.randomUUID(), fixture.dealId,
                UUID.randomUUID(), RatificationPackageStatus.RATIFIED, fixture.buyerId, fixture.sellerId,
                fixture.packageRecord.amountMinor(), fixture.packageRecord.currency(), fixture.createdAt.plusSeconds(1), 1,
                fixture.packageRecord.snapshotSchemaVersion(), fixture.packageRecord.canonicalSnapshot(), fixture.packageRecord.contentHash());
        stubVisible(fixture, fixture.buyerId);
        when(packages.listByDealId(fixture.dealId)).thenReturn(List.of(fixture.packageRecord, later));
        when(packages.listApprovals(fixture.packageRecord.id())).thenReturn(List.of());
        when(packages.listApprovals(later.id())).thenReturn(List.of(approvalFor(later.id(), fixture.sellerId, fixture.sellerUserId, 2)));

        var history = service.history(context(fixture.buyerId, RequestedOperation.DEAL_RATIFICATION_PACKAGE_HISTORY_READ),
                fixture.dealId);
        assertEquals(List.of(fixture.packageRecord.id(), later.id()), history.items().stream()
                .map(RatificationPackageReadDtos.Detail::id).toList());
        assertEquals(fixture.packageRecord.contentHash(), history.items().get(0).contentHash());
        assertEquals(fixture.packageRecord.contentHash(), history.items().get(1).contentHash());
        assertEquals(hasher.hash(fixture.packageRecord.canonicalSnapshot()), history.items().get(1).contentHash());
    }

    @Test
    void unifiesHiddenAndWrongDealPackageAsNotFoundAndRejectsWrongOperation() throws Exception {
        Fixture fixture = fixture();
        when(deals.findVisible(context(fixture.buyerId, RequestedOperation.DEAL_RATIFICATION_PACKAGE_READ), fixture.dealId))
                .thenReturn(Optional.empty());
        assertThrows(RatificationPackageReadService.PackageNotFound.class,
                () -> service.detail(context(fixture.buyerId, RequestedOperation.DEAL_RATIFICATION_PACKAGE_READ), fixture.dealId, fixture.packageRecord.id()));

        stubVisible(fixture, fixture.buyerId);
        when(packages.findByDealAndId(fixture.dealId, fixture.packageRecord.id())).thenReturn(Optional.empty());
        assertThrows(RatificationPackageReadService.PackageNotFound.class,
                () -> service.detail(context(fixture.buyerId, RequestedOperation.DEAL_RATIFICATION_PACKAGE_READ), fixture.dealId, fixture.packageRecord.id()));
        assertThrows(IllegalArgumentException.class,
                () -> service.detail(context(fixture.buyerId, RequestedOperation.DEAL_DETAIL_READ), fixture.dealId, fixture.packageRecord.id()));
    }

    @Test
    void failsClosedForCorruptHashWrapperOrOutsiderApproval() throws Exception {
        Fixture fixture = fixture();
        stubVisible(fixture, fixture.buyerId);
        when(packages.listApprovals(fixture.packageRecord.id())).thenReturn(List.of());
        var corruptHash = copy(fixture.packageRecord, "b".repeat(64), fixture.buyerId);
        when(packages.findByDealAndId(fixture.dealId, corruptHash.id())).thenReturn(Optional.of(corruptHash));
        assertThrows(RatificationPackageReadService.InvariantViolation.class,
                () -> service.detail(context(fixture.buyerId, RequestedOperation.DEAL_RATIFICATION_PACKAGE_READ), fixture.dealId, corruptHash.id()));

        var wrapperMismatch = copy(fixture.packageRecord, fixture.packageRecord.contentHash(), UUID.randomUUID());
        when(packages.findByDealAndId(fixture.dealId, wrapperMismatch.id())).thenReturn(Optional.of(wrapperMismatch));
        assertThrows(RatificationPackageReadService.InvariantViolation.class,
                () -> service.detail(context(fixture.buyerId, RequestedOperation.DEAL_RATIFICATION_PACKAGE_READ), fixture.dealId, wrapperMismatch.id()));

        when(packages.findByDealAndId(fixture.dealId, fixture.packageRecord.id())).thenReturn(Optional.of(fixture.packageRecord));
        when(packages.listApprovals(fixture.packageRecord.id())).thenReturn(List.of(
                approvalFor(fixture.packageRecord.id(), UUID.randomUUID(), UUID.randomUUID(), 1)));
        assertThrows(RatificationPackageReadService.InvariantViolation.class,
                () -> service.detail(context(fixture.buyerId, RequestedOperation.DEAL_RATIFICATION_PACKAGE_READ), fixture.dealId, fixture.packageRecord.id()));
    }

    private Fixture fixture() throws Exception {
        UUID dealId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID ruleSetId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-07-19T10:00:00Z");
        ObjectNode value = (ObjectNode) json.readTree("{\"type\":\"TEXT\",\"value\":\"value\"}");
        var target = new RatificationSourcePorts.Target(dealId, UUID.randomUUID(), "DRAFT", 1,
                "DL-0000000001", "Deal", true, new RatificationSourcePorts.Party(buyerId, "Buyer"),
                new RatificationSourcePorts.Party(sellerId, "Seller"), documentId, ruleSetId, null);
        var assembled = new RatificationSnapshotAssembler(json, hasher).assemble(target,
                new RatificationSourcePorts.Document(documentId, dealId, "v1", "a".repeat(64)),
                new RatificationSourcePorts.RuleSet(ruleSetId, dealId, 1, List.of(new RatificationSourcePorts.Rule(
                        "r1", "KEPT", "OTHER", "Rule", "Description", value, null, "EXTRACTED"))),
                99, "TRY");
        UUID packageId = UUID.randomUUID();
        var record = new RatificationRepository.PackageRecord(packageId, dealId, UUID.randomUUID(),
                RatificationPackageStatus.PENDING, buyerId, sellerId, 99, "TRY", createdAt, 0, 1,
                assembled.serializedSnapshot(), assembled.contentHash());
        return new Fixture(dealId, buyerId, sellerId, documentId, ruleSetId, createdAt, record,
                UUID.randomUUID(), UUID.randomUUID(), target);
    }

    private void stubVisible(Fixture fixture, UUID activeEntityId) {
        when(deals.findVisible(context(activeEntityId, RequestedOperation.DEAL_RATIFICATION_PACKAGE_READ), fixture.dealId))
                .thenReturn(Optional.of(fixture.target));
        when(deals.findVisible(context(activeEntityId, RequestedOperation.DEAL_RATIFICATION_PACKAGE_HISTORY_READ), fixture.dealId))
                .thenReturn(Optional.of(fixture.target));
    }

    private RatificationRepository.ApprovalRecord approval(Fixture fixture, UUID entityId, UUID userId, int seconds) {
        return approvalFor(fixture.packageRecord.id(), entityId, userId, seconds);
    }

    private RatificationRepository.ApprovalRecord approvalFor(UUID packageId, UUID entityId, UUID userId, int seconds) {
        return new RatificationRepository.ApprovalRecord(UUID.randomUUID(), packageId, entityId, userId,
                Instant.parse("2026-07-19T10:00:00Z").plusSeconds(seconds));
    }

    private RatificationRepository.PackageRecord copy(
            RatificationRepository.PackageRecord original, String hash, UUID buyerId) {
        return new RatificationRepository.PackageRecord(UUID.randomUUID(), original.dealId(), UUID.randomUUID(),
                original.status(), buyerId, original.sellerLegalEntityId(), original.amountMinor(), original.currency(),
                original.createdAt(), original.version(), original.snapshotSchemaVersion(), original.canonicalSnapshot(), hash);
    }

    private OperationContext context(UUID entityId, RequestedOperation operation) {
        return new OperationContext(USER_ID, TENANT_ID, entityId,
                com.m4trust.coreapi.organization.LegalEntityRole.ADMIN, operation);
    }

    private record Fixture(UUID dealId, UUID buyerId, UUID sellerId, UUID documentId, UUID ruleSetId,
            Instant createdAt, RatificationRepository.PackageRecord packageRecord, UUID buyerUserId,
            UUID sellerUserId, RatificationSourcePorts.Target target) { }
}
