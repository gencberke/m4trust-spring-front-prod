package com.m4trust.coreapi.contractintelligence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class RatificationAcceptedRuleSetAdapterTest {
    private final ObjectMapper json = new ObjectMapper();
    private final RuleSetRepository ruleSets = mock(RuleSetRepository.class);
    private final RatificationAcceptedRuleSetAdapter adapter =
            new RatificationAcceptedRuleSetAdapter(ruleSets, json);

    @Test
    void mapsEveryFinalRuleAndReturnsIndependentJsonCopies() throws Exception {
        UUID ruleSetId = UUID.randomUUID();
        ObjectNode storedRules = (ObjectNode) json.readTree("""
                {"rules":[
                  {"ruleReference":"money","decision":"KEPT","category":"PAYMENT","title":"Payment","description":"Amount","structuredValue":{"type":"MONEY","amountMinor":99,"currency":"USD"},"legalBasis":{"source":"tbk-6098","articleNo":"1"},"legalBasisProvenance":"EXTRACTED"},
                  {"ruleReference":"boolean","decision":"ADDED","category":"OTHER","title":"Boolean","description":"Flag","structuredValue":{"type":"BOOLEAN","value":true},"legalBasis":null,"legalBasisProvenance":"MANUALLY_ADDED"}
                ]}
                """);
        String persisted = json.writeValueAsString(storedRules.get("rules"));
        when(ruleSets.findAny(ruleSetId)).thenReturn(Optional.of(row(ruleSetId, persisted)));

        var result = adapter.find(ruleSetId).orElseThrow();
        ((ObjectNode) storedRules.at("/rules/0/structuredValue")).put("amountMinor", 7);
        assertEquals(ruleSetId, result.ruleSetVersionId());
        assertEquals(1, result.version());
        assertEquals(2, result.rules().size());
        assertEquals("MONEY", result.rules().get(0).structuredValue().get("type").asText());
        assertEquals(99, result.rules().get(0).structuredValue().get("amountMinor").asLong());
        assertEquals("tbk-6098", result.rules().get(0).legalBasis().get("source").asText());
        assertEquals("BOOLEAN", result.rules().get(1).structuredValue().get("type").asText());
        assertEquals(null, result.rules().get(1).legalBasis());

        ((ObjectNode) result.rules().get(0).structuredValue()).put("amountMinor", 8);
        assertEquals(99, adapter.find(ruleSetId).orElseThrow().rules().get(0)
                .structuredValue().get("amountMinor").asLong());
    }

    @Test
    void returnsEmptyForUnknownRuleSetAndFailsMalformedStoredRules() {
        UUID absent = UUID.randomUUID();
        when(ruleSets.findAny(absent)).thenReturn(Optional.empty());
        assertFalse(adapter.find(absent).isPresent());

        UUID malformed = UUID.randomUUID();
        when(ruleSets.findAny(malformed)).thenReturn(Optional.of(row(malformed,
                "[{\"ruleReference\":\"missing fields\"}]")));
        assertThrows(IllegalStateException.class, () -> adapter.find(malformed));
    }

    private static RuleSetRepository.Row row(UUID id, String rules) {
        return new RuleSetRepository.Row(id, UUID.randomUUID(), 1, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-07-19T10:00:00Z"),
                null, rules, "[]");
    }
}
