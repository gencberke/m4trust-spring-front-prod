package com.m4trust.coreapi.deal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class DealRatificationActivationTest {
    private static final Instant CREATED_AT = Instant.parse("2026-07-19T10:00:00Z");
    private static final Instant ACTIVATED_AT = Instant.parse("2026-07-19T11:00:00Z");

    @Test
    void activatesOnlyTheCurrentPackageOnADraftAndBumpsVersionExactlyOnce() {
        UUID currentPackageId = UUID.randomUUID();
        Deal deal = Deal.rehydrate(record(DealStatus.DRAFT, currentPackageId, 7));

        deal.activateCurrentRatificationPackage(currentPackageId, ACTIVATED_AT);

        assertEquals(DealStatus.ACTIVE, deal.status());
        assertEquals(currentPackageId, deal.currentRatificationPackageId());
        assertEquals(ACTIVATED_AT, deal.updatedAt());
        assertEquals(8, deal.version());
    }

    @Test
    void rejectsAFormerOrUnknownPackageWithoutChangingTheDraft() {
        UUID currentPackageId = UUID.randomUUID();
        Deal deal = Deal.rehydrate(record(DealStatus.DRAFT, currentPackageId, 7));

        assertThrows(DealStateConflictException.class,
                () -> deal.activateCurrentRatificationPackage(UUID.randomUUID(), ACTIVATED_AT));

        assertEquals(DealStatus.DRAFT, deal.status());
        assertEquals(CREATED_AT, deal.updatedAt());
        assertEquals(7, deal.version());
    }

    @Test
    void rejectsActivationUnlessTheDealIsDraft() {
        UUID currentPackageId = UUID.randomUUID();
        Deal deal = Deal.rehydrate(record(DealStatus.ACTIVE, currentPackageId, 8));

        assertThrows(DealStateConflictException.class,
                () -> deal.activateCurrentRatificationPackage(currentPackageId, ACTIVATED_AT));

        assertEquals(DealStatus.ACTIVE, deal.status());
        assertEquals(8, deal.version());
    }

    private static DealRepository.DealRecord record(
            DealStatus status,
            UUID currentPackageId,
            long version) {
        return new DealRepository.DealRecord(
                UUID.randomUUID(), UUID.randomUUID(), "DL-0000000001", "Deal", null,
                status, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                currentPackageId, UUID.randomUUID(), UUID.randomUUID(), CREATED_AT, CREATED_AT, version);
    }
}
