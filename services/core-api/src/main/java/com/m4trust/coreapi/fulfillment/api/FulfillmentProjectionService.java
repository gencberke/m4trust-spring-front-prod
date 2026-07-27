package com.m4trust.coreapi.fulfillment.api;

import com.m4trust.coreapi.fulfillment.api.dto.*;
import com.m4trust.coreapi.fulfillment.domain.*;
import com.m4trust.coreapi.fulfillment.domain.port.*;
import com.m4trust.coreapi.fulfillment.infra.persistence.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FulfillmentProjectionService implements FulfillmentProjectionPort {

  private final FulfillmentRepository fulfillmentRepository;
  private final EvidenceSubmissionRepository evidenceRepository;

  public FulfillmentProjectionService(
      FulfillmentRepository fulfillmentRepository,
      EvidenceSubmissionRepository evidenceRepository) {
    this.fulfillmentRepository = fulfillmentRepository;
    this.evidenceRepository = evidenceRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public Summary summarize(UUID dealId) {
    return fulfillmentRepository
        .findByDealId(dealId)
        .map(
            record -> {
              UUID currentId =
                  evidenceRepository
                      .findCurrentSubmittedByFulfillmentId(record.id())
                      .map(EvidenceSubmission.EvidenceSubmissionRecord::id)
                      .orElse(null);
              boolean hasEvidence = evidenceRepository.existsByFulfillmentId(record.id());
              return new Summary(
                  record.status(), record.id(), currentId, record.evidencePolicy(), hasEvidence);
            })
        .orElse(null);
  }
}
