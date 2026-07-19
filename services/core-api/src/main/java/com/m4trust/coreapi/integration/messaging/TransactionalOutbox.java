package com.m4trust.coreapi.integration.messaging;

import java.util.UUID;

/** Appends transport events to the transaction owned by the calling application service. */
public interface TransactionalOutbox {

    UUID enqueue(String eventType, String exchangeName, String routingKey, String payload);
}
