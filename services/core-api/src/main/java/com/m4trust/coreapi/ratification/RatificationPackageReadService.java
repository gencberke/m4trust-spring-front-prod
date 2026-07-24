package com.m4trust.coreapi.ratification;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.m4trust.coreapi.organization.LegalEntityRole;
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Read-only, participant-scoped package projection with stored-data invariant checks. */
@Service
class RatificationPackageReadService {
    private final RatificationSourcePorts.DealTarget deals;
    private final RatificationRepository packages;
    private final ObjectMapper json;
    private final CanonicalSnapshotHasher hasher;

    RatificationPackageReadService(
            RatificationSourcePorts.DealTarget deals,
            RatificationRepository packages,
            ObjectMapper json,
            CanonicalSnapshotHasher hasher) {
        this.deals = deals;
        this.packages = packages;
        this.json = json;
        this.hasher = hasher;
    }

    @Transactional(readOnly = true)
    RatificationPackageReadDtos.Detail detail(
            OperationContext context, UUID dealId, UUID packageId) {
        require(context, RequestedOperation.DEAL_RATIFICATION_PACKAGE_READ);
        RatificationSourcePorts.Target target = visible(context, dealId);
        RatificationRepository.PackageRecord packageRecord = packages.findByDealAndId(dealId, packageId)
                .orElseThrow(PackageNotFound::new);
        return project(context, target, packageRecord);
    }

    @Transactional(readOnly = true)
    RatificationPackageReadDtos.History history(OperationContext context, UUID dealId) {
        require(context, RequestedOperation.DEAL_RATIFICATION_PACKAGE_HISTORY_READ);
        RatificationSourcePorts.Target target = visible(context, dealId);
        return new RatificationPackageReadDtos.History(packages.listByDealId(dealId).stream()
                .map(packageRecord -> project(context, target, packageRecord))
                .toList());
    }

    private RatificationSourcePorts.Target visible(OperationContext context, UUID dealId) {
        return deals.findVisible(context, dealId).orElseThrow(PackageNotFound::new);
    }

    /** Internal projection shared by package mutations after they have obtained deal visibility. */
    RatificationPackageReadDtos.Detail project(
            OperationContext context,
            RatificationSourcePorts.Target target,
            RatificationRepository.PackageRecord packageRecord) {
        RatificationSnapshotAssembler.Snapshot snapshot = parseAndVerifySnapshot(packageRecord);
        if (!packageRecord.dealId().equals(target.dealId())) {
            throw corruption();
        }
        verifyWrapper(packageRecord, snapshot);
        List<RatificationRepository.ApprovalRecord> storedApprovals = packages.listApprovals(packageRecord.id());
        List<RatificationPackageReadDtos.Approval> approvals = approvalProjection(context, packageRecord,
                snapshot, storedApprovals);
        return new RatificationPackageReadDtos.Detail(packageRecord.id(), packageRecord.version(),
                packageRecord.status(), packageRecord.contentHash(), snapshot, approvals,
                availableActions(context, target, packageRecord, storedApprovals), packageRecord.createdAt());
    }

    /**
     * Wrapper-only, actor-aware projection; never part of the canonical
     * snapshot or contentHash input. Mirrors RatificationPackageActionService's
     * actual acceptance rules exactly so this projection never advertises an
     * action the action service would refuse: PENDING package, DRAFT Deal, the
     * active legal entity is the package buyer or seller, and the active
     * membership role is ADMIN. Approve additionally requires that entity has
     * not already recorded an effective approval; reject has no such
     * restriction because an entity that already approved may still reject
     * while the package remains PENDING.
     */
    private static RatificationPackageReadDtos.AvailableActions availableActions(
            OperationContext context,
            RatificationSourcePorts.Target target,
            RatificationRepository.PackageRecord packageRecord,
            List<RatificationRepository.ApprovalRecord> storedApprovals) {
        boolean assignedParty = context.activeLegalEntityId().equals(packageRecord.buyerLegalEntityId())
                || context.activeLegalEntityId().equals(packageRecord.sellerLegalEntityId());
        boolean baseEligible = packageRecord.status() == RatificationPackageStatus.PENDING
                && "DRAFT".equals(target.status())
                && assignedParty
                && context.activeLegalEntityRole() == LegalEntityRole.ADMIN;
        boolean alreadyApproved = baseEligible && storedApprovals.stream()
                .anyMatch(approval -> approval.legalEntityId().equals(context.activeLegalEntityId()));
        return new RatificationPackageReadDtos.AvailableActions(baseEligible && !alreadyApproved, baseEligible);
    }

