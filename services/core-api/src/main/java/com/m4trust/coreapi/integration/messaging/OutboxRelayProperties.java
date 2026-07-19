package com.m4trust.coreapi.integration.messaging;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.messaging.relay")
record OutboxRelayProperties(boolean enabled, Duration fixedDelay, Duration claimTimeout,
        int batchSize, Duration confirmTimeout) {
}
