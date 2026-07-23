package com.m4trust.coreapi.fulfillment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class FulfillmentStatusTransitionTest {

    private static final Instant NOW = Instant.parse("2026-07-20T12:00:00Z");

    @Test
    void fulfillmentTransitionsToReviewRequiredAndBackToEvidenceRequired() {
        Fulfillment fulfillment = Fulfillment.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), EvidencePolicy.REQUIRED, NOW);
        assertEquals(FulfillmentStatus.IN_PROGRESS, fulfillment.status());
        fulfillment.moveToEvidenceRequired(NOW);
        fulfillment.moveToReviewRequired(NOW);
        assertEquals(FulfillmentStatus.REVIEW_REQUIRED, fulfillment.status());
        fulfillment.returnToEvidenceRequired(NOW);
        assertEquals(FulfillmentStatus.EVIDENCE_REQUIRED, fulfillment.status());
    }

    @Test
    void milestoneAcceptMovesEverythingToCompleted() {
        Fulfillment fulfillment = Fulfillment.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), EvidencePolicy.REQUIRED, NOW);
        Milestone milestone = Milestone.create(
                UUID.randomUUID(), fulfillment.id(), fulfillment.dealId(),
                "Primary", null, NOW);
        fulfillment.moveToEvidenceRequired(NOW);
        fulfillment.moveToReviewRequired(NOW);
        milestone.moveToEvidenceRequired(NOW);
        milestone.moveToReviewRequired(NOW);
        milestone.moveToCompleted(NOW);
        fulfillment.moveToCompleted(NOW);
        assertEquals(FulfillmentStatus.COMPLETED, fulfillment.status());
        assertEquals(FulfillmentStatus.COMPLETED, milestone.status());
    }

    @Test
    void notRequiredPathCompletesFromInProgress() {
        Fulfillment fulfillment = Fulfillment.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), EvidencePolicy.NOT_REQUIRED, NOW);
        Milestone milestone = Milestone.create(
                UUID.randomUUID(), fulfillment.id(), fulfillment.dealId(),
                "Primary", null, NOW);
        milestone.moveToCompleted(NOW);
        fulfillment.moveToCompleted(NOW);
        assertEquals(FulfillmentStatus.COMPLETED, fulfillment.status());
        assertEquals(FulfillmentStatus.COMPLETED, milestone.status());
        assertEquals(NOW, fulfillment.completedAt());
    }

    @Test
    void cannotCompleteMilestoneFromEvidenceRequired() {
        Milestone milestone = Milestone.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Primary", null, NOW);
        milestone.moveToEvidenceRequired(NOW);
        assertThrows(IllegalStateException.class,
                () -> milestone.moveToCompleted(NOW));
    }
}
