package com.m4trust.coreapi.integration.payment.moka;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * Runtime-only settings for the bounded Moka transport. Values are deliberately
 * kept at the integration boundary: credentials must not reach payment domain
 * types, persistence, audit, logs, or public projections.
 */
public record MokaTransportSettings(URI baseUri, String dealerCode, String username, String password,
        Duration connectTimeout, Duration readTimeout, int maxRequestBytes, int maxResponseBytes) {

    static final int MAX_SUPPORTED_REQUEST_BYTES = 8_192;
    static final int MAX_SUPPORTED_RESPONSE_BYTES = 16_384;

    public MokaTransportSettings {
        Objects.requireNonNull(baseUri, "baseUri must not be null");
        requireText(dealerCode, "dealerCode");
        requireText(username, "username");
        requireText(password, "password");
        Objects.requireNonNull(connectTimeout, "connectTimeout must not be null");
        Objects.requireNonNull(readTimeout, "readTimeout must not be null");
        if (!baseUri.isAbsolute() || !"http".equals(baseUri.getScheme()) && !"https".equals(baseUri.getScheme())) {
            throw new IllegalArgumentException("baseUri must be an absolute HTTP(S) URI");
        }
        if (connectTimeout.isNegative() || connectTimeout.isZero()
                || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("transport timeouts must be positive");
        }
        if (maxRequestBytes < 1 || maxRequestBytes > MAX_SUPPORTED_REQUEST_BYTES) {
            throw new IllegalArgumentException("maxRequestBytes must be between 1 and "
                    + MAX_SUPPORTED_REQUEST_BYTES);
        }
        if (maxResponseBytes < 1 || maxResponseBytes > MAX_SUPPORTED_RESPONSE_BYTES) {
            throw new IllegalArgumentException("maxResponseBytes must be between 1 and "
                    + MAX_SUPPORTED_RESPONSE_BYTES);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
