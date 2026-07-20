package com.m4trust.coreapi.fulfillment;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class FulfillmentProjectionService implements FulfillmentProjectionPort {

    private final FulfillmentRepository fulfillmentRepository;
    private final EvidenceSubmissionRepository evidenceRepository;

    FulfillmentProjectionService(FulfillmentRepository fulfillmentRepository,
            EvidenceSubmissionRepository evidenceRepository) {
        this.fulfillmentRepository = fulfillmentRepository;
        this.evidenceRepository = evidenceRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Summary summarize(UUID dealId) {
        return fulfillmentRepository.findByDealId(dealId)
                .map(record -> {
                    UUID currentId = evidenceRepository
                            .findCurrentSubmittedByFulfillmentId(record.id())
                            .map(EvidenceSubmission.EvidenceSubmissionRecord::id)
                            .orElse(null);
                    return new Summary(record.status(), record.id(), currentId);
                })
                .orElse(null);
    }
}
