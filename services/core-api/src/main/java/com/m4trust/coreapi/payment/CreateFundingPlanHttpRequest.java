package com.m4trust.coreapi.payment;

/** Wire shape of {@code CreateFundingPlanRequest}: carries only the Deal expectedVersion. */
record CreateFundingPlanHttpRequest(long expectedVersion) { }
