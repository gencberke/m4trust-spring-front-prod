package com.m4trust.coreapi.ratification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Wire shape of {@code CreateRatificationPackageRequest} (OpenAPI). Initiator
 * exact commercial terms are always supplied explicitly; accepted MONEY rules
 * are suggestions only and are never silently selected by the server.
 */
record CreateRatificationPackageHttpRequest(
        @NotNull(message = "Expected version is required.")
        @PositiveOrZero(message = "Expected version must not be negative.")
        @Max(value = RatificationPackage.MAX_SAFE_INTEGER, message = "Expected version is out of range.")
        Long expectedVersion,

        @NotNull(message = "Commercial terms are required.")
        @Valid
        CommercialTerms commercialTerms) {

    record CommercialTerms(
            @NotNull(message = "amountMinor is required.")
            @Min(value = 1, message = "amountMinor must be a positive integer minor unit.")
            @Max(value = RatificationPackage.MAX_SAFE_INTEGER, message = "amountMinor is out of range.")
            Long amountMinor,

            @NotNull(message = "currency is required.")
            @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be an uppercase ISO 4217 code.")
            String currency) {
    }
}
