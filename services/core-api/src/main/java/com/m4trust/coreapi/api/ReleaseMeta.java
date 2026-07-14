package com.m4trust.coreapi.api;

/**
 * Release identity of the running instance, per ADR-007 (release identity /
 * info endpoint expectations).
 */
public record ReleaseMeta(
        String buildVersion,
        String gitCommitSha,
        String environment,
        String buildTime) {
}
