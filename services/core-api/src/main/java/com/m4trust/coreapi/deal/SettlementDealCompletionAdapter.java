package com.m4trust.coreapi.deal;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.m4trust.coreapi.payment.SettlementDealCompletionPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class SettlementDealCompletionAdapter implements SettlementDealCompletionPort {

    private final DealRepository deals;
    private final Clock clock;

    SettlementDealCompletionAdapter(DealRepository deals, Clock clock) {
        this.deals = deals;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean complete(UUID dealId, long expectedVersion) {
        return deals.findByIdForUpdate(dealId).map(record -> {
            if (record.status() != DealStatus.ACTIVE || record.version() != expectedVersion) {
                return false;
            }
            Instant now = clock.instant();
            DealStatus next = record.status().complete();
            return deals.completeDeal(dealId, DealStatus.ACTIVE, next, expectedVersion, now);
        }).orElse(false);
    }
}
