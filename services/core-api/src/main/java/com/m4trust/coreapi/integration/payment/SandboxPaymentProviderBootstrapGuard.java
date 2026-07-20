package com.m4trust.coreapi.integration.payment;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Fail-closed bootstrap check (ADR-010 §2.6): the sandbox provider must never
 * be the selected {@code PaymentProviderPort} outside {@code local-sandbox}.
 * {@code @Profile("local-sandbox")} on {@link SandboxPaymentProviderAdapter}
 * already prevents this under normal configuration; this bean is a second,
 * explicit guard so a misconfigured profile list (for example
 * {@code local-sandbox} left active alongside {@code staging}/{@code production})
 * still fails application startup instead of silently selecting the sandbox.
 */
@Component
class SandboxPaymentProviderBootstrapGuard {

    SandboxPaymentProviderBootstrapGuard(
            Environment environment, ObjectProvider<SandboxPaymentProviderAdapter> sandboxAdapter) {
        boolean sandboxBeanPresent = sandboxAdapter.getIfAvailable() != null;
        boolean productionLikeProfileActive = environment.acceptsProfiles(Profiles.of("staging", "production"));
        if (sandboxBeanPresent && productionLikeProfileActive) {
            throw new IllegalStateException(
                    "Sandbox payment provider must never be selected under a staging/production profile");
        }
    }
}
