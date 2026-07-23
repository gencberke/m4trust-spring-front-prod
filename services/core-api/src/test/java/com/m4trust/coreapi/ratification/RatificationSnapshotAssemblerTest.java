package com.m4trust.coreapi.ratification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class RatificationSnapshotAssemblerTest {
    private static final UUID DEAL = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DOCUMENT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID RULE_SET = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final ObjectMapper json = new ObjectMapper();
    private final CanonicalSnapshotHasher hasher = new CanonicalSnapshotHasher();
    private final RatificationSnapshotAssembler assembler = new RatificationSnapshotAssembler(json, hasher);

    @Test
    void absentDisputeWindowKeepsV1SnapshotShapeAndHash() throws Exception {
        var result = assemble(rule("money", object("{\"type\":\"MONEY\",\"amountMinor\":99,\"currency\":\"USD\"}"), null));
        JsonNode root = json.readTree(result.serializedSnapshot());
        assertEquals(1, root.get("schemaVersion").asInt());
        assertFalse(root.has("disputeWindowDays"));
        assertEquals(result.contentHash(), hasher.hash(result.serializedSnapshot()));
    }

    @Test
    void disputeWindowZeroAndOneProduceV2WithDistinctHashes() throws Exception {
        var zero = assembleWithWindow(0, rule("r", textValue(), null));
        var one = assembleWithWindow(1, rule("r", textValue(), null));
        JsonNode zeroRoot = json.readTree(zero.serializedSnapshot());
        JsonNode oneRoot = json.readTree(one.serializedSnapshot());
        assertEquals(2, zeroRoot.get("schemaVersion").asInt());
        assertEquals(0, zeroRoot.get("disputeWindowDays").asInt());
        assertEquals(1, oneRoot.get("disputeWindowDays").asInt());
        assertEquals(Set.of("schemaVersion", "dealId", "dealReference", "dealTitle", "buyer", "seller", "ruleSet",
                "commercialTerms", "document", "disputeWindowDays"), names(zeroRoot));
        assertEquals(zero.contentHash(), hasher.hash(zero.serializedSnapshot()));
        assertEquals(one.contentHash(), hasher.hash(one.serializedSnapshot()));
        assertEquals(false, zero.contentHash().equals(one.contentHash()));
    }

    @Test
    void rejectsOutOfRangeDisputeWindowDays() {
        assertThrows(IllegalArgumentException.class, () -> assembleWithWindow(-1, rule("r", textValue(), null)));
        assertThrows(IllegalArgumentException.class, () -> assembleWithWindow(366, rule("r", textValue(), null)));
    }

    @Test
    void emitsOnlyEveryContractFieldWithCopiedValues() throws Exception {
        ObjectNode value = object("{\"type\":\"MONEY\",\"amountMinor\":99,\"currency\":\"USD\"}");
        ObjectNode basis = object("{\"source\":\"tbk-6098\",\"articleNo\":\"1\"}");
        var result = assemble(rule("money", value, basis));

        JsonNode root = json.readTree(result.serializedSnapshot());
        assertEquals(Set.of("schemaVersion", "dealId", "dealReference", "dealTitle", "buyer", "seller", "ruleSet", "commercialTerms", "document"), names(root));
        assertEquals(Set.of("legalEntityId", "legalName"), names(root.get("buyer")));
        assertEquals(Set.of("legalEntityId", "legalName"), names(root.get("seller")));
        assertEquals(Set.of("ruleSetVersionId", "version", "rules"), names(root.get("ruleSet")));
        assertEquals(Set.of("ruleReference", "decision", "category", "title", "description", "structuredValue", "legalBasis", "legalBasisProvenance"), names(root.at("/ruleSet/rules/0")));
        assertEquals(Set.of("type", "amountMinor", "currency"), names(root.at("/ruleSet/rules/0/structuredValue")));
        assertEquals(Set.of("source", "articleNo"), names(root.at("/ruleSet/rules/0/legalBasis")));
        assertEquals(Set.of("amountMinor", "currency"), names(root.get("commercialTerms")));
        assertEquals(Set.of("documentId", "objectVersion", "sha256"), names(root.get("document")));
        assertEquals("DL-0000000001", root.get("dealReference").asText());
        assertEquals("Deal", root.get("dealTitle").asText());
        assertEquals("Buyer", root.at("/buyer/legalName").asText());
        assertEquals("Seller", root.at("/seller/legalName").asText());
        assertEquals(1, root.at("/ruleSet/version").asLong());
        assertEquals(99, root.at("/ruleSet/rules/0/structuredValue/amountMinor").asLong());
        assertEquals("tbk-6098", root.at("/ruleSet/rules/0/legalBasis/source").asText());
        assertEquals(123, root.at("/commercialTerms/amountMinor").asLong());
        assertEquals("TRY", root.at("/commercialTerms/currency").asText());
        assertEquals("v1", root.at("/document/objectVersion").asText());
    }

    @Test
    void copiesNodesOnInputAndEveryAccessorBoundary() throws Exception {
        ObjectNode value = object("{\"type\":\"TEXT\",\"value\":\"original\"}");
        ObjectNode basis = object("{\"source\":\"tbk-6098\",\"articleNo\":\"1\"}");
        var result = assemble(rule("copy", value, basis));
        String before = json.writeValueAsString(result.snapshot());
        String hash = hasher.hash(before);

        value.put("value", "input mutation");
        basis.put("articleNo", "2");
        ((ObjectNode) result.snapshot().ruleSet().rules().get(0).structuredValue()).put("value", "accessor mutation");
        ((ObjectNode) result.snapshot().ruleSet().rules().get(0).legalBasis()).put("articleNo", "3");

        String after = json.writeValueAsString(result.snapshot());
        assertEquals(before, after);
        assertEquals(hash, hasher.hash(after));
        assertEquals(result.serializedSnapshot(), after);
        assertEquals(result.contentHash(), hasher.hash(result.serializedSnapshot()));
    }

    @Test
    void ordersReferencesByUtf8AndRejectsDuplicateReferences() throws Exception {
        var result = assemble(
                rule("\uD800\uDC00", object("{\"type\":\"TEXT\",\"value\":\"a\"}"), null),
                rule("\uE000", object("{\"type\":\"TEXT\",\"value\":\"b\"}"), null));
        assertEquals("\uE000", json.readTree(result.serializedSnapshot()).at("/ruleSet/rules/0/ruleReference").asText());
        assertThrows(IllegalArgumentException.class, () -> assemble(
                rule("duplicate", object("{\"type\":\"TEXT\",\"value\":\"a\"}"), null),
                rule("duplicate", object("{\"type\":\"TEXT\",\"value\":\"b\"}"), null)));
    }

    @Test
    void acceptsEveryExactStructuredValueVariant() throws Exception {
        assertSnapshot(rule("text", object("{\"type\":\"TEXT\",\"value\":\"\"}"), null));
        assertSnapshot(rule("money", object("{\"type\":\"MONEY\",\"amountMinor\":0,\"currency\":\"USD\"}"), null));
        assertSnapshot(rule("percentage", object("{\"type\":\"PERCENTAGE\",\"basisPoints\":10000}"), null));
        assertSnapshot(rule("duration", object("{\"type\":\"DURATION\",\"valueSeconds\":0}"), null));
        assertSnapshot(rule("date", object("{\"type\":\"DATE\",\"value\":\"2026-07-19\"}"), null));
        assertSnapshot(rule("boolean", object("{\"type\":\"BOOLEAN\",\"value\":false}"), null));
        assertSnapshot(rule("quantity", object("{\"type\":\"QUANTITY\",\"value\":1.5,\"unit\":\"kg\"}"), null));
    }

    @Test
    void rejectsInvalidSourcesBoundariesAndNestedValues() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> assemble(rule("r", object("{\"type\":\"MONEY\",\"amountMinor\":-1,\"currency\":\"USD\"}"), null)));
        assertThrows(IllegalArgumentException.class, () -> assemble(rule("r", object("{\"type\":\"MONEY\",\"amountMinor\":9007199254740992,\"currency\":\"USD\"}"), null)));
        assertThrows(IllegalArgumentException.class, () -> assemble(rule("r", object("{\"type\":\"PERCENTAGE\",\"basisPoints\":10001}"), null)));
        assertThrows(IllegalArgumentException.class, () -> assemble(rule("r", object("{\"type\":\"DATE\",\"value\":\"not-a-date\"}"), null)));
        assertThrows(IllegalArgumentException.class, () -> assemble(rule("r", object("{\"type\":\"TEXT\",\"value\":\"x\",\"extra\":1}"), null)));
        assertThrows(IllegalArgumentException.class, () -> assemble(rule("r", object("{\"type\":\"TEXT\",\"value\":9007199254740992}"), null)));
        assertThrows(IllegalArgumentException.class, () -> assemble(rule("r", object("{\"type\":\"TEXT\",\"value\":\"x\"}"), object("{\"source\":\"invalid\",\"articleNo\":\"1\"}"))));
        assertThrows(IllegalArgumentException.class, () -> assemble(rule("r", object("{\"type\":\"TEXT\",\"value\":\"x\"}"), object("{\"source\":\"tbk-6098\",\"articleNo\":\"\"}"))));
        assertThrows(IllegalArgumentException.class, () -> assembler.assemble(target(), document(), rules(0, List.of(rule("r", textValue(), null))), 1, "TRY"));
        assertThrows(IllegalArgumentException.class, () -> assembler.assemble(target(), document(), rules(RatificationSnapshotAssembler.MAX_SAFE_INTEGER + 1, List.of(rule("r", textValue(), null))), 1, "TRY"));
        assertThrows(IllegalArgumentException.class, () -> assembler.assemble(target(), document(), rules(1, List.of(rule("r", textValue(), null))), 0, "TRY"));
        assertThrows(IllegalArgumentException.class, () -> assembler.assemble(target(), document(), rules(1, List.of(rule("r", textValue(), null))), RatificationSnapshotAssembler.MAX_SAFE_INTEGER + 1, "TRY"));
    }

    @Test
    void rejectsCurrentPointersAndRuleSetDealMismatches() throws Exception {
        var original = target();
        var wrongDocumentPointer = new RatificationSourcePorts.Target(
                original.dealId(), original.tenantId(), original.status(), original.version(), original.reference(), original.title(),
                original.initiator(), original.buyer(), original.seller(), UUID.randomUUID(), original.currentRuleSetId(), null);
        assertThrows(IllegalArgumentException.class, () -> assembler.assemble(wrongDocumentPointer, document(), rules(1, List.of(rule("r", textValue(), null))), 1, "TRY"));
        var wrongRulePointer = new RatificationSourcePorts.Target(
                original.dealId(), original.tenantId(), original.status(), original.version(), original.reference(), original.title(),
                original.initiator(), original.buyer(), original.seller(), original.currentDocumentId(), UUID.randomUUID(), null);
        assertThrows(IllegalArgumentException.class, () -> assembler.assemble(wrongRulePointer, document(), rules(1, List.of(rule("r", textValue(), null))), 1, "TRY"));
        var wrongRuleDeal = new RatificationSourcePorts.RuleSet(RULE_SET, UUID.randomUUID(), 1, List.of(rule("r", textValue(), null)));
        assertThrows(IllegalArgumentException.class, () -> assembler.assemble(target(), document(), wrongRuleDeal, 1, "TRY"));
    }

    private void assertSnapshot(RatificationSourcePorts.Rule rule) {
        assembler.assemble(target(), document(), rules(1, List.of(rule)), 1, "TRY");
    }

    private RatificationSnapshotAssembler.Result assemble(RatificationSourcePorts.Rule... rules) {
        return assembler.assemble(target(), document(), rules(1, List.of(rules)), 123, "TRY");
    }

    private RatificationSnapshotAssembler.Result assembleWithWindow(
            int disputeWindowDays, RatificationSourcePorts.Rule... rules) {
        return assembler.assemble(target(), document(), rules(1, List.of(rules)), 123, "TRY", disputeWindowDays);
    }

    private Set<String> names(JsonNode node) {
        return new java.util.HashSet<>(node.propertyNames());
    }

    private RatificationSourcePorts.Target target() {
        return new RatificationSourcePorts.Target(
                DEAL, UUID.randomUUID(), "DRAFT", 1, "DL-0000000001", "Deal", true,
                new RatificationSourcePorts.Party(UUID.randomUUID(), "Buyer"),
                new RatificationSourcePorts.Party(UUID.randomUUID(), "Seller"), DOCUMENT, RULE_SET, null);
    }

    private RatificationSourcePorts.Document document() {
        return new RatificationSourcePorts.Document(DOCUMENT, DEAL, "v1", "a".repeat(64));
    }

    private RatificationSourcePorts.RuleSet rules(long version, List<RatificationSourcePorts.Rule> rules) {
        return new RatificationSourcePorts.RuleSet(RULE_SET, DEAL, version, rules);
    }

    private RatificationSourcePorts.Rule rule(String reference, ObjectNode value, ObjectNode legalBasis) {
        return new RatificationSourcePorts.Rule(reference, "KEPT", "PAYMENT", "Title", "Description", value, legalBasis, "EXTRACTED");
    }

    private ObjectNode textValue() throws Exception {
        return object("{\"type\":\"TEXT\",\"value\":\"x\"}");
    }

    private ObjectNode object(String source) throws Exception {
        return (ObjectNode) json.readTree(source);
    }
}
