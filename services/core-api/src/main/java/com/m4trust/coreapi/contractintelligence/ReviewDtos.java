package com.m4trust.coreapi.contractintelligence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Public Slice 9 representations. They deliberately contain no persistence rows. */
final class ReviewDtos {
    private ReviewDtos() { }

    record Review(UUID analysisId, UUID documentId, List<ExtractedRule> rules) { }

    record ExtractedRule(String ruleReference, String category, String title, String description,
            StructuredValue structuredValue, BigDecimal confidence,
            List<Object> sourceReferences, LegalBasis legalBasis) { }

    record RuleSetRule(String ruleReference, String decision, String category, String title,
            String description, StructuredValue structuredValue, LegalBasis legalBasis,
            String legalBasisProvenance) { }

    record LegalBasis(String source, String articleNo) { }

    sealed interface StructuredValue permits TextValue, MoneyValue, PercentageValue,
            DurationValue, DateValue, BooleanValue, QuantityValue { }

    record TextValue(String type, String value) implements StructuredValue { }
    record MoneyValue(String type, long amountMinor, String currency) implements StructuredValue { }
    record PercentageValue(String type, long basisPoints) implements StructuredValue { }
    record DurationValue(String type, long valueSeconds) implements StructuredValue { }
    record DateValue(String type, String value) implements StructuredValue { }
    record BooleanValue(String type, boolean value) implements StructuredValue { }
    record QuantityValue(String type, BigDecimal value, String unit) implements StructuredValue { }

    record History(List<Summary> items) { }

    record Summary(UUID id, long version, UUID sourceAnalysisId,
            UUID sourceExtractionResultVersionId, Instant createdAt,
            UUID createdByUserId, UUID previousRuleSetVersionId, int ruleCount) { }

    record Version(UUID id, long version, UUID sourceAnalysisId,
            UUID sourceExtractionResultVersionId, Instant createdAt,
            UUID createdByUserId, UUID previousRuleSetVersionId, int ruleCount,
            List<RuleSetRule> rules, List<String> excludedRuleReferences) { }
}
