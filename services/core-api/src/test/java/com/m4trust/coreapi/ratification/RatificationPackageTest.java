package com.m4trust.coreapi.ratification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RatificationPackageTest {
    @Test
    void pendingPackageTransitionsOnceToEachAllowedTerminalState() {
        RatificationPackage rejected = pending();
        rejected.reject(0);
        assertEquals(RatificationPackageStatus.REJECTED, rejected.status());
        assertEquals(1, rejected.version());

        RatificationPackage ratified = pending();
        ratified.ratify(0);
        assertEquals(RatificationPackageStatus.RATIFIED, ratified.status());

        RatificationPackage superseded = pending();
        superseded.supersede(0);
        assertEquals(RatificationPackageStatus.SUPERSEDED, superseded.status());
    }

    @Test
    void distinguishesStaleVersionFromNonPendingStateAndRejectsUnsafeVersion() {
        RatificationPackage packageRecord = pending();
        assertThrows(RatificationPackage.StaleVersion.class, () -> packageRecord.ratify(1));
        packageRecord.ratify(0);
        assertThrows(RatificationPackage.StateConflict.class, () -> packageRecord.reject(1));

        var record = new RatificationRepository.PackageRecord(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), RatificationPackageStatus.PENDING, UUID.randomUUID(), UUID.randomUUID(),
                1, "TRY", Instant.parse("2026-07-19T10:00:00Z"), RatificationPackage.MAX_SAFE_INTEGER);
        RatificationPackage upperBound = RatificationPackage.rehydrate(record);
        assertThrows(IllegalStateException.class,
                () -> upperBound.supersede(RatificationPackage.MAX_SAFE_INTEGER));
    }

    @Test
    void approvalAdvancesVersionWithoutChangingPendingStatus() {
        RatificationPackage packageState = pending();
        assertThrows(RatificationPackage.StaleVersion.class,
                () -> packageState.approve(1));
        packageState.approve(0);
        assertEquals(RatificationPackageStatus.PENDING, packageState.status());
        assertEquals(1, packageState.version());

        packageState.reject(1);
        assertThrows(RatificationPackage.StateConflict.class,
                () -> packageState.approve(2));

        var record = new RatificationRepository.PackageRecord(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), RatificationPackageStatus.PENDING, UUID.randomUUID(), UUID.randomUUID(),
                1, "TRY", Instant.parse("2026-07-19T10:00:00Z"),
                RatificationPackage.MAX_SAFE_INTEGER);
        RatificationPackage upperBound = RatificationPackage.rehydrate(record);
        assertThrows(IllegalStateException.class,
                () -> upperBound.approve(RatificationPackage.MAX_SAFE_INTEGER));
    }

    private static RatificationPackage pending() {
        return RatificationPackage.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), 1, "TRY", Instant.parse("2026-07-19T10:00:00Z"));
    }
}
