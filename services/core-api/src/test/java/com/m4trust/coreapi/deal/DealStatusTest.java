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
        assertEquals(DealStatus.CANCELLED, DealStatus.ACTIVE.cancel());
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
    }

    @Test
    void basicFieldsAreEditableOnlyWhileDraftOrActive() {
        assertTrue(DealStatus.DRAFT.allowsBasicFieldEditing());
        assertTrue(DealStatus.ACTIVE.allowsBasicFieldEditing());
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
        assertEquals(DealLifecycleProjection.DRAFT,
                DealLifecycleProjectionCalculator.calculate(
                        DealStatus.ACTIVE));
    }
}
