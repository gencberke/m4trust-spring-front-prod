package com.m4trust.coreapi.contracts.api;

import com.m4trust.coreapi.contracts.infra.ContractBundleDigest;
import java.util.List;

/** ADR-016 private projection for {@code GET /internal/v1/contracts}. */
public record ContractBundleMetadata(
    String service,
    String releaseRevision,
    String contractBundleDigest,
    List<ContractBundleDigest.BundleFile> files) {}
