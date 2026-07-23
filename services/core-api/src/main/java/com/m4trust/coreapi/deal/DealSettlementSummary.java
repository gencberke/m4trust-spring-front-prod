package com.m4trust.coreapi.deal;

import java.util.UUID;

record DealSettlementSummary(UUID settlementId, String status, UUID currentReleaseOperationId) {
}
