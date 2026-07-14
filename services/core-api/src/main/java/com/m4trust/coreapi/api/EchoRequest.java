package com.m4trust.coreapi.api;

import jakarta.validation.constraints.NotBlank;

/**
 * Minimal request DTO used only to demonstrate field validation and the
 * resulting RFC 9457 Problem Details error shape. Not a business capability.
 */
public record EchoRequest(@NotBlank(message = "message must not be blank") String message) {
}
