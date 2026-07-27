package com.m4trust.coreapi.integration.infra.fulfillment;

import com.m4trust.coreapi.fulfillment.domain.port.VideoAnalysisCommandEnqueuePort;
import com.m4trust.coreapi.integration.api.port.TransactionalOutbox;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class VideoAnalysisCommandEnqueueAdapter implements VideoAnalysisCommandEnqueuePort {

  private final TransactionalOutbox outbox;

  VideoAnalysisCommandEnqueueAdapter(TransactionalOutbox outbox) {
    this.outbox = outbox;
  }

  @Override
  public UUID enqueueRequested(String serializedEvent) {
    return outbox.enqueue(
        REQUESTED_EVENT_TYPE, COMMANDS_EXCHANGE, REQUESTED_ROUTING_KEY, serializedEvent);
  }
}
