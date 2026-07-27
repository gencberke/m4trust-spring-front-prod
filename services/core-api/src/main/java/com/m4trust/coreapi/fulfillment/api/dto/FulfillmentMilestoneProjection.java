package com.m4trust.coreapi.fulfillment.api.dto;

import com.m4trust.coreapi.fulfillment.domain.MilestoneRuleReference;
import java.util.List;
import java.util.UUID;

public record FulfillmentMilestoneProjection(
    UUID id,
    String title,
    String description,
    List<MilestoneRuleReference> ruleReferences,
    MilestoneAvailableActions availableActions,
    long version) {}
