package com.m4trust.coreapi.integration.infra.fulfillment;

import com.m4trust.coreapi.fulfillment.domain.port.VideoAnalysisTerminalInboxPort;
import com.m4trust.coreapi.integration.api.port.TransactionalInbox;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class VideoAnalysisTerminalInboxAdapter implements VideoAnalysisTerminalInboxPort {

  private final TransactionalInbox inbox;

  VideoAnalysisTerminalInboxAdapter(TransactionalInbox inbox) {
    this.inbox = inbox;
  }

  @Override
  public boolean recordIfNew(UUID eventId, String eventType) {
    return inbox.recordIfNew(eventId, eventType);
  }
}
