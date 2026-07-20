package com.m4trust.coreapi.ratification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.m4trust.coreapi.organization.OperationContext;
import tools.jackson.databind.JsonNode;

/**
 * Ratification-owned projection port consumed by the Deal detail's embedded
 * ratification projection. The Deal module reaches ratification data only
 * through this narrow port; ratification never reads the Deal repository
 * (ADR-003 §23) and this port's implementation never crosses back into deal.
 * The returned shape mirrors the public {@code RatificationPackageDetail}
 * contract schema field-for-field; the wrapper (status, approvals,
 * actor-aware availableActions) is never part of the immutable snapshot hash.
 */
public interface RatificationPackageProjectionPort {

    Optional<CurrentPackage> findCurrentPackage(
            OperationContext context, UUID dealId, String dealStatus, UUID packageId);

    record CurrentPackage(UUID id, long version, String status, String contentHash,
            Snapshot snapshot, List<Approval> approvals, AvailableActions availableActions,
            Instant createdAt) {
        public CurrentPackage {
            approvals = List.copyOf(approvals);
        }
    }

    record Snapshot(int schemaVersion, String dealId, String dealReference, String dealTitle,
            Party buyer, Party seller, RuleSet ruleSet, Terms commercialTerms, Document document) {
    }

    record Party(String legalEntityId, String legalName) {
    }

    record RuleSet(String ruleSetVersionId, long version, List<Rule> rules) {
        public RuleSet {
            rules = List.copyOf(rules);
        }
    }

    record Rule(String ruleReference, String decision, String category, String title,
            String description, JsonNode structuredValue, JsonNode legalBasis,
            String legalBasisProvenance) {
    }

    record Terms(long amountMinor, String currency) {
    }

    record Document(String documentId, String objectVersion, String sha256) {
    }

    record Approval(UUID legalEntityId, String legalName, String status,
            Instant approvedAt, UUID approverUserId) {
    }

    record AvailableActions(boolean canApprove, boolean canReject) {
    }
}
