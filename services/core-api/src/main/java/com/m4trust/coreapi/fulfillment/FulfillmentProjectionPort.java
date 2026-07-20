package com.m4trust.coreapi.fulfillment;

import java.util.Optional;
import java.util.UUID;

/**
 * Fulfillment-owned projection port consumed by {@code deal.DealService}.
 * The Deal module reaches fulfillment data only through this narrow port and
 * fulfillment never reads DealRepository (ADR-003 §23).
 */
public interface FulfillmentProjectionPort {

    Summary summarize(UUID dealId);

    record Summary(
            FulfillmentStatus status,
            UUID fulfillmentId,
            UUID currentEvidenceSubmissionId) {
    }
}
