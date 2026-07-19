package com.m4trust.coreapi.ratification;

/** Exact terms and optimistic Deal version expected by a package create request. */
record CreateRatificationPackageRequest(long expectedDealVersion, long amountMinor, String currency) { }
