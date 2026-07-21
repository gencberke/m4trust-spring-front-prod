package com.m4trust.coreapi.integration.payment.moka;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class MokaPaymentProviderBootstrapGuardTest {

    @Test
    void bootFailsWhenLocalMokaIsCombinedWithAProductionLikeProfile() {
        new ApplicationContextRunner()
                .withUserConfiguration(GuardConfiguration.class)
                .withPropertyValues("spring.profiles.active=local-moka,production")
                .run(context -> org.assertj.core.api.Assertions.assertThat(context).hasFailed());
    }

    @Test
    void missingMokaSettingsFailClosedWhenAdapterIsSelected() {
        new ApplicationContextRunner()
                .withUserConfiguration(MokaPaymentConfiguration.class)
                .withPropertyValues("spring.profiles.active=local-moka")
                .run(context -> org.assertj.core.api.Assertions.assertThat(context).hasFailed());
    }

    @Test
    void localMokaBootsWithCompleteBoundedSettings() {
        new ApplicationContextRunner()
                .withUserConfiguration(MokaPaymentConfiguration.class, GuardConfiguration.class)
                .withPropertyValues(
                        "spring.profiles.active=local-moka",
                        "app.payment.moka.base-uri=http://127.0.0.1:18081",
                        "app.payment.moka.dealer-code=DEALER-001",
                        "app.payment.moka.username=fixture-user",
                        "app.payment.moka.password=fixture-password",
                        "app.payment.moka.connect-timeout=1s",
                        "app.payment.moka.read-timeout=1s",
                        "app.payment.moka.max-request-bytes=8192",
                        "app.payment.moka.max-response-bytes=16384")
                .run(context -> org.assertj.core.api.Assertions.assertThat(context).hasNotFailed());
    }

    @Configuration(proxyBeanMethods = false)
    static class GuardConfiguration {
        @Bean
        MokaPaymentProviderBootstrapGuard mokaPaymentProviderBootstrapGuard(
                org.springframework.core.env.Environment environment) {
            return new MokaPaymentProviderBootstrapGuard(environment);
        }
    }
}
