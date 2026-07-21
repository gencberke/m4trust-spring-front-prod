package com.m4trust.coreapi.integration.payment.moka;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Fails closed if the local emulator adapter is selected with a forbidden profile. */
@Component
class MokaPaymentProviderBootstrapGuard {

    MokaPaymentProviderBootstrapGuard(Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("local-moka"))
                && environment.acceptsProfiles(Profiles.of("staging", "production", "local-sandbox"))) {
            throw new IllegalStateException(
                    "Moka emulator payment provider requires local-moka alone and is forbidden with staging, production, or local-sandbox");
        }
    }
}
