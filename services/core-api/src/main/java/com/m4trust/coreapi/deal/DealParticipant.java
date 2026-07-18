package com.m4trust.coreapi.deal;

import java.time.Instant;
import java.util.UUID;

record DealParticipant(UUID legalEntityId, String legalName, Instant joinedAt) {
}
