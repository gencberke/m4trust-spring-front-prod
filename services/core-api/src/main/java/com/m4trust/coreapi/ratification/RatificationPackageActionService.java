package com.m4trust.coreapi.ratification;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.m4trust.coreapi.audit.AuditAppendPort;
import com.m4trust.coreapi.audit.AuditRecord;
import com.m4trust.coreapi.idempotency.IdempotencyClaim;
import com.m4trust.coreapi.idempotency.IdempotencyRequest;
import com.m4trust.coreapi.idempotency.IdempotencyResultReference;
import com.m4trust.coreapi.idempotency.IdempotencyService;
import com.m4trust.coreapi.organization.LegalEntityRole;
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** Applies one buyer/seller entity decision to the current immutable package. */
@Service
class RatificationPackageActionService {
    private static final String RESULT_TYPE = "RATIFICATION_PACKAGE";
    private static final String AUDIT_SUBJECT = "RATIFICATION_PACKAGE";

    private final RatificationSourcePorts.DealTarget deals;
    private final RatificationRepository packages;
    private final RatificationPackageReadService reads;
    private final IdempotencyService idempotency;
    private final AuditAppendPort audit;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final CanonicalSnapshotHasher hasher;
    private final ObjectMapper json;

    RatificationPackageActionService(
            RatificationSourcePorts.DealTarget deals,
            RatificationRepository packages,
            RatificationPackageReadService reads,
            IdempotencyService idempotency,
            AuditAppendPort audit,
            TransactionTemplate transactions,
            Clock clock,
            CanonicalSnapshotHasher hasher,
            ObjectMapper json) {
        this.deals = deals;
        this.packages = packages;
        this.reads = reads;
        this.idempotency = idempotency;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
        this.hasher = hasher;
        this.json = json;
    }

    RatificationPackageReadDtos.Detail approve(
            OperationContext context,
            UUID dealId,
            UUID packageId,
            RatificationPackageActionRequest request,
            UUID key,
            UUID correlationId) {
        return action(context, dealId, packageId, request, key, correlationId, Action.APPROVE);
    }

    RatificationPackageReadDtos.Detail reject(
            OperationContext context,
            UUID dealId,
            UUID packageId,
            RatificationPackageActionRequest request,
            UUID key,
            UUID correlationId) {
        return action(context, dealId, packageId, request, key, correlationId, Action.REJECT);
    }

    private RatificationPackageReadDtos.Detail action(
            OperationContext context,
            UUID dealId,
            UUID packageId,
            RatificationPackageActionRequest request,
            UUID key,
            UUID correlationId,
            Action action) {
        if (context.requestedOperation() != action.requestedOperation) {
            throw new IllegalArgumentException("Operation context mismatch");
        }
        IdempotencyRequest idempotencyRequest = idempotencyRequest(
                context, dealId, packageId, request, key, action);
        RatificationPackageReadDtos.Detail result = transactions.execute(status -> mutate(
                context, dealId, packageId, request, idempotencyRequest, correlationId, action));
        if (result == null) {
            throw new IllegalStateException("Transaction returned no result");
        }
        return result;
    }

    private RatificationPackageReadDtos.Detail mutate(
            OperationContext context,
            UUID dealId,
            UUID packageId,
            RatificationPackageActionRequest request,
            IdempotencyRequest idempotencyRequest,
            UUID correlationId,
            Action action) {
        // Every package lifecycle mutation serializes Deal -> current package.
        RatificationSourcePorts.Target target = deals.lockVisibleForCreate(context, dealId)
                .orElseThrow(NotFound::new);
        RatificationRepository.PackageRecord current = target.currentPackageId() == null
                ? null
                : packages.findByDealAndIdForUpdate(dealId, target.currentPackageId())
                        .orElseThrow(Stale::new);
        IdempotencyClaim claim = idempotency.claim(idempotencyRequest);
        if (claim.isReplay()) {
            return replay(context, target, claim.resultReference());
        }
        if (current == null || !packageId.equals(current.id())) {
            if (packages.findByDealAndId(dealId, packageId).isEmpty()) {
                throw new NotFound();
            }
            throw new Stale();
        }
        requireAuthority(context, current);
        if (!"DRAFT".equals(target.status())) {
            throw new State();
        }
        // A stale caller is rejected even when this entity already approved.
        if (current.version() != request.expectedPackageVersion()) {
            throw new Stale();
        }
        try {
            return action == Action.APPROVE
                    ? approveFresh(context, target, current, request, claim, correlationId)
                    : rejectFresh(context, target, current, request, claim, correlationId);
        } catch (RatificationPackage.StaleVersion exception) {
            throw new Stale();
        } catch (RatificationPackage.StateConflict exception) {
            throw new State();
        }
    }

