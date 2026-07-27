package com.m4trust.coreapi.ratification.api.dto;

import java.time.Instant;
import java.util.UUID;

public record RatificationPackageApproval(
    UUID legalEntityId,
    String legalName,
    String status,
    Instant approvedAt,
    UUID approverUserId) {}
