package com.m4trust.coreapi.deal.api.port;

import com.m4trust.coreapi.deal.domain.*;
import com.m4trust.coreapi.organization.domain.OperationContext;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Deal-owned boundary used by the document workflow while its transaction is active. */
public interface DealDocumentMutationPort {

  Optional<LockedDealDocumentTarget> lockForDocumentMutation(OperationContext context, UUID dealId);

  void repointCurrentDocument(UUID dealId, UUID documentId, Instant changedAt);

  record LockedDealDocumentTarget(
      UUID dealId,
      UUID tenantId,
      UUID currentDocumentId,
      UUID currentPackageId,
      boolean initiator,
      boolean acceptsDocuments) {}
}
