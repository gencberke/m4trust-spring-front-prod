package com.m4trust.coreapi.fulfillment.domain.port;

import com.m4trust.coreapi.fulfillment.domain.*;
import java.util.UUID;

/**
 * Fulfillment-owned projection port consumed by {@code deal.DealService}. The Deal module reaches
 * fulfillment data only through this narrow port and fulfillment never reads DealRepository.
 */
public interface FulfillmentProjectionPort {

  Summary summarize(UUID dealId);

  record Summary(
      FulfillmentStatus status,
      UUID fulfillmentId,
      UUID currentEvidenceSubmissionId,
      EvidencePolicy evidencePolicy,
      boolean hasEvidenceSubmission) {}
}
