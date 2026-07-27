package com.m4trust.coreapi.ratification.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Package-private OpenAPI-shaped read projections; transport mapping is added with HTTP. */
public final class RatificationPackageReadDtos {
  private RatificationPackageReadDtos() {}

  public record Approval(
      UUID legalEntityId,
      String legalName,
      String status,
      Instant approvedAt,
      UUID approverUserId) {}

  public record AvailableActions(boolean canApprove, boolean canReject) {}

  public record Detail(
      UUID id,
      long version,
      RatificationPackageStatus status,
      String contentHash,
      RatificationSnapshotAssembler.Snapshot snapshot,
      List<Approval> approvals,
      AvailableActions availableActions,
      Instant createdAt) {
    public Detail {
      approvals = List.copyOf(approvals);
    }
  }

  public record History(List<Detail> items) {
    public History {
      items = List.copyOf(items);
    }
  }
}
