package com.m4trust.coreapi.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Email @Size(min = 3, max = 320) String email,
        @NotBlank @Size(max = 128) String password) {

    public LoginRequest {
        email = EmailAddress.normalize(email);
    }
}
