package com.m4trust.coreapi.deal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DealStatusTest {

    @Test
    void allAllowedTransitionsFollowTheAuthoritativeLifecycle() {
        assertEquals(DealStatus.ACTIVE, DealStatus.DRAFT.activate());
        assertEquals(DealStatus.CANCELLED, DealStatus.DRAFT.cancel());
        assertEquals(DealStatus.COMPLETED, DealStatus.ACTIVE.complete());
        assertEquals(DealStatus.ARCHIVED, DealStatus.COMPLETED.archive());
        assertEquals(DealStatus.ARCHIVED, DealStatus.CANCELLED.archive());
    }

    @Test
    void forbiddenReactivationAndArchivedMutationAreRejected() {
        assertThrows(DealStateConflictException.class,
                () -> DealStatus.CANCELLED.activate());
        assertThrows(DealStateConflictException.class,
                () -> DealStatus.COMPLETED.activate());
        assertThrows(DealStateConflictException.class,
                () -> DealStatus.ARCHIVED.cancel());
        assertThrows(DealStateConflictException.class,
                () -> DealStatus.ACTIVE.cancel());
    }

    @Test
    void allDirectDealMutationsAreDraftOnly() {
        assertTrue(DealStatus.DRAFT.allowsBasicFieldEditing());
        assertTrue(DealStatus.DRAFT.allowsCancellation());
        assertTrue(DealStatus.DRAFT.allowsDocumentUpload());
        assertFalse(DealStatus.ACTIVE.allowsBasicFieldEditing());
        assertFalse(DealStatus.ACTIVE.allowsCancellation());
        assertFalse(DealStatus.ACTIVE.allowsDocumentUpload());
        assertFalse(DealStatus.CANCELLED.allowsBasicFieldEditing());
        assertFalse(DealStatus.COMPLETED.allowsBasicFieldEditing());
        assertFalse(DealStatus.ARCHIVED.allowsBasicFieldEditing());
        assertThrows(DealStateConflictException.class,
                DealStatus.CANCELLED::requireBasicFieldEditingAllowed);
    }

    @Test
    void lifecycleProjectionUsesTheCurrentAuthoritativeInputs() {
        assertEquals(DealLifecycleProjection.ARCHIVED,
                DealLifecycleProjectionCalculator.calculate(
                        DealStatus.ARCHIVED));
        assertEquals(DealLifecycleProjection.CANCELLED,
                DealLifecycleProjectionCalculator.calculate(
                        DealStatus.CANCELLED));
        assertEquals(DealLifecycleProjection.COMPLETED,
                DealLifecycleProjectionCalculator.calculate(
                        DealStatus.COMPLETED));
        assertEquals(DealLifecycleProjection.DRAFT,
                DealLifecycleProjectionCalculator.calculate(
                        DealStatus.DRAFT));
        assertEquals(DealLifecycleProjection.FUNDING,
                DealLifecycleProjectionCalculator.calculate(
                        DealStatus.ACTIVE));
    }

    @Test
    void activeDealsAlwaysShowTheFundingViewRegardlessOfAnalysisStatus() {
        assertEquals(DealLifecycleProjection.FUNDING,
                DealLifecycleProjectionCalculator.calculate(
                        DealStatus.ACTIVE, "ACCEPTED", true));
        assertEquals(DealLifecycleProjection.FUNDING,
                DealLifecycleProjectionCalculator.calculate(
                        DealStatus.ACTIVE, "NOT_REQUESTED", false));
        assertEquals(DealLifecycleProjection.RATIFICATION,
                DealLifecycleProjectionCalculator.calculate(
                        DealStatus.DRAFT, "ACCEPTED", true));
    }

    @Test
    void activeDisputeOverridesFulfillmentLifecycleForPartyActors() {
        assertEquals(DealLifecycleProjection.DISPUTE,
                DealLifecycleProjectionCalculator.calculate(
                        DealStatus.ACTIVE, "ACCEPTED", true, "FUNDED", true));
        assertEquals(DealLifecycleProjection.FULFILLMENT,
                DealLifecycleProjectionCalculator.calculate(
                        DealStatus.ACTIVE, "ACCEPTED", true, "FUNDED", false));
        assertEquals(DealLifecycleProjection.DISPUTE,
                DealLifecycleProjectionCalculator.calculate(
                        DealStatus.ACTIVE, "ACCEPTED", true, "FUNDED", true, "COMPLETED"));
    }

    @Test
    void fundedActiveDealsAdvanceToFulfillment() {
        assertEquals(DealLifecycleProjection.FULFILLMENT,
                DealLifecycleProjectionCalculator.calculate(
                        DealStatus.ACTIVE, "ACCEPTED", true, "FUNDED"));
        assertEquals(DealLifecycleProjection.FUNDING,
                DealLifecycleProjectionCalculator.calculate(
                        DealStatus.ACTIVE, "ACCEPTED", true, "PENDING"));
    }

    @Test
    void fundedActiveDealsAdvanceToSettlementWhenFulfillmentCompleted() {
        assertEquals(DealLifecycleProjection.SETTLEMENT,
                DealLifecycleProjectionCalculator.calculate(
                        DealStatus.ACTIVE, "ACCEPTED", true, "FUNDED", false, "COMPLETED"));
        assertEquals(DealLifecycleProjection.FULFILLMENT,
                DealLifecycleProjectionCalculator.calculate(
                        DealStatus.ACTIVE, "ACCEPTED", true, "FUNDED", false, "NOT_STARTED"));
        assertEquals(DealLifecycleProjection.FULFILLMENT,
                DealLifecycleProjectionCalculator.calculate(
                        DealStatus.ACTIVE, "ACCEPTED", true, "FUNDED", false, "IN_PROGRESS"));
        assertEquals(DealLifecycleProjection.FULFILLMENT,
                DealLifecycleProjectionCalculator.calculate(
                        DealStatus.ACTIVE, "ACCEPTED", true, "FUNDED", false, "EVIDENCE_REQUIRED"));
        assertEquals(DealLifecycleProjection.FULFILLMENT,
                DealLifecycleProjectionCalculator.calculate(
                        DealStatus.ACTIVE, "ACCEPTED", true, "FUNDED", false, "REVIEW_REQUIRED"));
    }

    @Test
    void terminalDealStatusesWinBeforeActiveStageLogic() {
        assertEquals(DealLifecycleProjection.COMPLETED,
                DealLifecycleProjectionCalculator.calculate(
                        DealStatus.COMPLETED, "ACCEPTED", true, "FUNDED", false, "COMPLETED"));
        assertEquals(DealLifecycleProjection.CANCELLED,
                DealLifecycleProjectionCalculator.calculate(
                        DealStatus.CANCELLED, "ACCEPTED", true, "FUNDED", false, "COMPLETED"));
        assertEquals(DealLifecycleProjection.ARCHIVED,
                DealLifecycleProjectionCalculator.calculate(
                        DealStatus.ARCHIVED, "ACCEPTED", true, "FUNDED", false, "COMPLETED"));
    }

    @Test
    void unknownFulfillmentStatusFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> DealLifecycleProjectionCalculator.calculate(
                        DealStatus.ACTIVE, "ACCEPTED", true, "FUNDED", false, "UNKNOWN"));
    }
}
