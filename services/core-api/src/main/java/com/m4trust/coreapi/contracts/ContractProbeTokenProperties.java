package com.m4trust.coreapi.contracts;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("m4trust.contracts")
public record ContractProbeTokenProperties(
        String probeToken,
        String probeTokenPrevious) {

    public ContractProbeTokenProperties {
        probeToken = probeToken == null ? "" : probeToken;
        probeTokenPrevious = probeTokenPrevious == null ? "" : probeTokenPrevious;
    }
}
