package com.m4trust.coreapi.integration.messaging;

import java.util.UUID;

/** Records an inbound event in the transaction that applies its business mutation. */
public interface TransactionalInbox {

    boolean recordIfNew(UUID eventId, String eventType);
}
