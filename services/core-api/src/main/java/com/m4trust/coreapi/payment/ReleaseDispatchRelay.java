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
 * Query-first release dispatch relay mirroring {@link PaymentDispatchRelay}
 * (ADR-014 §2.7). Gated by the same {@code app.payment.dispatch.relay}
 * configuration family.
 */
@Component
@ConditionalOnProperty(prefix = "app.payment.dispatch.relay", name = "enabled", havingValue = "true")
class ReleaseDispatchRelay {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReleaseDispatchRelay.class);
    private static final String AUDIT_SUBJECT_RELEASE = "RELEASE_OPERATION";
    private static final String AUDIT_SUBJECT_SETTLEMENT = "SETTLEMENT";
    private static final String AUDIT_SUBJECT_DEAL = "DEAL";

    private final ReleaseDispatchStore store;
    private final SettlementRepository settlements;
    private final SettlementSourcePorts.CaseworkTarget casework;
    private final SettlementDealCompletionPort dealCompletion;
    private final PaymentProviderPort provider;
    private final AuditAppendPort audit;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final PaymentDispatchRelayProperties properties;

    ReleaseDispatchRelay(ReleaseDispatchStore store, SettlementRepository settlements,
            SettlementSourcePorts.CaseworkTarget casework, SettlementDealCompletionPort dealCompletion,
            PaymentProviderPort provider, AuditAppendPort audit, TransactionTemplate transactions, Clock clock,
            PaymentDispatchRelayProperties properties) {
        this.store = store;
        this.settlements = settlements;
        this.casework = casework;
        this.dealCompletion = dealCompletion;
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

    void relayOnce() {
        for (ReleaseDispatchStore.DispatchClaim claim : store.claimAvailable(
                properties.batchSize(), properties.claimTimeout())) {
            try {
                processClaim(claim);
            } catch (RuntimeException exception) {
                LOGGER.warn("release dispatch deferred dispatchId={} operationId={}", claim.id(),
                        claim.releaseOperationId(), exception);
            }
        }
    }

    private void processClaim(ReleaseDispatchStore.DispatchClaim claim) {
        PaymentProviderPort.ReleaseProviderResult result = resolveOutsideTransaction(claim);
        applyResult(claim.releaseOperationId(), result);
        store.markCompleted(claim);
    }

    private PaymentProviderPort.ReleaseProviderResult resolveOutsideTransaction(
            ReleaseDispatchStore.DispatchClaim claim) {
        PaymentProviderPort.ProviderRequest request = new PaymentProviderPort.ProviderRequest(
                claim.providerKey().toString(), claim.amountMinor(), claim.currency());
        PaymentProviderPort.ReleaseProviderResult result = provider.queryReleaseStatus(request);
        if (result == null) {
            throw new IllegalStateException("Release provider is unavailable");
        }
        if (result.outcome() == PaymentProviderPort.ReleaseOutcome.NOT_FOUND) {
            SettlementRepository.ReleaseOperationLookup lookup = settlements.findOperationById(claim.releaseOperationId())
                    .orElseThrow(() -> new IllegalStateException("Dispatched release operation is unavailable"));
            if (casework.hasActiveDispute(lookup.dealId())) {
                return new PaymentProviderPort.ReleaseProviderResult(
                        PaymentProviderPort.ReleaseOutcome.UNCONFIRMED, null);
            }
            result = provider.initiateRelease(request);
            if (result == null) {
                throw new IllegalStateException("Release provider initiate is unavailable");
            }
        }
        return result;
    }

    private void applyResult(UUID operationId, PaymentProviderPort.ReleaseProviderResult result) {
        transactions.executeWithoutResult(status -> {
            SettlementRepository.ReleaseOperationLookup lookup = settlements.findOperationByIdForUpdate(operationId)
                    .orElseThrow(() -> new IllegalStateException("Dispatched release operation is unavailable"));
            ReleaseOperation operation = ReleaseOperation.rehydrate(lookup.operation());
            if (operation.terminal()) {
                return;
            }
            Instant now = clock.instant();
            UUID tenantId = lookup.tenantId();
            UUID dealId = lookup.dealId();
            SettlementRepository.SettlementRecord settlementRecord = settlements.findByIdForUpdate(
                    lookup.operation().settlementId()).orElseThrow();
            Settlement settlement = Settlement.rehydrate(settlementRecord);
            casework.lockActiveDisputesInOrder(dealId);
            boolean activeDispute = casework.hasActiveDispute(dealId);
            switch (result.outcome()) {
                case SIMULATED_SETTLED -> {
                    long previousOperationVersion = operation.version();
                    operation.applySimulatedSettled(result.providerReference(), now);
                    requireUpdated(settlements.updateOperation(operation.toRecord(), previousOperationVersion));
                    long previousSettlementVersion = settlement.version();
                    settlement.markSimulatedSettled(now);
                    requireUpdated(settlements.updateSettlement(settlement.toRecord(), previousSettlementVersion));
                    audit(tenantId, operation.id(), AUDIT_SUBJECT_RELEASE, "RELEASE_OPERATION_SIMULATED_SETTLED", now);
                    audit(tenantId, settlement.id(), AUDIT_SUBJECT_SETTLEMENT, "SETTLEMENT_SIMULATED_SETTLED", now);
                    if (!activeDispute) {
                        if (!dealCompletion.complete(dealId, lookup.dealVersion())) {
                            throw new IllegalStateException("Deal completion failed during release relay");
                        }
                        audit(tenantId, dealId, AUDIT_SUBJECT_DEAL, "DEAL_COMPLETED_SIMULATED_SETTLEMENT", now);
                    }
                }
                case SIMULATED_DECLINED -> {
                    long previousOperationVersion = operation.version();
                    operation.applySimulatedDeclined(result.providerReference(), now);
                    requireUpdated(settlements.updateOperation(operation.toRecord(), previousOperationVersion));
                    long previousSettlementVersion = settlement.version();
                    settlement.markFailed(now);
                    requireUpdated(settlements.updateSettlement(settlement.toRecord(), previousSettlementVersion));
                    audit(tenantId, operation.id(), AUDIT_SUBJECT_RELEASE, "RELEASE_OPERATION_SIMULATED_DECLINED", now);
                }
                case UNCONFIRMED, NOT_FOUND -> {
                    if (operation.status() != ReleaseOperationStatus.RECONCILIATION_REQUIRED) {
                        long previousOperationVersion = operation.version();
                        operation.markReconciliationRequired(now);
                        requireUpdated(settlements.updateOperation(operation.toRecord(), previousOperationVersion));
                    }
                    if (!settlement.status().terminal()) {
                        long previousSettlementVersion = settlement.version();
                        if (activeDispute) {
                            settlement.markOnHold(now);
                        }
                        requireUpdated(settlements.updateSettlement(settlement.toRecord(), previousSettlementVersion));
                    }
                }
            }
        });
    }

    private void audit(UUID tenantId, UUID subjectId, String subjectType, String action, Instant now) {
        audit.append(new AuditRecord(UUID.randomUUID(), tenantId, null, null, subjectType, subjectId, action,
                UUID.randomUUID(), null, now));
    }

    private static void requireUpdated(boolean updated) {
        if (!updated) {
            throw new IllegalStateException("Release relay result was applied to a concurrently modified row");
        }
    }
}
