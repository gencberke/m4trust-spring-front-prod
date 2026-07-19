package com.m4trust.coreapi.ratification;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class RatificationSnapshotAssemblerTest {
    private final ObjectMapper json = new ObjectMapper();
    private final CanonicalSnapshotHasher hasher = new CanonicalSnapshotHasher();
    private final RatificationSnapshotAssembler assembler = new RatificationSnapshotAssembler(json, hasher);

    @Test void emitsOnlyClosedSnapshotFieldsAndExplicitTerms() throws Exception {
        ObjectNode money = (ObjectNode) json.readTree("{\"type\":\"MONEY\",\"amountMinor\":99,\"currency\":\"USD\"}");
        var result = assembler.assemble(target(), document(), rules(List.of(rule("money", money, null))), 123, "TRY");
        var root = json.readTree(result.serializedSnapshot());
        assertEquals(Set.of("schemaVersion","dealId","dealReference","dealTitle","buyer","seller","ruleSet","commercialTerms","document"), names(root));
        assertEquals(Set.of("legalEntityId","legalName"), names(root.get("buyer")));
        assertEquals(Set.of("ruleSetVersionId","version","rules"), names(root.get("ruleSet")));
        assertEquals(Set.of("amountMinor","currency"), names(root.get("commercialTerms")));
        assertEquals(123, root.at("/commercialTerms/amountMinor").asLong()); assertEquals("TRY", root.at("/commercialTerms/currency").asText());
        assertEquals(99, root.at("/ruleSet/rules/0/structuredValue/amountMinor").asLong());
    }

    @Test void ordersUtf8AndDefensivelyCopiesJson() throws Exception {
        ObjectNode value = (ObjectNode) json.readTree("{\"type\":\"TEXT\",\"value\":\"original\"}"); ObjectNode basis=(ObjectNode)json.readTree("{\"source\":\"tbk-6098\",\"articleNo\":\"1\"}");
        var result=assembler.assemble(target(),document(),rules(List.of(rule("\uD800\uDC00",value,basis),rule("\uE000",value,basis))),1,"TRY");
        assertEquals("\uE000", json.readTree(result.serializedSnapshot()).at("/ruleSet/rules/0/ruleReference").asText());
        value.put("value","changed"); basis.put("articleNo","2");
        ((ObjectNode) result.snapshot().ruleSet().rules().get(0).structuredValue()).put("value", "caller-change");
        assertTrue(result.serializedSnapshot().contains("original")); assertEquals(result.contentHash(),hasher.hash(result.serializedSnapshot()));
        assertThrows(IllegalArgumentException.class,()->assembler.assemble(target(),document(),rules(List.of(rule("x",value,null),rule("x",value,null))),1,"TRY"));
    }

    @Test void rejectsPointersAndSafeBounds() throws Exception {
        var original=target(); var wrong=new RatificationSourcePorts.Target(original.dealId(),original.tenantId(),original.status(),original.version(),original.reference(),original.title(),original.initiator(),original.buyer(),original.seller(),UUID.randomUUID(),original.currentRuleSetId(),null);
        assertThrows(IllegalArgumentException.class,()->assembler.assemble(wrong,document(),rules(List.of(rule("a",(ObjectNode)json.readTree("{}"),null))),1,"TRY"));
        assertThrows(IllegalArgumentException.class,()->assembler.assemble(target(),document(),new RatificationSourcePorts.RuleSet(RULES,UUID.randomUUID(),1,List.of(rule("a",(ObjectNode)json.readTree("{}"),null))),1,"TRY"));
        assertThrows(IllegalArgumentException.class,()->assembler.assemble(target(),document(),rules(List.of(rule("a",(ObjectNode)json.readTree("{}"),null))),0,"TRY"));
    }
    private Set<String> names(tools.jackson.databind.JsonNode node){Set<String>s=new HashSet<>();s.addAll(node.propertyNames());return s;}
    private RatificationSourcePorts.Target target(){UUID d=UUID.fromString("11111111-1111-1111-1111-111111111111");return new RatificationSourcePorts.Target(d,UUID.randomUUID(),"DRAFT",1,"DL-0000000001","Deal",true,new RatificationSourcePorts.Party(UUID.randomUUID(),"Buyer"),new RatificationSourcePorts.Party(UUID.randomUUID(),"Seller"),DOC,RULES,null);}
    private static final UUID DOC=UUID.fromString("22222222-2222-2222-2222-222222222222"), RULES=UUID.fromString("33333333-3333-3333-3333-333333333333");
    private RatificationSourcePorts.Document document(){return new RatificationSourcePorts.Document(DOC,UUID.fromString("11111111-1111-1111-1111-111111111111"),"v1","a".repeat(64));}
    private RatificationSourcePorts.RuleSet rules(List<RatificationSourcePorts.Rule> r){return new RatificationSourcePorts.RuleSet(RULES,UUID.fromString("11111111-1111-1111-1111-111111111111"),1,r);}
    private RatificationSourcePorts.Rule rule(String ref,ObjectNode val,ObjectNode basis){return new RatificationSourcePorts.Rule(ref,"KEPT","PAYMENT","Title","Description",val,basis,"EXTRACTED");}
}
