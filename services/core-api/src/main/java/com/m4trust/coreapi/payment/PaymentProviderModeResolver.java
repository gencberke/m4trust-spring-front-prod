package com.m4trust.coreapi.payment;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Resolves the visible simulated-mode label from whichever
 * {@link PaymentProviderPort} bean is active, or {@code null} when no provider
 * is configured (2026-07-22 simulation-only decision §2; ADR-014 §2.1).
 * A {@link PaymentProviderPort} bean exists only under {@code local-moka},
 * {@code local-sandbox}, or {@code staging-simulated} — every read/write
 * funding projection depends on this resolver rather than the port itself so
 * production and plain-{@code staging} startup, where no provider bean is
 * wired, is unaffected.
 */
@Component
class PaymentProviderModeResolver {

    private final ObjectProvider<PaymentProviderPort> provider;

    PaymentProviderModeResolver(ObjectProvider<PaymentProviderPort> provider) {
        this.provider = provider;
    }

    PaymentProviderMode resolve() {
        PaymentProviderPort port = provider.getIfAvailable();
        return port == null ? null : port.mode();
    }
}
