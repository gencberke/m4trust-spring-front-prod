package com.m4trust.coreapi.deal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.m4trust.coreapi.audit.AuditAppendPort;
import com.m4trust.coreapi.audit.AuditRecord;
import com.m4trust.coreapi.idempotency.IdempotencyClaim;
import com.m4trust.coreapi.idempotency.IdempotencyRequest;
import com.m4trust.coreapi.idempotency.IdempotencyResultReference;
import com.m4trust.coreapi.idempotency.IdempotencyService;
import com.m4trust.coreapi.identity.CurrentUserEmailQueryPort;
import com.m4trust.coreapi.organization.InvitationLegalEntityQueryPort;
import com.m4trust.coreapi.organization.InvitationLegalEntityQueryPort.InvitationLegalEntityMembership;
import com.m4trust.coreapi.organization.LegalEntityNotFoundException;
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DealInvitationService {

    private static final String SUBJECT = "DEAL_INVITATION";
    private static final String CREATED = "DEAL_INVITATION_CREATED";
    private static final String ACCEPTED = "DEAL_INVITATION_ACCEPTED";
    private static final String REJECTED = "DEAL_INVITATION_REJECTED";
    private static final String REVOKED = "DEAL_INVITATION_REVOKED";
    private static final String IDEMPOTENCY_OPERATION =
            "DEAL_INVITATION_CREATE";
    private static final String IDEMPOTENCY_RESULT = "DEAL_INVITATION";

    private final DealRepository dealRepository;
    private final DealInvitationRepository invitationRepository;
    private final CurrentUserEmailQueryPort emailQueries;
    private final InvitationLegalEntityQueryPort legalEntityQueries;
    private final IdempotencyService idempotencyService;
    private final AuditAppendPort auditAppender;
    private final Clock clock;

    DealInvitationService(DealRepository dealRepository,
            DealInvitationRepository invitationRepository,
            CurrentUserEmailQueryPort emailQueries,
            InvitationLegalEntityQueryPort legalEntityQueries,
            IdempotencyService idempotencyService,
            AuditAppendPort auditAppender, Clock clock) {
        this.dealRepository = dealRepository;
        this.invitationRepository = invitationRepository;
        this.emailQueries = emailQueries;
        this.legalEntityQueries = legalEntityQueries;
        this.idempotencyService = idempotencyService;
        this.auditAppender = auditAppender;
        this.clock = clock;
    }

    @Transactional
    DealInvitationProjection create(OperationContext context, UUID dealId,
            CreateDealInvitationRequest request, UUID idempotencyKey,
            UUID correlationId) {
        requireOperation(context, RequestedOperation.DEAL_INVITATION_CREATE);
        IdempotencyClaim claim = idempotencyService.claim(
                new IdempotencyRequest(context.authenticatedUserId(),
                        context.tenantId(), IDEMPOTENCY_OPERATION,
                        idempotencyKey,
                        canonicalHash(context.activeLegalEntityId(), dealId,
                                request.recipientEmail())));
        if (claim.isReplay()) {
            DealInvitationRepository.InvitationRecord replayed =
                    invitationRepository.findById(claim.resultReference().id())
                            .orElseThrow(() -> new IllegalStateException(
                                    "Idempotent invitation result is unavailable"));
            Deal replayedDeal = loadVisibleDeal(context, replayed.dealId());
            if (!replayedDeal.isInitiatedBy(context.activeLegalEntityId())) {
                throw new DealInvitationForbiddenException();
            }
            return toInitiatorProjection(replayed, context);
        }

        Deal deal = loadVisibleDeal(context, dealId);
        requireDraftInitiator(context, deal);
        Instant now = clock.instant();
        DealInvitation invitation = DealInvitation.create(UUID.randomUUID(),
                deal.toRecord().tenantId(), deal.id(),
                request.recipientEmail(), now);
        try {
            invitationRepository.insert(invitation.toRecord());
        } catch (DuplicateKeyException exception) {
            throw new DealInvitationPendingExistsException();
        }
        idempotencyService.recordResult(claim,
                new IdempotencyResultReference(
                        IDEMPOTENCY_RESULT, invitation.id()));
        appendAudit(context.tenantId(), context.authenticatedUserId(),
                context.activeLegalEntityId(), invitation.id(), CREATED,
                correlationId, now);
        return invitationRepository.findById(invitation.id())
                .map(record -> toInitiatorProjection(record, context))
                .orElseThrow(() -> new IllegalStateException(
                        "Created invitation is unavailable"));
    }

    @Transactional(readOnly = true)
    DealInvitationPage listForDeal(OperationContext context, UUID dealId,
            InvitationPageQuery query) {
        requireOperation(context, RequestedOperation.DEAL_INVITATION_LIST_READ);
        Deal deal = loadVisibleDeal(context, dealId);
        requireDraftInitiator(context, deal);
        List<DealInvitationProjection> items = invitationRepository
                .findByDealId(dealId, query.size(), query.offset()).stream()
                .map(record -> toInitiatorProjection(record, context))
                .toList();
        long total = invitationRepository.countByDealId(dealId);
        return new DealInvitationPage(items, query.page(), query.size(), total,
                totalPages(total, query.size()));
    }

    @Transactional(readOnly = true)
    IncomingDealInvitationPage listIncoming(UUID userId,
            InvitationPageQuery query) {
        String email = currentEmail(userId);
        List<DealInvitationRepository.InvitationRecord> records =
                invitationRepository.findPendingForRecipient(
                        email, query.size(), query.offset());
        Map<UUID, String> initiatorNames = initiatorNames(records);
        List<IncomingDealInvitation> items = records.stream()
                .map(record -> toIncoming(record, initiatorNames))
                .toList();
        long total = invitationRepository.countPendingForRecipient(email);
        return new IncomingDealInvitationPage(items, query.page(), query.size(),
                total, totalPages(total, query.size()));
    }

    @Transactional
    IncomingDealInvitation accept(UUID userId, UUID invitationId,
            AcceptDealInvitationRequest request, UUID correlationId) {
        String email = currentEmail(userId);
        DealInvitationRepository.InvitationRecord current =
                loadRecipientInvitation(invitationId, email);
        InvitationLegalEntityMembership membership = legalEntityQueries
                .findCurrentMembership(userId, request.legalEntityId())
                .orElseThrow(LegalEntityNotFoundException::new);

        if (current.status() == DealInvitationStatus.ACCEPTED) {
            if (!request.legalEntityId().equals(
                    current.acceptedLegalEntityId())) {
                throw new DealInvitationAcceptedByOtherEntityException();
            }
            return toIncoming(current, initiatorNames(List.of(current)));
        }
        Instant now = clock.instant();
        DealInvitation.rehydrate(current).accept(membership.legalEntityId(),
                membership.tenantId(), request.version(), now);
        boolean accepted = invitationRepository.acceptPending(invitationId,
                email, request.version(), membership.legalEntityId(),
                membership.tenantId(), now);
        if (!accepted) {
            return classifyAcceptFailure(userId, email, invitationId, request,
                    membership);
        }
        invitationRepository.insertParticipant(current.dealId(),
                current.tenantId(), membership.legalEntityId(),
                membership.tenantId(), now);
        appendAudit(membership.tenantId(), userId, membership.legalEntityId(),
                invitationId, ACCEPTED, correlationId, now);
        DealInvitationRepository.InvitationRecord updated =
                loadRecipientInvitation(invitationId, email);
        return toIncoming(updated, initiatorNames(List.of(updated)));
    }

    @Transactional
    IncomingDealInvitation reject(UUID userId, UUID invitationId,
            DealInvitationTerminalActionRequest request, UUID correlationId) {
        String email = currentEmail(userId);
        DealInvitationRepository.InvitationRecord current =
                loadRecipientInvitation(invitationId, email);
        UUID actorTenantId = legalEntityQueries.findTenantIdForUser(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user has no technical tenant"));
        Instant now = clock.instant();
        DealInvitation.rehydrate(current).reject(request.version(), now);
        if (!invitationRepository.rejectPending(invitationId, email,
                request.version(), now)) {
            classifyTerminalFailure(invitationId, email, request.version());
        }
        appendAudit(actorTenantId, userId, null, invitationId, REJECTED,
                correlationId, now);
        DealInvitationRepository.InvitationRecord updated =
                loadRecipientInvitation(invitationId, email);
        return toIncoming(updated, initiatorNames(List.of(updated)));
    }

    @Transactional
    DealInvitationProjection revoke(OperationContext context,
            UUID invitationId, DealInvitationTerminalActionRequest request,
            UUID correlationId) {
        requireOperation(context, RequestedOperation.DEAL_INVITATION_REVOKE);
        DealInvitationRepository.InvitationRecord current = invitationRepository
                .findById(invitationId)
                .orElseThrow(DealInvitationNotFoundException::new);
        Deal deal = dealRepository.findVisibleById(context.tenantId(),
                        context.activeLegalEntityId(), current.dealId())
                .map(Deal::rehydrate)
                .orElseThrow(DealInvitationNotFoundException::new);
        requireDraftInitiator(context, deal);
        Instant now = clock.instant();
        DealInvitation.rehydrate(current).revoke(request.version(), now);
        if (!invitationRepository.revokePending(invitationId,
                context.activeLegalEntityId(), request.version(), now)) {
            classifyTerminalFailure(invitationId, null, request.version());
        }
        appendAudit(context.tenantId(), context.authenticatedUserId(),
                context.activeLegalEntityId(), invitationId, REVOKED,
                correlationId, now);
        DealInvitationRepository.InvitationRecord updated = invitationRepository
                .findById(invitationId)
                .orElseThrow(DealInvitationNotFoundException::new);
        return toInitiatorProjection(updated, context);
    }

    private IncomingDealInvitation classifyAcceptFailure(UUID userId,
            String email, UUID invitationId, AcceptDealInvitationRequest request,
            InvitationLegalEntityMembership membership) {
        DealInvitationRepository.InvitationRecord latest =
                loadRecipientInvitation(invitationId, email);
        if (latest.status() == DealInvitationStatus.ACCEPTED) {
            if (membership.legalEntityId().equals(
                    latest.acceptedLegalEntityId())) {
                return toIncoming(latest, initiatorNames(List.of(latest)));
            }
            throw new DealInvitationAcceptedByOtherEntityException();
        }
        requirePendingVersion(latest, request.version());
        throw new IllegalStateException(
                "Atomic invitation accept failed without a visible conflict");
    }

    private void classifyTerminalFailure(UUID invitationId,
            String recipientEmail, long expectedVersion) {
        DealInvitationRepository.InvitationRecord latest = recipientEmail == null
                ? invitationRepository.findById(invitationId)
                        .orElseThrow(DealInvitationNotFoundException::new)
                : loadRecipientInvitation(invitationId, recipientEmail);
        requirePendingVersion(latest, expectedVersion);
        throw new IllegalStateException(
                "Atomic invitation transition failed without a visible conflict");
    }

    private void requirePendingVersion(
            DealInvitationRepository.InvitationRecord invitation,
            long expectedVersion) {
        if (invitation.version() != expectedVersion) {
            throw new DealInvitationStaleVersionException();
        }
        if (invitation.status() != DealInvitationStatus.PENDING) {
            throw new DealInvitationStateConflictException();
        }
    }

    private Deal loadVisibleDeal(OperationContext context, UUID dealId) {
        return dealRepository.findVisibleById(context.tenantId(),
                        context.activeLegalEntityId(), dealId)
                .map(Deal::rehydrate)
                .orElseThrow(DealNotFoundException::new);
    }

    private void requireDraftInitiator(OperationContext context, Deal deal) {
        if (!deal.isInitiatedBy(context.activeLegalEntityId())
                || deal.status() != DealStatus.DRAFT) {
            throw new DealInvitationForbiddenException();
        }
    }

    private DealInvitationRepository.InvitationRecord loadRecipientInvitation(
            UUID invitationId, String recipientEmail) {
        return invitationRepository.findByIdAndRecipient(
                        invitationId, recipientEmail)
                .orElseThrow(DealInvitationNotFoundException::new);
    }

    private DealInvitationProjection toInitiatorProjection(
            DealInvitationRepository.InvitationRecord invitation,
            OperationContext context) {
        boolean canRevoke = invitation.status() == DealInvitationStatus.PENDING
                && invitation.deal() != null
                && invitation.deal().status() == DealStatus.DRAFT
                && invitation.deal().initiatorLegalEntityId()
                        .equals(context.activeLegalEntityId());
        return new DealInvitationProjection(invitation.id(),
                invitation.dealId(), invitation.recipientEmail(),
                invitation.status(), invitation.version(),
                invitation.createdAt(), invitation.updatedAt(),
                new DealInvitationAvailableActions(false, false, canRevoke));
    }

    private IncomingDealInvitation toIncoming(
            DealInvitationRepository.InvitationRecord invitation,
            Map<UUID, String> initiatorNames) {
        DealRepository.DealRecord deal = invitation.deal();
        String initiatorName = initiatorNames.get(
                deal.initiatorLegalEntityId());
        if (initiatorName == null) {
            throw new IllegalStateException(
                    "Invitation initiator projection is unavailable");
        }
        boolean pending = invitation.status() == DealInvitationStatus.PENDING;
        return new IncomingDealInvitation(invitation.id(),
                new DealInvitationDeal(deal.id(), deal.reference(), deal.title(),
                        initiatorName),
                invitation.status(), invitation.version(),
                invitation.createdAt(), invitation.updatedAt(),
                new DealInvitationAvailableActions(pending, pending, false));
    }

    private Map<UUID, String> initiatorNames(
            List<DealInvitationRepository.InvitationRecord> invitations) {
        Set<UUID> ids = invitations.stream()
                .map(DealInvitationRepository.InvitationRecord::deal)
                .map(DealRepository.DealRecord::initiatorLegalEntityId)
                .collect(Collectors.toSet());
        return legalEntityQueries.findLegalNames(ids);
    }

    private String currentEmail(UUID userId) {
        return emailQueries.findNormalizedEmail(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user email is unavailable"));
    }

    private void appendAudit(UUID actorTenantId, UUID actorUserId,
            UUID actorLegalEntityId, UUID invitationId, String action,
            UUID correlationId, Instant occurredAt) {
        auditAppender.append(new AuditRecord(UUID.randomUUID(), actorTenantId,
                actorUserId, actorLegalEntityId, SUBJECT, invitationId, action,
                correlationId, null, occurredAt));
    }

    private void requireOperation(OperationContext context,
            RequestedOperation expected) {
        if (context.requestedOperation() != expected) {
            throw new IllegalArgumentException(
                    "Operation context does not match the requested use case");
        }
    }

    private int totalPages(long total, int size) {
        return Math.toIntExact(total / size + (total % size == 0 ? 0 : 1));
    }

    private String canonicalHash(UUID activeLegalEntityId, UUID dealId,
            String recipientEmail) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    (activeLegalEntityId + "\n" + dealId + "\n"
                            + recipientEmail)
                            .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
