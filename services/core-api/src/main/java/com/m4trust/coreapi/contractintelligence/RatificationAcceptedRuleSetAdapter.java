package com.m4trust.coreapi.contractintelligence;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.m4trust.coreapi.ratification.RatificationSourcePorts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Contract-intelligence-owned source for an accepted, immutable rule-set version. */
@Service
class RatificationAcceptedRuleSetAdapter implements RatificationSourcePorts.AcceptedRuleSet {
    private final RuleSetRepository ruleSets;
    private final ObjectMapper json;
    private final ReviewAcceptanceRequestDecoder decoder = new ReviewAcceptanceRequestDecoder();

    RatificationAcceptedRuleSetAdapter(RuleSetRepository ruleSets, ObjectMapper json) {
        this.ruleSets = ruleSets;
        this.json = json;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RatificationSourcePorts.RuleSet> find(UUID ruleSetId) {
        return ruleSets.findAny(ruleSetId).map(this::toSourceRuleSet);
    }

    private RatificationSourcePorts.RuleSet toSourceRuleSet(RuleSetRepository.Row row) {
        try {
            JsonNode rulesNode = json.readTree(row.rules());
            if (!rulesNode.isArray()) {
                throw new IllegalArgumentException("Stored rules are not an array");
            }
            List<RatificationSourcePorts.Rule> rules = new ArrayList<>();
            for (JsonNode ruleNode : rulesNode) {
                rules.add(toSourceRule(decoder.decodeRuleSetRule(ruleNode)));
            }
            return new RatificationSourcePorts.RuleSet(row.id(), row.dealId(), row.version(), rules);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored accepted rule set is malformed", exception);
        }
    }

    private RatificationSourcePorts.Rule toSourceRule(ReviewDtos.RuleSetRule rule) throws Exception {
        return new RatificationSourcePorts.Rule(
                rule.ruleReference(),
                rule.decision(),
                rule.category(),
                rule.title(),
                rule.description(),
                json.readTree(json.writeValueAsString(rule.structuredValue())),
                rule.legalBasis() == null ? null : json.readTree(json.writeValueAsString(rule.legalBasis())),
                rule.legalBasisProvenance());
    }
}
