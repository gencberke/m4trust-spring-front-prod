package com.m4trust.coreapi.ratification;

import java.nio.charset.StandardCharsets;
import java.util.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Pure V1 snapshot assembler: only this closed value is passed to the JCS hasher. */
final class RatificationSnapshotAssembler {
    static final long MAX_SAFE_INTEGER = 9007199254740991L;
    private final ObjectMapper json;
    private final CanonicalSnapshotHasher hasher;
    RatificationSnapshotAssembler(ObjectMapper json, CanonicalSnapshotHasher hasher) { this.json=json; this.hasher=hasher; }
    Result assemble(RatificationSourcePorts.Target target, RatificationSourcePorts.Document document,
            RatificationSourcePorts.RuleSet ruleSet, long amountMinor, String currency) {
        if (amountMinor < 1 || amountMinor > MAX_SAFE_INTEGER || currency == null || !currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("Invalid exact commercial terms");
        if (!target.dealId().equals(document.dealId()) || target.buyer()==null || target.seller()==null || target.buyer().legalEntityId().equals(target.seller().legalEntityId())) throw new IllegalArgumentException("Invalid snapshot sources");
        List<RatificationSourcePorts.Rule> rules = new ArrayList<>(ruleSet.rules());
        rules.sort((a,b) -> Arrays.compareUnsigned(a.ruleReference().getBytes(StandardCharsets.UTF_8), b.ruleReference().getBytes(StandardCharsets.UTF_8)));
        Set<String> seen=new HashSet<>(); for (var rule:rules) if (!seen.add(rule.ruleReference())) throw new IllegalArgumentException("Duplicate ruleReference");
        Snapshot snapshot=new Snapshot(1, uuid(target.dealId()), target.reference(), target.title(), party(target.buyer()), party(target.seller()),
                new RuleSet(uuid(ruleSet.ruleSetVersionId()), ruleSet.version(), rules.stream().map(this::rule).toList()), new Terms(amountMinor,currency), new Document(uuid(document.documentId()),document.objectVersion(),hex(document.sha256())));
        try { String value=json.writeValueAsString(snapshot); return new Result(snapshot, hasher.hash(value)); } catch(Exception e){ throw new IllegalStateException(e); }
    }
    private Party party(RatificationSourcePorts.Party p){return new Party(uuid(p.legalEntityId()),p.legalName());}
    private Rule rule(RatificationSourcePorts.Rule r){return new Rule(r.ruleReference(),r.decision(),r.category(),r.title(),r.description(),r.structuredValue(),r.legalBasis(),r.legalBasisProvenance());}
    private static String uuid(UUID id){String s=Objects.requireNonNull(id).toString(); if(!s.matches("[0-9a-f-]{36}"))throw new IllegalArgumentException();return s;}
    private static String hex(String value){if(value==null||!value.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid sha256");return value;}
    record Result(Snapshot snapshot,String contentHash) { }
    record Snapshot(int schemaVersion,String dealId,String dealReference,String dealTitle,Party buyer,Party seller,RuleSet ruleSet,Terms commercialTerms,Document document) { }
    record Party(String legalEntityId,String legalName) { }
    record RuleSet(String ruleSetVersionId,long version,List<Rule> rules) { public RuleSet {rules=List.copyOf(rules);} }
    record Rule(String ruleReference,String decision,String category,String title,String description,JsonNode structuredValue,JsonNode legalBasis,String legalBasisProvenance) { }
    record Terms(long amountMinor,String currency) { }
    record Document(String documentId,String objectVersion,String sha256) { }
}
