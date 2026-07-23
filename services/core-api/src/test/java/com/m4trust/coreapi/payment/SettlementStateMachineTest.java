package com.m4trust.coreapi.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class SettlementStateMachineTest {

    private static final Instant NOW = Instant.parse("2026-07-23T10:00:00Z");

    @Test
    void settlementTransitionsFromReadyToProcessingAndTerminal() {
        Settlement settlement = Settlement.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), NOW);
        settlement.refreshReadiness(SettlementStatus.READY, NOW);
        settlement.beginRelease(1, NOW);
        assertEquals(SettlementStatus.PROCESSING, settlement.status());
        settlement.markSimulatedSettled(NOW);
        assertEquals(SettlementStatus.SIMULATED_SETTLED, settlement.status());
        assertTrue(settlement.status().terminal());
    }

    @Test
    void releaseOperationReachesSimulatedSettled() {
        ReleaseOperation operation = ReleaseOperation.create(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), NOW);
        operation.applySimulatedSettled("sandbox-ref", NOW);
        assertEquals(ReleaseOperationStatus.SIMULATED_SETTLED, operation.status());
        assertTrue(operation.terminal());
    }

    @Test
    void settlementRejectsReleaseWhenNotReady() {
        Settlement settlement = Settlement.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), NOW);
        assertThrows(IllegalStateException.class, () -> settlement.beginRelease(0, NOW));
    }
}
