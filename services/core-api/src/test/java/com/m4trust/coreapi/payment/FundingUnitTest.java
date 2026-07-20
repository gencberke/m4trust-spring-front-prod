package com.m4trust.coreapi.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Exact FundingUnit state machine (ADR-010 §2.3): PLANNED -> PENDING -> FUNDED|FAILED, FAILED -> PENDING. */
class FundingUnitTest {
    private static final Instant NOW = Instant.parse("2026-07-20T10:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(60);

    @Test
    void plannedToPendingToFundedIsAllowed() {
        FundingUnit unit = FundingUnit.create(UUID.randomUUID(), UUID.randomUUID(), 1000, "TRY", NOW);
        unit.beginPayment(0, LATER);
        assertEquals(FundingUnitStatus.PENDING, unit.status());
        assertEquals(1, unit.version());
        unit.markFunded(1, LATER);
        assertEquals(FundingUnitStatus.FUNDED, unit.status());
        assertEquals(2, unit.version());
    }

    @Test
    void pendingToFailedThenRetryToPendingIsAllowed() {
        FundingUnit unit = FundingUnit.create(UUID.randomUUID(), UUID.randomUUID(), 1000, "TRY", NOW);
        unit.beginPayment(0, LATER);
        unit.markFailed(1, LATER);
        assertEquals(FundingUnitStatus.FAILED, unit.status());
        unit.beginPayment(2, LATER);
        assertEquals(FundingUnitStatus.PENDING, unit.status());
        assertEquals(3, unit.version());
    }

    @Test
    void fundedUnitRejectsANewPayment() {
        FundingUnit unit = FundingUnit.create(UUID.randomUUID(), UUID.randomUUID(), 1000, "TRY", NOW);
        unit.beginPayment(0, LATER);
        unit.markFunded(1, LATER);
        assertThrows(FundingUnit.StateConflict.class, () -> unit.beginPayment(2, LATER));
    }

    @Test
    void plannedUnitCannotBeMarkedFundedOrFailedDirectly() {
        FundingUnit unit = FundingUnit.create(UUID.randomUUID(), UUID.randomUUID(), 1000, "TRY", NOW);
        assertThrows(FundingUnit.StateConflict.class, () -> unit.markFunded(0, LATER));
        assertThrows(FundingUnit.StateConflict.class, () -> unit.markFailed(0, LATER));
    }

    @Test
    void staleExpectedVersionIsRejected() {
        FundingUnit unit = FundingUnit.create(UUID.randomUUID(), UUID.randomUUID(), 1000, "TRY", NOW);
        assertThrows(FundingUnit.StaleVersion.class, () -> unit.beginPayment(5, LATER));
    }

    @Test
    void negativeOrZeroAmountIsRejectedByTheAggregate() {
        assertThrows(IllegalArgumentException.class,
                () -> FundingUnit.create(UUID.randomUUID(), UUID.randomUUID(), 0, "TRY", NOW));
        assertThrows(IllegalArgumentException.class,
                () -> FundingUnit.create(UUID.randomUUID(), UUID.randomUUID(), -1, "TRY", NOW));
    }
}
