package com.m4trust.coreapi.ratification.api.dto;

import com.m4trust.coreapi.ratification.domain.RatificationPackageStatus;
import com.m4trust.coreapi.ratification.domain.RatificationSnapshotAssembler;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RatificationPackageDetail(
    UUID id,
    long version,
    RatificationPackageStatus status,
    String contentHash,
    RatificationSnapshotAssembler.Snapshot snapshot,
    List<RatificationPackageApproval> approvals,
    RatificationPackageAvailableActions availableActions,
    Instant createdAt) {
  public RatificationPackageDetail {
    approvals = List.copyOf(approvals);
  }
}
