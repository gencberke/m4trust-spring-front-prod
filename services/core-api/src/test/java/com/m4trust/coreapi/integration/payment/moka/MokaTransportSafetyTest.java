package com.m4trust.coreapi.integration.payment.moka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;

class MokaTransportSafetyTest {

    @Test
    void computesCheckKeyInDocumentedOrder() {
        assertEquals("c58ed4af37993f564d88aa9a6560fc59e07fc4f07bbd1153a3a15924a38738c0",
                MokaAuthentication.checkKey("DEALER-001", "fixture-user", "fixture-password"));
        assertEquals("cfc237ded22892af9564f624070cf3264689967bdd409f7eddc7842d6e8d639f",
                MokaAuthentication.checkKey("dealer-42", "merchant.user", "not-a-secret"));
    }

    @Test
    void convertsMinorUnitsExactlyWithoutFloatingPoint() {
        assertEquals("0.01", MokaMoney.decimalMajor(1, "TRY"));
        assertEquals("27.50", MokaMoney.decimalMajor(2_750, "TRY"));
        assertEquals("1234567.89", MokaMoney.decimalMajor(123_456_789, "USD"));
        assertThrows(IllegalArgumentException.class, () -> MokaMoney.decimalMajor(1, "JPY"));
    }

    @Test
    void rejectsUnsafeOrUnboundedTransportSettings() {
        assertThrows(IllegalArgumentException.class, () -> settings(0, 16_384));
        assertThrows(IllegalArgumentException.class, () -> settings(8_192, 16_385));
        assertThrows(IllegalArgumentException.class, () -> new MokaTransportSettings(
                URI.create("file:///tmp/moka"), "dealer", "user", "password", Duration.ofSeconds(1),
                Duration.ofSeconds(1), 8_192, 16_384));
    }

    private static MokaTransportSettings settings(int maxRequestBytes, int maxResponseBytes) {
        return new MokaTransportSettings(URI.create("http://127.0.0.1:18081"), "dealer", "user", "password",
                Duration.ofSeconds(1), Duration.ofSeconds(1), maxRequestBytes, maxResponseBytes);
    }
}
