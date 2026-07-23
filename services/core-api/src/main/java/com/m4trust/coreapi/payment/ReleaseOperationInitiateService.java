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

@Service
class ReleaseOperationInitiateService {
    private static final String IDEMPOTENCY_OPERATION = "SETTLEMENT_RELEASE_INITIATE";
    private static final String IDEMPOTENCY_RESULT = "RELEASE_OPERATION";
    private static final String AUDIT_SUBJECT = "RELEASE_OPERATION";

    private final SettlementSourcePorts.DealTarget deals;
    private final SettlementSourcePorts.FulfillmentTarget fulfillments;
    private final SettlementSourcePorts.RatificationTarget ratifications;
    private final SettlementSourcePorts.CaseworkTarget casework;
    private final FundingRepository funding;
    private final SettlementRepository settlements;
    private final SettlementEligibilityEvaluator eligibility;
    private final IdempotencyService idempotency;
    private final AuditAppendPort audit;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final PaymentCanonicalHasher hasher;
    private final ObjectMapper json;
    private final PaymentProviderModeResolver mode;

    ReleaseOperationInitiateService(SettlementSourcePorts.DealTarget deals,
            SettlementSourcePorts.FulfillmentTarget fulfillments,
            SettlementSourcePorts.RatificationTarget ratifications,
            SettlementSourcePorts.CaseworkTarget casework, FundingRepository funding,
            SettlementRepository settlements, SettlementEligibilityEvaluator eligibility,
            IdempotencyService idempotency, AuditAppendPort audit, TransactionTemplate transactions,
            Clock clock, PaymentCanonicalHasher hasher, ObjectMapper json, PaymentProviderModeResolver mode) {
        this.deals = deals;
        this.fulfillments = fulfillments;
        this.ratifications = ratifications;
        this.casework = casework;
        this.funding = funding;
        this.settlements = settlements;
        this.eligibility = eligibility;
        this.idempotency = idempotency;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
        this.hasher = hasher;
        this.json = json;
        this.mode = mode;
    }

    SettlementReadDtos.ReleaseOperationView initiate(OperationContext context, UUID dealId,
            long expectedDealVersion, long expectedSettlementVersion, long expectedFulfillmentVersion,
            long expectedFundingUnitVersion, UUID idempotencyKey, UUID correlationId) {
        requireOperation(context);
        IdempotencyRequest request = idempotencyRequest(context, dealId, expectedDealVersion,
                expectedSettlementVersion, expectedFulfillmentVersion, expectedFundingUnitVersion, idempotencyKey);
        SettlementReadDtos.ReleaseOperationView result = transactions.execute(status -> initiateInTransaction(
                context, dealId, expectedDealVersion, expectedSettlementVersion, expectedFulfillmentVersion,
                expectedFundingUnitVersion, request, correlationId));
        if (result == null) {
            throw new IllegalStateException("Transaction returned no result");
        }
        return result;
    }

