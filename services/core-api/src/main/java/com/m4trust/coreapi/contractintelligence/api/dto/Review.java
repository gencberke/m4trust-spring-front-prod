package com.m4trust.coreapi.contractintelligence.api.dto;

import java.util.List;
import java.util.UUID;

public record Review(UUID analysisId, UUID documentId, List<ExtractedRule> rules) {}
