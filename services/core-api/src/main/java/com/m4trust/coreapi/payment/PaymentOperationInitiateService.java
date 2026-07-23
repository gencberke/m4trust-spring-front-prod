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
 * Buyer-ADMIN payment initiation. Writes the CREATED intent, its lifetime-fixed
 * provider key, the durable INITIATE dispatch record, audit, and the HTTP
 * idempotency result in one short transaction; the provider is never called
 * here (ADR-010 §2.4). {@link PaymentDispatchRelay} performs the out-of-transaction
 * provider call later.
 */
@Service
class PaymentOperationInitiateService {
    private static final String IDEMPOTENCY_OPERATION = "PAYMENT_OPERATION_INITIATE";
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
    private final PaymentProviderModeResolver mode;

    PaymentOperationInitiateService(FundingSourcePorts.DealTarget deals, FundingRepository funding,
            IdempotencyService idempotency, AuditAppendPort audit, TransactionTemplate transactions, Clock clock,
            PaymentCanonicalHasher hasher, ObjectMapper json, PaymentProviderModeResolver mode) {
        this.deals = deals;
        this.funding = funding;
        this.idempotency = idempotency;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
        this.hasher = hasher;
        this.json = json;
        this.mode = mode;
    }

    FundingReadDtos.PaymentOperationView initiate(OperationContext context, UUID fundingUnitId,
            long expectedVersion, UUID idempotencyKey, UUID correlationId) {
        requireOperation(context);
        IdempotencyRequest idempotencyRequest = idempotencyRequest(
                context, fundingUnitId, expectedVersion, idempotencyKey);
        FundingReadDtos.PaymentOperationView result = transactions.execute(status ->
                initiateInTransaction(context, fundingUnitId, expectedVersion, idempotencyRequest, correlationId));
        if (result == null) {
            throw new IllegalStateException("Transaction returned no result");
        }
        return result;
    }

    private FundingReadDtos.PaymentOperationView initiateInTransaction(OperationContext context,
            UUID fundingUnitId, long expectedVersion, IdempotencyRequest idempotencyRequest, UUID correlationId) {
        FundingRepository.UnitLookup lookup = funding.findUnitByIdForUpdate(fundingUnitId)
                .orElseThrow(PaymentExceptions.FundingUnitNotFound::new);
        FundingSourcePorts.Target target = deals.findVisible(context, lookup.dealId())
                .orElseThrow(PaymentExceptions.FundingUnitNotFound::new);
        IdempotencyClaim claim = idempotency.claim(idempotencyRequest);
        boolean buyerAdmin = FundingProjection.isBuyerAdmin(context, target);
        if (claim.isReplay()) {
            return replay(claim.resultReference(), buyerAdmin);
        }
        if (!buyerAdmin) {
            throw new PaymentExceptions.FundingMutationForbidden();
        }
        if (!"ACTIVE".equals(target.status())) {
            throw new PaymentExceptions.DealStateConflict();
        }
        FundingUnit unit = FundingUnit.rehydrate(lookup.unit());
        if (unit.status() == FundingUnitStatus.FUNDED) {
            throw new PaymentExceptions.FundingUnitAlreadyFunded();
        }
        if (funding.findInFlightOperation(fundingUnitId).isPresent()) {
            throw new PaymentExceptions.PaymentOperationInFlight();
        }
        try {
            unit.beginPayment(expectedVersion, clock.instant());
        } catch (FundingUnit.StaleVersion exception) {
            throw new PaymentExceptions.FundingUnitStaleVersion();
        } catch (FundingUnit.StateConflict exception) {
            throw new PaymentExceptions.PaymentOperationInFlight();
        }
        if (!funding.updateUnitStatus(unit.toRecord(), lookup.unit().version())) {
            throw new PaymentExceptions.FundingUnitStaleVersion();
        }
        Instant now = unit.updatedAt();
        UUID providerKey = UUID.randomUUID();
        PaymentOperation operation = PaymentOperation.create(UUID.randomUUID(), fundingUnitId, providerKey, now);
        funding.insertOperation(operation.toRecord());
        funding.insertDispatch(new FundingRepository.DispatchRecord(UUID.randomUUID(), operation.id(),
                FundingRepository.DispatchType.INITIATE, providerKey, unit.amountMinor(), unit.currency(), now));
        audit.append(auditRecord(context, operation.id(), "PAYMENT_OPERATION_INITIATED", correlationId, now));
        idempotency.recordResult(claim, new IdempotencyResultReference(IDEMPOTENCY_RESULT, operation.id()));
        return FundingProjection.operation(operation.toRecord(), buyerAdmin, mode.resolve());
    }

    private FundingReadDtos.PaymentOperationView replay(IdempotencyResultReference reference, boolean buyerAdmin) {
        if (!IDEMPOTENCY_RESULT.equals(reference.type())) {
            throw new IllegalStateException("Unexpected idempotency result type");
        }
        FundingRepository.OperationLookup current = funding.findOperationById(reference.id())
                .orElseThrow(() -> new IllegalStateException("Replayed payment operation is unavailable"));
        return FundingProjection.operation(current.operation(), buyerAdmin, mode.resolve());
    }

    private AuditRecord auditRecord(OperationContext context, UUID operationId, String action, UUID correlationId,
            Instant now) {
        return new AuditRecord(UUID.randomUUID(), context.tenantId(), context.authenticatedUserId(),
                context.activeLegalEntityId(), AUDIT_SUBJECT, operationId, action, correlationId, null, now);
    }

    private IdempotencyRequest idempotencyRequest(OperationContext context, UUID fundingUnitId,
            long expectedVersion, UUID key) {
        try {
            Map<String, Object> canonicalRequest = new LinkedHashMap<>();
            canonicalRequest.put("actorEntity", context.activeLegalEntityId().toString());
            canonicalRequest.put("fundingUnitId", fundingUnitId.toString());
            canonicalRequest.put("expectedVersion", expectedVersion);
            return new IdempotencyRequest(context.authenticatedUserId(), context.tenantId(), IDEMPOTENCY_OPERATION,
                    key, hasher.hash(json.writeValueAsString(canonicalRequest)));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot canonicalize payment operation initiate request", exception);
        }
    }

    private static void requireOperation(OperationContext context) {
        if (context.requestedOperation() != RequestedOperation.FUNDING_UNIT_PAYMENT_OPERATION_INITIATE) {
            throw new IllegalArgumentException("Operation context does not match payment operation initiate");
        }
    }
}
