package com.m4trust.coreapi.payment;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.payment.dispatch.relay")
record PaymentDispatchRelayProperties(Duration fixedDelay, Duration claimTimeout, int batchSize) {
}
