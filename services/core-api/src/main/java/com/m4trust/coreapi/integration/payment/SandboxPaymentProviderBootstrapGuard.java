package com.m4trust.coreapi.integration.payment;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Fail-closed bootstrap check (ADR-010 §2.6, extended by the 2026-07-22
 * simulation-only decision §2 and ADR-014 §2.1): the sandbox provider must
 * never be the selected {@code PaymentProviderPort} outside {@code local-sandbox}
 * or the separately named {@code staging-simulated} profile, and never
 * alongside {@code production} under any circumstance.
 * {@code @Profile({"local-sandbox", "staging-simulated"})} on
 * {@link SandboxPaymentProviderAdapter} already prevents most of this under
 * normal configuration; this bean is a second, explicit guard so a
 * misconfigured profile list still fails application startup instead of
 * silently selecting the sandbox. The enforced matrix:
 *
 * <ul>
 *   <li>sandbox bean present + {@code production} active — always fails,
 *       regardless of any other active profile.</li>
 *   <li>sandbox bean present + {@code staging} active without
 *       {@code staging-simulated} also active — fails (a stray
 *       {@code local-sandbox} left active alongside plain {@code staging}
 *       still fails; only the explicit {@code staging}+{@code staging-simulated}
 *       pair is allowed).</li>
 *   <li>{@code staging-simulated} + {@code production} together — always
 *       fails, independent of sandbox bean presence.</li>
 * </ul>
 */
@Component
class SandboxPaymentProviderBootstrapGuard {

    SandboxPaymentProviderBootstrapGuard(
            Environment environment, ObjectProvider<SandboxPaymentProviderAdapter> sandboxAdapter) {
        boolean sandboxBeanPresent = sandboxAdapter.getIfAvailable() != null;
        boolean productionActive = environment.acceptsProfiles(Profiles.of("production"));
        boolean stagingActive = environment.acceptsProfiles(Profiles.of("staging"));
        boolean stagingSimulatedActive = environment.acceptsProfiles(Profiles.of("staging-simulated"));

        if (stagingSimulatedActive && productionActive) {
            throw new IllegalStateException(
                    "staging-simulated must never be combined with the production profile "
                            + "(2026-07-22 simulation-only decision §2; ADR-014 §2.1)");
        }
        if (sandboxBeanPresent && productionActive) {
            throw new IllegalStateException(
                    "Sandbox payment provider must never be selected under the production profile");
        }
        if (sandboxBeanPresent && stagingActive && !stagingSimulatedActive) {
            throw new IllegalStateException(
                    "Sandbox payment provider under the staging profile requires staging-simulated to also be "
                            + "active (2026-07-22 simulation-only decision §2; ADR-014 §2.1)");
        }
    }
}
