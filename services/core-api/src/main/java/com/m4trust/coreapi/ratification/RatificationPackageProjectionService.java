package com.m4trust.coreapi.ratification;

import java.util.Optional;
import java.util.UUID;

import com.m4trust.coreapi.organization.OperationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reuses the same wrapper/actor-aware projection {@link RatificationPackageReadService}
 * builds for the dedicated ratification endpoints so the Deal detail's
 * embedded package never drifts from what those endpoints report.
 */
@Service
class RatificationPackageProjectionService implements RatificationPackageProjectionPort {

    private final RatificationRepository packages;
    private final RatificationPackageReadService reads;

    RatificationPackageProjectionService(RatificationRepository packages, RatificationPackageReadService reads) {
        this.packages = packages;
        this.reads = reads;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findPackageStatus(UUID dealId, UUID packageId) {
        if (packageId == null) {
            return Optional.empty();
        }
        return packages.findByDealAndId(dealId, packageId).map(record -> record.status().name());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CurrentPackage> findCurrentPackage(
            OperationContext context, UUID dealId, String dealStatus, UUID packageId) {
        if (packageId == null) {
            return Optional.empty();
        }
        RatificationRepository.PackageRecord record = packages.findByDealAndId(dealId, packageId)
                .orElseThrow(() -> new IllegalStateException("Deal current ratification package is unavailable"));
        // Only dealId and status are read by RatificationPackageReadService#project;
        // the remaining Target fields are irrelevant to a read-only wrapper projection.
        RatificationSourcePorts.Target target = new RatificationSourcePorts.Target(
                dealId, context.tenantId(), dealStatus, 0, null, null, false, null, null, null, null, packageId);
        RatificationPackageReadDtos.Detail detail = reads.project(context, target, record);
        return Optional.of(toPort(detail));
    }

    private static CurrentPackage toPort(RatificationPackageReadDtos.Detail detail) {
        RatificationSnapshotAssembler.Snapshot snapshot = detail.snapshot();
        return new CurrentPackage(
                detail.id(),
                detail.version(),
                detail.status().name(),
                detail.contentHash(),
                new Snapshot(
                        snapshot.schemaVersion(),
                        snapshot.dealId(),
                        snapshot.dealReference(),
                        snapshot.dealTitle(),
                        party(snapshot.buyer()),
                        party(snapshot.seller()),
                        new RuleSet(
                                snapshot.ruleSet().ruleSetVersionId(),
                                snapshot.ruleSet().version(),
                                snapshot.ruleSet().rules().stream().map(RatificationPackageProjectionService::rule).toList()),
                        new Terms(snapshot.commercialTerms().amountMinor(), snapshot.commercialTerms().currency()),
                        new Document(
                                snapshot.document().documentId(),
                                snapshot.document().objectVersion(),
                                snapshot.document().sha256())),
                detail.approvals().stream()
                        .map(approval -> new Approval(approval.legalEntityId(), approval.legalName(),
                                approval.status(), approval.approvedAt(), approval.approverUserId()))
                        .toList(),
                new AvailableActions(detail.availableActions().canApprove(), detail.availableActions().canReject()),
                detail.createdAt());
    }

    private static Party party(RatificationSnapshotAssembler.Party party) {
        return new Party(party.legalEntityId(), party.legalName());
    }

    private static Rule rule(RatificationSnapshotAssembler.Rule rule) {
        return new Rule(rule.ruleReference(), rule.decision(), rule.category(), rule.title(),
                rule.description(), rule.structuredValue(), rule.legalBasis(), rule.legalBasisProvenance());
    }
}
