package com.m4trust.coreapi.payment;

import java.util.Objects;

/**
 * Domain-owned narrow provider contract (ADR-010 §2.6). Request/outcome types
 * never carry raw provider payloads, card data, credentials, or other PII;
 * amount/currency are used only to size the simulated transfer, never to pick
 * a sandbox outcome. Implementations live outside {@code payment} (the sandbox
 * adapter lives in {@code integration}); the real Slice 11B adapter is a
 * separate future implementation of this same interface.
 */
public interface PaymentProviderPort {

    /**
     * Initiates a new provider-side attempt for the given fixed provider key.
     * Never called from within a database transaction (ADR-010 §2.4).
     */
    ProviderResult initiate(ProviderRequest request);

    /**
     * Queries the provider for the definitive outcome of a previously
     * initiated (or possibly never-received) attempt, keyed by the same
     * provider key. Returns {@link Outcome#NOT_FOUND} only when the provider
     * has no record of the key, in which case the caller may safely
     * {@link #initiate(ProviderRequest)} with the same key.
     */
    ProviderResult queryStatus(ProviderRequest request);

    record ProviderRequest(String providerKey, long amountMinor, String currency) {
        public ProviderRequest {
            Objects.requireNonNull(providerKey, "providerKey must not be null");
            Objects.requireNonNull(currency, "currency must not be null");
            if (amountMinor < 1) {
                throw new IllegalArgumentException("amountMinor must be positive");
            }
        }
    }

    record ProviderResult(Outcome outcome, String providerReference) {
    }

    enum Outcome {
        SUCCEEDED,
        DECLINED,
        UNCONFIRMED,
        NOT_FOUND
    }
}
