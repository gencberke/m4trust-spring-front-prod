package com.m4trust.coreapi.deal;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

record AcceptDealInvitationRequest(
        @NotNull(message = "Legal entity is required.") UUID legalEntityId,
        @NotNull(message = "Expected version is required.")
        @PositiveOrZero(message = "Expected version must not be negative.")
        Long expectedVersion) {

    long version() {
        return expectedVersion;
    }
}
