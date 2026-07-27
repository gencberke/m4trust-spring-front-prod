package com.m4trust.coreapi.integration.infra.storage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

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
    @NotNull Duration aiDownloadTtl,
    @Positive long maxUploadSizeBytes) {

  public ObjectStorageProperties {
    if (!endpoint.isAbsolute()
        || uploadTtl.isNegative()
        || uploadTtl.isZero()
        || downloadTtl.isNegative()
        || downloadTtl.isZero()
        || aiDownloadTtl.isNegative()
        || aiDownloadTtl.isZero()) {
      throw new IllegalArgumentException("object storage endpoint and TTLs must be valid");
    }
  }
}
