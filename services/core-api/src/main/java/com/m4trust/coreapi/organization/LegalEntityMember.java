package com.m4trust.coreapi.organization;

import java.util.UUID;

public record LegalEntityMember(
        UUID userId,
        String email,
        String displayName,
        LegalEntityRole role) {
}
