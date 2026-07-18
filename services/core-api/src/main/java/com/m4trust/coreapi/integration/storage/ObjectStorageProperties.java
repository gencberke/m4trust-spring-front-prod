package com.m4trust.coreapi.integration.storage;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Validated
@ConfigurationProperties("app.object-storage")
public record ObjectStorageProperties(
        @NotNull URI endpoint,
        @NotBlank String region,
        @NotBlank String bucket,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        @NotNull Duration uploadTtl,
        @NotNull Duration downloadTtl,
        @Positive long maxUploadSizeBytes) {

    public ObjectStorageProperties {
        if (!endpoint.isAbsolute() || uploadTtl.isNegative() || uploadTtl.isZero()
                || downloadTtl.isNegative() || downloadTtl.isZero()) {
            throw new IllegalArgumentException("object storage endpoint and TTLs must be valid");
        }
    }
}
