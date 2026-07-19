package com.m4trust.coreapi.contractintelligence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** The sole JSON boundary for review acceptance.  Services receive typed values only. */
final class ReviewAcceptanceRequestDecoder {
    record Request(UUID analysisId, long expectedVersion, List<Decision> decisions) { }
    sealed interface Decision permits Kept, Modified, Excluded, Added { }
    record Kept(String ruleReference) implements Decision { }
    record Excluded(String ruleReference) implements Decision { }
    record Modified(String ruleReference, JsonNode category, JsonNode title,
            JsonNode description, JsonNode structuredValue) implements Decision { }
    record Added(JsonNode category, JsonNode title, JsonNode description,
            JsonNode structuredValue) implements Decision { }

    Request decode(JsonNode root) {
        if (!objectWith(root, Set.of("analysisId", "expectedVersion", "decisions"))
                || !root.path("analysisId").isTextual()
                || !root.path("expectedVersion").isIntegralNumber()
                || root.path("expectedVersion").asLong() < 0
                || !root.path("decisions").isArray()) throw new AnalysisExceptions.MalformedRequest();
        UUID analysisId;
        try { analysisId = UUID.fromString(root.path("analysisId").asText()); }
        catch (IllegalArgumentException ex) { throw new AnalysisExceptions.MalformedRequest(); }
        List<Decision> decisions = new ArrayList<>();
        for (JsonNode node : root.path("decisions")) decisions.add(decision(node));
        return new Request(analysisId, root.path("expectedVersion").asLong(), List.copyOf(decisions));
    }
    String canonicalHash(UUID entityId, UUID dealId, Request request) {
        StringBuilder text = new StringBuilder(entityId + "\n" + dealId + "\n" + request.analysisId + "\n" + request.expectedVersion);
        for (Decision decision : request.decisions) {
            text.append('\n').append(decision.getClass().getSimpleName());
            if (decision instanceof Kept d) text.append('|').append(d.ruleReference());
            if (decision instanceof Excluded d) text.append('|').append(d.ruleReference());
            if (decision instanceof Modified d) text.append('|').append(d.ruleReference()).append('|').append(canonical(d.category())).append('|').append(canonical(d.title())).append('|').append(canonical(d.description())).append('|').append(canonical(d.structuredValue()));
            if (decision instanceof Added d) text.append('|').append(canonical(d.category())).append('|').append(canonical(d.title())).append('|').append(canonical(d.description())).append('|').append(canonical(d.structuredValue()));
        }
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException("SHA-256 is unavailable", ex); }
    }
    private static String canonical(JsonNode node) { if (!node.isObject()) return node.toString(); java.util.List<String> names=new java.util.ArrayList<>();node.properties().forEach(e->names.add(e.getKey()));java.util.Collections.sort(names);StringBuilder result=new StringBuilder("{");for(String name:names)result.append(name).append(':').append(canonical(node.get(name))).append(';');return result.append('}').toString(); }
    private Decision decision(JsonNode node) {
        if (!node.isObject() || !node.path("decision").isTextual()) throw new AnalysisExceptions.MalformedRequest();
        return switch (node.path("decision").asText()) {
            case "KEPT" -> new Kept(reference(node, Set.of("decision", "ruleReference")));
            case "EXCLUDED" -> new Excluded(reference(node, Set.of("decision", "ruleReference")));
            case "MODIFIED" -> modified(node, true);
            case "ADDED" -> added(node);
            default -> throw new AnalysisExceptions.MalformedRequest();
        };
    }
    private Modified modified(JsonNode node, boolean referenced) { if (!objectWith(node, Set.of("decision","ruleReference","category","title","description","structuredValue"))) throw new AnalysisExceptions.MalformedRequest(); return new Modified(reference(node, Set.of()),node.get("category"),node.get("title"),node.get("description"),node.get("structuredValue")); }
    private Added added(JsonNode node) { if (!objectWith(node, Set.of("decision","category","title","description","structuredValue"))) throw new AnalysisExceptions.MalformedRequest(); return new Added(node.get("category"),node.get("title"),node.get("description"),node.get("structuredValue")); }
    private static String reference(JsonNode node, Set<String> ignored) { if (!node.path("ruleReference").isTextual() || node.path("ruleReference").asText().isBlank()) throw new AnalysisExceptions.MalformedRequest(); return node.path("ruleReference").asText(); }
    private static boolean objectWith(JsonNode node, Set<String> fields) { if (!node.isObject()) return false; java.util.Set<String> actual=new java.util.HashSet<>(); node.properties().forEach(e->actual.add(e.getKey())); return actual.equals(fields); }
}
