package com.m4trust.coreapi.integration.payment.moka;

import java.math.BigDecimal;
import java.util.Set;

/** Exact adapter-only conversion from internal integer minor units to Moka decimal major units. */
final class MokaMoney {
    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("TRY", "USD", "EUR", "GBP");

    private MokaMoney() {
    }

    static String decimalMajor(long amountMinor, String currency) {
        if (amountMinor < 1) {
            throw new IllegalArgumentException("amountMinor must be positive");
        }
        if (!SUPPORTED_CURRENCIES.contains(currency)) {
            throw new IllegalArgumentException("unsupported Moka currency");
        }
        return BigDecimal.valueOf(amountMinor, 2).toPlainString();
    }
}
