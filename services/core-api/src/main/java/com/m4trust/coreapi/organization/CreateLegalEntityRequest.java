package com.m4trust.coreapi.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateLegalEntityRequest(
        @NotBlank(message = "legalName must not be blank")
        @Size(max = 200, message = "legalName must be at most 200 characters")
        String legalName,
        @NotBlank(message = "registrationNumber must not be blank")
        @Size(max = 100, message = "registrationNumber must be at most 100 characters")
        String registrationNumber) {

    public CreateLegalEntityRequest {
        legalName = legalName == null ? null : legalName.trim();
        registrationNumber = registrationNumber == null
                ? null : registrationNumber.trim();
    }
}
