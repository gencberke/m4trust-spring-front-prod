package com.m4trust.coreapi.integration.payment;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Startup-config-only deterministic scenario sequence (ADR-010 §2.6): there is
 * no runtime test-control endpoint, header, or body field, and business
 * amount/currency never select a scenario.
 */
@ConfigurationProperties("app.payment.sandbox")
record SandboxPaymentProviderProperties(List<SandboxScenario> scenarios) {
    SandboxPaymentProviderProperties {
        scenarios = (scenarios == null || scenarios.isEmpty())
                ? List.of(SandboxScenario.SUCCESS)
                : List.copyOf(scenarios);
    }
}
