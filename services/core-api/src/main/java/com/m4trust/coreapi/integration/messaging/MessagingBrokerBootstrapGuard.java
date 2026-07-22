package com.m4trust.coreapi.integration.messaging;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * ADR-022 demo runtime: RabbitMQ credentials are required only when messaging
 * topology or outbox relay is enabled. Both-disabled keeps startup broker-free.
 * When messaging is enabled, {@code spring.rabbitmq.port} must be an integer in
 * the TCP range 1..65535.
 */
@Component
class MessagingBrokerBootstrapGuard {

    private static final int MIN_TCP_PORT = 1;
    private static final int MAX_TCP_PORT = 65535;

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
        requireValidPort(environment.getProperty("spring.rabbitmq.port"));
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

    private static void requireValidPort(String portValue) {
        final int port;
        try {
            port = Integer.parseInt(portValue.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(
                    "Messaging is enabled but spring.rabbitmq.port is not a valid integer: "
                            + portValue,
                    ex);
        }
        if (port < MIN_TCP_PORT || port > MAX_TCP_PORT) {
            throw new IllegalStateException(
                    "Messaging is enabled but spring.rabbitmq.port is outside TCP range 1..65535: "
                            + port);
        }
    }
}
