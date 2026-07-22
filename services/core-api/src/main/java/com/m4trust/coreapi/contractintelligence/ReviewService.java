package com.m4trust.coreapi.contractintelligence;

import com.m4trust.coreapi.api.ApiErrorCode;
import com.m4trust.coreapi.audit.AuditAppendPort;
import com.m4trust.coreapi.audit.AuditRecord;
import com.m4trust.coreapi.deal.DealAnalysisMutationPort;
import com.m4trust.coreapi.deal.DealAnalysisReadPort;
import com.m4trust.coreapi.deal.DealRuleSetProjectionPort;
import com.m4trust.coreapi.idempotency.IdempotencyRequest;
import com.m4trust.coreapi.idempotency.IdempotencyResultReference;
import com.m4trust.coreapi.idempotency.IdempotencyService;
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
import com.m4trust.coreapi.ratification.RatificationSupersessionPort;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
class ReviewService implements DealRuleSetProjectionPort {
    private final AnalysisRepository analyses;
    private final RuleSetRepository ruleSets;
    private final DealAnalysisReadPort dealReads;
    private final DealAnalysisMutationPort dealMutations;
    private final RatificationSupersessionPort ratificationSupersessions;
    private final IdempotencyService idempotency;
    private final AuditAppendPort audit;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final ObjectMapper json;
    private final ReviewAcceptanceRequestDecoder decoder = new ReviewAcceptanceRequestDecoder();

    ReviewService(AnalysisRepository analyses, RuleSetRepository ruleSets,
            DealAnalysisReadPort dealReads, DealAnalysisMutationPort dealMutations,
            RatificationSupersessionPort ratificationSupersessions,
            IdempotencyService idempotency, AuditAppendPort audit,
            TransactionTemplate transaction, Clock clock, ObjectMapper json) {
        this.analyses = analyses;
        this.ruleSets = ruleSets;
        this.dealReads = dealReads;
        this.dealMutations = dealMutations;
        this.ratificationSupersessions = ratificationSupersessions;
        this.idempotency = idempotency;
        this.audit = audit;
        this.transaction = transaction;
        this.clock = clock;
        this.json = json;
    }

    ReviewDtos.Review review(OperationContext context, UUID dealId) {
        require(context, RequestedOperation.DEAL_EXTRACTION_REVIEW_READ);
        var visibility = dealReads.findAnalysisVisibility(context, dealId)
                .orElseThrow(AnalysisExceptions.DealNotFound::new);
        var job = analyses.findLatestForDocument(visibility.currentDocumentId())
                .filter(candidate -> candidate.status() == AnalysisJobStatus.REVIEW_REQUIRED)
                .orElseThrow(() -> new AnalysisExceptions.Conflict(ApiErrorCode.DEAL_STATE_CONFLICT));
        return new ReviewDtos.Review(job.id(), job.documentId(), extractedRules(job.id()));
    }

    ReviewDtos.Version accept(OperationContext context, UUID dealId, UUID idempotencyKey,
            UUID correlationId, JsonNode requestBody) {
        require(context, RequestedOperation.DEAL_EXTRACTION_REVIEW_ACCEPT);
        ReviewAcceptanceRequestDecoder.Request request = decoder.decode(requestBody);
        IdempotencyRequest idempotencyRequest = new IdempotencyRequest(
                context.authenticatedUserId(), context.tenantId(), "DEAL_EXTRACTION_REVIEW_ACCEPT",
                idempotencyKey, decoder.canonicalHash(context.activeLegalEntityId(), dealId, request));
        var completed = idempotency.findCompleted(idempotencyRequest);
        if (completed.isPresent()) {
            return versionForDeal(dealId, completed.get().id());
        }
        return transaction.execute(status -> acceptLocked(context, dealId, correlationId,
                idempotencyRequest, request));
    }

    ReviewDtos.History history(OperationContext context, UUID dealId) {
        require(context, RequestedOperation.DEAL_RULE_SET_VERSION_READ);
        requireVisible(context, dealId);
        return new ReviewDtos.History(ruleSets.list(dealId).stream().map(this::summary).toList());
    }

    ReviewDtos.Version version(OperationContext context, UUID dealId, UUID versionId) {
        require(context, RequestedOperation.DEAL_RULE_SET_VERSION_READ);
        // A single version is an opaque historical resource.  Unlike the history
        // endpoint, its not-found contract deliberately does not disclose Deal
        // visibility to callers that are not participants.
        if (dealReads.findAnalysisVisibility(context, dealId).isEmpty()) {
            throw new AnalysisExceptions.RuleSetVersionNotFound();
        }
        return versionForDeal(dealId, versionId);
    }

