package com.m4trust.coreapi.organization.domain;

import java.util.UUID;

public record LegalEntityMembership(
    UUID legalEntityId, String legalName, String registrationNumber, LegalEntityRole role) {}
