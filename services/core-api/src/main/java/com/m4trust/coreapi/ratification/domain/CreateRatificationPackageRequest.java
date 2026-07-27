package com.m4trust.coreapi.ratification.domain;

/** Exact terms and optimistic Deal version expected by a package create request. */
public record CreateRatificationPackageRequest(
    long expectedDealVersion,
    long amountMinor,
    String currency,
    Integer disputeWindowDays,
    String evidencePolicy) {}
