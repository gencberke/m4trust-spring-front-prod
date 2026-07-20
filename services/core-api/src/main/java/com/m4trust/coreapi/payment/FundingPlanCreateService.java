package com.m4trust.coreapi.payment;

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

/**
 * Explicit, buyer-ADMIN, idempotent FundingPlan creation from the Deal's
 * current RATIFIED package (ADR-010 §2.2). The Deal row is locked so a
 * concurrent create races safely; the DB unique invariant on
 * {@code funding_plan.deal_id} is still the final word.
 */
@Service
class FundingPlanCreateService {
    private static final String IDEMPOTENCY_OPERATION = "FUNDING_PLAN_CREATE";
    private static final String IDEMPOTENCY_RESULT = "FUNDING_PLAN";
    private static final String AUDIT_SUBJECT = "FUNDING_PLAN";

    private final FundingSourcePorts.DealTarget deals;
    private final FundingRepository funding;
    private final IdempotencyService idempotency;
    private final AuditAppendPort audit;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final PaymentCanonicalHasher hasher;
    private final ObjectMapper json;

    FundingPlanCreateService(FundingSourcePorts.DealTarget deals, FundingRepository funding,
            IdempotencyService idempotency, AuditAppendPort audit, TransactionTemplate transactions, Clock clock,
            PaymentCanonicalHasher hasher, ObjectMapper json) {
        this.deals = deals;
        this.funding = funding;
        this.idempotency = idempotency;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
        this.hasher = hasher;
        this.json = json;
    }

    FundingReadDtos.FundingPlanDetailView create(OperationContext context, UUID dealId, long expectedVersion,
            UUID idempotencyKey, UUID correlationId) {
        requireOperation(context);
        IdempotencyRequest idempotencyRequest = idempotencyRequest(context, dealId, expectedVersion, idempotencyKey);
        FundingReadDtos.FundingPlanDetailView result = transactions.execute(status ->
                createInTransaction(context, dealId, expectedVersion, idempotencyRequest, correlationId));
        if (result == null) {
            throw new IllegalStateException("Transaction returned no result");
        }
        return result;
    }

    private FundingReadDtos.FundingPlanDetailView createInTransaction(OperationContext context, UUID dealId,
            long expectedVersion, IdempotencyRequest idempotencyRequest, UUID correlationId) {
        FundingSourcePorts.Target target = deals.lockVisibleForCreate(context, dealId)
                .orElseThrow(PaymentExceptions.DealNotFound::new);
        IdempotencyClaim claim = idempotency.claim(idempotencyRequest);
        if (claim.isReplay()) {
            return replay(target, claim.resultReference());
        }
        if (!FundingProjection.isBuyerAdmin(context, target)) {
            throw new PaymentExceptions.FundingMutationForbidden();
        }
        if (!"ACTIVE".equals(target.status())) {
            throw new PaymentExceptions.DealStateConflict();
        }
        if (target.version() != expectedVersion) {
            throw new PaymentExceptions.DealStaleVersion();
        }
        if (funding.findPlanByDeal(dealId).isPresent()) {
            throw new PaymentExceptions.FundingPlanAlreadyExists();
        }
        if (target.ratifiedAmountMinor() == null || target.ratifiedCurrency() == null) {
            throw new IllegalStateException("ACTIVE Deal has no ratified funding terms");
        }
        Instant now = clock.instant();
        FundingPlan plan = FundingPlan.create(UUID.randomUUID(), dealId, target.tenantId(),
                target.ratifiedAmountMinor(), target.ratifiedCurrency(), now);
        FundingUnit unit = FundingUnit.create(UUID.randomUUID(), plan.id(), target.ratifiedAmountMinor(),
                target.ratifiedCurrency(), now);
        if (!funding.insertPlanAndUnit(plan.toRecord(), unit.toRecord())) {
            // Lost a race under the DB unique invariant despite the Deal-row lock
            // (defensive; the lock should already serialize this).
            throw new PaymentExceptions.FundingPlanAlreadyExists();
        }
        audit.append(auditRecord(context, plan.id(), "FUNDING_PLAN_CREATED", correlationId, now));
        idempotency.recordResult(claim, new IdempotencyResultReference(IDEMPOTENCY_RESULT, plan.id()));
        boolean buyerAdmin = FundingProjection.isBuyerAdmin(context, target);
        return FundingProjection.plan(plan.toRecord(), unit.toRecord(), null, buyerAdmin, true);
    }

    private FundingReadDtos.FundingPlanDetailView replay(FundingSourcePorts.Target target,
            IdempotencyResultReference reference) {
        if (!IDEMPOTENCY_RESULT.equals(reference.type())) {
            throw new IllegalStateException("Unexpected idempotency result type");
        }
        FundingRepository.PlanRecord plan = funding.findPlanByDeal(target.dealId())
                .filter(record -> record.id().equals(reference.id()))
                .orElseThrow(() -> new IllegalStateException("Replayed funding plan is unavailable"));
        FundingRepository.UnitRecord unit = funding.findUnitByPlan(plan.id())
                .orElseThrow(() -> new IllegalStateException("Funding plan is missing its funding unit"));
        FundingRepository.OperationRecord currentOperation = funding.findCurrentOperation(unit.id()).orElse(null);
        boolean buyerAdmin = target.buyerLegalEntityId() != null;
        return FundingProjection.plan(plan, unit, currentOperation, buyerAdmin, "ACTIVE".equals(target.status()));
    }

    private AuditRecord auditRecord(OperationContext context, UUID planId, String action, UUID correlationId,
            Instant now) {
        return new AuditRecord(UUID.randomUUID(), context.tenantId(), context.authenticatedUserId(),
                context.activeLegalEntityId(), AUDIT_SUBJECT, planId, action, correlationId, null, now);
    }

    private IdempotencyRequest idempotencyRequest(OperationContext context, UUID dealId, long expectedVersion,
            UUID key) {
        try {
            Map<String, Object> canonicalRequest = new LinkedHashMap<>();
            canonicalRequest.put("actorEntity", context.activeLegalEntityId().toString());
            canonicalRequest.put("dealId", dealId.toString());
            canonicalRequest.put("expectedVersion", expectedVersion);
            return new IdempotencyRequest(context.authenticatedUserId(), context.tenantId(), IDEMPOTENCY_OPERATION,
                    key, hasher.hash(json.writeValueAsString(canonicalRequest)));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot canonicalize funding plan create request", exception);
        }
    }

    private static void requireOperation(OperationContext context) {
        if (context.requestedOperation() != RequestedOperation.DEAL_FUNDING_PLAN_CREATE) {
            throw new IllegalArgumentException("Operation context does not match funding plan create");
        }
    }
}
