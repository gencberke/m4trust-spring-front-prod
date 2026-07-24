package com.m4trust.coreapi.fulfillment;

/** Ratified evidence requirement; persisted on fulfillment at start. */
public enum EvidencePolicy {
    REQUIRED,
    NOT_REQUIRED;

    public static EvidencePolicy effectiveFromSnapshot(int schemaVersion, String evidencePolicy) {
        if (schemaVersion == 3) {
            if (evidencePolicy == null) {
                throw new IllegalArgumentException("schemaVersion 3 requires evidencePolicy");
            }
            return EvidencePolicy.valueOf(evidencePolicy);
        }
        return REQUIRED;
    }

    public static EvidencePolicy parse(String value) {
        return EvidencePolicy.valueOf(value);
    }
}
