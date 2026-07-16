package com.m4trust.coreapi.organization;

import java.util.UUID;

public record LegalEntityMembership(
        UUID legalEntityId,
        String legalName,
        String registrationNumber,
        LegalEntityRole role) {
}
