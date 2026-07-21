package com.m4trust.coreapi.integration.payment.moka;

/**
 * Integration-only capability for future empirical pool probes. Its facts are
 * deliberately non-authoritative: no business service, release flow, or
 * settlement/finality projection may treat them as money-movement truth.
 */
public final class MokaPoolProbeClient {
    private final MokaHttpPaymentProviderAdapter transport;

    public MokaPoolProbeClient(MokaHttpPaymentProviderAdapter transport) {
        this.transport = transport;
    }

    public MokaHttpPaymentProviderAdapter.PoolProbeFact approve(String providerKey) {
        return transport.approvePoolProbe(providerKey);
    }

    public MokaHttpPaymentProviderAdapter.PoolProbeFact query(String providerKey) {
        return transport.queryPoolProbe(providerKey);
    }
}
