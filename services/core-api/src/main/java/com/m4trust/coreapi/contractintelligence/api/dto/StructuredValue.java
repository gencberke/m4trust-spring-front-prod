package com.m4trust.coreapi.contractintelligence.api.dto;

public sealed interface StructuredValue
    permits TextValue,
        MoneyValue,
        PercentageValue,
        DurationValue,
        DateValue,
        BooleanValue,
        QuantityValue {}
