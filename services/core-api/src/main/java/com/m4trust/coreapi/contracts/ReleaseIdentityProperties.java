package com.m4trust.coreapi.contracts;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("m4trust.release")
public record ReleaseIdentityProperties(String gitCommitSha) {

    private static final Pattern FORTY_HEX = Pattern.compile("^[a-f0-9]{40}$");
    private static final String UNKNOWN_ZEROS = "0".repeat(40);

    public ReleaseIdentityProperties {
        gitCommitSha = normalize(gitCommitSha);
    }

    public String releaseRevision() {
        return gitCommitSha;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN_ZEROS;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (FORTY_HEX.matcher(normalized).matches()) {
            return normalized;
        }
        // Non-40-hex values (including "unknown" and short build stubs) map to zeros.
        return UNKNOWN_ZEROS;
    }
}
