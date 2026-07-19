package com.m4trust.coreapi.ratification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Package-private OpenAPI-shaped read projections; transport mapping is added with HTTP. */
final class RatificationPackageReadDtos {
    private RatificationPackageReadDtos() { }

    record Approval(UUID legalEntityId, String legalName, String status,
            Instant approvedAt, UUID approverUserId) { }

    record AvailableActions(boolean canApprove, boolean canReject) { }

    record Detail(UUID id, long version, RatificationPackageStatus status,
            String contentHash, RatificationSnapshotAssembler.Snapshot snapshot,
            List<Approval> approvals, AvailableActions availableActions, Instant createdAt) {
        Detail {
            approvals = List.copyOf(approvals);
        }
    }

    record History(List<Detail> items) {
        History {
            items = List.copyOf(items);
        }
    }
}
