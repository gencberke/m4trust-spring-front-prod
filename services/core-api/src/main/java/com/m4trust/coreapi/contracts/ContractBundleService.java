package com.m4trust.coreapi.contracts;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public final class ContractBundleService {

    private final ReleaseIdentityProperties releaseIdentity;
    private final ContractBundleDigest.BundleComputation computation;

    @Autowired
    public ContractBundleService(ReleaseIdentityProperties releaseIdentity) {
        this(new ContractBundleDigest(), releaseIdentity);
    }

    ContractBundleService(ContractBundleDigest digestCalculator,
            ReleaseIdentityProperties releaseIdentity) {
        this.releaseIdentity = releaseIdentity;
        this.computation = digestCalculator.compute();
    }

    public ContractBundleMetadata metadata() {
        return new ContractBundleMetadata(
                "core",
                releaseIdentity.releaseRevision(),
                computation.digest(),
                computation.files());
    }

    public String digest() {
        return computation.digest();
    }
}
