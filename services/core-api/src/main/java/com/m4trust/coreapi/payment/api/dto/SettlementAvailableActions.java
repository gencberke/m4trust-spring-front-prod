package com.m4trust.coreapi.payment.api.dto;

public record SettlementAvailableActions(boolean canRequestRelease, boolean canReconcileRelease) {}
