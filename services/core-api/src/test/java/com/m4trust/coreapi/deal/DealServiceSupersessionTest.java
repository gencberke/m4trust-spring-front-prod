package com.m4trust.coreapi.deal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.m4trust.coreapi.audit.AuditAppendPort;
import com.m4trust.coreapi.casework.CaseworkDealProjectionPort;
import com.m4trust.coreapi.fulfillment.FulfillmentProjectionPort;
import com.m4trust.coreapi.organization.InvitationLegalEntityQueryPort;
import com.m4trust.coreapi.organization.LegalEntityRole;
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
import com.m4trust.coreapi.payment.FundingProjectionPort;
import com.m4trust.coreapi.ratification.RatificationPackageProjectionPort;
import com.m4trust.coreapi.ratification.RatificationSupersessionPort;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class DealServiceSupersessionTest {
    private static final Instant NOW = Instant.parse("2026-07-19T16:00:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ENTITY = UUID.randomUUID();
    private static final UUID BUYER = UUID.randomUUID();
    private static final UUID SELLER = UUID.randomUUID();
    private static final UUID REPLACEMENT_SELLER = UUID.randomUUID();

    private final DealRepository repository = mock(DealRepository.class);
    private final DealOperationPolicy policy = mock(DealOperationPolicy.class);
    private final InvitationLegalEntityQueryPort legalEntities =
            mock(InvitationLegalEntityQueryPort.class);
    private final DealCurrentDocumentQueryPort documents = mock(DealCurrentDocumentQueryPort.class);
    private final DealAnalysisProjectionPort analysis = mock(DealAnalysisProjectionPort.class);
    private final DealRuleSetProjectionPort ruleSets = mock(DealRuleSetProjectionPort.class);
    private final RatificationPackageProjectionPort ratificationProjections =
            mock(RatificationPackageProjectionPort.class);
    private final RatificationSupersessionPort supersessions =
            mock(RatificationSupersessionPort.class);
    private final FundingProjectionPort fundingProjections = mock(FundingProjectionPort.class);
    private final FulfillmentProjectionPort fulfillmentProjections = mock(FulfillmentProjectionPort.class);
    private final CaseworkDealProjectionPort caseworkProjections = mock(CaseworkDealProjectionPort.class);
    private final AuditAppendPort audit = mock(AuditAppendPort.class);
    private final DealService service = new DealService(
            repository, policy, legalEntities, documents, analysis, ruleSets,
            ratificationProjections, supersessions, fundingProjections, fulfillmentProjections,
            caseworkProjections, audit, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void titleChangeLocksDealThenSupersedesBeforePersistingTheDeal() {
        UUID dealId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        OperationContext context = context(RequestedOperation.DEAL_UPDATE);
        stubDeal(context, record(dealId, packageId, "Original", "description", BUYER, SELLER));
        when(repository.updateBasicFields(any(), any(), any(), any(Long.class),
                any(), any(), any())).thenReturn(true);
        UpdateDealRequest request = update("Changed", "description");

        service.update(context, dealId, request, UUID.randomUUID());

        InOrder order = inOrder(repository, supersessions);
        order.verify(repository).findVisibleByIdForUpdate(TENANT, ENTITY, dealId);
        order.verify(supersessions).supersedePending(
                eq(context), eq(dealId), eq(packageId), any(), eq(NOW));
        order.verify(repository).updateBasicFields(
                eq(TENANT), eq(ENTITY), eq(dealId), eq(3L),
                eq("Changed"), eq("description"), eq(NOW));
    }

    @Test
    void descriptionOnlyAndExactTitleUpdatesDoNotSupersede() {
        UUID dealId = UUID.randomUUID();
        OperationContext context = context(RequestedOperation.DEAL_UPDATE);
        stubDeal(context, record(
                dealId, UUID.randomUUID(), "Original", "description", BUYER, SELLER));
        when(repository.updateBasicFields(any(), any(), any(), any(Long.class),
                any(), any(), any())).thenReturn(true);

        service.update(context, dealId, update("Original", "new description"), UUID.randomUUID());

        verify(supersessions, never()).supersedePending(any(), any(), any(), any(), any());
    }

    @Test
    void changedAssignmentsSupersedeButExactAssignmentsDoNot() {
        UUID changedDealId = UUID.randomUUID();
        UUID unchangedDealId = UUID.randomUUID();
        UUID changedPackage = UUID.randomUUID();
        UUID unchangedPackage = UUID.randomUUID();
        OperationContext context = context(RequestedOperation.DEAL_PARTIES_UPDATE);
        when(repository.findVisibleByIdForUpdate(TENANT, ENTITY, changedDealId))
                .thenReturn(Optional.of(record(
                        changedDealId, changedPackage, "Deal", null, BUYER, SELLER)));
        when(repository.findVisibleByIdForUpdate(TENANT, ENTITY, unchangedDealId))
                .thenReturn(Optional.of(record(
                        unchangedDealId, unchangedPackage, "Deal", null, BUYER, SELLER)));
        stubParticipants(changedDealId);
        stubParticipants(unchangedDealId);
        stubRatificationProjection();
        stubFundingProjection();
        when(policy.availableActions(any(), eq(context))).thenReturn(new DealAvailableActions(
                true, true, true, true, true, false, false, false, false, false, false, false, false,
                false, false, false, false, false));
        when(policy.isInitiator(any(), eq(context))).thenReturn(true);
        when(repository.updateParties(any(), any(), any(), any(Long.class),
                any(), any(), any())).thenReturn(true);

        service.updateParties(context, changedDealId,
                parties(BUYER, REPLACEMENT_SELLER), UUID.randomUUID());
        service.updateParties(context, unchangedDealId,
                parties(BUYER, SELLER), UUID.randomUUID());

        verify(supersessions).supersedePending(
                eq(context), eq(changedDealId), eq(changedPackage), any(), eq(NOW));
        verify(supersessions, never()).supersedePending(
                eq(context), eq(unchangedDealId), eq(unchangedPackage), any(), any());
    }

    @Test
    void withdrawalLocksDealSupersedesCurrentPackageThenCancels() {
        UUID dealId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        OperationContext context = context(RequestedOperation.DEAL_CANCEL);
        stubDeal(context, record(dealId, packageId, "Deal", null, BUYER, SELLER));
        when(repository.updateStatus(any(), any(), any(), any(), any(), any(Long.class), any()))
                .thenReturn(true);

        service.cancel(context, dealId, UUID.randomUUID());

        InOrder order = inOrder(repository, supersessions);
        order.verify(repository).findVisibleByIdForUpdate(TENANT, ENTITY, dealId);
        order.verify(supersessions).supersedePending(
                eq(context), eq(dealId), eq(packageId), any(), eq(NOW));
        order.verify(repository).updateStatus(
                TENANT, ENTITY, dealId, DealStatus.DRAFT, DealStatus.CANCELLED, 3, NOW);
    }

    private void stubDeal(OperationContext context, DealRepository.DealRecord record) {
        when(repository.findVisibleByIdForUpdate(
                context.tenantId(), context.activeLegalEntityId(), record.id()))
                .thenReturn(Optional.of(record));
        stubParticipants(record.id());
        stubRatificationProjection();
        stubFundingProjection();
        when(policy.availableActions(any(), eq(context))).thenReturn(new DealAvailableActions(
                true, true, true, true, true, false, false, false, false, false, false, false, false,
                false, false, false, false, false));
        when(policy.isInitiator(any(), eq(context))).thenReturn(true);
    }

    private void stubFundingProjection() {
        when(fundingProjections.summarize(any(), anyBoolean(), anyBoolean()))
                .thenReturn(new FundingProjectionPort.Summary("NOT_CONFIGURED", null, null, null,
                        false, false, false));
    }

    {
        when(fulfillmentProjections.summarize(any())).thenReturn(null);
        when(caseworkProjections.forActor(any())).thenReturn(CaseworkDealProjectionPort.ActorSummary.hidden());
    }

    /** The current-package pointer is always set in these fixtures, so the
     * projection port must resolve it or DealService's invariant check throws. */
    private void stubRatificationProjection() {
        when(ratificationProjections.findCurrentPackage(any(), any(), any(), any()))
                .thenReturn(Optional.of(new RatificationPackageProjectionPort.CurrentPackage(
                        UUID.randomUUID(), 0, "PENDING", "a".repeat(64),
                        new RatificationPackageProjectionPort.Snapshot(1, "deal", "DL-1", "Deal",
                                new RatificationPackageProjectionPort.Party(BUYER.toString(), "Buyer"),
                                new RatificationPackageProjectionPort.Party(SELLER.toString(), "Seller"),
                                new RatificationPackageProjectionPort.RuleSet(UUID.randomUUID().toString(), 1, List.of()),
                                new RatificationPackageProjectionPort.Terms(1, "TRY"),
                                new RatificationPackageProjectionPort.Document(UUID.randomUUID().toString(), "v1", "a".repeat(64))),
                        List.of(),
                        new RatificationPackageProjectionPort.AvailableActions(false, false),
                        NOW)));
    }

    private void stubParticipants(UUID dealId) {
        when(repository.findParticipants(dealId)).thenReturn(List.of(
                new DealRepository.ParticipantRecord(BUYER, TENANT, NOW),
                new DealRepository.ParticipantRecord(SELLER, TENANT, NOW),
                new DealRepository.ParticipantRecord(REPLACEMENT_SELLER, TENANT, NOW)));
        when(legalEntities.findLegalNames(any())).thenReturn(Map.of(
                BUYER, "Buyer", SELLER, "Seller", REPLACEMENT_SELLER, "Replacement"));
    }

    private static DealRepository.DealRecord record(
            UUID dealId,
            UUID packageId,
            String title,
            String description,
            UUID buyer,
            UUID seller) {
        return new DealRepository.DealRecord(
                dealId, TENANT, "DL-0000000001", title, description, DealStatus.DRAFT,
                buyer, seller, null, null, packageId, ENTITY, USER, NOW.minusSeconds(60),
                NOW.minusSeconds(60), 3);
    }

    private static UpdateDealRequest update(String title, String description) {
        UpdateDealRequest request = new UpdateDealRequest();
        request.setTitle(title);
        request.setDescription(description);
        request.setExpectedVersion(3L);
        return request;
    }

    private static UpdateDealPartiesRequest parties(UUID buyer, UUID seller) {
        UpdateDealPartiesRequest request = new UpdateDealPartiesRequest();
        request.setBuyerLegalEntityId(buyer);
        request.setSellerLegalEntityId(seller);
        request.setExpectedVersion(3L);
        return request;
    }

    private static OperationContext context(RequestedOperation operation) {
        return new OperationContext(USER, TENANT, ENTITY, LegalEntityRole.ADMIN, operation);
    }
}
