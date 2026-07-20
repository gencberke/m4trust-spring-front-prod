package com.m4trust.coreapi.ratification;

import java.util.List;
import java.util.UUID;

import tools.jackson.databind.ObjectMapper;

/** Test-only helper for seeding valid ratification package rows outside this package. */
public final class RatificationPackageSeedSupport {

    private RatificationPackageSeedSupport() {
    }

    public record SeededSnapshot(UUID snapshotId, String serializedSnapshot, String contentHash) {
    }

    public static SeededSnapshot seedSnapshot(ObjectMapper objectMapper, UUID dealId, UUID tenantId,
            UUID buyerEntityId, UUID sellerEntityId, UUID packageId, String reference, String documentSha256) {
        RatificationSnapshotAssembler assembler = new RatificationSnapshotAssembler(objectMapper,
                new CanonicalSnapshotHasher());
        UUID documentId = UUID.randomUUID();
        UUID ruleSetId = UUID.randomUUID();
        RatificationSourcePorts.Target target = new RatificationSourcePorts.Target(dealId, tenantId, "ACTIVE", 3,
                reference, "Deal", true,
                new RatificationSourcePorts.Party(buyerEntityId, "Buyer Co"),
                new RatificationSourcePorts.Party(sellerEntityId, "Seller Co"),
                documentId, ruleSetId, packageId);
        RatificationSnapshotAssembler.Result assembled = assembler.assemble(target,
                new RatificationSourcePorts.Document(documentId, dealId, "v1", documentSha256),
                new RatificationSourcePorts.RuleSet(ruleSetId, dealId, 1, List.of()),
                5000L, "TRY");
        return new SeededSnapshot(UUID.randomUUID(), assembled.serializedSnapshot(), assembled.contentHash());
    }
}