    private RatificationPackageReadDtos.Detail approveFresh(
            OperationContext context,
            RatificationSourcePorts.Target target,
            RatificationRepository.PackageRecord record,
            RatificationPackageActionRequest request,
            IdempotencyClaim claim,
            UUID correlationId) {
        RatificationPackage packageState = RatificationPackage.rehydrate(record);
        if (packageState.status() != RatificationPackageStatus.PENDING) {
            throw new State();
        }
        if (packages.findApprovalByPackageAndEntity(
                packageState.id(), context.activeLegalEntityId()).isPresent()) {
            return finish(context, target, record.id(), claim);
        }

        Instant now = clock.instant();
        packages.insertApproval(new RatificationRepository.ApprovalRecord(
                UUID.randomUUID(), packageState.id(), context.activeLegalEntityId(),
                context.authenticatedUserId(), now));
        appendPackageAudit(context, packageState.id(),
                "RATIFICATION_PACKAGE_APPROVED", correlationId, now);

        boolean otherPartyAlreadyApproved = packages.listApprovals(packageState.id()).stream()
                .anyMatch(approval -> !approval.legalEntityId().equals(context.activeLegalEntityId()));
        if (otherPartyAlreadyApproved) {
            packageState.ratify(request.expectedPackageVersion());
            update(packageState, request.expectedPackageVersion());
            deals.activateCurrentPackage(target.dealId(), packageState.id(), now);
            appendPackageAudit(context, packageState.id(),
                    "RATIFICATION_PACKAGE_RATIFIED", correlationId, now);
            audit.append(new AuditRecord(
                    UUID.randomUUID(), context.tenantId(), context.authenticatedUserId(),
                    context.activeLegalEntityId(), "DEAL", target.dealId(), "DEAL_ACTIVATED",
                    correlationId, null, now));
        } else {
            packageState.approve(request.expectedPackageVersion());
            update(packageState, request.expectedPackageVersion());
        }
        return finish(context, target, packageState.id(), claim);
    }

    private RatificationPackageReadDtos.Detail rejectFresh(
            OperationContext context,
            RatificationSourcePorts.Target target,
            RatificationRepository.PackageRecord record,
            RatificationPackageActionRequest request,
            IdempotencyClaim claim,
            UUID correlationId) {
        RatificationPackage packageState = RatificationPackage.rehydrate(record);
        Instant now = clock.instant();
        packageState.reject(request.expectedPackageVersion());
        update(packageState, request.expectedPackageVersion());
        appendPackageAudit(context, packageState.id(),
                "RATIFICATION_PACKAGE_REJECTED", correlationId, now);
        return finish(context, target, packageState.id(), claim);
    }

    private RatificationPackageReadDtos.Detail replay(
            OperationContext context,
            RatificationSourcePorts.Target target,
            IdempotencyResultReference reference) {
        if (!RESULT_TYPE.equals(reference.type())) {
            throw new IllegalStateException("Unexpected result type");
        }
        RatificationRepository.PackageRecord result = packages
                .findByDealAndId(target.dealId(), reference.id())
                .orElseThrow(NotFound::new);
        return reads.project(context, target, result);
    }

    private RatificationPackageReadDtos.Detail finish(
            OperationContext context,
            RatificationSourcePorts.Target target,
            UUID packageId,
            IdempotencyClaim claim) {
        idempotency.recordResult(claim, new IdempotencyResultReference(RESULT_TYPE, packageId));
        RatificationRepository.PackageRecord stored = packages
                .findByDealAndId(target.dealId(), packageId)
                .orElseThrow(NotFound::new);
        return reads.project(context, target, stored);
    }

    private static void requireAuthority(
            OperationContext context,
            RatificationRepository.PackageRecord record) {
        boolean assignedParty = context.activeLegalEntityId().equals(record.buyerLegalEntityId())
                || context.activeLegalEntityId().equals(record.sellerLegalEntityId());
        if (context.activeLegalEntityRole() != LegalEntityRole.ADMIN || !assignedParty) {
            throw new Forbidden();
        }
    }

    private void update(RatificationPackage packageState, long previousVersion) {
        if (!packages.updateStatus(packageState.toRecord(), previousVersion)) {
            throw new Stale();
        }
    }

    private void appendPackageAudit(
            OperationContext context,
            UUID packageId,
            String action,
            UUID correlationId,
            Instant now) {
        audit.append(new AuditRecord(
                UUID.randomUUID(), context.tenantId(), context.authenticatedUserId(),
                context.activeLegalEntityId(), AUDIT_SUBJECT, packageId, action,
                correlationId, null, now));
    }

    private IdempotencyRequest idempotencyRequest(
            OperationContext context,
            UUID dealId,
            UUID packageId,
            RatificationPackageActionRequest request,
            UUID key,
            Action action) {
        try {
            Map<String, Object> canonicalRequest = new LinkedHashMap<>();
            canonicalRequest.put("action", action.name());
            canonicalRequest.put("actorEntity", context.activeLegalEntityId().toString());
            canonicalRequest.put("dealId", dealId.toString());
            canonicalRequest.put("packageId", packageId.toString());
            canonicalRequest.put("expectedPackageVersion", request.expectedPackageVersion());
            return new IdempotencyRequest(
                    context.authenticatedUserId(), context.tenantId(), action.idempotencyOperation,
                    key, hasher.hash(json.writeValueAsString(canonicalRequest)));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot canonicalize idempotency request", exception);
        }
    }

    private enum Action {
        APPROVE(
                RequestedOperation.DEAL_RATIFICATION_PACKAGE_APPROVE,
                "RATIFICATION_PACKAGE_APPROVE"),
        REJECT(
                RequestedOperation.DEAL_RATIFICATION_PACKAGE_REJECT,
                "RATIFICATION_PACKAGE_REJECT");

        private final RequestedOperation requestedOperation;
        private final String idempotencyOperation;

        Action(RequestedOperation requestedOperation, String idempotencyOperation) {
            this.requestedOperation = requestedOperation;
            this.idempotencyOperation = idempotencyOperation;
        }
    }

    static final class NotFound extends RuntimeException { }
    static final class Stale extends RuntimeException { }
    static final class State extends RuntimeException { }
    static final class Forbidden extends RuntimeException { }
}
