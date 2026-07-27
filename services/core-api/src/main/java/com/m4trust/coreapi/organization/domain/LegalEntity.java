package com.m4trust.coreapi.organization.domain;

import java.util.UUID;

public record LegalEntity(UUID id, String legalName, String registrationNumber) {}