    private RatificationSnapshotAssembler.Snapshot parseAndVerifySnapshot(
            RatificationRepository.PackageRecord packageRecord) {
        try {
            Integer storedSchemaVersion = packageRecord.snapshotSchemaVersion();
            if (storedSchemaVersion == null
                    || (storedSchemaVersion != 1 && storedSchemaVersion != 2 && storedSchemaVersion != 3)
                    || !packageRecord.contentHash().matches("[a-f0-9]{64}")) {
                throw corruption();
            }
            JsonNode raw = json.readTree(packageRecord.canonicalSnapshot());
            RatificationSnapshotAssembler.Snapshot snapshot = json.treeToValue(
                    raw, RatificationSnapshotAssembler.Snapshot.class);
            if (snapshot.schemaVersion() != storedSchemaVersion
                    || !hasher.hash(packageRecord.canonicalSnapshot()).equals(packageRecord.contentHash())
                    || !hasher.hash(json.writeValueAsString(snapshot)).equals(packageRecord.contentHash())) {
                throw corruption();
            }
            if (storedSchemaVersion == 1) {
                if (snapshot.disputeWindowDays() != null || snapshot.evidencePolicy() != null) {
                    throw corruption();
                }
            } else if (storedSchemaVersion == 2) {
                if (snapshot.disputeWindowDays() == null
                        || snapshot.disputeWindowDays() < 0
                        || snapshot.disputeWindowDays() > 365
                        || snapshot.evidencePolicy() != null) {
                    throw corruption();
                }
            } else {
                if (snapshot.disputeWindowDays() == null
                        || snapshot.disputeWindowDays() < 0
                        || snapshot.disputeWindowDays() > 365
                        || snapshot.evidencePolicy() == null
                        || (!"REQUIRED".equals(snapshot.evidencePolicy())
                                && !"NOT_REQUIRED".equals(snapshot.evidencePolicy()))) {
                    throw corruption();
                }
            }
            return snapshot;
        } catch (Exception exception) {
            if (exception instanceof InvariantViolation invariant) {
                throw invariant;
            }
            throw corruption();
        }
    }

    private static void verifyWrapper(
            RatificationRepository.PackageRecord packageRecord,
            RatificationSnapshotAssembler.Snapshot snapshot) {
        if (!snapshot.dealId().equals(packageRecord.dealId().toString())
                || snapshot.buyer() == null
                || snapshot.seller() == null
                || snapshot.buyer().legalEntityId().equals(snapshot.seller().legalEntityId())
                || !snapshot.buyer().legalEntityId().equals(packageRecord.buyerLegalEntityId().toString())
                || !snapshot.seller().legalEntityId().equals(packageRecord.sellerLegalEntityId().toString())
                || snapshot.commercialTerms().amountMinor() != packageRecord.amountMinor()
                || !snapshot.commercialTerms().currency().equals(packageRecord.currency())) {
            throw corruption();
        }
    }

    private static List<RatificationPackageReadDtos.Approval> approvalProjection(
            OperationContext context,
            RatificationRepository.PackageRecord packageRecord,
            RatificationSnapshotAssembler.Snapshot snapshot,
            List<RatificationRepository.ApprovalRecord> storedApprovals) {
        UUID buyer = packageRecord.buyerLegalEntityId();
        UUID seller = packageRecord.sellerLegalEntityId();
        Map<UUID, RatificationRepository.ApprovalRecord> byEntity = new HashMap<>();
        for (RatificationRepository.ApprovalRecord approval : storedApprovals) {
            if (!approval.packageId().equals(packageRecord.id())
                    || (!approval.legalEntityId().equals(buyer) && !approval.legalEntityId().equals(seller))
                    || byEntity.put(approval.legalEntityId(), approval) != null) {
                throw corruption();
            }
        }
        return List.of(approval(context, buyer, snapshot.buyer().legalName(), byEntity.get(buyer)),
                approval(context, seller, snapshot.seller().legalName(), byEntity.get(seller)));
    }

    private static RatificationPackageReadDtos.Approval approval(
            OperationContext context,
            UUID legalEntityId,
            String legalName,
            RatificationRepository.ApprovalRecord stored) {
        return stored == null
                ? new RatificationPackageReadDtos.Approval(legalEntityId, legalName, "PENDING", null, null)
                : new RatificationPackageReadDtos.Approval(legalEntityId, legalName, "APPROVED",
                        stored.approvedAt(), context.activeLegalEntityId().equals(legalEntityId)
                                ? stored.approvedByUserId() : null);
    }

    private static void require(OperationContext context, RequestedOperation expected) {
        if (context.requestedOperation() != expected) {
            throw new IllegalArgumentException("Operation context does not match ratification read");
        }
    }

    private static InvariantViolation corruption() {
        return new InvariantViolation();
    }

    static final class PackageNotFound extends RuntimeException { }
    static final class InvariantViolation extends RuntimeException { }
}
