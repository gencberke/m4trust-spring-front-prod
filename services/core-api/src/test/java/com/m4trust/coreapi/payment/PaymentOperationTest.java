package com.m4trust.coreapi.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Exact PaymentOperation state machine (ADR-010 §2.3): CREATED -> SUCCEEDED|DECLINED|UNCONFIRMED,
 * UNCONFIRMED -> SUCCEEDED|DECLINED; no other transition exists.
 */
class PaymentOperationTest {
    private static final Instant NOW = Instant.parse("2026-07-20T10:00:00Z");

    @Test
    void createdToUnconfirmedToSucceededIsAllowed() {
        PaymentOperation operation = PaymentOperation.create(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), NOW);
        operation.markUnconfirmed(NOW);
        assertEquals(PaymentOperationStatus.UNCONFIRMED, operation.status());
        assertTrue(operation.inFlight());
        assertFalse(operation.terminal());
        operation.applySucceeded("provider-ref", NOW);
        assertEquals(PaymentOperationStatus.SUCCEEDED, operation.status());
        assertTrue(operation.terminal());
        assertFalse(operation.inFlight());
    }

    @Test
    void createdToDeclinedIsTerminal() {
        PaymentOperation operation = PaymentOperation.create(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), NOW);
        operation.applyDeclined("provider-ref", NOW);
        assertEquals(PaymentOperationStatus.DECLINED, operation.status());
        assertTrue(operation.terminal());
        assertThrows(PaymentOperation.StateConflict.class, () -> operation.applySucceeded("x", NOW));
        assertThrows(PaymentOperation.StateConflict.class, () -> operation.markUnconfirmed(NOW));
    }

    @Test
    void terminalOperationsNeverReopen() {
        PaymentOperation succeeded = PaymentOperation.create(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), NOW);
        succeeded.applySucceeded("ref", NOW);
        assertThrows(PaymentOperation.StateConflict.class, () -> succeeded.applyDeclined("ref", NOW));
        assertThrows(PaymentOperation.StateConflict.class, () -> succeeded.markUnconfirmed(NOW));
    }

    @Test
    void unconfirmedCanStillResolveToDeclined() {
        PaymentOperation operation = PaymentOperation.create(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), NOW);
        operation.markUnconfirmed(NOW);
        operation.applyDeclined("ref", NOW);
        assertEquals(PaymentOperationStatus.DECLINED, operation.status());
    }

    @Test
    void providerKeyIsFixedForTheOperationsLifetime() {
        UUID providerKey = UUID.randomUUID();
        PaymentOperation operation = PaymentOperation.create(UUID.randomUUID(), UUID.randomUUID(),
                providerKey, NOW);
        operation.markUnconfirmed(NOW);
        operation.applySucceeded("ref", NOW);
        assertEquals(providerKey, operation.providerKey());
    }
}
