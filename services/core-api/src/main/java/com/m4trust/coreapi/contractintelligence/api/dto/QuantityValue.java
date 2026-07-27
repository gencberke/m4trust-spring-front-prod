package com.m4trust.coreapi.contractintelligence.api.dto;

import java.math.BigDecimal;

public record QuantityValue(String type, BigDecimal value, String unit)
    implements StructuredValue {}
