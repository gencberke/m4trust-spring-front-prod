package com.m4trust.coreapi.casework.api.dto;

import com.m4trust.coreapi.casework.domain.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OpenDisputeRequest(
    @NotBlank(message = "reasonCode is required") String reasonCode,
    @NotBlank(message = "subject is required")
        @Size(min = 1, max = 200, message = "subject must be 1-200 characters")
        String subject,
    @NotBlank(message = "statement is required")
        @Size(min = 1, max = 4000, message = "statement must be 1-4000 characters")
        String statement,
    @Min(value = 0, message = "expectedDealVersion must be non-negative") long expectedDealVersion,
    @Min(value = 0, message = "expectedFulfillmentVersion must be non-negative")
        long expectedFulfillmentVersion) {}
