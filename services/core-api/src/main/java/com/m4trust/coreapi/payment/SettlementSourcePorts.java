package com.m4trust.coreapi.payment;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.m4trust.coreapi.organization.OperationContext;

/**
 * Consumer-owned narrow ports for settlement eligibility and release (ADR-014 §2.6).
 * Payment never reads foreign repositories; deal, fulfillment, ratification, and
 * casework modules implement these adapters.
 */
public final class SettlementSourcePorts {

    private SettlementSourcePorts() { }

    public interface DealTarget {

        Optional<DealSnapshot> findVisible(OperationContext context, UUID dealId);

        Optional<DealSnapshot> lockVisible(OperationContext context, UUID dealId);

        /** Participant visibility is enforced by the caller (Deal detail path). */
        Optional<DealSnapshot> findForProjection(UUID dealId);
    }

    public record DealSnapshot(
            UUID dealId,
            UUID tenantId,
            String status,
            long version,
            UUID buyerLegalEntityId,
            UUID sellerLegalEntityId,
            UUID ratifiedPackageId) { }

    public interface FulfillmentTarget {

        Optional<FulfillmentSnapshot> findVisible(OperationContext context, UUID dealId);

        Optional<FulfillmentSnapshot> lockVisible(OperationContext context, UUID dealId);

        Optional<FulfillmentSnapshot> findForProjection(UUID dealId);
    }

    public record FulfillmentSnapshot(
            UUID fulfillmentId,
            String status,
            long version,
            Instant completedAt) { }

    public interface RatificationTarget {

        Optional<RatificationSnapshot> findRatifiedPackage(OperationContext context, UUID dealId, UUID packageId);

        Optional<RatificationSnapshot> findForProjection(UUID dealId, UUID packageId);
    }

    /**
     * @param disputeWindowDays Present for schemaVersion=2 or schemaVersion=3 ratified snapshots.
     */
    public record RatificationSnapshot(int schemaVersion, String status, Integer disputeWindowDays) { }

    public interface CaseworkTarget {

        boolean hasActiveDispute(UUID dealId);

        void lockActiveDisputesInOrder(UUID dealId);
    }
}
