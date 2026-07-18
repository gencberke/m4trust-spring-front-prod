package com.m4trust.coreapi.deal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import com.m4trust.coreapi.deal.DealRepository.DealRecord;
import org.junit.jupiter.api.Test;

class DealPartyAssignmentTest {

    @Test
    void domainRejectsEqualPartiesDuringRehydration() {
        UUID partyId = UUID.randomUUID();
        DealRecord invalidRecord = new DealRecord(
                UUID.randomUUID(), UUID.randomUUID(), "DL-0000000001",
                "Deal", null, DealStatus.DRAFT, partyId, partyId,
                null,
                UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-07-18T10:00:00Z"),
                Instant.parse("2026-07-18T10:00:00Z"), 0);
        assertThrows(IllegalArgumentException.class,
                () -> Deal.rehydrate(invalidRecord));

    }

    @Test
    void partyAssignmentUpdatesDraftPartiesVersionAndTimestampTogether() {
        Deal deal = newDraftDeal();
        UUID buyerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        Instant changedAt = Instant.parse("2026-07-18T10:01:00Z");

        assertDoesNotThrow(() -> deal.assignParties(buyerId, sellerId, 0,
                changedAt));

        assertEquals(buyerId, deal.buyerLegalEntityId());
        assertEquals(sellerId, deal.sellerLegalEntityId());
        assertEquals(changedAt, deal.updatedAt());
        assertEquals(1, deal.version());
    }

    @Test
    void failedPartyAssignmentsLeaveTheAggregateUnchanged() {
        Deal equalPartyDeal = newDraftDeal();
        Instant originalUpdatedAt = equalPartyDeal.updatedAt();
        UUID partyId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> equalPartyDeal.assignParties(partyId, partyId, 0,
                        Instant.parse("2026-07-18T10:01:00Z")));
        assertDealState(equalPartyDeal, null, null, originalUpdatedAt, 0);

        Deal staleVersionDeal = newDraftDeal();
        UUID originalBuyerId = UUID.randomUUID();
        Instant assignedAt = Instant.parse("2026-07-18T10:01:00Z");
        staleVersionDeal.assignParties(originalBuyerId, null, 0, assignedAt);
        assertThrows(DealStaleVersionException.class,
                () -> staleVersionDeal.assignParties(UUID.randomUUID(),
                        UUID.randomUUID(), 0,
                        Instant.parse("2026-07-18T10:02:00Z")));
        assertDealState(staleVersionDeal, originalBuyerId, null, assignedAt, 1);

        UUID activeBuyerId = UUID.randomUUID();
        UUID activeSellerId = UUID.randomUUID();
        Instant activeUpdatedAt = Instant.parse("2026-07-18T10:03:00Z");
        Deal activeDeal = Deal.rehydrate(new DealRecord(
                UUID.randomUUID(), UUID.randomUUID(), "DL-0000000002",
                "Deal", null, DealStatus.ACTIVE, activeBuyerId, activeSellerId,
                null,
                UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-07-18T10:00:00Z"), activeUpdatedAt, 4));
        assertThrows(DealStateConflictException.class,
                () -> activeDeal.assignParties(UUID.randomUUID(),
                        UUID.randomUUID(), 4,
                        Instant.parse("2026-07-18T10:04:00Z")));
        assertDealState(activeDeal, activeBuyerId, activeSellerId,
                activeUpdatedAt, 4);
    }

    private Deal newDraftDeal() {
        return Deal.create(
                UUID.randomUUID(), UUID.randomUUID(), "DL-0000000002", "Deal",
                null, UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-07-18T10:00:00Z"));
    }

    private void assertDealState(Deal deal, UUID buyerId, UUID sellerId,
            Instant updatedAt, long version) {
        assertEquals(buyerId, deal.buyerLegalEntityId());
        assertEquals(sellerId, deal.sellerLegalEntityId());
        assertEquals(updatedAt, deal.updatedAt());
        assertEquals(version, deal.version());
    }
}