    @Override
    public Optional<CurrentRuleSet> findCurrent(UUID ruleSetVersionId) {
        return ruleSets.findAny(ruleSetVersionId).map(row -> new CurrentRuleSet(row.id(), row.version(),
                row.analysisId(), row.extractionId(), row.createdAt(), row.createdBy(), row.previousId(),
                ruleCount(row)));
    }

    private ReviewDtos.Version acceptLocked(OperationContext context, UUID dealId, UUID correlationId,
            IdempotencyRequest idempotencyRequest, ReviewAcceptanceRequestDecoder.Request request) {
        var target = dealMutations.lockForReview(context, dealId)
                .orElseThrow(AnalysisExceptions.DealNotFound::new);
        var claim = idempotency.claim(idempotencyRequest);
        if (claim.isReplay()) {
            return versionForDeal(dealId, claim.resultReference().id());
        }
        if (!target.initiator()) {
            throw new AnalysisExceptions.ReviewAcceptanceForbidden();
        }
        if (!target.reviewEligible()) {
            throw new AnalysisExceptions.Conflict(ApiErrorCode.DEAL_STATE_CONFLICT);
        }
        if (target.version() != request.expectedVersion()) {
            throw new AnalysisExceptions.Conflict(ApiErrorCode.DEAL_STALE_VERSION);
        }
        Instant now = clock.instant();
        ratificationSupersessions.supersedePending(
                context, dealId, target.currentPackageId(), correlationId, now);
        var job = analyses.findByIdForUpdate(request.analysisId())
                .filter(candidate -> candidate.dealId().equals(dealId)
                        && candidate.documentId().equals(target.currentDocumentId())
                        && candidate.status() == AnalysisJobStatus.REVIEW_REQUIRED)
                .orElseThrow(() -> new AnalysisExceptions.Conflict(ApiErrorCode.DEAL_STATE_CONFLICT));
        UUID extractionId = analyses.findResultId(job.id())
                .orElseThrow(() -> new AnalysisExceptions.Conflict(ApiErrorCode.DEAL_STATE_CONFLICT));
        DecisionOutput output = new ReviewDecisionAssembler().assemble(extractedRules(job.id()), request.decisions());
        UUID versionId = UUID.randomUUID();
        var previous = ruleSets.latest(dealId).orElse(null);
        ruleSets.insert(versionId, dealId, previous == null ? 1 : previous.version() + 1,
                job.id(), extractionId, context.authenticatedUserId(), now,
                previous == null ? null : previous.id(), write(output.rules()), write(output.excludedRuleReferences()));
        analyses.accept(job);
        dealMutations.setCurrentRuleSet(dealId, versionId, now);
        audit.append(new AuditRecord(UUID.randomUUID(), target.owningTenantId(),
                context.authenticatedUserId(), context.activeLegalEntityId(), "RULE_SET_VERSION", versionId,
                "EXTRACTION_REVIEW_ACCEPTED", correlationId, null, now));
        idempotency.recordResult(claim, new IdempotencyResultReference("RULE_SET_VERSION", versionId));
        return versionForDeal(dealId, versionId);
    }

    private List<ReviewDtos.ExtractedRule> extractedRules(UUID analysisId) {
        try {
            JsonNode root = json.readTree(analyses.findResult(analysisId)
                    .orElseThrow(() -> new AnalysisExceptions.Conflict(ApiErrorCode.DEAL_STATE_CONFLICT)));
            if (!root.path("rules").isArray()) throw new IllegalStateException("Canonical extraction has no rules");
            List<ReviewDtos.ExtractedRule> result = new ArrayList<>();
            for (JsonNode rule : root.get("rules")) result.add(decoder.decodeExtractedRule(rule));
            return List.copyOf(result);
        } catch (AnalysisExceptions.Validation | AnalysisExceptions.MalformedRequest exception) {
            throw new IllegalStateException("Canonical extraction is invalid", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read canonical extraction", exception);
        }
    }

    private ReviewDtos.Version versionForDeal(UUID dealId, UUID versionId) {
        RuleSetRepository.Row row = ruleSets.find(dealId, versionId)
                .orElseThrow(AnalysisExceptions.RuleSetVersionNotFound::new);
        try {
            JsonNode rulesNode = json.readTree(row.rules());
            List<ReviewDtos.RuleSetRule> finalRules = new ArrayList<>();
            for (JsonNode rule : rulesNode) finalRules.add(decoder.decodeRuleSetRule(rule));
            JsonNode excludedNode = json.readTree(row.excluded());
            List<String> excluded = new ArrayList<>();
            if (!excludedNode.isArray()) throw new IllegalStateException("Invalid excluded-rule history");
            for (JsonNode reference : excludedNode) excluded.add(reference.asText());
            return new ReviewDtos.Version(row.id(), row.version(), row.analysisId(), row.extractionId(),
                    row.createdAt(), row.createdBy(), row.previousId(), finalRules.size(),
                    List.copyOf(finalRules), List.copyOf(excluded));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read accepted rule set", exception);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize accepted rule set", exception);
        }
    }

