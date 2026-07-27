package com.m4trust.coreapi.integration.api.port;

import java.util.UUID;

/** Records an inbound event in the transaction that applies its business mutation. */
public interface TransactionalInbox {

  boolean recordIfNew(UUID eventId, String eventType);
}
