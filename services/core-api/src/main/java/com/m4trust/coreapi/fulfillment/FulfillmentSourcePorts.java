package com.m4trust.coreapi.fulfillment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.m4trust.coreapi.organization.OperationContext;

/**
 * Consumer-owned contract toward {@code deal}; the {@code deal} module
 * implements this port without fulfillment ever reading DealRepository
 * (ADR-003 §23).
 */
public final class FulfillmentSourcePorts {
    private FulfillmentSourcePorts() { }

    public interface DealTarget {

        /** Read-only visibility + status lookup, used by start/read paths. */
        Optional<Target> findVisible(OperationContext context, UUID dealId);

        /** Locks the Deal row; used only by fulfillment start. */
        Optional<Target> lockVisibleForStart(OperationContext context, UUID dealId);
    }

    /**
     * @param fundingStatus Deal-level funding projection, e.g. "FUNDED".
     * @param ratifiedPackageId The current RATIFIED package id, or null.
     * @param buyerLegalEntityId The Deal's buyer entity id.
     * @param sellerLegalEntityId The Deal's seller entity id.
     * @param ruleReferences DELIVERY/QUALITY rule references from the ratified package.
     */
    public record Target(
            UUID dealId,
            UUID tenantId,
            String status,
            long version,
            UUID buyerLegalEntityId,
            UUID sellerLegalEntityId,
            String fundingStatus,
            UUID ratifiedPackageId,
            List<MilestoneRuleReference> ruleReferences) { }
}
