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
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** Creates a pending package from the Deal's current ready sources. */
@Service
class RatificationPackageCreateService {
    private static final String IDEMPOTENCY_OPERATION = "RATIFICATION_PACKAGE_CREATE";
    private static final String IDEMPOTENCY_RESULT = "RATIFICATION_PACKAGE";
    private static final String AUDIT_SUBJECT = "RATIFICATION_PACKAGE";
    private static final String AUDIT_CREATED = "RATIFICATION_PACKAGE_CREATED";
    private static final String AUDIT_SUPERSEDED = "RATIFICATION_PACKAGE_SUPERSEDED";

    private final RatificationSourcePorts.DealTarget deals;
    private final RatificationSourcePorts.AvailableDocument documents;
    private final RatificationSourcePorts.AcceptedRuleSet ruleSets;
    private final RatificationRepository packages;
    private final RatificationSnapshotAssembler assembler;
    private final CanonicalSnapshotHasher hasher;
    private final ObjectMapper json;
    private final RatificationPackageReadService reads;
    private final IdempotencyService idempotency;
    private final AuditAppendPort audit;
    private final TransactionTemplate transactions;
    private final Clock clock;

    RatificationPackageCreateService(RatificationSourcePorts.DealTarget deals,
            RatificationSourcePorts.AvailableDocument documents,
            RatificationSourcePorts.AcceptedRuleSet ruleSets,
            RatificationRepository packages, RatificationSnapshotAssembler assembler,
            CanonicalSnapshotHasher hasher, ObjectMapper json, RatificationPackageReadService reads, IdempotencyService idempotency,
            AuditAppendPort audit, TransactionTemplate transactions, Clock clock) {
        this.deals = deals;
        this.documents = documents;
        this.ruleSets = ruleSets;
        this.packages = packages;
        this.assembler = assembler;
        this.hasher = hasher;
        this.json = json;
        this.reads = reads;
        this.idempotency = idempotency;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
    }

    RatificationPackageReadDtos.Detail create(OperationContext context, UUID dealId,
            CreateRatificationPackageRequest request, UUID idempotencyKey, UUID correlationId) {
        requireOperation(context);
        IdempotencyRequest idempotencyRequest = idempotencyRequest(context, dealId, request, idempotencyKey);
        return required(transactions.execute(status -> createInTransaction(context, dealId, request,
                idempotencyRequest, correlationId)));
    }

    private RatificationPackageReadDtos.Detail createInTransaction(OperationContext context, UUID dealId,
            CreateRatificationPackageRequest request, IdempotencyRequest idempotencyRequest,
            UUID correlationId) {
        // This ordering is shared with all package lifecycle mutations.
        RatificationSourcePorts.Target target = deals.lockVisibleForCreate(context, dealId)
                .orElseThrow(PackageNotFound::new);
        RatificationRepository.PackageRecord current = target.currentPackageId() == null ? null
                : packages.findByDealAndIdForUpdate(dealId, target.currentPackageId())
                        .orElseThrow(() -> new IllegalStateException("Deal current package is unavailable"));
        IdempotencyClaim claim = idempotency.claim(idempotencyRequest);
        if (claim.isReplay()) {
            return replay(context, target, claim.resultReference());
        }
        validateCreateTarget(target, request);
        RatificationSourcePorts.Document document = documents.find(target.currentDocumentId())
                .orElseThrow(NotReady::new);
        RatificationSourcePorts.RuleSet ruleSet = ruleSets.find(target.currentRuleSetId())
                .orElseThrow(NotReady::new);
        RatificationSnapshotAssembler.Result snapshot = assembler.assemble(target, document, ruleSet,
                request.amountMinor(), request.currency(), request.disputeWindowDays());
        if (current != null && current.status() == RatificationPackageStatus.PENDING
                && current.contentHash().equals(snapshot.contentHash())) {
            idempotency.recordResult(claim, new IdempotencyResultReference(IDEMPOTENCY_RESULT, current.id()));
            return reads.project(context, target, current);
        }
        Instant now = clock.instant();
        if (current != null && current.status() == RatificationPackageStatus.PENDING) {
            RatificationPackage previous = RatificationPackage.rehydrate(current);
            long previousVersion = previous.version();
            previous.supersede(previousVersion);
            if (!packages.updateStatus(previous.toRecord(), previousVersion)) {
                throw new RatificationPackage.StaleVersion();
            }
            audit.append(audit(context, current.id(), AUDIT_SUPERSEDED, correlationId, now));
        }
        UUID snapshotId = UUID.randomUUID();
        RatificationPackage created = RatificationPackage.create(UUID.randomUUID(), dealId, snapshotId,
                target.buyer().legalEntityId(), target.seller().legalEntityId(), request.amountMinor(),
                request.currency(), now);
        RatificationRepository.SnapshotRecord snapshotRecord = new RatificationRepository.SnapshotRecord(
                snapshotId, snapshot.snapshot().schemaVersion(), snapshot.serializedSnapshot(),
                snapshot.contentHash(), now);
        RatificationRepository.PackageRecord createdRecord = created.toRecord();
        packages.insert(snapshotRecord, createdRecord);
        deals.pointCurrentPackage(dealId, created.id(), now);
        audit.append(audit(context, created.id(), AUDIT_CREATED, correlationId, now));
        idempotency.recordResult(claim, new IdempotencyResultReference(IDEMPOTENCY_RESULT, created.id()));
        return reads.project(context, target, withSnapshot(createdRecord, snapshot));
    }

