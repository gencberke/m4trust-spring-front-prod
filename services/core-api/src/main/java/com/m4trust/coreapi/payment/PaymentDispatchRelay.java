package com.m4trust.coreapi.payment;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.m4trust.coreapi.audit.AuditAppendPort;
import com.m4trust.coreapi.audit.AuditRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Claims durable payment dispatch records, closes the claiming transaction,
 * and only then calls {@link PaymentProviderPort} outside any database
 * transaction (ADR-010 §2.4). Every dispatch — INITIATE or RECONCILE — is
 * resolved query-first: {@code queryStatus} is tried with the operation's
 * unchanged provider key first; only an explicit {@code NOT_FOUND} triggers a
 * same-key {@code initiate}. This single procedure is what makes crash
 * recovery safe in both the "died before any provider call" and "died after
 * the provider call but before the local result was applied" windows: on
 * retry the same key is queried again rather than blindly re-initiated.
 */
@Component
@ConditionalOnProperty(prefix = "app.payment.dispatch.relay", name = "enabled", havingValue = "true")
class PaymentDispatchRelay {
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentDispatchRelay.class);
    private static final String AUDIT_SUBJECT = "PAYMENT_OPERATION";

    private final PaymentDispatchStore store;
    private final FundingRepository funding;
    private final PaymentProviderPort provider;
    private final AuditAppendPort audit;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final PaymentDispatchRelayProperties properties;

    PaymentDispatchRelay(PaymentDispatchStore store, FundingRepository funding, PaymentProviderPort provider,
            AuditAppendPort audit, TransactionTemplate transactions, Clock clock,
            PaymentDispatchRelayProperties properties) {
        this.store = store;
        this.funding = funding;
        this.provider = provider;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.payment.dispatch.relay.fixed-delay:5m}")
    void relay() {
        relayOnce();
    }

    /** Public so integration tests can drive dispatch deterministically instead of waiting on the schedule. */
    void relayOnce() {
        for (PaymentDispatchStore.DispatchClaim claim : store.claimAvailable(
                properties.batchSize(), properties.claimTimeout())) {
            try {
                processClaim(claim);
            } catch (RuntimeException exception) {
                // The claim expires and is retried; query-first idempotency makes a repeat safe.
                LOGGER.warn("payment dispatch deferred dispatchId={} operationId={}", claim.id(),
                        claim.paymentOperationId(), exception);
            }
        }
    }

    private void processClaim(PaymentDispatchStore.DispatchClaim claim) {
        PaymentProviderPort.ProviderResult result = resolveOutsideTransaction(claim);
        applyResult(claim.paymentOperationId(), result);
        store.markCompleted(claim);
    }

    /** Query-first with a same-key initiate fallback; never runs inside a database transaction. */
    private PaymentProviderPort.ProviderResult resolveOutsideTransaction(PaymentDispatchStore.DispatchClaim claim) {
        PaymentProviderPort.ProviderRequest request = new PaymentProviderPort.ProviderRequest(
                claim.providerKey().toString(), claim.amountMinor(), claim.currency());
        PaymentProviderPort.ProviderResult result = provider.queryStatus(request);
        if (result.outcome() == PaymentProviderPort.Outcome.NOT_FOUND) {
            result = provider.initiate(request);
        }
        return result;
    }

    private void applyResult(UUID operationId, PaymentProviderPort.ProviderResult result) {
        transactions.executeWithoutResult(status -> {
            FundingRepository.OperationLookup lookup = funding.findOperationByIdForUpdate(operationId)
                    .orElseThrow(() -> new IllegalStateException("Dispatched payment operation is unavailable"));
            PaymentOperation operation = PaymentOperation.rehydrate(lookup.operation());
            if (operation.terminal()) {
                // Already resolved by an earlier relay pass (e.g. a reclaim after a crash); idempotent no-op.
                return;
            }
            Instant now = clock.instant();
            UUID tenantId = lookup.tenantId();
            switch (result.outcome()) {
                case SUCCEEDED -> {
                    long previousVersion = operation.version();
                    operation.applySucceeded(result.providerReference(), now);
                    requireUpdated(funding.updateOperationStatus(operation.toRecord(), previousVersion));
                    fundUnit(operation.fundingUnitId(), now);
                    audit(tenantId, operation.id(), "PAYMENT_OPERATION_SUCCEEDED", now);
                }
                case DECLINED -> {
                    long previousVersion = operation.version();
                    operation.applyDeclined(result.providerReference(), now);
                    requireUpdated(funding.updateOperationStatus(operation.toRecord(), previousVersion));
                    failUnit(operation.fundingUnitId(), now);
                    audit(tenantId, operation.id(), "PAYMENT_OPERATION_DECLINED", now);
                }
                case UNCONFIRMED, NOT_FOUND -> {
                    if (operation.status() != PaymentOperationStatus.UNCONFIRMED) {
                        long previousVersion = operation.version();
                        operation.markUnconfirmed(now);
                        requireUpdated(funding.updateOperationStatus(operation.toRecord(), previousVersion));
                        audit(tenantId, operation.id(), "PAYMENT_OPERATION_UNCONFIRMED", now);
                    }
                }
            }
        });
    }

    private void fundUnit(UUID fundingUnitId, Instant now) {
        FundingRepository.UnitLookup lookup = funding.findUnitByIdForUpdate(fundingUnitId)
                .orElseThrow(() -> new IllegalStateException("Funded payment operation's unit is unavailable"));
        FundingUnit unit = FundingUnit.rehydrate(lookup.unit());
        if (unit.status() == FundingUnitStatus.FUNDED) {
            return;
        }
        long previousVersion = unit.version();
        unit.markFunded(previousVersion, now);
        requireUpdated(funding.updateUnitStatus(unit.toRecord(), previousVersion));
    }

    private void failUnit(UUID fundingUnitId, Instant now) {
        FundingRepository.UnitLookup lookup = funding.findUnitByIdForUpdate(fundingUnitId)
                .orElseThrow(() -> new IllegalStateException("Declined payment operation's unit is unavailable"));
        FundingUnit unit = FundingUnit.rehydrate(lookup.unit());
        if (unit.status() == FundingUnitStatus.FAILED) {
            return;
        }
        long previousVersion = unit.version();
        unit.markFailed(previousVersion, now);
        requireUpdated(funding.updateUnitStatus(unit.toRecord(), previousVersion));
    }

    private void audit(UUID tenantId, UUID subjectId, String action, Instant now) {
        audit.append(new AuditRecord(UUID.randomUUID(), tenantId, null, null, AUDIT_SUBJECT, subjectId, action,
                UUID.randomUUID(), null, now));
    }

    private static void requireUpdated(boolean updated) {
        if (!updated) {
            throw new IllegalStateException("Payment relay result was applied to a concurrently modified row");
        }
    }
}