    private SettlementReadDtos.ReleaseOperationView initiateInTransaction(OperationContext context, UUID dealId,
            long expectedDealVersion, long expectedSettlementVersion, long expectedFulfillmentVersion,
            long expectedFundingUnitVersion, IdempotencyRequest idempotencyRequest, UUID correlationId) {
        SettlementSourcePorts.DealSnapshot deal = deals.lockVisible(context, dealId)
                .orElseThrow(SettlementExceptions.SettlementNotFound::new);
        IdempotencyClaim claim = idempotency.claim(idempotencyRequest);
        boolean buyerAdmin = SettlementProjection.isBuyerAdmin(context, deal);
        if (claim.isReplay()) {
            return replay(claim.resultReference(), buyerAdmin);
        }
        if (!buyerAdmin) {
            throw new SettlementExceptions.MutationForbidden();
        }
        if (!"ACTIVE".equals(deal.status())) {
            throw new SettlementExceptions.DealStateConflict();
        }
        if (deal.version() != expectedDealVersion) {
            throw new SettlementExceptions.DealStaleVersion();
        }
        casework.lockActiveDisputesInOrder(dealId);
        if (casework.hasActiveDispute(dealId)) {
            throw new SettlementExceptions.ActiveDispute();
        }
        SettlementSourcePorts.FulfillmentSnapshot fulfillment = fulfillments.lockVisible(context, dealId)
                .orElseThrow(SettlementExceptions.SettlementNotFound::new);
        if (fulfillment.version() != expectedFulfillmentVersion) {
            throw new SettlementExceptions.FulfillmentStaleVersion();
        }
        FundingRepository.PlanRecord plan = funding.findPlanByDeal(dealId)
                .orElseThrow(SettlementExceptions.SettlementNotFound::new);
        FundingRepository.UnitRecord unit = funding.findUnitByPlan(plan.id())
                .orElseThrow(SettlementExceptions.SettlementNotFound::new);
        FundingRepository.UnitLookup unitLookup = funding.findUnitByIdForUpdate(unit.id())
                .orElseThrow(SettlementExceptions.SettlementNotFound::new);
        if (unitLookup.unit().version() != expectedFundingUnitVersion) {
            throw new SettlementExceptions.FundingUnitStaleVersion();
        }
        SettlementRepository.SettlementRecord settlementRecord = settlements.findByDealIdForUpdate(dealId)
                .orElseThrow(SettlementExceptions.SettlementNotFound::new);
        if (settlementRecord.version() != expectedSettlementVersion) {
            throw new SettlementExceptions.SettlementStaleVersion();
        }
        if (settlementRecord.status().terminal()) {
            throw new SettlementExceptions.AlreadyTerminal();
        }
        if (settlements.findOperationBySettlementForUpdate(settlementRecord.id()).isPresent()) {
            throw new SettlementExceptions.OperationAlreadyExists();
        }
        SettlementSourcePorts.RatificationSnapshot ratification = deal.ratifiedPackageId() == null
                ? null
                : ratifications.findRatifiedPackage(context, dealId, deal.ratifiedPackageId()).orElse(null);
        SettlementEligibilityEvaluator.Evaluation evaluation = eligibility.evaluate(context,
                new SettlementEligibilityEvaluator.Context(deal, fulfillment, ratification, unitLookup.unit(),
                        false, settlementRecord, null));
        if (evaluation.disputeWindowDays() == null) {
            throw new SettlementExceptions.ContractualWindowMissing();
        }
        if (!evaluation.releaseEligible()) {
            if (fulfillment.completedAt() != null && evaluation.releaseEligibleAt() != null
                    && clock.instant().isBefore(evaluation.releaseEligibleAt())) {
                throw new SettlementExceptions.DisputeWindowNotElapsed();
            }
            throw new SettlementExceptions.SettlementNotFound();
        }
        if (settlementRecord.status() != SettlementStatus.READY) {
            throw new SettlementExceptions.SettlementNotFound();
        }
        Instant now = clock.instant();
        Settlement settlement = Settlement.rehydrate(settlementRecord);
        long previousSettlementVersion = settlement.version();
        try {
            settlement.beginRelease(previousSettlementVersion, now);
        } catch (Settlement.StaleVersion exception) {
            throw new SettlementExceptions.SettlementStaleVersion();
        }
        if (!settlements.updateSettlement(settlement.toRecord(), previousSettlementVersion)) {
            throw new SettlementExceptions.SettlementStaleVersion();
        }
        UUID providerKey = UUID.randomUUID();
        ReleaseOperation operation = ReleaseOperation.create(UUID.randomUUID(), settlement.id(), providerKey, now);
        settlements.insertOperation(operation.toRecord());
        settlements.insertDispatch(new SettlementRepository.DispatchRecord(UUID.randomUUID(), operation.id(),
                SettlementRepository.DispatchType.INITIATE, providerKey, unitLookup.unit().amountMinor(),
                unitLookup.unit().currency(), now));
        audit.append(auditRecord(context, operation.id(), "RELEASE_OPERATION_INITIATED", correlationId, now));
        idempotency.recordResult(claim, new IdempotencyResultReference(IDEMPOTENCY_RESULT, operation.id()));
        PaymentProviderMode providerMode = mode.resolve();
        SettlementProjection.requireNonNull(providerMode);
        return SettlementProjection.operation(operation.toRecord(), true, providerMode);
    }

    private SettlementReadDtos.ReleaseOperationView replay(IdempotencyResultReference reference, boolean buyerAdmin) {
        if (!IDEMPOTENCY_RESULT.equals(reference.type())) {
            throw new IllegalStateException("Unexpected idempotency result type");
        }
        SettlementRepository.ReleaseOperationLookup current = settlements.findOperationById(reference.id())
                .orElseThrow(() -> new IllegalStateException("Replayed release operation is unavailable"));
        PaymentProviderMode providerMode = mode.resolve();
        SettlementProjection.requireNonNull(providerMode);
        return SettlementProjection.operation(current.operation(), buyerAdmin, providerMode);
    }

    private AuditRecord auditRecord(OperationContext context, UUID operationId, String action, UUID correlationId,
            Instant now) {
        return new AuditRecord(UUID.randomUUID(), context.tenantId(), context.authenticatedUserId(),
                context.activeLegalEntityId(), AUDIT_SUBJECT, operationId, action, correlationId, null, now);
    }

    private IdempotencyRequest idempotencyRequest(OperationContext context, UUID dealId, long expectedDealVersion,
            long expectedSettlementVersion, long expectedFulfillmentVersion, long expectedFundingUnitVersion, UUID key) {
        try {
            Map<String, Object> canonicalRequest = new LinkedHashMap<>();
            canonicalRequest.put("actorEntity", context.activeLegalEntityId().toString());
            canonicalRequest.put("dealId", dealId.toString());
            canonicalRequest.put("expectedDealVersion", expectedDealVersion);
            canonicalRequest.put("expectedSettlementVersion", expectedSettlementVersion);
            canonicalRequest.put("expectedFulfillmentVersion", expectedFulfillmentVersion);
            canonicalRequest.put("expectedFundingUnitVersion", expectedFundingUnitVersion);
            return new IdempotencyRequest(context.authenticatedUserId(), context.tenantId(), IDEMPOTENCY_OPERATION,
                    key, hasher.hash(json.writeValueAsString(canonicalRequest)));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot canonicalize settlement release request", exception);
        }
    }

    private static void requireOperation(OperationContext context) {
        if (context.requestedOperation() != RequestedOperation.DEAL_SETTLEMENT_RELEASE) {
            throw new IllegalArgumentException("Operation context does not match settlement release");
        }
    }
}
