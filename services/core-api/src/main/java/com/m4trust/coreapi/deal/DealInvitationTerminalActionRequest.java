package com.m4trust.coreapi.deal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

record DealInvitationTerminalActionRequest(
        @NotNull(message = "Expected version is required.")
        @PositiveOrZero(message = "Expected version must not be negative.")
        Long expectedVersion) {

    long version() {
        return expectedVersion;
    }
}
