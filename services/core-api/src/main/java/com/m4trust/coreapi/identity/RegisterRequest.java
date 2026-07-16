package com.m4trust.coreapi.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(min = 3, max = 320) String email,
        @NotBlank @Size(min = 15, max = 128) String password,
        @NotBlank @Size(max = 200) String displayName) {

    public RegisterRequest {
        email = EmailAddress.normalize(email);
        displayName = displayName == null ? null : displayName.trim();
    }
}
