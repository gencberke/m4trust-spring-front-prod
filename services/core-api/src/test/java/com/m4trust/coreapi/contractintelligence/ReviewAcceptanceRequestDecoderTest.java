package com.m4trust.coreapi.contractintelligence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ReviewAcceptanceRequestDecoderTest {
    private final ReviewAcceptanceRequestDecoder decoder = new ReviewAcceptanceRequestDecoder();
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void canonicalHashIgnoresObjectMemberOrderButNotDecisionOrderOrMeaning() throws Exception {
        UUID analysis = UUID.randomUUID();
        UUID entity = UUID.randomUUID();
        UUID deal = UUID.randomUUID();
        var first = decode(analysis, json("[{'decision':'ADDED','category':'OTHER','title':'x','description':'y','structuredValue':{'type':'MONEY','amountMinor':1,'currency':'TRY'}}]"));
        var reordered = decode(analysis, json("[{'structuredValue':{'currency':'TRY','amountMinor':1,'type':'MONEY'},'description':'y','title':'x','category':'OTHER','decision':'ADDED'}]"));
        var changed = decode(analysis, json("[{'decision':'ADDED','category':'OTHER','title':'x','description':'y','structuredValue':{'type':'MONEY','amountMinor':2,'currency':'TRY'}}]"));
        var ordered = decode(analysis, json("[{'decision':'KEPT','ruleReference':'one'},{'decision':'KEPT','ruleReference':'two'}]"));
        var reversed = decode(analysis, json("[{'decision':'KEPT','ruleReference':'two'},{'decision':'KEPT','ruleReference':'one'}]"));
        assertEquals(decoder.canonicalHash(entity, deal, first), decoder.canonicalHash(entity, deal, reordered));
        org.junit.jupiter.api.Assertions.assertNotEquals(decoder.canonicalHash(entity, deal, first), decoder.canonicalHash(entity, deal, changed));
        org.junit.jupiter.api.Assertions.assertNotEquals(decoder.canonicalHash(entity, deal, ordered), decoder.canonicalHash(entity, deal, reversed));
    }

    @Test
    void decodesEveryClosedStructuredValueVariant() throws Exception {
        UUID analysis = UUID.randomUUID();
        String decisions = """
                [{"decision":"ADDED","category":"OTHER","title":"x","description":"y","structuredValue":{"type":"TEXT","value":"text"}},
                 {"decision":"ADDED","category":"OTHER","title":"x","description":"y","structuredValue":{"type":"MONEY","amountMinor":0,"currency":"TRY"}},
                 {"decision":"ADDED","category":"OTHER","title":"x","description":"y","structuredValue":{"type":"PERCENTAGE","basisPoints":10000}},
                 {"decision":"ADDED","category":"OTHER","title":"x","description":"y","structuredValue":{"type":"DURATION","valueSeconds":0}},
                 {"decision":"ADDED","category":"OTHER","title":"x","description":"y","structuredValue":{"type":"DATE","value":"2026-01-02"}},
                 {"decision":"ADDED","category":"OTHER","title":"x","description":"y","structuredValue":{"type":"BOOLEAN","value":true}},
                 {"decision":"ADDED","category":"OTHER","title":"x","description":"y","structuredValue":{"type":"QUANTITY","value":1.5,"unit":"kg"}}]
                """;
        List<ReviewAcceptanceRequestDecoder.Decision> decoded = decode(analysis, decisions).decisions();
        assertInstanceOf(ReviewDtos.TextValue.class, ((ReviewAcceptanceRequestDecoder.Added) decoded.get(0)).rule().structuredValue());
        assertInstanceOf(ReviewDtos.MoneyValue.class, ((ReviewAcceptanceRequestDecoder.Added) decoded.get(1)).rule().structuredValue());
        assertInstanceOf(ReviewDtos.PercentageValue.class, ((ReviewAcceptanceRequestDecoder.Added) decoded.get(2)).rule().structuredValue());
        assertInstanceOf(ReviewDtos.DurationValue.class, ((ReviewAcceptanceRequestDecoder.Added) decoded.get(3)).rule().structuredValue());
        assertInstanceOf(ReviewDtos.DateValue.class, ((ReviewAcceptanceRequestDecoder.Added) decoded.get(4)).rule().structuredValue());
        assertInstanceOf(ReviewDtos.BooleanValue.class, ((ReviewAcceptanceRequestDecoder.Added) decoded.get(5)).rule().structuredValue());
        assertInstanceOf(ReviewDtos.QuantityValue.class, ((ReviewAcceptanceRequestDecoder.Added) decoded.get(6)).rule().structuredValue());
    }

    @Test
    void rejectsMalformedFloatAndReportsSemanticInvalidityAtDecisionField() throws Exception {
        UUID analysis = UUID.randomUUID();
        assertThrows(AnalysisExceptions.MalformedRequest.class, () -> decode(analysis,
                json("[{'decision':'ADDED','category':'OTHER','title':'x','description':'y','structuredValue':{'type':'MONEY','amountMinor':1.5,'currency':'TRY'}}]")));
        AnalysisExceptions.Validation validation = assertThrows(AnalysisExceptions.Validation.class, () -> decode(analysis,
                json("[{'decision':'ADDED','category':'OTHER','title':'x','description':'y','structuredValue':{'type':'PERCENTAGE','basisPoints':10001}}]")));
        assertEquals("decisions[0].structuredValue.basisPoints", validation.field());
    }

    @Test
    void rejectsExtraOrMissingDecisionProperties() throws Exception {
        UUID analysis = UUID.randomUUID();
        assertThrows(AnalysisExceptions.MalformedRequest.class, () -> decoder.decode(json.readTree("{\"analysisId\":\"" + analysis + "\",\"expectedVersion\":0,\"decisions\":[{\"decision\":\"KEPT\",\"ruleReference\":\"r\",\"extra\":true}]}")));
        assertThrows(AnalysisExceptions.MalformedRequest.class, () -> decoder.decode(json.readTree("{\"analysisId\":\"" + analysis + "\",\"expectedVersion\":0}")));
    }

    private ReviewAcceptanceRequestDecoder.Request decode(UUID analysis, String decisions) throws Exception {
        return decoder.decode(json.readTree("{\"analysisId\":\"" + analysis + "\",\"expectedVersion\":1,\"decisions\":" + decisions + "}"));
    }

    private static String json(String value) {
        return value.replace('\'', '"');
    }
}
