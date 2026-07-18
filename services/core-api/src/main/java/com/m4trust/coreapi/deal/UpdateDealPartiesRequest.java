package com.m4trust.coreapi.deal;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

final class UpdateDealPartiesRequest {

    private UUID buyerLegalEntityId;
    private UUID sellerLegalEntityId;

    @NotNull(message = "Expected version is required.")
    @PositiveOrZero(message = "Expected version must not be negative.")
    private Long expectedVersion;

    private boolean buyerLegalEntityIdPresent;
    private boolean sellerLegalEntityIdPresent;

    public UUID getBuyerLegalEntityId() {
        return buyerLegalEntityId;
    }

    public void setBuyerLegalEntityId(UUID buyerLegalEntityId) {
        this.buyerLegalEntityId = buyerLegalEntityId;
        buyerLegalEntityIdPresent = true;
    }

    public UUID getSellerLegalEntityId() {
        return sellerLegalEntityId;
    }

    public void setSellerLegalEntityId(UUID sellerLegalEntityId) {
        this.sellerLegalEntityId = sellerLegalEntityId;
        sellerLegalEntityIdPresent = true;
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }

    @JsonIgnore
    boolean buyerLegalEntityIdPresent() {
        return buyerLegalEntityIdPresent;
    }

    @JsonIgnore
    boolean sellerLegalEntityIdPresent() {
        return sellerLegalEntityIdPresent;
    }

    UUID buyerLegalEntityId() {
        return buyerLegalEntityId;
    }

    UUID sellerLegalEntityId() {
        return sellerLegalEntityId;
    }

    long expectedVersion() {
        return expectedVersion;
    }
}
