package com.m4trust.coreapi.contractintelligence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReviewDecisionAssemblerTest {
    private final ReviewService.ReviewDecisionAssembler assembler = new ReviewService.ReviewDecisionAssembler();
    private final ReviewDtos.ExtractedRule extracted = new ReviewDtos.ExtractedRule("r-1", "PAYMENT", "Original", "Original description", new ReviewDtos.MoneyValue("MONEY", 5, "TRY"), null, List.of(), new ReviewDtos.LegalBasis("tbk-6098", "1"));

    @Test
    void preservesFinalFieldsAndLegalBasisProvenanceInRequestOrder() {
        var output = assembler.assemble(List.of(extracted), List.of(
                new ReviewAcceptanceRequestDecoder.Modified("r-1", editable("Changed")),
                new ReviewAcceptanceRequestDecoder.Added(editable("Manual"))));
        assertEquals(List.of("r-1", "manual-1"), output.rules().stream().map(ReviewDtos.RuleSetRule::ruleReference).toList());
        assertEquals("REVIEWER_MODIFIED", output.rules().get(0).legalBasisProvenance());
        assertEquals(extracted.legalBasis(), output.rules().get(0).legalBasis());
        assertEquals("MANUALLY_ADDED", output.rules().get(1).legalBasisProvenance());
        assertNull(output.rules().get(1).legalBasis());
    }

    @Test
    void rejectsDuplicateUnknownAndMissingExtractedDecisionsAndAllowsEmptyExtraction() {
        assertThrows(AnalysisExceptions.MalformedRequest.class, () -> assembler.assemble(List.of(extracted), List.of(
                new ReviewAcceptanceRequestDecoder.Kept("r-1"), new ReviewAcceptanceRequestDecoder.Excluded("r-1"))));
        assertThrows(AnalysisExceptions.MalformedRequest.class, () -> assembler.assemble(List.of(extracted), List.of(new ReviewAcceptanceRequestDecoder.Kept("unknown"))));
        assertThrows(AnalysisExceptions.MalformedRequest.class, () -> assembler.assemble(List.of(extracted), List.of()));
        assertEquals(0, assembler.assemble(List.of(), List.of()).rules().size());
    }

    @Test
    void keptRulesMustSatisfyEveryFinalRuleSetBoundary() {
        assertKeptInvalid(new ReviewDtos.ExtractedRule("r-1", "INVALID", "title", "description",
                new ReviewDtos.TextValue("TEXT", "value"), null, List.of(), null), "category");
        assertKeptInvalid(new ReviewDtos.ExtractedRule("r-1", "OTHER", " ", "description",
                new ReviewDtos.TextValue("TEXT", "value"), null, List.of(), null), "title");
        assertKeptInvalid(new ReviewDtos.ExtractedRule("r-1", "OTHER", "title", " ",
                new ReviewDtos.TextValue("TEXT", "value"), null, List.of(), null), "description");
        assertKeptInvalid(new ReviewDtos.ExtractedRule("r-1", "OTHER", "x".repeat(501), "description",
                new ReviewDtos.TextValue("TEXT", "value"), null, List.of(), null), "title");
        assertKeptInvalid(new ReviewDtos.ExtractedRule("r-1", "OTHER", "title", "x".repeat(4001),
                new ReviewDtos.TextValue("TEXT", "value"), null, List.of(), null), "description");
    }

    private void assertKeptInvalid(ReviewDtos.ExtractedRule invalid, String expectedField) {
        AnalysisExceptions.Validation exception = assertThrows(AnalysisExceptions.Validation.class,
                () -> assembler.assemble(List.of(invalid), List.of(new ReviewAcceptanceRequestDecoder.Kept("r-1"))));
        assertEquals(expectedField, exception.field());
    }

    private ReviewAcceptanceRequestDecoder.EditableRule editable(String title) {
        return new ReviewAcceptanceRequestDecoder.EditableRule("OTHER", title, "description",
                new ReviewDtos.TextValue("TEXT", "value"));
    }
}
