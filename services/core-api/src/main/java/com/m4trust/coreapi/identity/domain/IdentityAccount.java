package com.m4trust.coreapi.identity.domain;

import java.util.UUID;

public record IdentityAccount(
    UUID id, String email, String passwordHash, String displayName, boolean enabled) {}
