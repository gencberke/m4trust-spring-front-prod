package com.m4trust.coreapi.fulfillment.api.dto;

public record FulfillmentAvailableActions(
    boolean canStart, boolean canAccept, boolean canReject, boolean canAcceptWithoutEvidence) {}
