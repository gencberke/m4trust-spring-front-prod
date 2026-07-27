package com.m4trust.coreapi.deal.api;

import com.m4trust.coreapi.deal.api.port.*;
import com.m4trust.coreapi.deal.domain.*;
import com.m4trust.coreapi.deal.infra.repository.*;
import com.m4trust.coreapi.ratification.domain.RatificationPackageProjectionPort;

/**
 * Backend-derived readiness plus the current package projection, if any. READY is never persisted:
 * it is derived from parties, an accepted rule-set, and a current AVAILABLE document existing
 * together.
 */
record DealRatificationProjection(
    DealRatificationReadiness readiness,
    RatificationPackageProjectionPort.CurrentPackage currentPackage) {}
