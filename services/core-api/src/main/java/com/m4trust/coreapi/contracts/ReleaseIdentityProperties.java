package com.m4trust.coreapi.contracts;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ADR-016/020 release revision: exact 40-hex git SHA only.
 * Missing or malformed values fail closed; never substitute forty zeros.
 */
@ConfigurationProperties("m4trust.release")
public record ReleaseIdentityProperties(String gitCommitSha) {

    private static final Pattern FORTY_HEX = Pattern.compile("^[a-f0-9]{40}$");

    public ReleaseIdentityProperties {
        gitCommitSha = requireValidFortyHex(gitCommitSha);
    }

    public String releaseRevision() {
        return gitCommitSha;
    }

    static String requireValidFortyHex(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "m4trust.release.git-commit-sha is required (40-hex); "
                            + "missing values fail closed and must never become forty zeros");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("0000000000000000000000000000000000000000".equals(normalized)) {
            throw new IllegalArgumentException(
                    "m4trust.release.git-commit-sha must not be forty zeros (ADR-020)");
        }
        if (!FORTY_HEX.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "m4trust.release.git-commit-sha must be a 40-hex git SHA; "
                            + "malformed values fail closed and must never become forty zeros: "
                            + value);
        }
        return normalized;
    }
}
