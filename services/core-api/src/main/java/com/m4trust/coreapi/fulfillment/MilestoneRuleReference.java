package com.m4trust.coreapi.fulfillment;

import java.util.Objects;

public record MilestoneRuleReference(String ruleReference, String category) {

    public MilestoneRuleReference {
        Objects.requireNonNull(ruleReference);
        Objects.requireNonNull(category);
        if (ruleReference.isBlank() || category.isBlank()) {
            throw new IllegalArgumentException("rule reference and category must not be blank");
        }
    }
}
