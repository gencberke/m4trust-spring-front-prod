package com.m4trust.coreapi.deal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record CreateDealRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must not exceed 200 characters.")
        String title,
        @Size(max = 4000,
                message = "Description must not exceed 4000 characters.")
        String description) {

    CreateDealRequest {
        title = title == null ? null : title.trim();
    }
}
