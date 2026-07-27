package com.m4trust.coreapi.deal.api.port;

import com.m4trust.coreapi.deal.domain.*;
import com.m4trust.coreapi.organization.domain.OperationContext;
import java.util.Optional;
import java.util.UUID;

/**
 * Deal-owned boundary used by the document module for non-mutating document reads (history listing
 * and download-link minting). A present result means the active legal entity is a current Deal
 * participant; participant visibility alone never grants document mutation authority.
 */
public interface DealDocumentReadPort {

  Optional<DealDocumentVisibility> findVisibility(OperationContext context, UUID dealId);

  record DealDocumentVisibility(
      UUID dealId, UUID tenantId, UUID currentDocumentId, boolean initiator) {}
}
