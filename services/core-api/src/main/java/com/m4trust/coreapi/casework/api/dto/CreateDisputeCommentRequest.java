package com.m4trust.coreapi.casework.api.dto;

import com.m4trust.coreapi.casework.domain.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDisputeCommentRequest(
    @NotBlank(message = "body is required")
        @Size(min = 1, max = 4000, message = "body must be 1-4000 characters")
        String body,
    @Min(value = 0, message = "expectedVersion must be non-negative") long expectedVersion) {}
