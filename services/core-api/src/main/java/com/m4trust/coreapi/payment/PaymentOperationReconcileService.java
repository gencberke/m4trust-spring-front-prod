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
 * Buyer-ADMIN query-first reconciliation dispatch (ADR-010 §2.4). Never calls
 * the provider within the request: writes a durable RECONCILE dispatch record,
 * audit, and the HTTP idempotency result in one short transaction and returns
 * 202 with the same operation projection. {@link PaymentDispatchRelay} performs
 * the out-of-transaction {@code queryStatus} call later.
 */
@Service
class PaymentOperationReconcileService {
    private static final String IDEMPOTENCY_OPERATION = "PAYMENT_OPERATION_RECONCILE";
    private static final String IDEMPOTENCY_RESULT = "PAYMENT_OPERATION";
    private static final String AUDIT_SUBJECT = "PAYMENT_OPERATION";

    private final FundingSourcePorts.DealTarget deals;
    private final FundingRepository funding;
    private final IdempotencyService idempotency;
    private final AuditAppendPort audit;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final PaymentCanonicalHasher hasher;
    private final ObjectMapper json;

    PaymentOperationReconcileService(FundingSourcePorts.DealTarget deals, FundingRepository funding,
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

    FundingReadDtos.PaymentOperationView reconcile(OperationContext context, UUID paymentOperationId,
            long expectedVersion, UUID idempotencyKey, UUID correlationId) {
        requireOperation(context);
        IdempotencyRequest idempotencyRequest = idempotencyRequest(
                context, paymentOperationId, expectedVersion, idempotencyKey);
        FundingReadDtos.PaymentOperationView result = transactions.execute(status -> reconcileInTransaction(
                context, paymentOperationId, expectedVersion, idempotencyRequest, correlationId));
        if (result == null) {
            throw new IllegalStateException("Transaction returned no result");
        }
        return result;
    }

    private FundingReadDtos.PaymentOperationView reconcileInTransaction(OperationContext context,
            UUID paymentOperationId, long expectedVersion, IdempotencyRequest idempotencyRequest,
            UUID correlationId) {
        FundingRepository.OperationLookup lookup = funding.findOperationByIdForUpdate(paymentOperationId)
                .orElseThrow(PaymentExceptions.PaymentOperationNotFound::new);
        FundingSourcePorts.Target target = deals.findVisible(context, lookup.dealId())
                .orElseThrow(PaymentExceptions.PaymentOperationNotFound::new);
        IdempotencyClaim claim = idempotency.claim(idempotencyRequest);
        boolean buyerAdmin = FundingProjection.isBuyerAdmin(context, target);
        if (claim.isReplay()) {
            return replay(claim.resultReference(), buyerAdmin);
        }
        if (!buyerAdmin) {
            throw new PaymentExceptions.FundingMutationForbidden();
        }
        PaymentOperation operation = PaymentOperation.rehydrate(lookup.operation());
        if (operation.terminal()) {
            throw new PaymentExceptions.PaymentOperationStateConflict();
        }
        if (operation.version() != expectedVersion) {
            throw new PaymentExceptions.PaymentOperationStaleVersion();
        }
        Instant now = clock.instant();
        funding.insertDispatch(new FundingRepository.DispatchRecord(UUID.randomUUID(), operation.id(),
                FundingRepository.DispatchType.RECONCILE, operation.providerKey(), lookup.unitAmountMinor(),
                lookup.unitCurrency(), now));
        audit.append(auditRecord(context, operation.id(), "PAYMENT_OPERATION_RECONCILE_REQUESTED", correlationId,
                now));
        idempotency.recordResult(claim, new IdempotencyResultReference(IDEMPOTENCY_RESULT, operation.id()));
        return FundingProjection.operation(operation.toRecord(), buyerAdmin);
    }

    private FundingReadDtos.PaymentOperationView replay(IdempotencyResultReference reference, boolean buyerAdmin) {
        if (!IDEMPOTENCY_RESULT.equals(reference.type())) {
            throw new IllegalStateException("Unexpected idempotency result type");
        }
        FundingRepository.OperationLookup current = funding.findOperationById(reference.id())
                .orElseThrow(() -> new IllegalStateException("Replayed payment operation is unavailable"));
        return FundingProjection.operation(current.operation(), buyerAdmin);
    }

    private AuditRecord auditRecord(OperationContext context, UUID operationId, String action, UUID correlationId,
            Instant now) {
        return new AuditRecord(UUID.randomUUID(), context.tenantId(), context.authenticatedUserId(),
                context.activeLegalEntityId(), AUDIT_SUBJECT, operationId, action, correlationId, null, now);
    }

    private IdempotencyRequest idempotencyRequest(OperationContext context, UUID paymentOperationId,
            long expectedVersion, UUID key) {
        try {
            Map<String, Object> canonicalRequest = new LinkedHashMap<>();
            canonicalRequest.put("actorEntity", context.activeLegalEntityId().toString());
            canonicalRequest.put("paymentOperationId", paymentOperationId.toString());
            canonicalRequest.put("expectedVersion", expectedVersion);
            return new IdempotencyRequest(context.authenticatedUserId(), context.tenantId(), IDEMPOTENCY_OPERATION,
                    key, hasher.hash(json.writeValueAsString(canonicalRequest)));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot canonicalize payment operation reconcile request", exception);
        }
    }

    private static void requireOperation(OperationContext context) {
        if (context.requestedOperation() != RequestedOperation.PAYMENT_OPERATION_RECONCILE) {
            throw new IllegalArgumentException("Operation context does not match payment operation reconcile");
        }
    }
}
