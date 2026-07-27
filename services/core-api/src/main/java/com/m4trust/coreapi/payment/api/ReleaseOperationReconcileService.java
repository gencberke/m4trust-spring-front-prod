package com.m4trust.coreapi.payment.api;

import com.m4trust.coreapi.audit.domain.AuditRecord;
import com.m4trust.coreapi.audit.domain.port.AuditAppendPort;
import com.m4trust.coreapi.idempotency.domain.IdempotencyClaim;
import com.m4trust.coreapi.idempotency.domain.IdempotencyRequest;
import com.m4trust.coreapi.idempotency.domain.IdempotencyResultReference;
import com.m4trust.coreapi.idempotency.domain.IdempotencyService;
import com.m4trust.coreapi.organization.domain.OperationContext;
import com.m4trust.coreapi.organization.domain.RequestedOperation;
import com.m4trust.coreapi.payment.api.dto.*;
import com.m4trust.coreapi.payment.domain.*;
import com.m4trust.coreapi.payment.infra.*;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Service
class ReleaseOperationReconcileService {
  private static final String IDEMPOTENCY_OPERATION = "RELEASE_OPERATION_RECONCILE";
  private static final String IDEMPOTENCY_RESULT = "RELEASE_OPERATION";
  private static final String AUDIT_SUBJECT = "RELEASE_OPERATION";

  private final SettlementSourcePorts.DealTarget deals;
  private final SettlementRepository settlements;
  private final IdempotencyService idempotency;
  private final AuditAppendPort audit;
  private final TransactionTemplate transactions;
  private final Clock clock;
  private final PaymentCanonicalHasher hasher;
  private final ObjectMapper json;
  private final PaymentProviderModeResolver mode;

  ReleaseOperationReconcileService(
      SettlementSourcePorts.DealTarget deals,
      SettlementRepository settlements,
      IdempotencyService idempotency,
      AuditAppendPort audit,
      TransactionTemplate transactions,
      Clock clock,
      PaymentCanonicalHasher hasher,
      ObjectMapper json,
      PaymentProviderModeResolver mode) {
    this.deals = deals;
    this.settlements = settlements;
    this.idempotency = idempotency;
    this.audit = audit;
    this.transactions = transactions;
    this.clock = clock;
    this.hasher = hasher;
    this.json = json;
    this.mode = mode;
  }

  ReleaseOperationView reconcile(
      OperationContext context,
      UUID operationId,
      long expectedVersion,
      UUID idempotencyKey,
      UUID correlationId) {
    requireOperation(context);
    IdempotencyRequest request =
        idempotencyRequest(context, operationId, expectedVersion, idempotencyKey);
    ReleaseOperationView result =
        transactions.execute(
            status ->
                reconcileInTransaction(
                    context, operationId, expectedVersion, request, correlationId));
    if (result == null) {
      throw new IllegalStateException("Transaction returned no result");
    }
    return result;
  }

  private ReleaseOperationView reconcileInTransaction(
      OperationContext context,
      UUID operationId,
      long expectedVersion,
      IdempotencyRequest idempotencyRequest,
      UUID correlationId) {
    SettlementRecords.ReleaseOperationLookup lookup =
        settlements
            .findOperationByIdForUpdate(operationId)
            .orElseThrow(SettlementExceptions.ReleaseOperationNotFound::new);
    SettlementSourcePorts.DealSnapshot deal =
        deals
            .findVisible(context, lookup.dealId())
            .orElseThrow(SettlementExceptions.ReleaseOperationNotFound::new);
    IdempotencyClaim claim = idempotency.claim(idempotencyRequest);
    boolean buyerAdmin = SettlementProjection.isBuyerAdmin(context, deal);
    if (claim.isReplay()) {
      return replay(claim.resultReference(), buyerAdmin);
    }
    if (!buyerAdmin) {
      throw new SettlementExceptions.MutationForbidden();
    }
    ReleaseOperation operation = ReleaseOperation.rehydrate(lookup.operation());
    if (operation.status() != ReleaseOperationStatus.RECONCILIATION_REQUIRED) {
      throw new SettlementExceptions.ReconciliationUnavailable();
    }
    if (operation.version() != expectedVersion) {
      throw new SettlementExceptions.ReleaseOperationStaleVersion();
    }
    SettlementRecords.SettlementRecord settlementRecord =
        settlements
            .findByIdForUpdate(lookup.operation().settlementId())
            .orElseThrow(SettlementExceptions.SettlementNotFound::new);
    if (settlementRecord.status().terminal()) {
      throw new SettlementExceptions.AlreadyTerminal();
    }
    Instant now = clock.instant();
    settlements.insertDispatch(
        new SettlementRecords.DispatchRecord(
            UUID.randomUUID(),
            operation.id(),
            SettlementRecords.DispatchType.RECONCILE,
            operation.providerKey(),
            lookup.amountMinor(),
            lookup.currency(),
            now));
    audit.append(
        auditRecord(
            context, operation.id(), "RELEASE_OPERATION_RECONCILE_REQUESTED", correlationId, now));
    idempotency.recordResult(
        claim, new IdempotencyResultReference(IDEMPOTENCY_RESULT, operation.id()));
    PaymentProviderMode providerMode = mode.resolve();
    SettlementProjection.requireNonNull(providerMode);
    return SettlementProjection.operation(operation.toRecord(), true, providerMode);
  }

  private ReleaseOperationView replay(IdempotencyResultReference reference, boolean buyerAdmin) {
    if (!IDEMPOTENCY_RESULT.equals(reference.type())) {
      throw new IllegalStateException("Unexpected idempotency result type");
    }
    SettlementRecords.ReleaseOperationLookup current =
        settlements
            .findOperationById(reference.id())
            .orElseThrow(
                () -> new IllegalStateException("Replayed release operation is unavailable"));
    PaymentProviderMode providerMode = mode.resolve();
    SettlementProjection.requireNonNull(providerMode);
    return SettlementProjection.operation(current.operation(), buyerAdmin, providerMode);
  }

  private AuditRecord auditRecord(
      OperationContext context, UUID operationId, String action, UUID correlationId, Instant now) {
    return new AuditRecord(
        UUID.randomUUID(),
        context.tenantId(),
        context.authenticatedUserId(),
        context.activeLegalEntityId(),
        AUDIT_SUBJECT,
        operationId,
        action,
        correlationId,
        null,
        now);
  }

  private IdempotencyRequest idempotencyRequest(
      OperationContext context, UUID operationId, long expectedVersion, UUID key) {
    try {
      Map<String, Object> canonicalRequest = new LinkedHashMap<>();
      canonicalRequest.put("actorEntity", context.activeLegalEntityId().toString());
      canonicalRequest.put("releaseOperationId", operationId.toString());
      canonicalRequest.put("expectedVersion", expectedVersion);
      return new IdempotencyRequest(
          context.authenticatedUserId(),
          context.tenantId(),
          IDEMPOTENCY_OPERATION,
          key,
          hasher.hash(json.writeValueAsString(canonicalRequest)));
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot canonicalize release reconcile request", exception);
    }
  }

  private static void requireOperation(OperationContext context) {
    if (context.requestedOperation() != RequestedOperation.RELEASE_OPERATION_RECONCILE) {
      throw new IllegalArgumentException("Operation context does not match release reconcile");
    }
  }
}
