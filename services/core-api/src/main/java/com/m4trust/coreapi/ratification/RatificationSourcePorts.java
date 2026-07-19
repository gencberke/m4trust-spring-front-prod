package com.m4trust.coreapi.ratification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.m4trust.coreapi.organization.OperationContext;
import tools.jackson.databind.JsonNode;

/** Consumer-owned contracts; source modules implement these without ratification reading their repositories. */
public final class RatificationSourcePorts {
    private RatificationSourcePorts() { }
    public interface DealTarget {
        Optional<Target> findVisible(OperationContext context, UUID dealId);
        Optional<Target> lockVisibleForCreate(OperationContext context, UUID dealId);
    }
    public interface AvailableDocument { Optional<Document> find(UUID documentId); }
    public interface AcceptedRuleSet { Optional<RuleSet> find(UUID ruleSetId); }
    public record Target(UUID dealId, UUID tenantId, String status, long version, String reference, String title,
            boolean initiator, Party buyer, Party seller, UUID currentDocumentId, UUID currentRuleSetId, UUID currentPackageId) { }
    public record Party(UUID legalEntityId, String legalName) { }
    public record Document(UUID documentId, UUID dealId, String objectVersion, String sha256) { }
    public record RuleSet(UUID ruleSetVersionId, UUID dealId, long version, List<Rule> rules) { public RuleSet { rules=List.copyOf(rules); } }
    public record Rule(String ruleReference, String decision, String category, String title, String description,
            JsonNode structuredValue, JsonNode legalBasis, String legalBasisProvenance) { }
}
