package com.m4trust.coreapi.ratification;

import java.util.Optional;
import java.util.UUID;

import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.payment.SettlementSourcePorts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Settlement-owned ratification read boundary that accepts schema v2 snapshots
 * with disputeWindowDays without weakening the dedicated package read path.
 */
@Service
class SettlementRatificationSourceAdapter implements SettlementSourcePorts.RatificationTarget {

    private final RatificationRepository packages;
    private final ObjectMapper json;

    SettlementRatificationSourceAdapter(RatificationRepository packages, ObjectMapper json) {
        this.packages = packages;
        this.json = json;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SettlementSourcePorts.RatificationSnapshot> findForProjection(UUID dealId, UUID packageId) {
        return findRatifiedPackage(null, dealId, packageId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SettlementSourcePorts.RatificationSnapshot> findRatifiedPackage(
            OperationContext context, UUID dealId, UUID packageId) {
        return packages.findByDealAndId(dealId, packageId).map(record -> {
            Integer disputeWindowDays = null;
            try {
                JsonNode raw = json.readTree(record.canonicalSnapshot());
                int schemaVersion = raw.path("schemaVersion").asInt(0);
                if (schemaVersion == 2 && raw.hasNonNull("disputeWindowDays")) {
                    disputeWindowDays = raw.get("disputeWindowDays").asInt();
                }
                return new SettlementSourcePorts.RatificationSnapshot(schemaVersion, record.status().name(),
                        disputeWindowDays);
            } catch (Exception exception) {
                throw new IllegalStateException("Ratification snapshot is unavailable for settlement", exception);
            }
        });
    }
}
