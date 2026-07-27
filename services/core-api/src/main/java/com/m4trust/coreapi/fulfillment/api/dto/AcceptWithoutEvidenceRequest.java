package com.m4trust.coreapi.fulfillment.api.dto;

import jakarta.validation.constraints.Min;

public record AcceptWithoutEvidenceRequest(
    @Min(value = 0, message = "expectedDealVersion must be non-negative") long expectedDealVersion,
    @Min(value = 0, message = "expectedFulfillmentVersion must be non-negative")
        long expectedFulfillmentVersion) {}
