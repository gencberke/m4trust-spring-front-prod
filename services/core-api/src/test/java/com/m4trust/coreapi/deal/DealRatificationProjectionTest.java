package com.m4trust.coreapi.deal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.m4trust.coreapi.audit.AuditAppendPort;
import com.m4trust.coreapi.organization.InvitationLegalEntityQueryPort;
import com.m4trust.coreapi.organization.LegalEntityRole;
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
import com.m4trust.coreapi.ratification.RatificationPackageProjectionPort;
import com.m4trust.coreapi.ratification.RatificationSupersessionPort;
import org.junit.jupiter.api.Test;

/**
 * Deal detail's ratification projection: readiness derivation and the
 * actor-aware create/approve/reject action flags. canApproveRatification and
 * canRejectRatification are pure mirrors of the ratification-owned package
 * projection's own availableActions (already exhaustively actor-tested in
 * RatificationPackageReadServiceTest); this file focuses on the Deal-owned
 * inputs: readiness derivation and canCreateRatificationPackage's
 * initiator/DRAFT/READY gate, plus the ACTIVE-deal mutation lockout.
 */
class DealRatificationProjectionTest {
    private static final Instant NOW = Instant.parse("2026-07-19T18:00:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID INITIATOR = UUID.randomUUID();
    private static final UUID BUYER = UUID.randomUUID();
    private static final UUID SELLER = UUID.randomUUID();
    private static final UUID DOCUMENT = UUID.randomUUID();
    private static final UUID RULE_SET = UUID.randomUUID();
    private static final UUID PACKAGE = UUID.randomUUID();

    private final DealRepository repository = mock(DealRepository.class);
    private final DealOperationPolicy policy = new DealOperationPolicy();
    private final InvitationLegalEntityQueryPort legalEntities = mock(InvitationLegalEntityQueryPort.class);
    private final DealCurrentDocumentQueryPort documents = mock(DealCurrentDocumentQueryPort.class);
    private final DealAnalysisProjectionPort analysis = mock(DealAnalysisProjectionPort.class);
    private final DealRuleSetProjectionPort ruleSets = mock(DealRuleSetProjectionPort.class);
    private final RatificationPackageProjectionPort ratificationProjections =
            mock(RatificationPackageProjectionPort.class);
    private final RatificationSupersessionPort supersessions = mock(RatificationSupersessionPort.class);
    private final AuditAppendPort audit = mock(AuditAppendPort.class);
    private final DealService service = new DealService(
            repository, policy, legalEntities, documents, analysis, ruleSets,
            ratificationProjections, supersessions, audit, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void readinessRequiresPartiesAcceptedRuleSetAndCurrentDocumentTogether() {
        assertEquals("READY", readinessFor(BUYER, SELLER, DOCUMENT, RULE_SET));
        assertEquals("NOT_READY", readinessFor(null, SELLER, DOCUMENT, RULE_SET));
        assertEquals("NOT_READY", readinessFor(BUYER, null, DOCUMENT, RULE_SET));
        assertEquals("NOT_READY", readinessFor(BUYER, SELLER, null, RULE_SET));
        assertEquals("NOT_READY", readinessFor(BUYER, SELLER, DOCUMENT, null));
    }

    @Test
    void canCreateRatificationPackageRequiresInitiatorDraftAndReady() {
        assertTrue(canCreate(INITIATOR, DealStatus.DRAFT, BUYER, SELLER, DOCUMENT, RULE_SET));
        // Not the initiator, even though everything else is ready.
        assertFalse(canCreate(BUYER, DealStatus.DRAFT, BUYER, SELLER, DOCUMENT, RULE_SET));
        // Initiator, but not READY (no accepted rule-set).
        assertFalse(canCreate(INITIATOR, DealStatus.DRAFT, BUYER, SELLER, DOCUMENT, null));
        // Initiator and READY, but the Deal is no longer DRAFT.
        assertFalse(canCreate(INITIATOR, DealStatus.ACTIVE, BUYER, SELLER, DOCUMENT, RULE_SET));
    }

    @Test
    void approveAndRejectFlagsMirrorTheCurrentPackageProjectionExactly() {
        stubDeal(BUYER, DealStatus.DRAFT, BUYER, SELLER, DOCUMENT, RULE_SET, PACKAGE);
        stubParticipants();

        when(ratificationProjections.findCurrentPackage(any(), any(), any(), eq(PACKAGE)))
                .thenReturn(Optional.of(currentPackage(true, false)));
        DealDetail approvableOnly = service.get(context(RequestedOperation.DEAL_DETAIL_READ, BUYER), dealId(BUYER));
        assertTrue(approvableOnly.availableActions().canApproveRatification());
        assertFalse(approvableOnly.availableActions().canRejectRatification());

        when(ratificationProjections.findCurrentPackage(any(), any(), any(), eq(PACKAGE)))
                .thenReturn(Optional.of(currentPackage(false, true)));
        DealDetail rejectableOnly = service.get(context(RequestedOperation.DEAL_DETAIL_READ, BUYER), dealId(BUYER));
        assertFalse(rejectableOnly.availableActions().canApproveRatification());
        assertTrue(rejectableOnly.availableActions().canRejectRatification());
    }

    @Test
    void noCurrentPackageMeansNeitherApprovalActionIsAvailable() {
        stubDeal(BUYER, DealStatus.DRAFT, BUYER, SELLER, DOCUMENT, RULE_SET, null);
        stubParticipants();

        DealDetail detail = service.get(context(RequestedOperation.DEAL_DETAIL_READ, BUYER), dealId(BUYER));

        assertFalse(detail.availableActions().canApproveRatification());
        assertFalse(detail.availableActions().canRejectRatification());
        assertNull(detail.ratification().currentPackage());
    }

    @Test
    void activeDealExposesNoDraftMutationOrCreateActionsRegardlessOfActor() {
        stubDeal(INITIATOR, DealStatus.ACTIVE, BUYER, SELLER, DOCUMENT, RULE_SET, PACKAGE);
        stubParticipants();
        when(ratificationProjections.findCurrentPackage(any(), any(), any(), eq(PACKAGE)))
                .thenReturn(Optional.of(currentPackage(false, false)));

        DealDetail asInitiator = service.get(context(RequestedOperation.DEAL_DETAIL_READ, INITIATOR), dealId(INITIATOR));
        assertFalse(asInitiator.availableActions().canUpdate());
        assertFalse(asInitiator.availableActions().canCancel());
        assertFalse(asInitiator.availableActions().canManageParties());
        assertFalse(asInitiator.availableActions().canCreateDocumentUploadIntent());
        assertFalse(asInitiator.availableActions().canCreateRatificationPackage());

        // Even the buyer party (never the initiator here) sees no create
        // action either, since ACTIVE always fails the DRAFT gate.
        DealDetail asBuyer = service.get(context(RequestedOperation.DEAL_DETAIL_READ, BUYER), dealId(INITIATOR));
        assertFalse(asBuyer.availableActions().canCreateRatificationPackage());
        assertFalse(asBuyer.availableActions().canManageParties());
    }

    private String readinessFor(UUID buyer, UUID seller, UUID documentId, UUID ruleSetId) {
        stubDeal(INITIATOR, DealStatus.DRAFT, buyer, seller, documentId, ruleSetId, null);
        stubParticipants();
        DealDetail detail = service.get(context(RequestedOperation.DEAL_DETAIL_READ, INITIATOR), dealId(INITIATOR));
        return detail.ratification().readiness().name();
    }

    private boolean canCreate(UUID activeEntity, DealStatus status, UUID buyer, UUID seller,
            UUID documentId, UUID ruleSetId) {
        stubDeal(INITIATOR, status, buyer, seller, documentId, ruleSetId, null);
        stubParticipants();
        DealDetail detail = service.get(context(RequestedOperation.DEAL_DETAIL_READ, activeEntity), dealId(INITIATOR));
        return detail.availableActions().canCreateRatificationPackage();
    }

    private UUID dealId(UUID discriminator) {
        // Deterministic per-scenario id keeps repository stubbing simple across calls.
        return UUID.nameUUIDFromBytes(("deal-" + discriminator).getBytes());
    }

    private void stubDeal(UUID initiator, DealStatus status, UUID buyer, UUID seller,
            UUID documentId, UUID ruleSetId, UUID currentPackageId) {
        UUID id = dealId(initiator);
        DealRepository.DealRecord record = new DealRepository.DealRecord(
                id, TENANT, "DL-0000000001", "Deal", null, status,
                buyer, seller, documentId, ruleSetId, currentPackageId,
                initiator, USER, NOW.minusSeconds(60), NOW.minusSeconds(60), 5);
        when(repository.findVisibleById(eq(TENANT), any(), eq(id))).thenReturn(Optional.of(record));
        if (documentId != null) {
            when(documents.findAvailable(documentId)).thenReturn(Optional.of(
                    new DealCurrentDocumentQueryPort.CurrentDealDocument(documentId, id, "contract.pdf",
                            "application/pdf", "AVAILABLE", 10, "a".repeat(64), "v1", NOW, NOW,
                            new DealCurrentDocumentQueryPort.CurrentDealDocumentActions(false, true))));
            when(analysis.summary(documentId)).thenReturn(new DealAnalysisProjectionPort.AnalysisSummary(
                    documentId, "ACCEPTED", NOW, NOW, NOW, null, null));
        }
        if (ruleSetId != null) {
            when(ruleSets.findCurrent(ruleSetId)).thenReturn(Optional.of(
                    new DealRuleSetProjectionPort.CurrentRuleSet(ruleSetId, 1, UUID.randomUUID(),
                            UUID.randomUUID(), NOW, USER, null, 0)));
        }
    }

    private void stubParticipants() {
        when(repository.findParticipants(any())).thenReturn(List.of(
                new DealRepository.ParticipantRecord(BUYER, TENANT, NOW),
                new DealRepository.ParticipantRecord(SELLER, TENANT, NOW),
                new DealRepository.ParticipantRecord(INITIATOR, TENANT, NOW)));
        when(legalEntities.findLegalNames(any())).thenReturn(Map.of(
                BUYER, "Buyer", SELLER, "Seller", INITIATOR, "Initiator"));
    }

    private RatificationPackageProjectionPort.CurrentPackage currentPackage(boolean canApprove, boolean canReject) {
        return new RatificationPackageProjectionPort.CurrentPackage(
                PACKAGE, 0, "PENDING", "a".repeat(64),
                new RatificationPackageProjectionPort.Snapshot(1, "deal", "DL-1", "Deal",
                        new RatificationPackageProjectionPort.Party(BUYER.toString(), "Buyer"),
                        new RatificationPackageProjectionPort.Party(SELLER.toString(), "Seller"),
                        new RatificationPackageProjectionPort.RuleSet(RULE_SET.toString(), 1, List.of()),
                        new RatificationPackageProjectionPort.Terms(1, "TRY"),
                        new RatificationPackageProjectionPort.Document(DOCUMENT.toString(), "v1", "a".repeat(64))),
                List.of(),
                new RatificationPackageProjectionPort.AvailableActions(canApprove, canReject),
                NOW);
    }

    private static OperationContext context(RequestedOperation operation, UUID activeEntity) {
        return new OperationContext(USER, TENANT, activeEntity, LegalEntityRole.ADMIN, operation);
    }
}
