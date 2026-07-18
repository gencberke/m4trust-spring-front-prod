package com.m4trust.coreapi.deal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import com.m4trust.coreapi.deal.DealRepository.DealRecord;
import org.junit.jupiter.api.Test;

class DealPartyAssignmentTest {

    @Test
    void domainRejectsEqualPartiesDuringRehydrationAndMutation() {
        UUID partyId = UUID.randomUUID();
        DealRecord invalidRecord = new DealRecord(
                UUID.randomUUID(), UUID.randomUUID(), "DL-0000000001",
                "Deal", null, DealStatus.DRAFT, partyId, partyId,
                UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-07-18T10:00:00Z"),
                Instant.parse("2026-07-18T10:00:00Z"), 0);
        assertThrows(IllegalArgumentException.class,
                () -> Deal.rehydrate(invalidRecord));

        Deal deal = Deal.create(
                UUID.randomUUID(), UUID.randomUUID(), "DL-0000000002", "Deal",
                null, UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-07-18T10:00:00Z"));
        assertThrows(IllegalArgumentException.class,
                () -> deal.assignParties(partyId, partyId));
        assertDoesNotThrow(() -> deal.assignParties(partyId, null));
    }
}
