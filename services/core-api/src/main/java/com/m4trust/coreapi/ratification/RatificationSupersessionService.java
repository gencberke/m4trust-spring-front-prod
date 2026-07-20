package com.m4trust.coreapi.ratification;

import java.time.Instant;
import java.util.UUID;

import com.m4trust.coreapi.audit.AuditAppendPort;
import com.m4trust.coreapi.audit.AuditRecord;
import com.m4trust.coreapi.organization.OperationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Invalidates only the caller's already-locked Deal current package. */
@Service
class RatificationSupersessionService implements RatificationSupersessionPort {
    private static final String AUDIT_ACTION = "RATIFICATION_PACKAGE_SUPERSEDED";

    private final RatificationRepository packages;
    private final AuditAppendPort audit;

    RatificationSupersessionService(RatificationRepository packages, AuditAppendPort audit) {
        this.packages = packages;
        this.audit = audit;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void supersedePending(
            OperationContext context,
            UUID dealId,
            UUID currentPackageId,
            UUID correlationId,
            Instant occurredAt) {
        if (currentPackageId == null) {
            return;
        }
        RatificationRepository.PackageRecord current = packages
                .findByDealAndIdForUpdate(dealId, currentPackageId)
                .orElseThrow(InvariantViolation::new);
        if (current.status() == RatificationPackageStatus.REJECTED
                || current.status() == RatificationPackageStatus.SUPERSEDED) {
            return;
        }
        if (current.status() == RatificationPackageStatus.RATIFIED) {
            throw new InvariantViolation();
        }

        RatificationPackage packageState = RatificationPackage.rehydrate(current);
        long previousVersion = packageState.version();
        packageState.supersede(previousVersion);
        if (!packages.updateStatus(packageState.toRecord(), previousVersion)) {
            throw new Stale();
        }
        audit.append(new AuditRecord(
                UUID.randomUUID(), context.tenantId(), context.authenticatedUserId(),
                context.activeLegalEntityId(), "RATIFICATION_PACKAGE", currentPackageId,
                AUDIT_ACTION, correlationId, null, occurredAt));
    }
}
