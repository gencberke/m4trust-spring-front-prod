package com.m4trust.coreapi.deal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

final class UpdateDealRequest {

    @NotBlank(message = "Title is required.")
    @Size(max = 200, message = "Title must not exceed 200 characters.")
    private String title;

    @Size(max = 4000,
            message = "Description must not exceed 4000 characters.")
    private String description;

    @NotNull(message = "Expected version is required.")
    @PositiveOrZero(message = "Expected version must not be negative.")
    private Long expectedVersion;

    private boolean descriptionPresent;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        descriptionPresent = true;
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }

    @JsonIgnore
    boolean descriptionPresent() {
        return descriptionPresent;
    }

    String title() {
        return title;
    }

    String description() {
        return description;
    }

    long expectedVersion() {
        return expectedVersion;
    }
}
