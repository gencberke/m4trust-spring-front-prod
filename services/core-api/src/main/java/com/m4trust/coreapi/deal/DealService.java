package com.m4trust.coreapi.deal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.m4trust.coreapi.audit.AuditAppendPort;
import com.m4trust.coreapi.audit.AuditRecord;
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.InvitationLegalEntityQueryPort;
import com.m4trust.coreapi.organization.RequestedOperation;
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
    private final AuditAppendPort auditAppender;
    private final Clock clock;

    DealService(DealRepository repository,
            DealOperationPolicy operationPolicy,
            InvitationLegalEntityQueryPort legalEntityQueries,
            DealCurrentDocumentQueryPort currentDocumentQueries,
            DealAnalysisProjectionPort analysisProjections,
            AuditAppendPort auditAppender, Clock clock) {
        this.repository = repository;
        this.operationPolicy = operationPolicy;
        this.legalEntityQueries = legalEntityQueries;
        this.currentDocumentQueries = currentDocumentQueries;
        this.analysisProjections = analysisProjections;
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
                    "description", "REQUIRED",
                    "Description must be provided, and may be null.");
        }
        Deal deal = loadVisible(context, dealId);
        operationPolicy.requireInitiator(deal, context);
        long currentVersion = deal.version();
        Instant now = clock.instant();
        deal.updateBasicFields(request.title(), request.description(),
                request.expectedVersion(), now);
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
        Deal deal = loadVisible(context, dealId);
        operationPolicy.requireInitiator(deal, context);
        if (!tryPersistCancel(context, deal, now)) {
            // Cancel carries no expectedVersion, so a plain concurrent version
            // bump must not fail it: retry once against the latest state.
            deal = loadVisible(context, dealId);
            operationPolicy.requireInitiator(deal, context);
            if (!tryPersistCancel(context, deal, now)) {
                throw new DealStateConflictException(
                        "Deal changed while cancellation was attempted");
            }
        }
        appendAudit(context, deal.id(), DEAL_CANCELLED, correlationId, now);
        return toDetail(deal, context);
    }

    @Transactional
    DealDetail updateParties(OperationContext context, UUID dealId,
            UpdateDealPartiesRequest request, UUID correlationId) {
        requireOperation(context, RequestedOperation.DEAL_PARTIES_UPDATE);
        Deal deal = loadVisible(context, dealId);
        operationPolicy.requireInitiator(deal, context);
        deal.status().requirePartyManagementAllowed();
        validatePartyRequest(request, deal.id());
        long currentVersion = deal.version();
        Instant now = clock.instant();
        deal.assignParties(request.buyerLegalEntityId(),
                request.sellerLegalEntityId(), request.expectedVersion(), now);
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

    private boolean tryPersistCancel(
            OperationContext context, Deal deal, Instant now) {
        long currentVersion = deal.version();
        DealStatus previousStatus = deal.status();
        deal.cancel(now);
        return repository.updateStatus(
                context.tenantId(),
                context.activeLegalEntityId(),
                deal.id(),
                previousStatus,
                deal.status(),
                currentVersion,
                deal.updatedAt());
    }

    private Deal loadVisible(OperationContext context, UUID dealId) {
        return repository.findVisibleById(
                        context.tenantId(),
                        context.activeLegalEntityId(),
                        dealId)
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
            throw new DealValidationException("buyerLegalEntityId", "REQUIRED",
                    "Buyer legal entity id must be provided and may be null.");
        }
        if (!request.sellerLegalEntityIdPresent()) {
            throw new DealValidationException("sellerLegalEntityId", "REQUIRED",
                    "Seller legal entity id must be provided and may be null.");
        }
        if (request.buyerLegalEntityId() != null
                && request.buyerLegalEntityId().equals(
                        request.sellerLegalEntityId())) {
            throw new DealValidationException("buyerLegalEntityId", "MUST_DIFFER",
                    "Buyer and seller must be different legal entities.");
        }
        java.util.Set<UUID> participantIds = repository.findParticipants(dealId)
                .stream()
                .map(DealRepository.ParticipantRecord::legalEntityId)
                .collect(java.util.stream.Collectors.toSet());
        if (request.buyerLegalEntityId() != null
                && !participantIds.contains(request.buyerLegalEntityId())) {
            throw new DealValidationException("buyerLegalEntityId",
                    "NOT_A_PARTICIPANT",
                    "Buyer must be a current Deal participant.");
        }
        if (request.sellerLegalEntityId() != null
                && !participantIds.contains(request.sellerLegalEntityId())) {
            throw new DealValidationException("sellerLegalEntityId",
                    "NOT_A_PARTICIPANT",
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
        return new DealDetail(
                deal.id(),
                deal.reference(),
                deal.title(),
                deal.description(),
                deal.status(),
                lifecycle(deal),
                deal.version(),
                deal.createdAt(),
                deal.updatedAt(),
                actionsWithAnalysis(deal, context),
                party(deal.buyerLegalEntityId(), participantProjections),
                party(deal.sellerLegalEntityId(), participantProjections),
                participantProjections,
                currentDocument(deal), analysis(deal));
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
        return new DealSummary(
                deal.id(),
                deal.reference(),
                deal.title(),
                deal.status(),
                lifecycle(deal),
                deal.version(),
                deal.createdAt(),
                deal.updatedAt(),
                actions(deal, context));
    }

    private DealAvailableActions actions(Deal deal, OperationContext context) {
        return operationPolicy.availableActions(deal, context);
    }

    private DealAvailableActions actionsWithAnalysis(Deal deal,
            OperationContext context) {
        DealAvailableActions base = actions(deal, context);
        UUID documentId = deal.currentDocumentId();
        boolean allowed = operationPolicy.isInitiator(deal, context)
                && deal.status().allowsDocumentUpload()
                && documentId != null
                && currentDocumentQueries.findAvailable(documentId).isPresent()
                && !analysisProjections.hasActiveJob(documentId);
        return new DealAvailableActions(base.canUpdate(), base.canCancel(),
                base.canCreateInvitation(), base.canManageParties(),
                base.canCreateDocumentUploadIntent(), allowed);
    }

    private DealAnalysisProjectionPort.AnalysisSummary analysis(Deal deal) {
        UUID documentId = deal.currentDocumentId();
        return documentId == null
                ? new DealAnalysisProjectionPort.AnalysisSummary(null, "NOT_REQUESTED",
                        null, null, null, null, null)
                : analysisProjections.summary(documentId);
    }

    private DealLifecycleProjection lifecycle(Deal deal) {
        UUID documentId = deal.currentDocumentId();
        boolean current = documentId != null && currentDocumentQueries.findAvailable(documentId).isPresent();
        String status = current ? analysisProjections.summary(documentId).status() : "NOT_REQUESTED";
        return DealLifecycleProjectionCalculator.calculate(deal.status(), status, current);
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
