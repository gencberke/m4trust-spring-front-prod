package com.m4trust.coreapi.contractintelligence.api.dto;

public record DurationValue(String type, long valueSeconds) implements StructuredValue {}
