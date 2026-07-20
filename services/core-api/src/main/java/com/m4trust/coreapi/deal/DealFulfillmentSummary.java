package com.m4trust.coreapi.deal;

import java.util.UUID;

record DealFulfillmentSummary(
        String status,
        UUID fulfillmentId,
        UUID currentEvidenceSubmissionId) {
}
