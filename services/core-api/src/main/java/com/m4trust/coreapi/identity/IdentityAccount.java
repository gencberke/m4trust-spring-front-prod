package com.m4trust.coreapi.identity;

import java.util.UUID;

record IdentityAccount(
        UUID id,
        String email,
        String passwordHash,
        String displayName,
        boolean enabled) {
}
