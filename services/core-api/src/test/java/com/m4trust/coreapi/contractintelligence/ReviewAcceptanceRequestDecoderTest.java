package com.m4trust.coreapi.contractintelligence;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ReviewAcceptanceRequestDecoderTest {
    private final ReviewAcceptanceRequestDecoder decoder = new ReviewAcceptanceRequestDecoder();
    private final JsonMapper json = JsonMapper.builder().build();
    @Test void canonicalHashIgnoresObjectMemberOrderButNotDecisionOrder() throws Exception {
        UUID analysis=UUID.randomUUID(); UUID entity=UUID.randomUUID(); UUID deal=UUID.randomUUID();
        var first=decoder.decode(json.readTree("{\"analysisId\":\""+analysis+"\",\"expectedVersion\":1,\"decisions\":[{\"decision\":\"ADDED\",\"category\":\"OTHER\",\"title\":\"x\",\"description\":\"y\",\"structuredValue\":{\"type\":\"MONEY\",\"amountMinor\":1,\"currency\":\"TRY\"}}]}"));
        var reordered=decoder.decode(json.readTree("{\"decisions\":[{\"structuredValue\":{\"currency\":\"TRY\",\"amountMinor\":1,\"type\":\"MONEY\"},\"description\":\"y\",\"title\":\"x\",\"category\":\"OTHER\",\"decision\":\"ADDED\"}],\"expectedVersion\":1,\"analysisId\":\""+analysis+"\"}"));
        assertEquals(decoder.canonicalHash(entity,deal,first),decoder.canonicalHash(entity,deal,reordered));
    }
    @Test void rejectsExtraDecisionProperty() throws Exception {
        assertThrows(AnalysisExceptions.MalformedRequest.class, () -> decoder.decode(json.readTree("{\"analysisId\":\""+UUID.randomUUID()+"\",\"expectedVersion\":0,\"decisions\":[{\"decision\":\"KEPT\",\"ruleReference\":\"r\",\"extra\":true}]}")));
    }
}
