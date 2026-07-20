package com.m4trust.coreapi.payment;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Deal-scoped, immutable-after-create amount/currency snapshot (ADR-010 §2.2). */
final class FundingPlan {
    static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;

    private final UUID id;
    private final UUID dealId;
    private final UUID ratificationPackageId;
    private final UUID tenantId;
    private final long amountMinor;
    private final String currency;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final long version;

    private FundingPlan(UUID id, UUID dealId, UUID ratificationPackageId, UUID tenantId,
            long amountMinor, String currency,
            Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id);
        this.dealId = Objects.requireNonNull(dealId);
        this.ratificationPackageId = Objects.requireNonNull(ratificationPackageId);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.amountMinor = amountMinor;
        this.currency = Objects.requireNonNull(currency);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.version = version;
        if (amountMinor < 1 || amountMinor > MAX_SAFE_INTEGER || !currency.matches("[A-Z]{3}") || version < 0) {
            throw new IllegalArgumentException("Invalid funding plan stable fields");
        }
    }

    static FundingPlan create(UUID id, UUID dealId, UUID ratificationPackageId, UUID tenantId,
            long amountMinor, String currency, Instant now) {
        return new FundingPlan(id, dealId, ratificationPackageId, tenantId, amountMinor, currency, now, now, 0);
    }

    static FundingPlan rehydrate(FundingRepository.PlanRecord record) {
        return new FundingPlan(record.id(), record.dealId(), record.ratificationPackageId(), record.tenantId(),
                record.amountMinor(), record.currency(), record.createdAt(), record.updatedAt(), record.version());
    }

    FundingRepository.PlanRecord toRecord() {
        return new FundingRepository.PlanRecord(id, dealId, ratificationPackageId, tenantId, amountMinor, currency,
                createdAt, updatedAt, version);
    }

    UUID id() { return id; }
    UUID dealId() { return dealId; }
    UUID ratificationPackageId() { return ratificationPackageId; }
    UUID tenantId() { return tenantId; }
    long amountMinor() { return amountMinor; }
    String currency() { return currency; }
    Instant createdAt() { return createdAt; }
    Instant updatedAt() { return updatedAt; }
    long version() { return version; }
}
