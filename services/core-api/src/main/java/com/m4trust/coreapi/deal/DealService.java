package com.m4trust.coreapi.deal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.m4trust.coreapi.audit.AuditAppendPort;
import com.m4trust.coreapi.audit.AuditRecord;
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DealService {

    private static final String DEAL_SUBJECT = "DEAL";
    private static final String DEAL_CREATED = "DEAL_CREATED";
    private static final String DEAL_UPDATED = "DEAL_UPDATED";
    private static final String DEAL_CANCELLED = "DEAL_CANCELLED";

    private final DealRepository repository;
    private final AuditAppendPort auditAppender;
    private final Clock clock;

    DealService(DealRepository repository, AuditAppendPort auditAppender,
            Clock clock) {
        this.repository = repository;
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
        repository.insert(deal.toRecord());
        appendAudit(context, deal.id(), DEAL_CREATED, correlationId, now);
        return toDetail(deal);
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
                .map(this::toSummary)
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
        return toDetail(loadVisible(context, dealId));
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
        return toDetail(deal);
    }

    @Transactional
    DealDetail cancel(OperationContext context, UUID dealId,
            UUID correlationId) {
        requireOperation(context, RequestedOperation.DEAL_CANCEL);
        Instant now = clock.instant();
        Deal deal = loadVisible(context, dealId);
        if (!tryPersistCancel(context, deal, now)) {
            // Cancel carries no expectedVersion, so a plain concurrent version
            // bump must not fail it: retry once against the latest state.
            deal = loadVisible(context, dealId);
            if (!tryPersistCancel(context, deal, now)) {
                throw new DealStateConflictException(
                        "Deal changed while cancellation was attempted");
            }
        }
        appendAudit(context, deal.id(), DEAL_CANCELLED, correlationId, now);
        return toDetail(deal);
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
        latest.status().requireBasicFieldEditingAllowed();
        if (latest.version() != expectedVersion) {
            throw new DealStaleVersionException();
        }
        throw new IllegalStateException(
                "Atomic Deal update failed without a visible conflict");
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

    private DealDetail toDetail(Deal deal) {
        return new DealDetail(
                deal.id(),
                deal.reference(),
                deal.title(),
                deal.description(),
                deal.status(),
                DealLifecycleProjectionCalculator.calculate(deal.status()),
                deal.version(),
                deal.createdAt(),
                deal.updatedAt(),
                actions(deal.status()));
    }

    private DealSummary toSummary(Deal deal) {
        return new DealSummary(
                deal.id(),
                deal.reference(),
                deal.title(),
                deal.status(),
                DealLifecycleProjectionCalculator.calculate(deal.status()),
                deal.version(),
                deal.createdAt(),
                deal.updatedAt(),
                actions(deal.status()));
    }

    private DealAvailableActions actions(DealStatus status) {
        return new DealAvailableActions(
                status.allowsBasicFieldEditing(),
                status.allowsCancellation());
    }

    private void requireOperation(
            OperationContext context, RequestedOperation expected) {
        if (context.requestedOperation() != expected) {
            throw new IllegalArgumentException(
                    "Operation context does not match the requested use case");
        }
    }
}
