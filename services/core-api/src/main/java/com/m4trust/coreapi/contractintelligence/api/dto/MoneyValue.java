package com.m4trust.coreapi.contractintelligence.api.dto;

public record MoneyValue(String type, long amountMinor, String currency)
    implements StructuredValue {}
