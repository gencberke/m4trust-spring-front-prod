package com.m4trust.coreapi.payment.api;

import com.m4trust.coreapi.payment.api.dto.*;
import com.m4trust.coreapi.payment.domain.*;
import com.m4trust.coreapi.payment.infra.*;

/** Wire shape of {@code CreateFundingPlanRequest}: carries only the Deal expectedVersion. */
record CreateFundingPlanHttpRequest(long expectedVersion) {}
