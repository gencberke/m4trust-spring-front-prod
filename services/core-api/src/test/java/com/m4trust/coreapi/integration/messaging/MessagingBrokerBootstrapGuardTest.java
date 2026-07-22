package com.m4trust.coreapi.integration.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

class MessagingBrokerBootstrapGuardTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(GuardConfiguration.class);

    @Test
    void bothDisabledBootsWithoutBrokerSettings() {
        runner.withPropertyValues(
                        "app.messaging.topology.enabled=false",
                        "app.messaging.relay.enabled=false")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void topologyEnabledWithoutHostFailsClosed() {
        runner.withPropertyValues(
                        "app.messaging.topology.enabled=true",
                        "app.messaging.relay.enabled=false",
                        "spring.rabbitmq.port=5672",
                        "spring.rabbitmq.username=user",
                        "spring.rabbitmq.password=secret")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("spring.rabbitmq.host");
                });
    }

    @Test
    void relayEnabledWithoutPasswordFailsClosed() {
        runner.withPropertyValues(
                        "app.messaging.topology.enabled=false",
                        "app.messaging.relay.enabled=true",
                        "spring.rabbitmq.host=127.0.0.1",
                        "spring.rabbitmq.port=5672",
                        "spring.rabbitmq.username=user")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("spring.rabbitmq.password");
                });
    }

    @Test
    void blankBrokerSettingFailsWhenMessagingEnabled() {
        runner.withPropertyValues(
                        "app.messaging.topology.enabled=true",
                        "app.messaging.relay.enabled=false",
                        "spring.rabbitmq.host= ",
                        "spring.rabbitmq.port=5672",
                        "spring.rabbitmq.username=user",
                        "spring.rabbitmq.password=secret")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("spring.rabbitmq.host");
                });
    }

    @Test
    void enabledMessagingBootsWithCompleteBrokerSettings() {
        runner.withPropertyValues(
                        "app.messaging.topology.enabled=true",
                        "app.messaging.relay.enabled=true",
                        "spring.rabbitmq.host=127.0.0.1",
                        "spring.rabbitmq.port=5672",
                        "spring.rabbitmq.username=fixture-user",
                        "spring.rabbitmq.password=fixture-password")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Configuration(proxyBeanMethods = false)
    static class GuardConfiguration {
        @Bean
        MessagingBrokerBootstrapGuard messagingBrokerBootstrapGuard(Environment environment) {
            return new MessagingBrokerBootstrapGuard(environment);
        }
    }
}
