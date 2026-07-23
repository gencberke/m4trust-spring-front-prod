package com.m4trust.coreapi.integration.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ADR-010 §2.6 fail-closed bootstrap, extended by the 2026-07-22
 * simulation-only decision §2 and ADR-014 §2.1: the sandbox provider must
 * never be selected outside {@code local-sandbox} or the explicit
 * {@code staging}+{@code staging-simulated} pair, and {@code production} is
 * always rejected regardless of any other active profile.
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

    @Test
    void bootFailsWhenSandboxIsActiveUnderProductionEvenWithStagingSimulated() {
        new ApplicationContextRunner()
                .withUserConfiguration(SandboxOnlyConfiguration.class, GuardConfiguration.class)
                .withPropertyValues("spring.profiles.active=staging-simulated,production")
                .run(context -> org.assertj.core.api.Assertions.assertThat(context).hasFailed());
    }

    @Test
    void bootFailsWhenSandboxIsActiveUnderStagingWithoutStagingSimulated() {
        new ApplicationContextRunner()
                .withUserConfiguration(SandboxOnlyConfiguration.class, GuardConfiguration.class)
                .withPropertyValues("spring.profiles.active=staging")
                .run(context -> org.assertj.core.api.Assertions.assertThat(context).hasFailed());
    }

    @Test
    void bootFailsWhenStagingSimulatedIsActiveAlongsideProductionEvenWithoutSandboxBean() {
        new ApplicationContextRunner()
                .withUserConfiguration(GuardConfiguration.class)
                .withPropertyValues("spring.profiles.active=staging-simulated,production")
                .run(context -> org.assertj.core.api.Assertions.assertThat(context).hasFailed());
    }

    @Test
    void bootSucceedsWhenSandboxIsActiveUnderTheExplicitStagingSimulatedPair() {
        new ApplicationContextRunner()
                .withUserConfiguration(SandboxOnlyConfiguration.class, GuardConfiguration.class)
                .withPropertyValues("spring.profiles.active=staging,staging-simulated")
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
