package com.m4trust.coreapi.casework.api.dto;

import com.m4trust.coreapi.casework.domain.*;
import jakarta.validation.constraints.Min;

public record WithdrawDisputeRequest(
    @Min(value = 0, message = "expectedVersion must be non-negative") long expectedVersion) {}
