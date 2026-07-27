package com.m4trust.coreapi.deal.api;

import com.m4trust.coreapi.deal.api.port.*;
import com.m4trust.coreapi.deal.domain.*;
import com.m4trust.coreapi.deal.infra.repository.*;
import java.util.UUID;

/** Wire shape of the optional {@code DealDetail.funding} (contract {@code DealFundingSummary}). */
record DealFundingSummary(
    String fundingStatus, UUID fundingPlanId, Long amountMinor, String currency) {}
