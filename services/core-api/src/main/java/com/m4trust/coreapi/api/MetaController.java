package com.m4trust.coreapi.api;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes minimal release identity information so the platform team can
 * confirm what is actually deployed, without requiring a database or any
 * other external dependency.
 */
@RestController
public class MetaController {

    private static final String UNKNOWN = "unknown";

    private final Optional<BuildProperties> buildProperties;
    private final String environment;
    private final String gitCommitSha;

    public MetaController(
            Optional<BuildProperties> buildProperties,
            @Value("${app.environment:local}") String environment,
            @Value("${app.git-commit-sha:unknown}") String gitCommitSha) {
        this.buildProperties = buildProperties;
        this.environment = environment;
        this.gitCommitSha = gitCommitSha;
    }

    @GetMapping("/api/v1/meta")
    public ReleaseMeta meta() {
        String buildVersion = buildProperties.map(BuildProperties::getVersion).orElse(UNKNOWN);
        String buildTime = buildProperties
                .map(bp -> bp.getTime() != null ? bp.getTime().toString() : null)
                .orElse(UNKNOWN);
        String resolvedGitSha = (gitCommitSha == null || gitCommitSha.isBlank()) ? UNKNOWN : gitCommitSha;

        return new ReleaseMeta(buildVersion, resolvedGitSha, environment, buildTime);
    }
}
