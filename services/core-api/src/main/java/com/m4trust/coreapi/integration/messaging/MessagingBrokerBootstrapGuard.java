package com.m4trust.coreapi.integration.messaging;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * ADR-022 demo runtime: RabbitMQ credentials are required only when messaging
 * topology or outbox relay is enabled. Both-disabled keeps startup broker-free.
 */
@Component
class MessagingBrokerBootstrapGuard {

    MessagingBrokerBootstrapGuard(Environment environment) {
        boolean topologyEnabled = environment.getProperty(
                "app.messaging.topology.enabled", Boolean.class, false);
        boolean relayEnabled = environment.getProperty(
                "app.messaging.relay.enabled", Boolean.class, false);
        if (!topologyEnabled && !relayEnabled) {
            return;
        }
        requirePresent(environment, "spring.rabbitmq.host");
        requirePresent(environment, "spring.rabbitmq.port");
        requirePresent(environment, "spring.rabbitmq.username");
        requirePresent(environment, "spring.rabbitmq.password");
    }

    private static void requirePresent(Environment environment, String property) {
        String value = environment.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Messaging is enabled but required broker setting is missing or blank: "
                            + property);
        }
    }
}