    private RatificationPackageReadDtos.Detail replay(OperationContext context,
            RatificationSourcePorts.Target target, IdempotencyResultReference reference) {
        if (!IDEMPOTENCY_RESULT.equals(reference.type())) {
            throw new IllegalStateException("Unexpected idempotency result type");
        }
        RatificationRepository.PackageRecord record = packages.findByDealAndId(target.dealId(), reference.id())
                .orElseThrow(PackageNotFound::new);
        return reads.project(context, target, record);
    }

    private static RatificationRepository.PackageRecord withSnapshot(RatificationRepository.PackageRecord record,
            RatificationSnapshotAssembler.Result snapshot) {
        return new RatificationRepository.PackageRecord(record.id(), record.dealId(), record.snapshotId(),
                record.status(), record.buyerLegalEntityId(), record.sellerLegalEntityId(), record.amountMinor(),
                record.currency(), record.createdAt(), record.version(), snapshot.snapshot().schemaVersion(),
                snapshot.serializedSnapshot(), snapshot.contentHash());
    }

    private static void validateCreateTarget(RatificationSourcePorts.Target target,
            CreateRatificationPackageRequest request) {
        if (!target.initiator()) throw new Forbidden();
        if (!"DRAFT".equals(target.status())) throw new StateConflict();
        if (target.version() != request.expectedDealVersion()) throw new StaleDealVersion();
        if (target.currentDocumentId() == null || target.currentRuleSetId() == null
                || target.buyer() == null || target.seller() == null) throw new NotReady();
        if (request.amountMinor() < 1 || request.amountMinor() > RatificationPackage.MAX_SAFE_INTEGER
                || request.currency() == null || !request.currency().matches("[A-Z]{3}")) throw new InvalidTerms();
        if (request.disputeWindowDays() != null
                && (request.disputeWindowDays() < 0 || request.disputeWindowDays() > 365)) {
            throw new InvalidDisputeWindow();
        }
    }

    private AuditRecord audit(OperationContext context, UUID packageId, String action,
            UUID correlationId, Instant now) {
        return new AuditRecord(UUID.randomUUID(), context.tenantId(), context.authenticatedUserId(),
                context.activeLegalEntityId(), AUDIT_SUBJECT, packageId, action, correlationId, null, now);
    }

    private IdempotencyRequest idempotencyRequest(OperationContext context, UUID dealId,
            CreateRatificationPackageRequest request, UUID key) {
        return new IdempotencyRequest(context.authenticatedUserId(), context.tenantId(), IDEMPOTENCY_OPERATION,
                key, canonicalRequestHash(context, dealId, request));
    }

    private String canonicalRequestHash(OperationContext context, UUID dealId,
            CreateRatificationPackageRequest request) {
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("activeLegalEntityId", context.activeLegalEntityId().toString());
            requestBody.put("dealId", dealId.toString());
            requestBody.put("expectedDealVersion", request.expectedDealVersion());
            requestBody.put("amountMinor", request.amountMinor());
            requestBody.put("currency", request.currency());
            if (request.disputeWindowDays() != null) {
                requestBody.put("disputeWindowDays", request.disputeWindowDays());
            }
            return hasher.hash(json.writeValueAsString(requestBody));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot canonicalize ratification package create request", exception);
        }
    }

    private static void requireOperation(OperationContext context) {
        if (context.requestedOperation() != RequestedOperation.DEAL_RATIFICATION_PACKAGE_CREATE) {
            throw new IllegalArgumentException("Operation context does not match ratification package create");
        }
    }

    private static <T> T required(T value) {
        if (value == null) throw new IllegalStateException("Transaction returned no result");
        return value;
    }

    static final class PackageNotFound extends RuntimeException { }
    static final class NotReady extends RuntimeException { }
    static final class Forbidden extends RuntimeException { }
    static final class StateConflict extends RuntimeException { }
    static final class StaleDealVersion extends RuntimeException { }
    static final class InvalidTerms extends RuntimeException { }
    static final class InvalidDisputeWindow extends RuntimeException { }
}
