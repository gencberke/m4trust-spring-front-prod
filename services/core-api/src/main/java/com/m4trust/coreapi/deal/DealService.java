package com.m4trust.coreapi.deal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.m4trust.coreapi.api.FieldErrorCode;
import com.m4trust.coreapi.audit.AuditAppendPort;
import com.m4trust.coreapi.audit.AuditRecord;
import com.m4trust.coreapi.casework.CaseworkDealProjectionPort;
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.InvitationLegalEntityQueryPort;
import com.m4trust.coreapi.organization.RequestedOperation;
import com.m4trust.coreapi.fulfillment.FulfillmentProjectionPort;
import com.m4trust.coreapi.payment.FundingProjectionPort;
import com.m4trust.coreapi.payment.SettlementProjectionPort;
import com.m4trust.coreapi.ratification.RatificationPackageProjectionPort;
import com.m4trust.coreapi.ratification.RatificationSupersessionPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DealService {

    private static final String DEAL_SUBJECT = "DEAL";
    private static final String DEAL_CREATED = "DEAL_CREATED";
    private static final String DEAL_UPDATED = "DEAL_UPDATED";
    private static final String DEAL_PARTIES_UPDATED = "DEAL_PARTIES_UPDATED";
    private static final String DEAL_CANCELLED = "DEAL_CANCELLED";

    private final DealRepository repository;
    private final DealOperationPolicy operationPolicy;
    private final InvitationLegalEntityQueryPort legalEntityQueries;
    private final DealCurrentDocumentQueryPort currentDocumentQueries;
    private final DealAnalysisProjectionPort analysisProjections;
    private final DealRuleSetProjectionPort ruleSetProjections;
    private final RatificationPackageProjectionPort ratificationProjections;
    private final RatificationSupersessionPort ratificationSupersessions;
    private final FundingProjectionPort fundingProjections;
    private final SettlementProjectionPort settlementProjections;
    private final FulfillmentProjectionPort fulfillmentProjections;
    private final CaseworkDealProjectionPort caseworkProjections;
    private final AuditAppendPort auditAppender;
    private final Clock clock;

    DealService(DealRepository repository,
            DealOperationPolicy operationPolicy,
            InvitationLegalEntityQueryPort legalEntityQueries,
            DealCurrentDocumentQueryPort currentDocumentQueries,
            DealAnalysisProjectionPort analysisProjections,
            DealRuleSetProjectionPort ruleSetProjections,
            RatificationPackageProjectionPort ratificationProjections,
            RatificationSupersessionPort ratificationSupersessions,
            FundingProjectionPort fundingProjections,
            SettlementProjectionPort settlementProjections,
            FulfillmentProjectionPort fulfillmentProjections,
            CaseworkDealProjectionPort caseworkProjections,
            AuditAppendPort auditAppender, Clock clock) {
        this.repository = repository;
        this.operationPolicy = operationPolicy;
        this.legalEntityQueries = legalEntityQueries;
        this.currentDocumentQueries = currentDocumentQueries;
        this.analysisProjections = analysisProjections;
        this.ruleSetProjections = ruleSetProjections;
        this.ratificationProjections = ratificationProjections;
        this.ratificationSupersessions = ratificationSupersessions;
        this.fundingProjections = fundingProjections;
        this.settlementProjections = settlementProjections;
        this.fulfillmentProjections = fulfillmentProjections;
        this.caseworkProjections = caseworkProjections;
        this.auditAppender = auditAppender;
        this.clock = clock;
    }

    @Transactional
    DealDetail create(OperationContext context, CreateDealRequest request,
            UUID correlationId) {
        requireOperation(context, RequestedOperation.DEAL_CREATE);
        Instant now = clock.instant();
        Deal deal = Deal.create(
                UUID.randomUUID(),
                context.tenantId(),
                repository.nextReference(),
                request.title(),
                request.description(),
                context.activeLegalEntityId(),
                context.authenticatedUserId(),
                now);
        repository.insert(deal.toRecord(), context.tenantId());
        appendAudit(context, deal.id(), DEAL_CREATED, correlationId, now);
        return toDetail(deal, context);
    }

    @Transactional(readOnly = true)
    DealPage list(OperationContext context, DealQuery query) {
        requireOperation(context, RequestedOperation.DEAL_LIST_READ);
        List<DealSummary> items = repository.findVisiblePage(
                        context.tenantId(),
                        context.activeLegalEntityId(),
                        query.status(),
                        query.sort(),
                        query.size(),
                        query.offset())
                .stream()
                .map(Deal::rehydrate)
                .map(deal -> toSummary(deal, context))
                .toList();
        long totalElements = repository.countVisible(
                context.tenantId(), context.activeLegalEntityId(),
                query.status());
        long pageCount = totalElements / query.size()
                + (totalElements % query.size() == 0 ? 0 : 1);
        int totalPages = Math.toIntExact(pageCount);
        return new DealPage(items, query.page(), query.size(), totalElements,
                totalPages);
    }

    @Transactional(readOnly = true)
    DealDetail get(OperationContext context, UUID dealId) {
        requireOperation(context, RequestedOperation.DEAL_DETAIL_READ);
        return toDetail(loadVisible(context, dealId), context);
    }

    @Transactional
    DealDetail update(OperationContext context, UUID dealId,
            UpdateDealRequest request, UUID correlationId) {
        requireOperation(context, RequestedOperation.DEAL_UPDATE);
        if (!request.descriptionPresent()) {
            throw new DealValidationException(
                    "description", FieldErrorCode.REQUIRED,
                    "Description must be provided, and may be null.");
        }
        Deal deal = loadVisibleForUpdate(context, dealId);
        operationPolicy.requireInitiator(deal, context);
        long currentVersion = deal.version();
        Instant now = clock.instant();
        boolean titleChanged = !deal.title().equals(request.title());
        deal.updateBasicFields(request.title(), request.description(),
                request.expectedVersion(), now);
        if (titleChanged) {
            ratificationSupersessions.supersedePending(
                    context, deal.id(), deal.currentRatificationPackageId(), correlationId, now);
        }
        boolean updated = repository.updateBasicFields(
                context.tenantId(),
                context.activeLegalEntityId(),
                deal.id(),
                currentVersion,
                deal.title(),
                deal.description(),
                deal.updatedAt());
        if (!updated) {
            classifyFailedUpdate(context, dealId, request.expectedVersion());
        }
        appendAudit(context, deal.id(), DEAL_UPDATED, correlationId, now);
        return toDetail(deal, context);
    }

    @Transactional
    DealDetail cancel(OperationContext context, UUID dealId,
            UUID correlationId) {
        requireOperation(context, RequestedOperation.DEAL_CANCEL);
        Instant now = clock.instant();
        Deal deal = loadVisibleForUpdate(context, dealId);
        operationPolicy.requireInitiator(deal, context);
        long currentVersion = deal.version();
        DealStatus previousStatus = deal.status();
        deal.cancel(now);
        ratificationSupersessions.supersedePending(
                context, deal.id(), deal.currentRatificationPackageId(), correlationId, now);
        if (!repository.updateStatus(
                context.tenantId(), context.activeLegalEntityId(), deal.id(),
                previousStatus, deal.status(), currentVersion, deal.updatedAt())) {
            throw new DealStateConflictException(
                    "Deal changed while cancellation was attempted");
        }
        appendAudit(context, deal.id(), DEAL_CANCELLED, correlationId, now);
        return toDetail(deal, context);
    }

    @Transactional
    DealDetail updateParties(OperationContext context, UUID dealId,
            UpdateDealPartiesRequest request, UUID correlationId) {
        requireOperation(context, RequestedOperation.DEAL_PARTIES_UPDATE);
        Deal deal = loadVisibleForUpdate(context, dealId);
        operationPolicy.requireInitiator(deal, context);
        deal.status().requirePartyManagementAllowed();
        validatePartyRequest(request, deal.id());
        long currentVersion = deal.version();
        Instant now = clock.instant();
        boolean assignmentsChanged = !Objects.equals(
                deal.buyerLegalEntityId(), request.buyerLegalEntityId())
                || !Objects.equals(deal.sellerLegalEntityId(), request.sellerLegalEntityId());
        deal.assignParties(request.buyerLegalEntityId(),
                request.sellerLegalEntityId(), request.expectedVersion(), now);
        if (assignmentsChanged) {
            ratificationSupersessions.supersedePending(
                    context, deal.id(), deal.currentRatificationPackageId(), correlationId, now);
        }
        boolean updated = repository.updateParties(
                context.tenantId(),
                context.activeLegalEntityId(),
                deal.id(),
                currentVersion,
                deal.buyerLegalEntityId(),
                deal.sellerLegalEntityId(),
                deal.updatedAt());
        if (!updated) {
            classifyFailedPartyUpdate(context, dealId, request.expectedVersion());
        }
        appendAudit(context, deal.id(), DEAL_PARTIES_UPDATED, correlationId, now);
        return toDetail(deal, context);
    }

    private Deal loadVisible(OperationContext context, UUID dealId) {
        return repository.findVisibleById(
                        context.tenantId(),
                        context.activeLegalEntityId(),
                        dealId)
                .map(Deal::rehydrate)
                .orElseThrow(DealNotFoundException::new);
    }

    private Deal loadVisibleForUpdate(OperationContext context, UUID dealId) {
        return repository.findVisibleByIdForUpdate(
                        context.tenantId(), context.activeLegalEntityId(), dealId)
                .map(Deal::rehydrate)
                .orElseThrow(DealNotFoundException::new);
    }

    private void classifyFailedUpdate(OperationContext context, UUID dealId,
            long expectedVersion) {
        Deal latest = loadVisible(context, dealId);
        operationPolicy.requireInitiator(latest, context);
        latest.status().requireBasicFieldEditingAllowed();
        if (latest.version() != expectedVersion) {
            throw new DealStaleVersionException();
        }
        throw new IllegalStateException(
                "Atomic Deal update failed without a visible conflict");
    }

    private void classifyFailedPartyUpdate(OperationContext context, UUID dealId,
            long expectedVersion) {
        Deal latest = loadVisible(context, dealId);
        operationPolicy.requireInitiator(latest, context);
        latest.status().requirePartyManagementAllowed();
        if (latest.version() != expectedVersion) {
            throw new DealStaleVersionException();
        }
        throw new IllegalStateException(
                "Atomic Deal party update failed without a visible conflict");
    }

    private void validatePartyRequest(UpdateDealPartiesRequest request,
            UUID dealId) {
        if (!request.buyerLegalEntityIdPresent()) {
            throw new DealValidationException("buyerLegalEntityId", FieldErrorCode.REQUIRED,
                    "Buyer legal entity id must be provided and may be null.");
        }
        if (!request.sellerLegalEntityIdPresent()) {
            throw new DealValidationException("sellerLegalEntityId", FieldErrorCode.REQUIRED,
                    "Seller legal entity id must be provided and may be null.");
        }
        if (request.buyerLegalEntityId() != null
                && request.buyerLegalEntityId().equals(
                        request.sellerLegalEntityId())) {
            throw new DealValidationException("buyerLegalEntityId", FieldErrorCode.MUST_DIFFER,
                    "Buyer and seller must be different legal entities.");
        }
        java.util.Set<UUID> participantIds = repository.findParticipants(dealId)
                .stream()
                .map(DealRepository.ParticipantRecord::legalEntityId)
                .collect(java.util.stream.Collectors.toSet());
        if (request.buyerLegalEntityId() != null
                && !participantIds.contains(request.buyerLegalEntityId())) {
            throw new DealValidationException("buyerLegalEntityId",
                    FieldErrorCode.NOT_A_PARTICIPANT,
                    "Buyer must be a current Deal participant.");
        }
        if (request.sellerLegalEntityId() != null
                && !participantIds.contains(request.sellerLegalEntityId())) {
            throw new DealValidationException("sellerLegalEntityId",
                    FieldErrorCode.NOT_A_PARTICIPANT,
                    "Seller must be a current Deal participant.");
        }
    }

    private void appendAudit(OperationContext context, UUID dealId,
            String action, UUID correlationId, Instant occurredAt) {
        auditAppender.append(new AuditRecord(
                UUID.randomUUID(),
                context.tenantId(),
                context.authenticatedUserId(),
                context.activeLegalEntityId(),
                DEAL_SUBJECT,
                dealId,
                action,
                correlationId,
                null,
                occurredAt));
    }

    private DealDetail toDetail(Deal deal, OperationContext context) {
        List<DealParticipant> participantProjections = participants(deal);
        DealRatificationProjection ratification = ratification(deal, context);
        boolean callerIsBuyerAdmin = deal.buyerLegalEntityId() != null
                && context.activeLegalEntityId().equals(deal.buyerLegalEntityId())
                && context.activeLegalEntityRole() == com.m4trust.coreapi.organization.LegalEntityRole.ADMIN;
        FundingProjectionPort.Summary fundingSummary = fundingProjections.summarize(
                deal.id(), deal.status() == DealStatus.ACTIVE, callerIsBuyerAdmin);
        SettlementProjectionPort.Summary settlementSummary = settlementProjections.summarize(
                deal.id(), deal.status() == DealStatus.ACTIVE, callerIsBuyerAdmin);
        FulfillmentProjectionPort.Summary fulfillmentSummary = fulfillmentProjections.summarize(deal.id());
        CaseworkDealProjectionPort.ActorSummary caseworkSummary = caseworkProjection(
                deal, context, fulfillmentSummary);
        return new DealDetail(
                deal.id(),
                deal.reference(),
                deal.title(),
                deal.description(),
                deal.status(),
                lifecycle(deal, fundingSummary.fundingStatus(), caseworkSummary, fulfillmentSummary),
                deal.version(),
                deal.createdAt(),
                deal.updatedAt(),
                actionsWithAnalysis(deal, context, ratification, fundingSummary, settlementSummary,
                        fulfillmentSummary, caseworkSummary),
                party(deal.buyerLegalEntityId(), participantProjections),
                party(deal.sellerLegalEntityId(), participantProjections),
                participantProjections,
                currentDocument(deal), analysis(deal), currentRuleSet(deal), ratification,
                fundingSummary(fundingSummary),
                fulfillmentSummary(fulfillmentSummary),
                toCaseworkSummary(caseworkSummary),
                toSettlementSummary(settlementSummary));
    }

    private DealFundingSummary fundingSummary(FundingProjectionPort.Summary summary) {
        return new DealFundingSummary(summary.fundingStatus(), summary.fundingPlanId(), summary.amountMinor(),
                summary.currency());
    }

    private DealFulfillmentSummary fulfillmentSummary(FulfillmentProjectionPort.Summary summary) {
        if (summary == null) {
            return null;
        }
        return new DealFulfillmentSummary(
                summary.status() == null ? null : summary.status().name(),
                summary.fulfillmentId(),
                summary.currentEvidenceSubmissionId(),
                summary.evidencePolicy() == null ? null : summary.evidencePolicy().name());
    }

    /**
     * READY is derived, never persisted: parties, an accepted rule-set, and a
     * current document pointer must exist together (§5 NOT_READY/READY
     * hesabı). The current-package pointer is looked up by id regardless of
     * its terminal status so SUPERSEDED/REJECTED history stays visible.
     */
    private DealRatificationProjection ratification(Deal deal, OperationContext context) {
        boolean ready = deal.buyerLegalEntityId() != null
                && deal.sellerLegalEntityId() != null
                && deal.currentRuleSetVersionId() != null
                && deal.currentDocumentId() != null;
        DealRatificationReadiness readiness = ready
                ? DealRatificationReadiness.READY : DealRatificationReadiness.NOT_READY;
        UUID currentPackageId = deal.currentRatificationPackageId();
        RatificationPackageProjectionPort.CurrentPackage currentPackage = currentPackageId == null
                ? null
                : ratificationProjections.findCurrentPackage(
                                context, deal.id(), deal.status().name(), currentPackageId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Deal current ratification package is unavailable"));
        return new DealRatificationProjection(readiness, currentPackage);
    }

    private DealCurrentDocumentQueryPort.CurrentDealDocument currentDocument(Deal deal) {
        UUID currentDocumentId = deal.currentDocumentId();
        if (currentDocumentId == null) {
            return null;
        }
        return currentDocumentQueries.findAvailable(currentDocumentId)
                .orElseThrow(() -> new IllegalStateException(
                        "Deal current document is unavailable"));
    }

    private DealSummary toSummary(Deal deal, OperationContext context) {
        FundingProjectionPort.Summary fundingSummary = fundingProjections.summarize(
                deal.id(), deal.status() == DealStatus.ACTIVE, false);
        FulfillmentProjectionPort.Summary fulfillmentSummary = fulfillmentProjections.summarize(deal.id());
        CaseworkDealProjectionPort.ActorSummary caseworkSummary = caseworkProjection(
                deal, context, fulfillmentSummary);
        return new DealSummary(
                deal.id(),
                deal.reference(),
                deal.title(),
                deal.status(),
                lifecycle(deal, fundingSummary.fundingStatus(), caseworkSummary, fulfillmentSummary),
                deal.version(),
                deal.createdAt(),
                deal.updatedAt(),
                actions(deal, context, caseworkSummary.canOpenDispute()));
    }

    private DealAvailableActions actions(Deal deal, OperationContext context) {
        return actions(deal, context, false);
    }

    private DealAvailableActions actions(Deal deal, OperationContext context, boolean canOpenDispute) {
        DealAvailableActions base = operationPolicy.availableActions(deal, context);
        return new DealAvailableActions(base.canUpdate(), base.canCancel(),
                base.canCreateInvitation(), base.canManageParties(),
                base.canCreateDocumentUploadIntent(), base.canRequestAnalysis(),
                base.canReviewExtraction(), base.canCreateRatificationPackage(),
                base.canApproveRatification(), base.canRejectRatification(),
                base.canCreateFundingPlan(), base.canInitiateFunding(),
                base.canReconcilePaymentOperation(), base.canStartFulfillment(),
                base.canUploadEvidence(), base.canAcceptEvidence(), base.canRejectEvidence(),
                base.canAcceptWithoutEvidence(), canOpenDispute, false, false);
    }

    private DealAvailableActions actionsWithAnalysis(Deal deal,
            OperationContext context, DealRatificationProjection ratification,
            FundingProjectionPort.Summary fundingSummary,
            SettlementProjectionPort.Summary settlementSummary,
            FulfillmentProjectionPort.Summary fulfillmentSummary,
            CaseworkDealProjectionPort.ActorSummary caseworkSummary) {
        DealAvailableActions base = actions(deal, context);
        UUID documentId = deal.currentDocumentId();
        boolean allowed = operationPolicy.isInitiator(deal, context)
                && deal.status().allowsDocumentUpload()
                && documentId != null
                && currentDocumentQueries.findAvailable(documentId).isPresent()
                && !analysisProjections.hasActiveJob(documentId);
        boolean canCreateRatificationPackage = operationPolicy.isInitiator(deal, context)
                && deal.status() == DealStatus.DRAFT
                && ratification.readiness() == DealRatificationReadiness.READY;
        RatificationPackageProjectionPort.CurrentPackage currentPackage = ratification.currentPackage();
        boolean canApproveRatification = currentPackage != null
                && currentPackage.availableActions().canApprove();
        boolean canRejectRatification = currentPackage != null
                && currentPackage.availableActions().canReject();
        String fulfillmentStatus = fulfillmentSummary == null || fulfillmentSummary.status() == null
                ? null : fulfillmentSummary.status().name();
        boolean isActive = deal.status() == DealStatus.ACTIVE;
        boolean isFunded = "FUNDED".equals(fundingSummary.fundingStatus());
        boolean canStartFulfillment = deal.sellerLegalEntityId() != null
                && context.activeLegalEntityId().equals(deal.sellerLegalEntityId())
                && isActive && isFunded
                && (fulfillmentStatus == null || "NOT_STARTED".equals(fulfillmentStatus));
        boolean canUploadEvidence = isActive
                && deal.sellerLegalEntityId() != null
                && context.activeLegalEntityId().equals(deal.sellerLegalEntityId())
                && ("IN_PROGRESS".equals(fulfillmentStatus) || "EVIDENCE_REQUIRED".equals(fulfillmentStatus))
                && fulfillmentSummary != null
                && fulfillmentSummary.evidencePolicy() == com.m4trust.coreapi.fulfillment.EvidencePolicy.REQUIRED;
        boolean canAcceptEvidence = isActive
                && context.activeLegalEntityRole() == com.m4trust.coreapi.organization.LegalEntityRole.ADMIN
                && deal.buyerLegalEntityId() != null
                && context.activeLegalEntityId().equals(deal.buyerLegalEntityId())
                && "REVIEW_REQUIRED".equals(fulfillmentStatus)
                && fulfillmentSummary.currentEvidenceSubmissionId() != null;
        boolean canRejectEvidence = canAcceptEvidence;
        boolean canAcceptWithoutEvidence = isActive && isFunded
                && context.activeLegalEntityRole() == com.m4trust.coreapi.organization.LegalEntityRole.ADMIN
                && deal.buyerLegalEntityId() != null
                && context.activeLegalEntityId().equals(deal.buyerLegalEntityId())
                && fulfillmentSummary != null
                && fulfillmentSummary.evidencePolicy() == com.m4trust.coreapi.fulfillment.EvidencePolicy.NOT_REQUIRED
                && fulfillmentSummary.status() == com.m4trust.coreapi.fulfillment.FulfillmentStatus.IN_PROGRESS
                && !fulfillmentSummary.hasEvidenceSubmission();
        return new DealAvailableActions(base.canUpdate(), base.canCancel(),
                base.canCreateInvitation(), base.canManageParties(),
                base.canCreateDocumentUploadIntent(), allowed,
                operationPolicy.isInitiator(deal, context)
                        && deal.status() == DealStatus.DRAFT
                        && documentId != null
                        && "REVIEW_REQUIRED".equals(analysisProjections.summary(documentId).status()),
                canCreateRatificationPackage, canApproveRatification, canRejectRatification,
                fundingSummary.canCreateFundingPlan(), fundingSummary.canInitiateFunding(),
                fundingSummary.canReconcilePaymentOperation(),
                canStartFulfillment, canUploadEvidence, canAcceptEvidence, canRejectEvidence,
                canAcceptWithoutEvidence,
                caseworkSummary.canOpenDispute(), settlementSummary.canRequestRelease(),
                settlementSummary.canReconcileRelease());
    }

    private CaseworkDealProjectionPort.ActorSummary caseworkProjection(
            Deal deal,
            OperationContext context,
            FulfillmentProjectionPort.Summary fulfillmentSummary) {
        String fulfillmentStatus = fulfillmentSummary == null || fulfillmentSummary.status() == null
                ? null : fulfillmentSummary.status().name();
        return caseworkProjections.forActor(new CaseworkDealProjectionPort.ActorContext(
                deal.id(),
                deal.status() == DealStatus.ACTIVE,
                deal.buyerLegalEntityId(),
                deal.sellerLegalEntityId(),
                context.activeLegalEntityId(),
                context.activeLegalEntityRole(),
                fulfillmentStatus));
    }

    private DealSettlementSummary toSettlementSummary(SettlementProjectionPort.Summary summary) {
        if (summary.settlementId() == null) {
            return null;
        }
        return new DealSettlementSummary(summary.settlementId(), summary.status(),
                summary.currentReleaseOperationId());
    }

    private DealCaseworkSummary toCaseworkSummary(CaseworkDealProjectionPort.ActorSummary summary) {
        CaseworkDealProjectionPort.ActiveDispute active = summary.activeDispute();
        if (active == null) {
            return null;
        }
        return new DealCaseworkSummary(
                active.disputeId(),
                active.status(),
                active.reasonCode(),
                active.subject(),
                new DealCaseworkOpeningLegalEntity(
                        active.openingLegalEntityId(), active.openingLegalName()),
                active.openedAt(),
                active.acknowledgedAt(),
                active.version());
    }

    private DealAnalysisProjectionPort.AnalysisSummary analysis(Deal deal) {
        UUID documentId = deal.currentDocumentId();
        return documentId == null
                ? new DealAnalysisProjectionPort.AnalysisSummary(null, "NOT_REQUESTED",
                        null, null, null, null, null)
                : analysisProjections.summary(documentId);
    }
    private DealRuleSetProjectionPort.CurrentRuleSet currentRuleSet(Deal deal) {
        UUID pointer = deal.currentRuleSetVersionId();
        return pointer == null ? null : ruleSetProjections.findCurrent(pointer)
                .orElseThrow(() -> new IllegalStateException("Deal rule-set pointer is unavailable"));
    }

    private DealLifecycleProjection lifecycle(
            Deal deal, String fundingStatus, CaseworkDealProjectionPort.ActorSummary caseworkSummary,
            FulfillmentProjectionPort.Summary fulfillmentSummary) {
        UUID documentId = deal.currentDocumentId();
        boolean current = documentId != null && currentDocumentQueries.findAvailable(documentId).isPresent();
        String status = current ? analysisProjections.summary(documentId).status() : "NOT_REQUESTED";
        String fulfillmentStatus = fulfillmentSummary == null || fulfillmentSummary.status() == null
                ? null : fulfillmentSummary.status().name();
        return DealLifecycleProjectionCalculator.calculate(
                deal.status(), status, current, fundingStatus,
                caseworkSummary.activeDispute() != null, fulfillmentStatus);
    }

    private DealLifecycleProjection lifecycle(Deal deal, String fundingStatus) {
        UUID documentId = deal.currentDocumentId();
        boolean current = documentId != null && currentDocumentQueries.findAvailable(documentId).isPresent();
        String status = current ? analysisProjections.summary(documentId).status() : "NOT_REQUESTED";
        return DealLifecycleProjectionCalculator.calculate(deal.status(), status, current, fundingStatus);
    }

    private List<DealParticipant> participants(Deal deal) {
        List<DealRepository.ParticipantRecord> participantRecords =
                repository.findParticipants(deal.id());
        java.util.Map<UUID, String> names = legalEntityQueries.findLegalNames(
                participantRecords.stream()
                        .map(DealRepository.ParticipantRecord::legalEntityId)
                        .collect(java.util.stream.Collectors.toSet()));
        return participantRecords.stream().map(participant -> {
            String legalName = names.get(participant.legalEntityId());
            if (legalName == null) {
                throw new IllegalStateException(
                        "Participant legal entity projection is unavailable");
            }
            return new DealParticipant(participant.legalEntityId(), legalName,
                    participant.createdAt(), partyRoles(deal,
                            participant.legalEntityId()));
        }).toList();
    }

    private List<DealPartyRole> partyRoles(Deal deal, UUID legalEntityId) {
        if (legalEntityId.equals(deal.buyerLegalEntityId())) {
            return List.of(DealPartyRole.BUYER);
        }
        if (legalEntityId.equals(deal.sellerLegalEntityId())) {
            return List.of(DealPartyRole.SELLER);
        }
        return List.of();
    }

    private DealParty party(UUID legalEntityId,
            List<DealParticipant> participants) {
        if (legalEntityId == null) {
            return null;
        }
        return participants.stream()
                .filter(participant -> participant.legalEntityId().equals(
                        legalEntityId))
                .findFirst()
                .map(participant -> new DealParty(participant.legalEntityId(),
                        participant.legalName()))
                .orElseThrow(() -> new IllegalStateException(
                        "Deal party is not represented by a participant"));
    }

    private void requireOperation(
            OperationContext context, RequestedOperation expected) {
        if (context.requestedOperation() != expected) {
            throw new IllegalArgumentException(
                    "Operation context does not match the requested use case");
        }
    }
}
