package com.m4trust.coreapi.deal;

import java.util.UUID;

/** Wire shape of the optional {@code DealDetail.funding} (contract {@code DealFundingSummary}). */
record DealFundingSummary(String fundingStatus, UUID fundingPlanId, Long amountMinor, String currency) {
}
