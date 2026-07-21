package com.m4trust.coreapi.integration.payment.moka;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Profile-selected non-production Moka adapter settings; all values are required at startup. */
@ConfigurationProperties("app.payment.moka")
record MokaPaymentProviderProperties(String baseUri, String dealerCode, String username, String password,
        Duration connectTimeout, Duration readTimeout, Integer maxRequestBytes, Integer maxResponseBytes) {

    MokaTransportSettings transportSettings() {
        return new MokaTransportSettings(URI.create(require(baseUri, "baseUri")), require(dealerCode, "dealerCode"),
                require(username, "username"), require(password, "password"), require(connectTimeout, "connectTimeout"),
                require(readTimeout, "readTimeout"), require(maxRequestBytes, "maxRequestBytes"),
                require(maxResponseBytes, "maxResponseBytes"));
    }

    private static <T> T require(T value, String name) {
        if (value == null || value instanceof String text && text.isBlank()) {
            throw new IllegalStateException("Moka payment " + name + " must be configured");
        }
        return value;
    }
}
