package com.m4trust.coreapi.integration.infra.messaging;

import java.util.UUID;

record OutboxClaim(
    UUID id, UUID claimToken, String exchangeName, String routingKey, String payload) {}