    private ReviewDtos.Summary summary(RuleSetRepository.Row row) {
        return new ReviewDtos.Summary(row.id(), row.version(), row.analysisId(), row.extractionId(),
                row.createdAt(), row.createdBy(), row.previousId(), ruleCount(row));
    }

    private int ruleCount(RuleSetRepository.Row row) {
        try {
            return json.readTree(row.rules()).size();
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot count accepted rules", exception);
        }
    }

    private void requireVisible(OperationContext context, UUID dealId) {
        if (dealReads.findAnalysisVisibility(context, dealId).isEmpty()) {
            throw new AnalysisExceptions.DealNotFound();
        }
    }

    private static void require(OperationContext context, RequestedOperation operation) {
        if (context.requestedOperation() != operation) {
            throw new IllegalArgumentException("Operation context does not match review operation");
        }
    }

    record DecisionOutput(List<ReviewDtos.RuleSetRule> rules, List<String> excludedRuleReferences) { }

    /** Pure business transformation: all request and extraction data is already typed. */
    static final class ReviewDecisionAssembler {
        DecisionOutput assemble(List<ReviewDtos.ExtractedRule> extracted,
                List<ReviewAcceptanceRequestDecoder.Decision> decisions) {
            Map<String, ReviewAcceptanceRequestDecoder.Decision> byReference = new HashMap<>();
            List<ReviewAcceptanceRequestDecoder.Added> added = new ArrayList<>();
            for (ReviewAcceptanceRequestDecoder.Decision decision : decisions) {
                if (decision instanceof ReviewAcceptanceRequestDecoder.Added manual) {
                    added.add(manual);
                } else {
                    String reference = referenceOf(decision);
                    if (byReference.put(reference, decision) != null) throw malformed();
                }
            }
            Set<String> extractedReferences = new HashSet<>();
            List<ReviewDtos.RuleSetRule> rules = new ArrayList<>();
            List<String> excluded = new ArrayList<>();
            for (ReviewDtos.ExtractedRule original : extracted) {
                if (!extractedReferences.add(original.ruleReference())) throw malformed();
                ReviewAcceptanceRequestDecoder.Decision decision = byReference.remove(original.ruleReference());
                if (decision == null) throw malformed();
                if (decision instanceof ReviewAcceptanceRequestDecoder.Kept) {
                    ReviewAcceptanceRequestDecoder.validateFinalRule(original);
                    rules.add(finalRule(original.ruleReference(), "KEPT", original.category(), original.title(),
                            original.description(), original.structuredValue(), original.legalBasis(), "EXTRACTED"));
                } else if (decision instanceof ReviewAcceptanceRequestDecoder.Modified modified) {
                    var edited = modified.rule();
                    rules.add(finalRule(original.ruleReference(), "MODIFIED", edited.category(), edited.title(),
                            edited.description(), edited.structuredValue(), original.legalBasis(), "REVIEWER_MODIFIED"));
                } else if (decision instanceof ReviewAcceptanceRequestDecoder.Excluded) {
                    excluded.add(original.ruleReference());
                } else {
                    throw malformed();
                }
            }
            if (!byReference.isEmpty()) throw malformed();
            int manualNumber = 1;
            for (ReviewAcceptanceRequestDecoder.Added manual : added) {
                var edited = manual.rule();
                rules.add(finalRule("manual-" + manualNumber++, "ADDED", edited.category(), edited.title(),
                        edited.description(), edited.structuredValue(), null, "MANUALLY_ADDED"));
            }
            return new DecisionOutput(List.copyOf(rules), List.copyOf(excluded));
        }

        private static ReviewDtos.RuleSetRule finalRule(String reference, String decision,
                String category, String title, String description, ReviewDtos.StructuredValue value,
                ReviewDtos.LegalBasis legalBasis, String provenance) {
            return new ReviewDtos.RuleSetRule(reference, decision, category, title, description, value,
                    legalBasis, provenance);
        }

        private static String referenceOf(ReviewAcceptanceRequestDecoder.Decision decision) {
            if (decision instanceof ReviewAcceptanceRequestDecoder.Kept kept) return kept.ruleReference();
            if (decision instanceof ReviewAcceptanceRequestDecoder.Modified modified) return modified.ruleReference();
            if (decision instanceof ReviewAcceptanceRequestDecoder.Excluded excluded) return excluded.ruleReference();
            throw malformed();
        }

        private static AnalysisExceptions.MalformedRequest malformed() {
            return new AnalysisExceptions.MalformedRequest();
        }
    }
}
