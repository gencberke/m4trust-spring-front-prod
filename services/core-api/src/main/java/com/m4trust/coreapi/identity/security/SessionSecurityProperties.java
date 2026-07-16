package com.m4trust.coreapi.identity.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.security.session")
public record SessionSecurityProperties(Duration absoluteTimeout) {

    public SessionSecurityProperties {
        if (absoluteTimeout == null || absoluteTimeout.isZero()
                || absoluteTimeout.isNegative()) {
            throw new IllegalArgumentException("Absolute session timeout must be positive.");
        }
    }
}
