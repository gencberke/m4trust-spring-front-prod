package com.m4trust.coreapi.integration.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ADR-010 §2.6 fail-closed bootstrap: the sandbox provider must never be
 * selected outside {@code local-sandbox}, even if a profile misconfiguration
 * leaves {@code local-sandbox} active alongside a staging/production profile.
 */
class SandboxPaymentProviderBootstrapGuardTest {

    @Test
    void bootFailsWhenSandboxIsActiveAlongsideAProductionLikeProfile() {
        new ApplicationContextRunner()
                .withUserConfiguration(SandboxOnlyConfiguration.class, GuardConfiguration.class)
                .withPropertyValues("spring.profiles.active=local-sandbox,staging")
                .run(context -> org.assertj.core.api.Assertions.assertThat(context).hasFailed());
    }

    @Test
    void bootSucceedsWhenOnlyLocalSandboxIsActive() {
        new ApplicationContextRunner()
                .withUserConfiguration(SandboxOnlyConfiguration.class, GuardConfiguration.class)
                .withPropertyValues("spring.profiles.active=local-sandbox")
                .run(context -> org.assertj.core.api.Assertions.assertThat(context).hasNotFailed());
    }

    @Test
    void bootSucceedsWhenSandboxBeanIsAbsent() {
        new ApplicationContextRunner()
                .withUserConfiguration(GuardConfiguration.class)
                .withPropertyValues("spring.profiles.active=staging")
                .run(context -> org.assertj.core.api.Assertions.assertThat(context).hasNotFailed());
    }

    @Configuration(proxyBeanMethods = false)
    static class SandboxOnlyConfiguration {
        @Bean
        SandboxPaymentProviderAdapter fakeSandboxAdapter() {
            return new SandboxPaymentProviderAdapter(new SandboxPaymentProviderProperties(null));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class GuardConfiguration {
        @Bean
        SandboxPaymentProviderBootstrapGuard sandboxPaymentProviderBootstrapGuard(
                org.springframework.core.env.Environment environment,
                ObjectProvider<SandboxPaymentProviderAdapter> sandboxAdapter) {
            return new SandboxPaymentProviderBootstrapGuard(environment, sandboxAdapter);
        }
    }
}
